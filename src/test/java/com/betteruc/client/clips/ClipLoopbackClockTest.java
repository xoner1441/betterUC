package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class ClipLoopbackClockTest {
    private static final long ORIGIN = 10_000_000_000L;

    @Test void outputPacketJitterAndInvalidTimestampsDoNotChopStereoSamples() {
        var clock = new ClipLoopbackClock();
        var fixed = new ClipAudioBuffer(15);
        var old = new ClipAudioBuffer(15);
        var expected = ByteBuffer.allocate(48000 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int p = 0; p < 100; p++) {
            byte[] pcm = new byte[480 * 4];
            var input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 480; i++) {
                input.putShort((short) (Math.sin((p * 480 + i) * 2 * Math.PI * 437 / 48000) * 12000));
                input.putShort((short) (Math.sin((p * 480 + i) * 2 * Math.PI * 701 / 48000) * 10000));
            }
            expected.put(pcm);
            long jitter = p == 0 ? 0 : p % 2 == 0 ? 3_000_000L : -3_000_000L;
            long time = ORIGIN + p * 10_000_000L;
            boolean valid = p % 7 != 6;
            long arrival = time + 20_000_000L;
            old.add(valid ? time + jitter : arrival - 10_000_000L, pcm);
            fixed.add(clock.timestamp(480, p * 480L, time + jitter, valid, false, arrival, 480), pcm);
            clock.noPacket(arrival); // Normal drain-to-empty is NOT a playback pause.
        }
        assertArrayEquals(expected.array(), fixed.slice(ORIGIN, ORIGIN + 1_000_000_000L).pcm());
        assertFalse(java.util.Arrays.equals(expected.array(), old.slice(ORIGIN, ORIGIN + 1_000_000_000L).pcm()));
    }

    @Test void firstInvalidTimestampUsesQueueDepthAndBatchedPacketsStayContiguous() {
        var clock = new ClipLoopbackClock();
        assertTrue(clock.needsQueueAnchor());
        for (int p = 0; p < 5; p++) {
            assertEquals(ORIGIN + p * 10_000_000L,
                    clock.timestamp(480, 0, 0, false, false, ORIGIN + 50_000_000L, (5 - p) * 480L));
        }
        assertFalse(clock.needsQueueAnchor());
    }

    @Test void observedPlaybackPauseKeepsAbsoluteTimeEvenWhenDeviceCounterDoesNotAdvance() {
        var clock = new ClipLoopbackClock();
        assertEquals(ORIGIN, clock.timestamp(480, 0, ORIGIN, true, false, ORIGIN + 10_000_000L, 480));
        clock.noPacket(ORIGIN + 10_000_000L);
        clock.noPacket(ORIGIN + 35_000_000L);
        clock.noPacket(ORIGIN + 5_000_000_000L);
        assertEquals(ORIGIN + 5_000_000_000L, clock.timestamp(480, 480, ORIGIN + 5_000_000_000L,
                true, false, ORIGIN + 5_010_000_000L, 480));
        assertTrue(clock.diagnostics("Ausgabeton").contains("1 fortgesetzte Tonabschnitte"));
    }

    @Test void invalidTimestampsAfterSilenceUseOldestQueuedPacketNotWorkerArrival() {
        var clock = new ClipLoopbackClock();
        clock.timestamp(480, 0, ORIGIN, true, false, ORIGIN + 10_000_000L, 480);
        clock.noPacket(ORIGIN + 10_000_000L);
        clock.noPacket(ORIGIN + 500_000_000L);
        for (int p = 0; p < 10; p++) {
            assertEquals(ORIGIN + 1_000_000_000L + p * 10_000_000L, clock.timestamp(480, 0, 0,
                    false, false, ORIGIN + 1_100_000_000L, (10 - p) * 480L));
        }
    }

    @Test void schedulerStallAloneMustNotCreateSilenceInAContinuousBacklog() {
        var clock = new ClipLoopbackClock();
        clock.timestamp(480, 0, ORIGIN, true, false, ORIGIN + 10_000_000L, 480);
        clock.noPacket(ORIGIN + 10_000_000L);
        // No further empty polls: the worker was delayed while continuous audio queued up.
        for (int p = 1; p < 50; p++) {
            assertEquals(ORIGIN + p * 10_000_000L,
                    clock.timestamp(480, 0, 0, false, false, ORIGIN + 500_000_000L, (50 - p) * 480L));
        }
        assertTrue(clock.diagnostics("Spielton").contains("0 fortgesetzte Tonabschnitte"));
    }

    @Test void realPacketLossAndLongDeviceResetRemainGaps() {
        var clock = new ClipLoopbackClock();
        clock.timestamp(480, 0, ORIGIN, true, false, ORIGIN + 10_000_000L, 480);
        assertEquals(ORIGIN + 30_000_000L, clock.timestamp(480, 1440, ORIGIN + 30_000_000L,
                true, true, ORIGIN + 40_000_000L, 480));
        assertEquals(ORIGIN + 30_000_000_000L, clock.timestamp(480, 0, ORIGIN + 30_000_000_000L,
                true, true, ORIGIN + 30_010_000_000L, 480));
    }

    @Test void continuousDevicePositionsSurviveFiveMinutesOfQpcJitterAndDoNotAlterMicrophoneClock() {
        var output = new ClipLoopbackClock();
        var microphone = new ClipAudioSampleClock();
        long frames = 0;
        for (int p = 0; frames < 300 * 48000; p++) {
            int count = Math.min(p % 2 == 0 ? 441 : 527, 300 * 48000 - (int) frames);
            long time = ORIGIN + Math.round(frames * 1_000_000_000.0 / 48000);
            long reported = time + (p == 0 ? 0 : p % 2 == 0 ? 3_000_000L : -3_000_000L);
            boolean valid = p % 11 != 10;
            assertEquals(time, output.timestamp(count, frames, reported, valid, false, time + 20_000_000L, count));
            assertEquals(time, microphone.timestamp(count, frames, reported, valid, false, time + 20_000_000L));
            output.noPacket(time + 20_000_000L);
            frames += count;
        }
        assertTrue(output.diagnostics("Ausgabeton").contains("0 fortgesetzte Tonabschnitte"));
    }
}
