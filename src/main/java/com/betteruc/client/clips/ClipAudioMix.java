package com.betteruc.client.clips;

import java.util.List;

/** Time-aligned output + microphone mix. Never plays captured audio back to a device. */
final class ClipAudioMix implements ClipPcmSource {
    record Track(ClipAudioBuffer.Slice slice, int volume) {
        Track { volume = Math.clamp(volume, 0, 100); }
    }
    private final List<Track> tracks;
    private final int frames;
    ClipAudioMix(List<Track> tracks) {
        this.tracks = List.copyOf(tracks);
        frames = tracks.isEmpty() ? 0 : tracks.getFirst().slice().frames();
        if (tracks.size() > 2) throw new IllegalArgumentException("At most output and microphone");
        for (Track track : tracks) {
            if (track.slice().frames() != frames || track.slice().startNanos() != tracks.getFirst().slice().startNanos()) {
                throw new IllegalArgumentException("Audio tracks must share the same replay interval");
            }
        }
    }
    public int frames() { return frames; }
    public boolean hasCapturedAudio() { return tracks.stream().anyMatch(t -> t.volume() > 0 && t.slice().hasCapturedAudio()); }
    public Reader reader() {
        var readers = tracks.stream().map(t -> t.slice().reader()).toList();
        return new Reader() {
            private byte[][] buffers = new byte[tracks.size()][0];
            public int read(byte[] destination) {
                if (destination.length == 0 || destination.length % ClipAudioBuffer.FRAME_BYTES != 0) {
                    throw new IllegalArgumentException("PCM destination must contain whole frames");
                }
                int count = 0;
                for (int t = 0; t < tracks.size(); t++) {
                    if (buffers[t].length != destination.length) buffers[t] = new byte[destination.length];
                    count = readers.get(t).read(buffers[t]);
                }
                for (int i = 0; i < destination.length; i += 2) {
                    double sum = 0;
                    for (int t = 0; t < tracks.size(); t++) {
                        short sample = (short) ((buffers[t][i] & 255) | (buffers[t][i + 1] << 8));
                        sum += sample * (tracks.get(t).volume() / 100.0);
                    }
                    // Saturate instead of integer wraparound when loud sources overlap.
                    int mixed = Math.clamp((int) Math.round(sum), Short.MIN_VALUE, Short.MAX_VALUE);
                    destination[i] = (byte) mixed;
                    destination[i + 1] = (byte) (mixed >> 8);
                }
                return count;
            }
        };
    }
}
