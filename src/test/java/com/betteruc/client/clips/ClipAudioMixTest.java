package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClipAudioMixTest {
    private static final long ORIGIN = 10_000_000_000L;
    private static ClipAudioBuffer.Slice source(int frames, int offset, short... samples) {
        var pcm = ByteBuffer.allocate(samples.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) pcm.putShort(sample).putShort(sample);
        return new ClipAudioBuffer.Slice(ORIGIN, frames, List.of(new ClipAudioBuffer.Chunk(
                ORIGIN + Math.round(offset * 1_000_000_000.0 / 48_000), pcm.array())));
    }
    @Test void mixesIndependentSourcesAtTheirOriginalTimeIncludingGapsAndNegativeOffsets() {
        var output = source(8, 0, (short) 1000, (short) 2000, (short) 3000);
        var mic = source(8, 2, (short) 5000, (short) 6000, (short) 7000, (short) 8000);
        var mix = new ClipAudioMix(List.of(new ClipAudioMix.Track(output, 100), new ClipAudioMix.Track(mic, 50)));
        assertTrue(mix.hasCapturedAudio());
        var reader = mix.reader();
        var result = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        byte[] block = new byte[12]; // Cross both source boundaries and the final partial block.
        int frames;
        while ((frames = reader.read(block)) != 0) result.put(block, 0, frames * 4);
        short[] expected = {1000, 2000, 5500, 3000, 3500, 4000, 0, 0};
        for (int i = 0; i < 8; i++) for (int c = 0; c < 2; c++) assertEquals(expected[i], result.getShort(i * 4 + c * 2));
        var trimmed = new ClipAudioMix(List.of(new ClipAudioMix.Track(source(2, -1, (short) 50, (short) 60), 100)));
        byte[] two = new byte[8];
        assertEquals(2, trimmed.reader().read(two));
        assertEquals(60, ByteBuffer.wrap(two).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }
    @Test void saturatesLoudOverlapInsteadOfWrappingAndKeepsMutedAudioSilent() {
        var loud = source(2, 0, (short) 30000, (short) -30000);
        var mix = new ClipAudioMix(List.of(new ClipAudioMix.Track(loud, 100), new ClipAudioMix.Track(loud, 100)));
        byte[] block = new byte[8];
        mix.reader().read(block);
        var input = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(32767, input.getShort(0));
        assertEquals(-32768, input.getShort(4));
        var muted = new ClipAudioMix(List.of(new ClipAudioMix.Track(loud, 0)));
        assertFalse(muted.hasCapturedAudio());
        muted.reader().read(block);
        assertArrayEquals(new byte[8], block);
    }
    @Test void rejectsDifferentTimelinesAndDoesNotMutateSnapshots() {
        var slice = source(5, 0, (short) 999);
        byte[] original = slice.pcm();
        var mix = new ClipAudioMix(List.of(new ClipAudioMix.Track(slice, 50)));
        byte[] block = new byte[20];
        mix.reader().read(block);
        assertArrayEquals(original, slice.pcm());
        assertThrows(IllegalArgumentException.class, () -> mix.reader().read(new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> new ClipAudioMix(List.of(new ClipAudioMix.Track(slice, 100),
                new ClipAudioMix.Track(source(4, 0, (short) 2), 100))));
        assertFalse(new ClipAudioMix(List.of()).hasCapturedAudio());
    }
    @Test void fiveMinuteTwoSourceMixStreamsWithoutFullLengthCopies() {
        int frames = 300 * 48000;
        var output = source(frames, 0, (short) 1000);
        var mic = source(frames, frames - 1, (short) 2000);
        var reader = new ClipAudioMix(List.of(new ClipAudioMix.Track(output, 100), new ClipAudioMix.Track(mic, 100))).reader();
        byte[] block = new byte[4096];
        long count = 0;
        int read;
        short last = 0;
        while ((read = reader.read(block)) > 0) {
            if (count == 0) assertEquals(1000, ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).getShort());
            last = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).getShort((read - 1) * 4);
            count += read;
        }
        assertEquals(frames, count);
        assertEquals(2000, last);
    }
}
