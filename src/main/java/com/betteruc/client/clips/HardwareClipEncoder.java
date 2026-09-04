package com.betteruc.client.clips;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

/** Native libraries loaded in-process. No subprocesses, downloads or software-encoder fallback. */
public final class HardwareClipEncoder implements AutoCloseable {
    private final ClipSettings settings;
    private final String name;
    private AVCodecContext context;
    private AVFrame frame;
    private AVPacket packet;
    private SwsContext scaler;
    private PointerPointer<BytePointer> source;
    private IntPointer strides;
    private AVRational timeBase;

    public record Header(ClipSettings settings, byte[] extraData) {}

    public static HardwareClipEncoder open(ClipSettings settings, Consumer<String> diagnostics) throws IOException {
        diagnostics.accept("FFmpeg Clip-Runtime: " + av_version_info().getString());
        return ClipEncoderSelection.open(name -> openTested(settings, name), diagnostics);
    }

    private static HardwareClipEncoder openTested(ClipSettings settings, String name) throws IOException {
        // Opening alone is not enough: actually encode and drain test frames before selecting a GPU.
        try (HardwareClipEncoder probe = new HardwareClipEncoder(settings, name)) {
            long size = (long) settings.width() * settings.height() * 4;
            BytePointer memory = new BytePointer(av_mallocz(size));
            if (memory.isNull()) throw new IOException("Kein Speicher für Encoder-Test");
            try {
                ByteBuffer pixels = memory.capacity(size).asByteBuffer();
                int[] packets = {0};
                for (int i = 0; i < 4; i++) probe.encode(pixels, i, p -> packets[0]++);
                probe.flush(p -> packets[0]++);
                if (packets[0] == 0) throw new IOException("Kein Videopaket erzeugt");
            } finally { av_free(memory); }
        }
        return new HardwareClipEncoder(settings, name);
    }

    private HardwareClipEncoder(ClipSettings settings, String name) throws IOException {
        this.settings = settings;
        this.name = name;
        try {
            AVCodec codec = avcodec_find_encoder_by_name(name);
            if (codec == null || codec.isNull()) throw new ClipEncoderSelection.MissingEncoderException();
            context = avcodec_alloc_context3(codec);
            if (context == null || context.isNull()) throw new IOException("Encoder konnte nicht angelegt werden");
            timeBase = new AVRational().num(1).den(settings.fps());
            context.width(settings.width()).height(settings.height()).pix_fmt(AV_PIX_FMT_NV12)
                    .time_base(timeBase)
                    .bit_rate(settings.bitrate()).rc_max_rate(settings.bitrate() * 3L / 2)
                    .rc_buffer_size(settings.bitrate()).gop_size(settings.fps()).max_b_frames(0)
                    .colorspace(AVCOL_SPC_BT709).color_primaries(AVCOL_PRI_BT709)
                    .color_trc(AVCOL_TRC_BT709).color_range(AVCOL_RANGE_MPEG)
                    .flags(context.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
            try (var rate = new AVRational().num(settings.fps()).den(1)) { context.framerate(rate); }
            AVDictionary options = new AVDictionary(null);
            try {
                configureOptions(name, options);
                check(avcodec_open2(context, codec, options), "Hardware-Encoder öffnen");
            } finally { av_dict_free(options); }
            frame = av_frame_alloc();
            if (frame == null || frame.isNull()) throw new IOException("Kein Speicher für Videobild");
            frame.format(AV_PIX_FMT_NV12).width(settings.width()).height(settings.height());
            frame.colorspace(AVCOL_SPC_BT709).color_primaries(AVCOL_PRI_BT709)
                    .color_trc(AVCOL_TRC_BT709).color_range(AVCOL_RANGE_MPEG);
            check(av_frame_get_buffer(frame, 32), "Videopuffer anlegen");
            packet = av_packet_alloc();
            if (packet == null || packet.isNull()) throw new IOException("Kein Speicher für Videopaket");
            scaler = sws_getContext(settings.width(), settings.height(), AV_PIX_FMT_RGBA,
                    settings.width(), settings.height(), AV_PIX_FMT_NV12, SWS_BILINEAR, null, null, (double[]) null);
            if (scaler == null || scaler.isNull()) throw new IOException("Farbkonvertierung nicht verfügbar");
            check(sws_setColorspaceDetails(scaler, sws_getCoefficients(SWS_CS_ITU709), 1,
                    sws_getCoefficients(SWS_CS_ITU709), 0, 0, 1 << 16, 1 << 16), "BT.709-Farbkonvertierung");
            source = new PointerPointer<>(4);
            strides = new IntPointer(4);
            for (int i = 0; i < 4; i++) { source.put(i, (Pointer) null); strides.put(i, 0); }
            strides.put(0, -settings.width() * 4);
        } catch (Exception | LinkageError error) {
            close();
            if (error instanceof IOException io) throw io;
            throw new IOException(error.getMessage(), error);
        }
    }

    public String name() { return name; }

    /** Also validated against each real codec's AVOptions without requiring that vendor's GPU. */
    static void configureOptions(String name, AVDictionary options) {
        switch (name) {
            case "h264_nvenc" -> {
                av_dict_set(options, "preset", "p4", 0);
                av_dict_set(options, "tune", "ll", 0);
                av_dict_set(options, "rc", "vbr", 0);
                av_dict_set(options, "rc-lookahead", "0", 0);
            }
            case "h264_amf" -> {
                av_dict_set(options, "usage", "lowlatency", 0);
                av_dict_set(options, "quality", "balanced", 0);
                av_dict_set(options, "rc", "vbr_peak", 0);
            }
            case "h264_qsv" -> {
                av_dict_set(options, "preset", "veryfast", 0);
                av_dict_set(options, "look_ahead", "0", 0);
                av_dict_set(options, "async_depth", "1", 0);
            }
            default -> throw new IllegalArgumentException("Nicht erlaubter Hardware-Encoder: " + name);
        }
    }

    public void encode(ByteBuffer rgbaBottomUp, long pts, Consumer<ClipPacket> sink) throws IOException {
        check(av_frame_make_writable(frame), "Videopuffer freigeben");
        // Minecraft screenshot readback is bottom-up; flip via negative stride, not a Java pixel loop.
        try (BytePointer input = new BytePointer(rgbaBottomUp)) {
            input.position((long) settings.width() * (settings.height() - 1) * 4);
            source.put(0, input);
            int rows = sws_scale(scaler, source, strides, 0, settings.height(), frame.data(), frame.linesize());
            if (rows != settings.height()) throw new IOException("Unvollständiges Videobild");
            frame.pts(pts);
            check(avcodec_send_frame(context, frame), "Videobild kodieren");
            drain(sink);
        }
    }

    public void flush(Consumer<ClipPacket> sink) throws IOException {
        check(avcodec_send_frame(context, null), "Encoder abschließen");
        drain(sink);
    }

    private void drain(Consumer<ClipPacket> sink) throws IOException {
        int result;
        while ((result = avcodec_receive_packet(context, packet)) >= 0) {
            try {
                byte[] bytes = new byte[packet.size()];
                packet.data().get(bytes);
                sink.accept(new ClipPacket(bytes, packet.pts(), packet.dts(), packet.duration(),
                        (packet.flags() & AV_PKT_FLAG_KEY) != 0));
            } finally { av_packet_unref(packet); }
        }
        if (result != AVERROR_EOF && result != -11) check(result, "Videopaket empfangen");
    }

    public Header header() {
        byte[] extra = new byte[context.extradata_size()];
        if (extra.length > 0) context.extradata().get(extra);
        return new Header(settings, extra);
    }

    /** Lossless remux of compressed packets. Runs on the export thread, never the render/encoder thread. */
    public static void writeMp4(Path output, Header header, List<ClipPacket> packets) throws IOException {
        writeMp4(output, header, packets, null);
    }

    static void writeMp4(Path output, Header header, List<ClipPacket> packets, ClipAudioEncoder.Encoded audio) throws IOException {
        if (packets.isEmpty() || !packets.getFirst().keyframe()) throw new IOException("Clip enthält kein Startbild");
        AVFormatContext mux = new AVFormatContext(null);
        AVIOContext io = new AVIOContext(null);
        AVPacket packet = null;
        boolean opened = false;
        try (AVRational inputTimeBase = new AVRational().num(1).den(header.settings().fps());
             AVRational audioTimeBase = new AVRational().num(1).den(ClipAudioBuffer.SAMPLE_RATE)) {
            check(avformat_alloc_output_context2(mux, null, "mp4", output.toString()), "MP4 vorbereiten");
            AVStream stream = avformat_new_stream(mux, null);
            if (stream == null || stream.isNull()) throw new IOException("MP4-Spur konnte nicht angelegt werden");
            stream.time_base(inputTimeBase);
            try (var rate = new AVRational().num(header.settings().fps()).den(1)) { stream.avg_frame_rate(rate); }
            var parameters = stream.codecpar();
            parameters.codec_type(AVMEDIA_TYPE_VIDEO).codec_id(AV_CODEC_ID_H264).codec_tag(0)
                    .width(header.settings().width()).height(header.settings().height()).format(AV_PIX_FMT_NV12);
            if (header.extraData().length > 0) {
                BytePointer extra = new BytePointer(av_mallocz(header.extraData().length + AV_INPUT_BUFFER_PADDING_SIZE));
                if (extra.isNull()) throw new IOException("Kein Speicher für MP4-Parameter");
                extra.put(header.extraData());
                parameters.extradata(extra).extradata_size(header.extraData().length);
            }
            AVStream audioStream = null;
            if (audio != null && !audio.packets().isEmpty()) {
                audioStream = avformat_new_stream(mux, null);
                if (audioStream == null || audioStream.isNull()) throw new IOException("AAC-Spur konnte nicht angelegt werden");
                audioStream.time_base(audioTimeBase);
                var audioParameters = audioStream.codecpar();
                audioParameters.codec_type(AVMEDIA_TYPE_AUDIO).codec_id(AV_CODEC_ID_AAC).codec_tag(0)
                        .sample_rate(ClipAudioBuffer.SAMPLE_RATE).format(AV_SAMPLE_FMT_FLTP).bit_rate(192_000)
                        .initial_padding(audio.initialPadding()).frame_size(audio.frameSize());
                av_channel_layout_default(audioParameters.ch_layout(), ClipAudioBuffer.CHANNELS);
                if (audio.extraData().length > 0) {
                    BytePointer extra = new BytePointer(av_mallocz(audio.extraData().length + AV_INPUT_BUFFER_PADDING_SIZE));
                    if (extra.isNull()) throw new IOException("Kein Speicher für AAC-Parameter");
                    extra.put(audio.extraData());
                    audioParameters.extradata(extra).extradata_size(audio.extraData().length);
                }
            }
            check(avio_open(io, output.toString(), AVIO_FLAG_WRITE), "Clipdatei öffnen");
            opened = true;
            mux.pb(io);
            check(avformat_write_header(mux, (AVDictionary) null), "MP4-Kopf schreiben");
            packet = av_packet_alloc();
            if (packet == null || packet.isNull()) throw new IOException("Kein Speicher für MP4-Paket");
            long start = packets.getFirst().dts();
            int videoIndex = 0, audioIndex = 0;
            List<ClipPacket> audioPackets = audioStream == null ? List.of() : audio.packets();
            while (videoIndex < packets.size() || audioIndex < audioPackets.size()) {
                boolean useAudio = audioIndex < audioPackets.size() && (videoIndex == packets.size()
                        || av_compare_ts(audioPackets.get(audioIndex).dts(), audioTimeBase,
                        packets.get(videoIndex).dts() - start, inputTimeBase) <= 0);
                if (useAudio) {
                    ClipPacket item = audioPackets.get(audioIndex++);
                    writePacket(mux, packet, audioStream, item, 0, Math.max(1, item.duration()), audioTimeBase);
                } else {
                    ClipPacket item = packets.get(videoIndex);
                    long duration = videoIndex + 1 < packets.size() ? packets.get(videoIndex + 1).pts() - item.pts() : Math.max(1, item.duration());
                    writePacket(mux, packet, stream, item, start, Math.max(1, duration), inputTimeBase);
                    videoIndex++;
                }
            }
            check(av_write_trailer(mux), "MP4 abschließen");
        } finally {
            if (packet != null) av_packet_free(packet);
            if (opened) avio_closep(io);
            if (mux != null && !mux.isNull()) avformat_free_context(mux);
        }
        if (Files.size(output) == 0) throw new IOException("Leere Clipdatei");
    }

    private static void writePacket(AVFormatContext mux, AVPacket packet, AVStream stream, ClipPacket item,
                                    long start, long duration, AVRational timeBase) throws IOException {
        check(av_new_packet(packet, item.bytes().length), "MP4-Paket anlegen");
        try {
            packet.data().put(item.bytes());
            packet.pts(item.pts() - start).dts(item.dts() - start).duration(duration)
                    .stream_index(stream.index()).flags(item.keyframe() ? AV_PKT_FLAG_KEY : 0).pos(-1);
            av_packet_rescale_ts(packet, timeBase, stream.time_base());
            check(av_interleaved_write_frame(mux, packet), "MP4-Paket schreiben");
        } finally { av_packet_unref(packet); }
    }

    private static void check(int result, String action) throws IOException {
        if (result >= 0) return;
        try (BytePointer message = new BytePointer(256)) {
            av_strerror(result, message, 256);
            throw new IOException(action + ": " + message.getString() + " (" + result + ")");
        }
    }

    @Override public void close() {
        if (scaler != null && !scaler.isNull()) { sws_freeContext(scaler); scaler = null; }
        if (packet != null && !packet.isNull()) { av_packet_free(packet); packet = null; }
        if (frame != null && !frame.isNull()) { av_frame_free(frame); frame = null; }
        if (context != null && !context.isNull()) { avcodec_free_context(context); context = null; }
        if (source != null) { source.close(); source = null; }
        if (strides != null) { strides.close(); strides = null; }
        if (timeBase != null) { timeBase.close(); timeBase = null; }
    }
}
