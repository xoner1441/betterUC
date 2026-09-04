package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class ClipMicrophoneClockTest {
    private static final long ORIGIN = 10_000_000_000L;
    @Test void jitterReproducesOldChoppedPcmButContinuousSampleClockPreservesEverySample() {
        var raw = new ClipAudioBuffer(15);
        var fixed = new ClipAudioBuffer(15);
        var clock = new ClipAudioSampleClock();
        var expected = ByteBuffer.allocate(2 * 48_000 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int packet = 0; packet < 200; packet++) {
            var pcm = ByteBuffer.allocate(480 * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 480; i++) {
                short sample = (short) (Math.sin((packet * 480 + i) * 2 * Math.PI * 437 / 48_000) * 12000);
                pcm.putShort(sample).putShort(sample);
            }
            expected.put(pcm.array());
            long jitter = packet == 0 ? 0 : switch (packet % 3) { case 0 -> -2_000_000L; case 1 -> 3_000_000L; default -> 1_000_000L; };
            long reported = ORIGIN + packet * 10_000_000L + jitter;
            raw.add(reported, pcm.array());
            fixed.add(clock.timestamp(480, packet * 480L, reported, true, false,
                    ORIGIN + (packet + 1L) * 10_000_000L), pcm.array());
        }
        var oldPcm = ByteBuffer.wrap(raw.slice(ORIGIN, ORIGIN + 2_000_000_000L).pcm()).order(ByteOrder.LITTLE_ENDIAN);
        int corrupted = 0;
        for (int i = 0; i < 96000; i++) if (oldPcm.getShort(i * 4) != expected.getShort(i * 4)) corrupted++;
        assertTrue(corrupted > 10000, "Regression must reproduce gaps/overlap in the old packet placement");
        assertArrayEquals(expected.array(), fixed.slice(ORIGIN, ORIGIN + 2_000_000_000L).pcm());
    }
    @Test void badTimestampsAndBatchedDeliveryDoNotMoveOrDropPackets() {
        var clock = new ClipAudioSampleClock();
        assertEquals(ORIGIN, clock.timestamp(480, 0, ORIGIN, true, false, ORIGIN + 10_000_000));
        for (int packet = 1; packet < 100; packet++) {
            assertEquals(ORIGIN + packet * 10_000_000L, clock.timestamp(480, 0, 0, false, false, ORIGIN + 1_000_000_000L));
        }
        assertEquals(ORIGIN + 1_000_000_000L, clock.timestamp(480, 48000, ORIGIN + 1_000_000_000L,
                true, false, ORIGIN + 1_010_000_000L));
    }
    @Test void realLostFramesRemainASilentGapAndSpuriousFlagsDoNotCutAudio() {
        var clock = new ClipAudioSampleClock();
        assertEquals(ORIGIN, clock.timestamp(480, 0, ORIGIN, true, true, ORIGIN + 10_000_000));
        assertEquals(ORIGIN + 30_000_000, clock.timestamp(480, 1440, ORIGIN + 30_000_000,
                true, true, ORIGIN + 40_000_000));
        assertEquals(ORIGIN + 40_000_000, clock.timestamp(480, 1920, ORIGIN + 45_000_000,
                true, true, ORIGIN + 50_000_000));
        assertTrue(clock.diagnostics().contains("960 fehlende Samples"));
    }
    @Test void initializationWithoutTimestampAndCounterResetNeverRunBackwards() {
        var clock = new ClipAudioSampleClock();
        assertEquals(ORIGIN, clock.timestamp(480, 0, 0, false, false, ORIGIN + 10_000_000));
        assertEquals(ORIGIN + 10_000_000, clock.timestamp(480, 1234, ORIGIN + 12_000_000,
                true, false, ORIGIN + 20_000_000));
        assertEquals(ORIGIN + 50_000_000, clock.timestamp(480, 0, ORIGIN + 50_000_000,
                true, true, ORIGIN + 60_000_000));
        assertThrows(IllegalArgumentException.class, () -> clock.timestamp(0, 0, 0, false, false, 0));
    }
    @Test void fiveMinutesWithVariablePacketSizesHaveNoAccumulatedRoundingGaps() {
        var clock = new ClipAudioSampleClock();
        long frames = 0;
        int packet = 0;
        while (frames < 300 * 48_000) {
            int count = Math.min(packet++ % 2 == 0 ? 441 : 527, 300 * 48_000 - (int) frames);
            long expected = ORIGIN + Math.round(frames * 1_000_000_000.0 / 48_000);
            long jitter = frames == 0 ? 0 : packet % 2 == 0 ? 3_000_000 : -3_000_000;
            assertEquals(expected, clock.timestamp(count, frames, expected + jitter, true, false, expected + 20_000_000));
            frames += count;
        }
        assertEquals(ORIGIN + 300_000_000_000L,
                clock.timestamp(480, frames, ORIGIN + 300_000_000_000L, true, false, ORIGIN + 300_010_000_000L));
    }
}
