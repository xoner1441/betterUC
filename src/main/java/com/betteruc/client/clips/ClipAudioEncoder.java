package com.betteruc.client.clips;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

/** AAC encoding runs only when saving, off the render/video-encoder threads. PCM gaps remain silent. */
final class ClipAudioEncoder {
    record Encoded(byte[] extraData, int initialPadding, int frameSize, long samples, List<ClipPacket> packets) {}
    private ClipAudioEncoder() {}

    static Encoded encode(ClipPcmSource audio) throws IOException {
        if (audio == null || !audio.hasCapturedAudio() || audio.frames() == 0) return null;
        AVCodecContext context = null;
        AVFrame frame = null;
        AVPacket packet = null;
        try (AVRational time = new AVRational().num(1).den(ClipAudioBuffer.SAMPLE_RATE)) {
            var codec = avcodec_find_encoder_by_name("aac");
            if (codec == null || codec.isNull()) throw new IOException("AAC-Encoder nicht verfügbar");
            context = avcodec_alloc_context3(codec);
            if (context == null || context.isNull()) throw new IOException("Kein Speicher für AAC");
            context.sample_rate(ClipAudioBuffer.SAMPLE_RATE).sample_fmt(AV_SAMPLE_FMT_FLTP).bit_rate(192_000)
                    .time_base(time).profile(AV_PROFILE_AAC_LOW).thread_count(1)
                    .flags(context.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
            av_channel_layout_default(context.ch_layout(), ClipAudioBuffer.CHANNELS);
            check(avcodec_open2(context, codec, (AVDictionary) null), "AAC öffnen");
            frame = av_frame_alloc();
            packet = av_packet_alloc();
            if (frame == null || frame.isNull() || packet == null || packet.isNull()) throw new IOException("Kein Speicher für AAC-Puffer");
            int frameSize = context.frame_size();
            if (frameSize <= 0 || frameSize > 8192) throw new IOException("Unerwartete AAC-Framegröße");
            frame.format(AV_SAMPLE_FMT_FLTP).sample_rate(ClipAudioBuffer.SAMPLE_RATE).nb_samples(frameSize);
            check(av_channel_layout_copy(frame.ch_layout(), context.ch_layout()), "AAC-Kanäle setzen");
            check(av_frame_get_buffer(frame, 0), "AAC-Puffer anlegen");
            byte[] pcm = new byte[frameSize * ClipAudioBuffer.FRAME_BYTES];
            var reader = audio.reader();
            ByteBuffer input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            List<ClipPacket> packets = new ArrayList<>();
            for (int start = 0; start < audio.frames(); start += frameSize) {
                frame.nb_samples(frameSize);
                check(av_frame_make_writable(frame), "AAC-Puffer freigeben");
                int count = reader.read(pcm);
                for (int channel = 0; channel < ClipAudioBuffer.CHANNELS; channel++) {
                    // Non-owning views: the AVFrame owns this memory.
                    var plane = new FloatPointer(frame.data(channel)).capacity(frameSize).asBuffer();
                    for (int i = 0; i < frameSize; i++) {
                        float value = i < count ? input.getShort(i * ClipAudioBuffer.FRAME_BYTES + channel * 2) / 32768.0f : 0;
                        plane.put(i, value);
                    }
                }
                frame.nb_samples(count).pts(start);
                check(avcodec_send_frame(context, frame), "AAC kodieren");
                drain(context, packet, packets);
            }
            check(avcodec_send_frame(context, null), "AAC abschließen");
            drain(context, packet, packets);
            byte[] extra = new byte[context.extradata_size()];
            if (extra.length > 0) context.extradata().get(extra);
            return new Encoded(extra, context.initial_padding(), frameSize, audio.frames(), List.copyOf(packets));
        } finally {
            if (packet != null && !packet.isNull()) av_packet_free(packet);
            if (frame != null && !frame.isNull()) av_frame_free(frame);
            if (context != null && !context.isNull()) avcodec_free_context(context);
        }
    }

    private static void drain(AVCodecContext context, AVPacket packet, List<ClipPacket> output) throws IOException {
        int result;
        while ((result = avcodec_receive_packet(context, packet)) >= 0) {
            try {
                byte[] bytes = new byte[packet.size()];
                packet.data().get(bytes);
                output.add(new ClipPacket(bytes, packet.pts(), packet.dts(), packet.duration(), true));
            } finally { av_packet_unref(packet); }
        }
        if (result != AVERROR_EOF && result != -11) check(result, "AAC-Paket empfangen");
    }
    private static void check(int result, String message) throws IOException {
        if (result >= 0) return;
        try (BytePointer text = new BytePointer(256)) {
            av_strerror(result, text, 256);
            throw new IOException(message + ": " + text.getString());
        }
    }
}
