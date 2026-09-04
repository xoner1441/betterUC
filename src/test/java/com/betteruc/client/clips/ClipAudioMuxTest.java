package com.betteruc.client.clips;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.FloatPointer;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "betteruc.clipHardwareTest", matches = "true")
class ClipAudioMuxTest {
    @ParameterizedTest
    @CsvSource({"30,3,false", "60,3,false", "30,300,false", "60,300,false", "60,3,true", "60,300,true"})
    void aacAndTrimmedVideoKeepTheSameTimelineWithDroppedFrames(int fps, int seconds, boolean mixed) throws Exception {
        var settings = new ClipSettings(320, 180, fps, Math.max(15, seconds), 2_000_000);
        var packets = new ArrayList<ClipPacket>();
        HardwareClipEncoder.Header header;
        try (var video = HardwareClipEncoder.open(settings, System.out::println)) {
            ByteBuffer pixels = ByteBuffer.allocateDirect(320 * 180 * 4);
            for (int i = 0; i < fps * seconds; i++) {
                if (i < fps * 3 / 2 || i >= fps * 3 / 2 + fps / 10) video.encode(pixels, i, packets::add);
            }
            video.flush(packets::add);
            header = video.header();
        }
        // Select a real keyframe after the beginning rather than assuming the encoder's exact GOP cadence.
        int first = -1;
        for (int i = 1; i < packets.size(); i++) if (packets.get(i).keyframe()) { first = i; break; }
        assertTrue(first > 0);
        var replay = packets.subList(first, packets.size());
        long beginTick = replay.getFirst().pts();
        double duration = (fps * seconds - beginTick) / (double) fps;
        assertTrue(duration > 1.2);
        long origin = 10_000_000_000L;
        var pcm = ByteBuffer.allocate(seconds * 48_000 * 4).order(ByteOrder.LITTLE_ENDIAN);
        int toneStart = (int) (beginTick * (48_000 / fps) + 24_000); // 0.5 seconds into the selected replay.
        for (int i = 0; i < seconds * 48_000; i++) {
            boolean tone = i >= toneStart && i < toneStart + 24_000 || !mixed && seconds > 3 && i >= seconds * 48_000 - 24_000;
            short value = (short) (tone ? Math.sin(i * 2 * Math.PI * 440 / 48_000) * 9000 : 0);
            pcm.putShort(value).putShort(value);
        }
        var buffer = new ClipAudioBuffer(Math.max(15, seconds));
        var outputClock = new ClipLoopbackClock();
        boolean firstOutputPacket = true;
        for (int p = 0; p < seconds * 100; p++) {
            byte[] chunk = java.util.Arrays.copyOfRange(pcm.array(), p * 480 * 4, (p + 1) * 480 * 4);
            long expected = origin + p * 10_000_000L;
            boolean silent = true;
            for (byte value : chunk) if (value != 0) { silent = false; break; }
            if (silent) { outputClock.noPacket(expected + 10_000_000L); continue; }
            long jitter = firstOutputPacket ? 0 : p % 2 == 0 ? 3_000_000L : -3_000_000L;
            long time = outputClock.timestamp(480, p * 480L, expected + jitter, firstOutputPacket || p % 17 != 16,
                    false, expected + 10_000_000L, 480);
            firstOutputPacket = false;
            buffer.add(time, chunk);
            outputClock.noPacket(expected + 10_000_000L);
        }
        var slice = buffer.slice(origin + beginTick * 1_000_000_000L / fps, origin + seconds * 1_000_000_000L);
        ClipPcmSource selected = slice;
        if (mixed) {
            // Independent synthetic microphone packet arrives near the end, not at video's origin.
            // This validates the mixed streaming path without recording any real device or conversation.
            var microphone = new ClipAudioBuffer(Math.max(15, seconds));
            var tone = ByteBuffer.allocate(24_000 * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 24_000; i++) {
                short value = (short) (Math.sin(i * 2 * Math.PI * 660 / 48_000) * 9000);
                tone.putShort(value).putShort(value);
            }
            var microphoneClock = new ClipAudioSampleClock();
            for (int p = 0; p < 50; p++) {
                long expected = origin + seconds * 1_000_000_000L - 500_000_000L + p * 10_000_000L;
                long jitter = p == 0 ? 0 : p % 2 == 0 ? 3_000_000L : -3_000_000L;
                long timestamp = microphoneClock.timestamp(480, p * 480L, expected + jitter,
                        p % 7 != 6, false, expected + 10_000_000L);
                microphone.add(timestamp, java.util.Arrays.copyOfRange(tone.array(), p * 480 * 4, (p + 1) * 480 * 4));
            }
            selected = new ClipAudioMix(java.util.List.of(new ClipAudioMix.Track(slice, 100),
                    new ClipAudioMix.Track(microphone.slice(slice.startNanos(), origin + seconds * 1_000_000_000L), 100)));
        }
        var audio = ClipAudioEncoder.encode(selected);
        assertNotNull(audio);
        assertFalse(audio.packets().isEmpty());
        Path output = Path.of("build", "clip-test", "synthetic-av-sync-" + fps + "fps-" + seconds + "s" + (mixed ? "-mixed" : "") + ".mp4");
        Files.createDirectories(output.getParent());
        HardwareClipEncoder.writeMp4(output, header, replay, audio);
        verify(output, duration, seconds > 3);
    }

    private void verify(Path path, double expectedDuration, boolean checkEnd) {
        AVFormatContext format = new AVFormatContext(null);
        AVCodecContext decoder = null;
        AVPacket packet = null;
        AVFrame frame = null;
        try {
            assertTrue(avformat_open_input(format, path.toString(), null, null) >= 0);
            assertTrue(avformat_find_stream_info(format, (AVDictionary) null) >= 0);
            assertEquals(2, format.nb_streams());
            var video = format.streams(0);
            var audio = format.streams(1);
            assertEquals(AV_CODEC_ID_H264, video.codecpar().codec_id());
            assertEquals(AV_CODEC_ID_AAC, audio.codecpar().codec_id());
            assertEquals(48_000, audio.codecpar().sample_rate());
            assertEquals(2, audio.codecpar().ch_layout().nb_channels());
            assertEquals(0, video.start_time() * av_q2d(video.time_base()), 0.001);
            assertEquals(0, audio.start_time() * av_q2d(audio.time_base()), 0.001);
            assertEquals(expectedDuration, video.duration() * av_q2d(video.time_base()), 0.002);
            assertEquals(expectedDuration, audio.duration() * av_q2d(audio.time_base()), 0.025);
            var codec = avcodec_find_decoder(AV_CODEC_ID_AAC);
            decoder = avcodec_alloc_context3(codec);
            assertTrue(avcodec_parameters_to_context(decoder, audio.codecpar()) >= 0);
            decoder.pkt_timebase(audio.time_base());
            assertTrue(avcodec_open2(decoder, codec, (AVDictionary) null) >= 0);
            packet = av_packet_alloc();
            frame = av_frame_alloc();
            double[] energy = new double[8];
            while (av_read_frame(format, packet) >= 0) {
                if (packet.stream_index() == audio.index()) {
                    assertTrue(avcodec_send_packet(decoder, packet) >= 0);
                    while (avcodec_receive_frame(decoder, frame) >= 0) accumulate(frame, energy, expectedDuration);
                }
                av_packet_unref(packet);
            }
            assertTrue(avcodec_send_packet(decoder, null) >= 0);
            while (avcodec_receive_frame(decoder, frame) >= 0) accumulate(frame, energy, expectedDuration);
            assertTrue(energy[1] > 1000 && energy[3] > 1000);
            assertTrue(Math.sqrt(energy[0] / energy[1]) < 0.002, "Before 0.5s must stay silent");
            assertTrue(Math.sqrt(energy[2] / energy[3]) > 0.1, "Tone at 0.5s must remain synchronized after trim");
            if (checkEnd) {
                assertTrue(energy[5] > 1000 && energy[7] > 1000);
                assertTrue(Math.sqrt(energy[4] / energy[5]) < 0.002, "Before the final marker must stay silent");
                assertTrue(Math.sqrt(energy[6] / energy[7]) > 0.1, "Final marker must remain synchronized after five minutes");
            }
            System.out.println("AAC stereo MP4 verified: video/audio start at zero, matched duration, tone at correct replay timestamp despite dropped video frames.");
        } finally {
            if (frame != null) av_frame_free(frame);
            if (packet != null) av_packet_free(packet);
            if (decoder != null) avcodec_free_context(decoder);
            if (!format.isNull()) avformat_close_input(format);
        }
    }

    private void accumulate(AVFrame frame, double[] energy, double duration) {
        assertEquals(AV_SAMPLE_FMT_FLTP, frame.format());
        var left = new FloatPointer(frame.data(0)).capacity(frame.nb_samples()).asBuffer();
        for (int i = 0; i < frame.nb_samples(); i++) {
            double seconds = (frame.pts() + i) / 48_000.0;
            int offset = seconds >= 0.1 && seconds < 0.3 ? 0 : seconds >= 0.6 && seconds < 0.9 ? 2
                    : seconds >= duration - 0.9 && seconds < duration - 0.7 ? 4
                    : seconds >= duration - 0.4 && seconds < duration - 0.2 ? 6 : -1;
            if (offset >= 0) { energy[offset] += left.get(i) * left.get(i); energy[offset + 1]++; }
        }
    }
}
