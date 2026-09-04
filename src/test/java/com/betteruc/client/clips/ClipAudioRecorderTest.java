package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class ClipAudioRecorderTest {
    private static final long ORIGIN = 10_000_000_000L;
    static final class FakeSource implements ClipAudioCaptureSource {
        final AtomicBoolean closed = new AtomicBoolean();
        boolean sent;
        public void awaitSamples() { LockSupport.parkNanos(1_000_000); }
        public WindowsProcessAudioCapture.Samples read() {
            if (sent) return null;
            sent = true;
            return new WindowsProcessAudioCapture.Samples(ORIGIN, new byte[480 * 4], false);
        }
        public void close() { closed.set(true); }
    }
    private static void await(BooleanSupplier ready) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(1_000_000);
        assertTrue(ready.getAsBoolean(), "Worker did not reach expected state");
    }
    @Test void offNeverOpensAnyCaptureDevice() {
        try (var recorder = new ClipAudioRecorder(30, ClipAudioOptions.legacy(false), s -> {}, n -> {},
                (kind, device) -> { fail("No device should be opened"); return null; })) {
            assertEquals(0, recorder.bytes());
            assertFalse(recorder.slice(ORIGIN, ORIGIN + 1_000_000_000L, ClipAudioOptions.legacy(false)).hasCapturedAudio());
        }
    }
    @Test void systemAndMicrophoneUseExactlyTwoIndependentSourcesAndSnapshotsSurviveClose() {
        var kinds = new CopyOnWriteArrayList<ClipAudioRecorder.Kind>();
        var sources = new CopyOnWriteArrayList<FakeSource>();
        var options = new ClipAudioOptions(ClipAudioOptions.Mode.SYSTEM, true, "headset", "mic", 80, 100);
        var recorder = new ClipAudioRecorder(30, options, s -> {}, n -> fail(n.toString()), (kind, device) -> {
            kinds.add(kind);
            assertEquals(kind == ClipAudioRecorder.Kind.MICROPHONE ? "mic" : "headset", device);
            var source = new FakeSource(); sources.add(source); return source;
        });
        try {
            await(() -> recorder.bytes() == 2 * 480 * 4);
            assertEquals(2, kinds.size());
            assertTrue(kinds.containsAll(List.of(ClipAudioRecorder.Kind.SYSTEM, ClipAudioRecorder.Kind.MICROPHONE)));
            assertFalse(kinds.contains(ClipAudioRecorder.Kind.GAME), "System mode must not duplicate Minecraft");
            var snapshot = recorder.slice(ORIGIN, ORIGIN + 1_000_000_000L, options);
            recorder.close();
            await(() -> sources.stream().allMatch(s -> s.closed.get()));
            assertEquals(0, recorder.bytes());
            assertTrue(snapshot.hasCapturedAudio());
        } finally { recorder.close(); }
    }
    @Test void unavailableMicrophoneDoesNotStopOutputOrFallBackToAnotherDevice() {
        var notices = new CopyOnWriteArrayList<ClipNotice>();
        var calls = new CopyOnWriteArrayList<String>();
        var source = new FakeSource();
        var options = new ClipAudioOptions(ClipAudioOptions.Mode.GAME, true, "", "missing-mic", 100, 100);
        try (var recorder = new ClipAudioRecorder(30, options, s -> {}, notices::add, (kind, device) -> {
            calls.add(kind + ":" + device);
            if (kind == ClipAudioRecorder.Kind.MICROPHONE) throw new java.io.IOException("Unplugged");
            return source;
        })) {
            await(() -> !notices.isEmpty() && recorder.bytes() > 0);
            assertEquals(2, calls.size());
            assertEquals(1, notices.size());
            assertTrue(notices.getFirst().title().contains("Mikrofon"));
            assertTrue(recorder.slice(ORIGIN, ORIGIN + 1_000_000_000L, options).hasCapturedAudio());
            assertFalse(source.closed.get());
        }
        await(source.closed::get);
    }
    @Test void microphoneOnlyDoesNotOpenOutputAndLateActivationIsClosedAfterStop() throws Exception {
        var opening = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var source = new FakeSource();
        var options = new ClipAudioOptions(ClipAudioOptions.Mode.OFF, true, "", "mic", 100, 100);
        var recorder = new ClipAudioRecorder(30, options, s -> {}, n -> {}, (kind, device) -> {
            assertEquals(ClipAudioRecorder.Kind.MICROPHONE, kind);
            opening.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return source;
        });
        try {
            assertTrue(opening.await(5, TimeUnit.SECONDS));
            recorder.close(); finish.countDown();
            await(source.closed::get);
            assertEquals(0, recorder.bytes());
            assertFalse(source.sent, "No microphone packets read after stop");
        } finally { recorder.close(); finish.countDown(); }
    }
}
