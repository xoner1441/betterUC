import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.javacpp.FloatPointer;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

/** Explicit offline diagnostic. Reads a supplied clip; never opens audio devices or modifies its input. */
public class InspectClipAudio {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input.mp4 output.f32le");
        Path input = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (output.equals(input)) throw new IllegalArgumentException("Output must differ from input");
        AVFormatContext format = new AVFormatContext(null);
        AVCodecContext codec = null;
        AVPacket packet = null;
        AVFrame frame = null;
        try {
            check(avformat_open_input(format, input.toString(), null, null));
            check(avformat_find_stream_info(format, (AVDictionary) null));
            int index = -1;
            for (int i = 0; i < format.nb_streams(); i++) {
                var stream = format.streams(i);
                System.out.println("stream=" + i + " codec=" + stream.codecpar().codec_id() + " duration="
                        + stream.duration() * av_q2d(stream.time_base()) + " start=" + stream.start_time() * av_q2d(stream.time_base()));
                if (stream.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) index = i;
            }
            if (index < 0) throw new IllegalStateException("No audio");
            var stream = format.streams(index);
            codec = avcodec_alloc_context3(avcodec_find_decoder(stream.codecpar().codec_id()));
            check(avcodec_parameters_to_context(codec, stream.codecpar()));
            codec.pkt_timebase(stream.time_base());
            check(avcodec_open2(codec, codec.codec(), (AVDictionary) null));
            packet = av_packet_alloc(); frame = av_frame_alloc();
            Files.createDirectories(output.getParent());
            long frames = 0;
            // CREATE_NEW prevents accidentally overwriting any existing diagnostic or user file.
            try (var file = FileChannel.open(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                while (av_read_frame(format, packet) >= 0) {
                    if (packet.stream_index() == index) {
                        check(avcodec_send_packet(codec, packet));
                        frames += drain(codec, frame, file);
                    }
                    av_packet_unref(packet);
                }
                check(avcodec_send_packet(codec, null));
                frames += drain(codec, frame, file);
            }
            System.out.println("rate=" + codec.sample_rate() + " channels=" + codec.ch_layout().nb_channels() + " frames=" + frames + " floatPCM=" + output);
        } finally {
            if (frame != null) av_frame_free(frame);
            if (packet != null) av_packet_free(packet);
            if (codec != null) avcodec_free_context(codec);
            if (!format.isNull()) avformat_close_input(format);
        }
    }
    private static long drain(AVCodecContext codec, AVFrame frame, FileChannel output) throws Exception {
        long total = 0;
        int result;
        while ((result = avcodec_receive_frame(codec, frame)) >= 0) {
            if (frame.format() != AV_SAMPLE_FMT_FLTP || frame.ch_layout().nb_channels() != 2) throw new IllegalStateException("Expected AAC stereo float PCM");
            var left = new FloatPointer(frame.data(0)).capacity(frame.nb_samples()).asBuffer();
            var right = new FloatPointer(frame.data(1)).capacity(frame.nb_samples()).asBuffer();
            var bytes = ByteBuffer.allocate(frame.nb_samples() * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frame.nb_samples(); i++) bytes.putFloat(left.get(i)).putFloat(right.get(i));
            bytes.flip();
            while (bytes.hasRemaining()) output.write(bytes);
            total += frame.nb_samples();
        }
        if (result != AVERROR_EOF && result != -11) check(result);
        return total;
    }
    private static void check(int result) { if (result < 0) throw new IllegalStateException("FFmpeg error " + result); }
}
