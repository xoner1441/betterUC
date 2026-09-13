package com.betteruc.client.clips;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * JDK-only reflective boundary for the isolated FFmpeg classloader.
 * This class is packaged into the private clip runtime and is never initialized by Fabric's shared loader.
 */
public final class ClipRuntimeEntry {
    private ClipRuntimeEntry() {
    }

    public static Encoder openEncoder(
            int width,
            int height,
            int fps,
            int seconds,
            int bitrate,
            Consumer<String> diagnostics
    ) throws Exception {
        return new Encoder(HardwareClipEncoder.open(
                new ClipSettings(width, height, fps, seconds, bitrate), diagnostics
        ));
    }

    public static String runtimeInfo() throws Exception {
        return org.bytedeco.ffmpeg.global.avutil.av_version_info().getString()
                + " | JavaCPP " + org.bytedeco.javacpp.Loader.getVersion()
                + " | Cache " + org.bytedeco.javacpp.Loader.getCacheDir();
    }

    public static Object[] encodeAudio(byte[] pcm, int frames) throws Exception {
        if (pcm == null || frames <= 0) return null;
        int expected = Math.multiplyExact(frames, ClipAudioBuffer.FRAME_BYTES);
        if (pcm.length != expected) throw new IllegalArgumentException("PCM size does not match frame count");

        ClipPcmSource source = new ClipPcmSource() {
            @Override public int frames() { return frames; }
            @Override public boolean hasCapturedAudio() { return true; }
            @Override public Reader reader() {
                return new Reader() {
                    private int position;
                    @Override public int read(byte[] destination) {
                        int remaining = pcm.length - position;
                        if (remaining <= 0) return 0;
                        int bytes = Math.min(destination.length, remaining);
                        System.arraycopy(pcm, position, destination, 0, bytes);
                        if (bytes < destination.length) java.util.Arrays.fill(destination, bytes, destination.length, (byte) 0);
                        position += bytes;
                        return bytes / ClipAudioBuffer.FRAME_BYTES;
                    }
                };
            }
        };
        ClipAudioEncoder.Encoded encoded = ClipAudioEncoder.encode(source);
        if (encoded == null) return null;
        return new Object[]{
                encoded.extraData(), encoded.initialPadding(), encoded.frameSize(), encoded.samples(),
                encodePackets(encoded.packets())
        };
    }

    public static void writeMp4(
            String output,
            int width,
            int height,
            int fps,
            int seconds,
            int bitrate,
            byte[] extraData,
            List<Object[]> videoPackets,
            Object[] encodedAudio
    ) throws Exception {
        ClipSettings settings = new ClipSettings(width, height, fps, seconds, bitrate);
        HardwareClipEncoder.Header header = new HardwareClipEncoder.Header(settings, extraData);
        ClipAudioEncoder.Encoded audio = decodeAudio(encodedAudio);
        HardwareClipEncoder.writeMp4(Path.of(output), header, decodePackets(videoPackets), audio);
    }

    public static String thumbnail(String input, BooleanSupplier cancelled) throws Exception {
        return ClipNativeThumbnail.thumbnail(Path.of(input), cancelled);
    }

    private static ClipAudioEncoder.Encoded decodeAudio(Object[] raw) {
        if (raw == null) return null;
        @SuppressWarnings("unchecked")
        List<Object[]> packets = (List<Object[]>) raw[4];
        return new ClipAudioEncoder.Encoded(
                (byte[]) raw[0], (Integer) raw[1], (Integer) raw[2], (Long) raw[3], decodePackets(packets)
        );
    }

    private static List<Object[]> encodePackets(List<ClipPacket> packets) {
        List<Object[]> encoded = new ArrayList<>(packets.size());
        for (ClipPacket packet : packets) {
            encoded.add(new Object[]{packet.bytes(), packet.pts(), packet.dts(), packet.duration(), packet.keyframe()});
        }
        return encoded;
    }

    private static List<ClipPacket> decodePackets(List<Object[]> packets) {
        List<ClipPacket> decoded = new ArrayList<>(packets.size());
        for (Object[] packet : packets) {
            decoded.add(new ClipPacket(
                    (byte[]) packet[0], (Long) packet[1], (Long) packet[2], (Long) packet[3], (Boolean) packet[4]
            ));
        }
        return decoded;
    }

    public static final class Encoder implements AutoCloseable {
        private final HardwareClipEncoder delegate;

        private Encoder(HardwareClipEncoder delegate) {
            this.delegate = delegate;
        }

        public String name() {
            return delegate.name();
        }

        public List<Object[]> encode(ByteBuffer pixels, long pts) throws Exception {
            List<ClipPacket> packets = new ArrayList<>();
            delegate.encode(pixels, pts, packets::add);
            return encodePackets(packets);
        }

        public List<Object[]> repeatLastFrame(long pts) throws Exception {
            List<ClipPacket> packets = new ArrayList<>();
            delegate.repeatLastFrame(pts, packets::add);
            return encodePackets(packets);
        }

        public List<Object[]> flush() throws Exception {
            List<ClipPacket> packets = new ArrayList<>();
            delegate.flush(packets::add);
            return encodePackets(packets);
        }

        public byte[] headerExtraData() {
            return delegate.header().extraData();
        }

        @Override public void close() {
            delegate.close();
        }
    }
}
