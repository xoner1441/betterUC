package com.betteruc.client.clips;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

/** Bounded 48 kHz stereo PCM history. Chunks share the monotonic clock used by video. */
public final class ClipAudioBuffer {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 2;
    public static final int FRAME_BYTES = 4;
    public static final int MAX_SLICE_SECONDS = ClipSettings.MAX_SECONDS + ClipSettings.AUDIO_RESERVE_SECONDS;
    public record Chunk(long startNanos, byte[] pcm) {}
    public record Slice(long startNanos, int frames, List<Chunk> chunks) implements ClipPcmSource {
        public Slice {
            if (frames < 0 || frames > MAX_SLICE_SECONDS * SAMPLE_RATE) {
                throw new IllegalArgumentException("Audio clip exceeds safety limit");
            }
            chunks = List.copyOf(chunks);
        }
        public byte[] pcm() {
            byte[] result = new byte[Math.multiplyExact(frames, FRAME_BYTES)];
            if (frames > 0) reader().read(result);
            return result;
        }
        public PcmReader reader() { return new PcmReader(this); }
        public boolean hasCapturedAudio() { return !chunks.isEmpty(); }
    }

    /** AAC reads one small block at a time; no second full-length PCM allocation on export. */
    public static final class PcmReader implements ClipPcmSource.Reader {
        private final Slice slice;
        private int position;
        private int firstChunk;
        private PcmReader(Slice slice) { this.slice = slice; }

        public int read(byte[] destination) {
            if (destination.length == 0 || destination.length % FRAME_BYTES != 0) {
                throw new IllegalArgumentException("PCM destination must contain whole sample frames");
            }
            Arrays.fill(destination, (byte) 0);
            int count = Math.min(destination.length / FRAME_BYTES, slice.frames() - position);
            if (count == 0) return 0;
            var chunks = slice.chunks();
            while (firstChunk < chunks.size()) {
                Chunk chunk = chunks.get(firstChunk);
                long end = sampleOffset(chunk.startNanos() - slice.startNanos()) + chunk.pcm().length / FRAME_BYTES;
                if (end > position) break;
                firstChunk++;
            }
            for (int i = firstChunk; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                long offset = sampleOffset(chunk.startNanos() - slice.startNanos());
                if (offset >= (long) position + count) break;
                int source = (int) Math.min(chunk.pcm().length / FRAME_BYTES, Math.max(0, position - offset));
                int target = (int) Math.min(count, Math.max(0, offset - position));
                int copied = Math.min(chunk.pcm().length / FRAME_BYTES - source, count - target);
                if (copied > 0) System.arraycopy(chunk.pcm(), source * FRAME_BYTES, destination, target * FRAME_BYTES, copied * FRAME_BYTES);
            }
            position += count;
            return count; // Missing packets remain silence at their original timeline positions.
        }
    }
    private final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
    private final long retentionNanos;
    private final long maxBytes;
    private long bytes;

    public ClipAudioBuffer(int seconds) {
        int retention = ClipSettings.normalizeSeconds(seconds) + ClipSettings.AUDIO_RESERVE_SECONDS;
        retentionNanos = retention * 1_000_000_000L;
        maxBytes = (long) retention * SAMPLE_RATE * FRAME_BYTES;
    }
    public synchronized void add(long startNanos, byte[] ownedPcm) {
        if (ownedPcm.length == 0 || ownedPcm.length % FRAME_BYTES != 0 || ownedPcm.length > maxBytes) return;
        if (!chunks.isEmpty() && startNanos < chunks.getLast().startNanos()) return;
        chunks.addLast(new Chunk(startNanos, ownedPcm));
        bytes += ownedPcm.length;
        long cutoff = startNanos - retentionNanos;
        while (!chunks.isEmpty() && (bytes > maxBytes || endNanos(chunks.getFirst()) <= cutoff)) {
            bytes -= chunks.removeFirst().pcm().length;
        }
    }
    public synchronized Slice slice(long startNanos, long endNanos) {
        long frames = sampleOffset(endNanos - startNanos);
        if (frames < 0 || frames > (long) MAX_SLICE_SECONDS * SAMPLE_RATE) throw new IllegalArgumentException("Audio clip exceeds safety limit");
        return new Slice(startNanos, (int) frames, chunks.stream()
                .filter(chunk -> chunk.startNanos() < endNanos && endNanos(chunk) > startNanos).toList());
    }
    static long sampleOffset(long nanos) { return Math.round(nanos * (SAMPLE_RATE / 1_000_000_000.0)); }
    private static long endNanos(Chunk chunk) { return chunk.startNanos() + (long) (chunk.pcm().length / FRAME_BYTES) * 1_000_000_000L / SAMPLE_RATE; }
    public synchronized long bytes() { return bytes; }
    public synchronized void clear() { chunks.clear(); bytes = 0; }
}
