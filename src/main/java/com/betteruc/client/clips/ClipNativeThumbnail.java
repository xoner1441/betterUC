package com.betteruc.client.clips;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.BooleanSupplier;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

/** Native thumbnail decoder. This class is loaded exclusively by the private clip runtime. */
final class ClipNativeThumbnail {
    private ClipNativeThumbnail() {
    }

    static String thumbnail(Path path, BooleanSupplier cancelled) throws Exception {
        AVFormatContext format = new AVFormatContext(null);
        AVCodecContext decoder = null;
        AVFrame frame = null, rgb = null;
        AVPacket packet = null;
        SwsContext scaler = null;
        try {
            check(avformat_open_input(format, path.toString(), null, (AVDictionary) null));
            check(avformat_find_stream_info(format, (AVDictionary) null));
            int stream = -1;
            for (int i = 0; i < format.nb_streams(); i++) {
                if (format.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                    stream = i;
                    break;
                }
            }
            if (stream < 0) throw new IOException("Clip enthält kein Video.");
            var parameters = format.streams(stream).codecpar();
            if (parameters.codec_id() != AV_CODEC_ID_H264 || parameters.width() > 1920 || parameters.height() > 1080
                    || parameters.width() < 1 || parameters.height() < 1) {
                throw new IOException("Erwartet wird ein H.264-Clip bis 1080p.");
            }
            var codec = avcodec_find_decoder(parameters.codec_id());
            decoder = avcodec_alloc_context3(codec);
            if (decoder == null || decoder.isNull()) throw new IOException("Videovorschau nicht verfügbar.");
            check(avcodec_parameters_to_context(decoder, parameters));
            decoder.thread_count(1);
            check(avcodec_open2(decoder, codec, (AVDictionary) null));
            frame = av_frame_alloc();
            packet = av_packet_alloc();
            rgb = av_frame_alloc();
            if (frame == null || frame.isNull() || packet == null || packet.isNull() || rgb == null || rgb.isNull()) {
                throw new IOException("Kein Speicher für Vorschau.");
            }
            boolean decoded = false;
            for (int count = 0; count < 2000 && !decoded; count++) {
                checkCancelled(cancelled);
                int read = av_read_frame(format, packet);
                if (read < 0) {
                    avcodec_send_packet(decoder, null);
                    decoded = avcodec_receive_frame(decoder, frame) == 0;
                    break;
                }
                if (packet.stream_index() == stream) {
                    check(avcodec_send_packet(decoder, packet));
                    decoded = avcodec_receive_frame(decoder, frame) == 0;
                }
                av_packet_unref(packet);
            }
            if (!decoded) throw new IOException("Videovorschau konnte nicht erstellt werden.");
            double factor = Math.min(1, Math.min(360.0 / frame.width(), 202.0 / frame.height()));
            int width = Math.max(1, (int) (frame.width() * factor));
            int height = Math.max(1, (int) (frame.height() * factor));
            rgb.format(AV_PIX_FMT_RGB24).width(width).height(height);
            check(av_frame_get_buffer(rgb, 1));
            scaler = sws_getContext(frame.width(), frame.height(), frame.format(), width, height,
                    AV_PIX_FMT_RGB24, SWS_BILINEAR, null, null, (double[]) null);
            if (scaler == null || scaler.isNull()) throw new IOException("Videovorschau nicht verfügbar.");
            check(sws_scale(scaler, frame.data(), frame.linesize(), 0, frame.height(), rgb.data(), rgb.linesize()));
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            byte[] row = new byte[width * 3];
            BytePointer pixels = rgb.data(0);
            for (int y = 0; y < height; y++) {
                pixels.position((long) y * rgb.linesize(0)).get(row);
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, ((row[x * 3] & 255) << 16) | ((row[x * 3 + 1] & 255) << 8)
                            | (row[x * 3 + 2] & 255));
                }
            }
            pixels.position(0);
            try (var output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                return Base64.getEncoder().encodeToString(output.toByteArray());
            }
        } finally {
            if (scaler != null && !scaler.isNull()) sws_freeContext(scaler);
            if (packet != null && !packet.isNull()) av_packet_free(packet);
            if (frame != null && !frame.isNull()) av_frame_free(frame);
            if (rgb != null && !rgb.isNull()) av_frame_free(rgb);
            if (decoder != null && !decoder.isNull()) avcodec_free_context(decoder);
            if (format != null && !format.isNull()) avformat_close_input(format);
        }
    }

    private static void check(int result) throws IOException {
        if (result < 0) throw new IOException("Clip konnte nicht gelesen werden (" + result + ").");
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
}
