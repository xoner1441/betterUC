package com.betteruc.client.clips;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in integration test: synthetic pixels only, never screen/audio capture. */
@EnabledIfSystemProperty(named = "betteruc.clipHardwareTest", matches = "true")
class HardwareClipEncoderTest {
    @Test void storageChangesApplyToNewSavesWithoutRedirectingAnExport(@TempDir Path directory) throws Exception {
        var settings = new ClipSettings(320, 180, 60, 15, 2_000_000);
        var exports = new LinkedBlockingQueue<Runnable>();
        var saved = new CopyOnWriteArrayList<Path>();
        var exporting = new AtomicBoolean();
        Path original = directory.resolve("Alter Ort äöü").resolve("buclips");
        Path next = directory.resolve("Neuer Ort ß").resolve("buclips");
        Path unusedDefault = directory.resolve("betteruc-clips");
        var session = new ClipRecorderSession(settings, unusedDefault, System.out::println,
                System.out::println, saved::add, exports::add, exporting);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!session.ready() && !session.finished() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.ready(), session.failure());
            var pixels = ByteBuffer.allocateDirect(320 * 180 * 4);
            for (int i = 0; i < 100; i++) {
                var done = new CountDownLatch(1);
                session.submit(pixels, i, done::countDown);
                assertTrue(done.await(2, TimeUnit.SECONDS));
            }
            session.requestSave(original);
            Runnable first = exports.poll(10, TimeUnit.SECONDS);
            assertNotNull(first);
            // Simulate choosing another directory while the first export is pending.
            session.requestSave(next);
            first.run();
            assertFalse(exporting.get());
            assertEquals(1, saved.size());
            assertEquals(original, saved.getFirst().getParent());
            assertTrue(Files.size(saved.getFirst()) > 1000);
            assertFalse(Files.exists(next));
            session.requestSave(next);
            Runnable second = exports.poll(10, TimeUnit.SECONDS);
            assertNotNull(second);
            second.run();
            assertEquals(2, saved.size());
            assertEquals(next, saved.getLast().getParent());
            assertTrue(Files.size(saved.getLast()) > 1000);
            assertTrue(Files.exists(saved.getFirst()));
            assertFalse(Files.exists(unusedDefault));
            assertTrue(session.ready());
        } finally {
            session.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!session.finished() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.finished());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedOrRejectedExportEndsSavingNoticeWithoutStoppingCapture(boolean reject, @TempDir Path directory) throws Exception {
        var settings = new ClipSettings(320, 180, 60, 15, 2_000_000);
        var exporting = new AtomicBoolean();
        var failed = new CountDownLatch(1);
        var notices = new CopyOnWriteArrayList<ClipNotice>();
        Path output = reject ? directory : Files.createFile(directory.resolve("not-a-directory"));
        Executor executor = reject ? task -> { throw new RejectedExecutionException("Synthetic rejection"); } : Runnable::run;
        var session = new ClipRecorderSession(settings, output, System.out::println, notice -> {
            notices.add(notice);
            if (notice.kind() == ClipNotice.Kind.EXPORT_FAILED) failed.countDown();
        }, path -> fail("No file should be saved"), executor, exporting);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!session.ready() && !session.finished() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.ready(), session.failure());
            var pixels = ByteBuffer.allocateDirect(320 * 180 * 4);
            for (int i = 0; i < 90; i++) {
                var done = new CountDownLatch(1);
                session.submit(pixels, i, done::countDown);
                assertTrue(done.await(2, TimeUnit.SECONDS));
            }
            session.requestSave();
            assertTrue(failed.await(10, TimeUnit.SECONDS));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (exporting.get() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(exporting.get());
            assertTrue(session.ready(), "Export failure must leave the recording available");
            assertEquals(List.of(ClipNotice.Kind.SAVING, ClipNotice.Kind.EXPORT_FAILED),
                    notices.stream().map(ClipNotice::kind).toList());
        } finally {
            session.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!session.finished() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.finished());
        }
    }

    @Test void sessionExportsAndReleasesEverySubmittedBuffer() throws Exception {
        var settings = new ClipSettings(320, 180, 60, 15, 2_000_000);
        var exporting = new AtomicBoolean();
        var released = new AtomicInteger();
        var output = new AtomicReference<Path>();
        var saved = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var notices = new CopyOnWriteArrayList<ClipNotice>();
        var session = new ClipRecorderSession(settings, Path.of("build", "clip-test", "session"),
                System.out::println, notices::add, path -> { output.set(path); saved.countDown(); }, executor, exporting);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!session.ready() && !session.finished() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.ready(), session.failure());
            ByteBuffer frame = ByteBuffer.allocateDirect(320 * 180 * 4);
            for (int i = 0; i < 100; i++) {
                var done = new CountDownLatch(1);
                session.submit(frame, i, () -> { released.incrementAndGet(); done.countDown(); });
                assertTrue(done.await(2, TimeUnit.SECONDS));
            }
            session.requestSave();
            assertTrue(saved.await(10, TimeUnit.SECONDS));
            assertTrue(Files.size(output.get()) > 1000);
            assertEquals(List.of(ClipNotice.Kind.SAVING, ClipNotice.Kind.SAVED),
                    notices.stream().map(ClipNotice::kind).toList());
            assertTrue(notices.getLast().detail().contains("320x180 · 60 FPS"));
            assertTrue(notices.getLast().footer().startsWith("Ohne Ton"));
            // Closing and late GPU callbacks must not leak their buffer leases or enqueue after shutdown.
            session.close();
            session.submit(frame, 100, released::incrementAndGet);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!session.finished() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(session.finished());
            assertEquals(101, released.get());
            assertEquals(0, session.bufferBytes());
        } finally {
            session.close();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @CsvSource({"720,30", "720,60", "1080,30", "1080,60"})
    void hardwarePresetEncodeRemuxAndDecode(int resolutionHeight, int fps) throws Exception {
        System.out.println("FFmpeg license: " + avcodec_license().getString());
        System.out.println("FFmpeg build: " + avcodec_configuration().getString());
        var settings = ClipSettings.forViewport(1920, 1080, 30, resolutionHeight, fps);
        int frameCount = fps * 3;
        ByteBuffer pixels = ByteBuffer.allocateDirect(settings.width() * settings.height() * 4);
        // Bottom-up input: black bottom half, white top half. Detect orientation via decoded luminance.
        for (int y = 0; y < settings.height(); y++) {
            byte color = (byte) (y < settings.height() / 2 ? 0 : 255);
            for (int x = 0; x < settings.width(); x++) pixels.put(color).put(color).put(color).put((byte) 255);
        }
        pixels.flip();
        List<ClipPacket> packets = new ArrayList<>();
        HardwareClipEncoder.Header header;
        long elapsed;
        try (var encoder = HardwareClipEncoder.open(settings, System.out::println)) {
            long start = System.nanoTime();
            for (int i = 0; i < frameCount; i++) encoder.encode(pixels, i < frameCount / 2 ? i : i + fps / 10, packets::add);
            encoder.flush(packets::add);
            elapsed = System.nanoTime() - start;
            header = encoder.header();
            System.out.printf("Encoder: %s; synthetic %dp%d throughput: %.1f FPS%n",
                    encoder.name(), resolutionHeight, fps, frameCount * 1e9 / elapsed);
        }
        assertEquals(frameCount, packets.size());
        assertTrue(packets.getFirst().keyframe());
        for (int i = 1; i < packets.size(); i++) assertTrue(packets.get(i).pts() > packets.get(i - 1).pts());
        Path directory = Path.of("build", "clip-test");
        Files.createDirectories(directory);
        Path video = directory.resolve("synthetic-" + resolutionHeight + "p" + fps + ".mp4");
        HardwareClipEncoder.writeMp4(video, header, packets);
        inspectAndDecode(video, settings, frameCount, 3.1);
        var ring = new ClipPacketRing(fps, settings.maxBufferBytes());
        packets.forEach(ring::add);
        var trimmed = ring.snapshot();
        assertTrue(trimmed.getFirst().pts() > 0);
        Path replay = directory.resolve("synthetic-trimmed-" + resolutionHeight + "p" + fps + ".mp4");
        HardwareClipEncoder.writeMp4(replay, header, trimmed);
        inspectAndDecode(replay, settings, trimmed.size(), (trimmed.getLast().pts() - trimmed.getFirst().pts() + 1) / (double) fps);
        assertTrue(Files.size(video) > 1000);
        System.out.println("Validated video and keyframe-trimmed replay: " + directory.toAbsolutePath());
    }

    private void inspectAndDecode(Path video, ClipSettings settings, int expectedFrames, double expectedSeconds) {
        AVFormatContext format = new AVFormatContext(null);
        AVCodecContext decoder = null;
        AVFrame frame = null;
        AVPacket packet = null;
        try {
            assertTrue(avformat_open_input(format, video.toString(), null, null) >= 0);
            assertTrue(avformat_find_stream_info(format, (AVDictionary) null) >= 0);
            assertEquals(1, format.nb_streams(), "No audio or additional tracks");
            var stream = format.streams(0);
            assertEquals(settings.width(), stream.codecpar().width());
            assertEquals(settings.height(), stream.codecpar().height());
            assertEquals(settings.fps(), av_q2d(stream.r_frame_rate()), 0.001);
            assertEquals(AV_CODEC_ID_H264, stream.codecpar().codec_id());
            assertEquals(expectedSeconds, stream.duration() * av_q2d(stream.time_base()), 0.025);
            var codec = avcodec_find_decoder(AV_CODEC_ID_H264);
            decoder = avcodec_alloc_context3(codec);
            assertTrue(avcodec_parameters_to_context(decoder, stream.codecpar()) >= 0);
            assertTrue(avcodec_open2(decoder, codec, (AVDictionary) null) >= 0);
            frame = av_frame_alloc();
            packet = av_packet_alloc();
            int decoded = 0;
            while (av_read_frame(format, packet) >= 0) {
                assertTrue(avcodec_send_packet(decoder, packet) >= 0);
                av_packet_unref(packet);
                while (avcodec_receive_frame(decoder, frame) >= 0) { checkOrientation(frame); decoded++; }
            }
            assertTrue(avcodec_send_packet(decoder, null) >= 0);
            while (avcodec_receive_frame(decoder, frame) >= 0) { checkOrientation(frame); decoded++; }
            assertEquals(expectedFrames, decoded);
        } finally {
            if (packet != null) av_packet_free(packet);
            if (frame != null) av_frame_free(frame);
            if (decoder != null) avcodec_free_context(decoder);
            if (!format.isNull()) avformat_close_input(format);
        }
    }

    private void checkOrientation(AVFrame frame) {
        int top = frame.data(0).get(10L * frame.linesize(0) + 10) & 255;
        int bottom = frame.data(0).get((frame.height() - 10L) * frame.linesize(0) + 10) & 255;
        assertTrue(top > 210, "Top must be white, got " + top);
        assertTrue(bottom < 30, "Bottom must be black, got " + bottom);
    }
}
