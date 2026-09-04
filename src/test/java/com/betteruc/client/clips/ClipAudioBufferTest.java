package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ClipAudioBufferTest {
    @Test void trimsToVideoStartAndPadsMissingSoundWithSilence() {
        long second = 1_000_000_000L;
        var buffer = new ClipAudioBuffer(30);
        byte[] sound = new byte[48_000 * 4];
        Arrays.fill(sound, (byte) 42);
        buffer.add(10 * second, sound);
        var slice = buffer.slice(10 * second + second / 2, 12 * second);
        assertEquals(72_000, slice.frames());
        byte[] pcm = slice.pcm();
        for (int i = 0; i < 24_000 * 4; i++) assertEquals(42, pcm[i]);
        for (int i = 24_000 * 4; i < pcm.length; i++) assertEquals(0, pcm[i]);
        buffer.clear();
        assertTrue(slice.hasCapturedAudio());
        assertEquals(42, slice.pcm()[0], "Export snapshot survives recording reset");
        assertEquals(0, buffer.bytes());
    }
    @Test void lostPacketsLeaveAHoleInsteadOfMovingFollowingAudioEarlier() {
        var buffer = new ClipAudioBuffer(30);
        buffer.add(1_000_000_000L, new byte[]{1, 1, 1, 1});
        buffer.add(1_000_062_500L, new byte[]{2, 2, 2, 2}); // Exactly 3 sample frames later.
        assertArrayEquals(new byte[]{1,1,1,1, 0,0,0,0, 0,0,0,0, 2,2,2,2},
                buffer.slice(1_000_000_000L, 1_000_083_333L).pcm());
    }
    @Test void pcmHistoryHasHardMemoryLimitAndRejectsOutOfOrderChunks() {
        var buffer = new ClipAudioBuffer(15);
        byte[] second = new byte[48_000 * 4];
        for (int i = 0; i < 25; i++) buffer.add(i * 1_000_000_000L, second);
        assertEquals(20L * second.length, buffer.bytes());
        buffer.add(0, second);
        assertEquals(20L * second.length, buffer.bytes());
        assertFalse(buffer.slice(0, 1_000_000_000L).hasCapturedAudio());
        assertThrows(IllegalArgumentException.class, () -> buffer.slice(0, (ClipAudioBuffer.MAX_SLICE_SECONDS + 1L) * 1_000_000_000L));
    }

    @Test void streamingReadsMatchTheTimelineAcrossPartialBlocksGapsAndOverlaps() {
        var buffer = new ClipAudioBuffer(30);
        byte[] first = new byte[8000];
        byte[] overlap = new byte[64];
        byte[] last = new byte[516];
        Arrays.fill(first, (byte) 1);
        Arrays.fill(overlap, (byte) 2);
        Arrays.fill(last, (byte) 3);
        buffer.add(0, first);
        buffer.add(1_000_000L, overlap);
        buffer.add(100_000_000L, last);
        var slice = buffer.slice(500_000L, 150_020_833L);
        byte[] expected = new byte[slice.frames() * 4];
        for (var chunk : slice.chunks()) {
            int offset = (int) ClipAudioBuffer.sampleOffset(chunk.startNanos() - slice.startNanos());
            for (int i = 0; i < chunk.pcm().length / 4; i++) {
                int target = offset + i;
                if (target >= 0 && target < slice.frames()) System.arraycopy(chunk.pcm(), i * 4, expected, target * 4, 4);
            }
        }
        buffer.clear();
        for (int blockSize : new int[]{4, 28, 4096, 16384}) {
            var reader = slice.reader();
            var output = new ByteArrayOutputStream();
            byte[] block = new byte[blockSize];
            int count;
            while ((count = reader.read(block)) > 0) output.write(block, 0, count * 4);
            assertArrayEquals(expected, output.toByteArray());
            assertEquals(0, reader.read(block));
        }
    }

    @Test void fiveMinuteAudioHistoryStaysBoundedAndStreamsWithoutFullCopy() {
        var buffer = new ClipAudioBuffer(300);
        byte[] second = new byte[48_000 * 4];
        Arrays.fill(second, (byte) 7);
        for (int i = 0; i < 320; i++) buffer.add(i * 1_000_000_000L, second);
        assertEquals(305L * second.length, buffer.bytes());
        assertFalse(buffer.slice(0, 1_000_000_000L).hasCapturedAudio());
        var slice = buffer.slice(20_000_000_000L, 320_000_000_000L);
        assertEquals(300 * 48_000, slice.frames());
        var reader = slice.reader();
        buffer.clear();
        int frames = 0;
        int count;
        byte[] block = new byte[4096];
        while ((count = reader.read(block)) > 0) {
            frames += count;
            assertEquals(7, block[0]);
            assertEquals(7, block[count * 4 - 1]);
        }
        assertEquals(300 * 48_000, frames);
    }
}
