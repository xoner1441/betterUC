package com.betteruc.client.clips;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Bounded hand-off from GPU readback to one encoder thread. No Minecraft classes or rendering here. */
public final class ClipRecorderSession implements AutoCloseable {
    private record Frame(ByteBuffer pixels, long pts, Runnable release) {}
    private final ClipSettings settings;
    private final Path outputDirectory;
    private final Consumer<String> diagnostics;
    private final Consumer<Path> saved;
    private final Consumer<ClipNotice> notification;
    private final Executor exportExecutor;
    private final AtomicBoolean exporting;
    private final AtomicReference<Path> saveRequested = new AtomicReference<>();
    private final ArrayBlockingQueue<Frame> frames = new ArrayBlockingQueue<>(3);
    private final ClipPacketRing ring;
    private volatile ClipAudioOptions audioOptions;
    private volatile ClipAudioRecorder gameAudio;
    private volatile long timelineStartNanos;
    private volatile boolean running = true;
    private volatile boolean ready;
    private volatile boolean finished;
    private volatile String failure = "";
    private volatile String encoderName = "Encoder wird geprüft";
    private volatile double encodedFps;
    private volatile long skipped;

    public ClipRecorderSession(ClipSettings settings, Path outputDirectory, Consumer<String> diagnostics,
                               Consumer<ClipNotice> notification, Consumer<Path> saved,
                               Executor exportExecutor, AtomicBoolean exporting) {
        this(settings, outputDirectory, diagnostics, notification, saved, exportExecutor, exporting, false);
    }

    public ClipRecorderSession(ClipSettings settings, Path outputDirectory, Consumer<String> diagnostics,
                               Consumer<ClipNotice> notification, Consumer<Path> saved,
                               Executor exportExecutor, AtomicBoolean exporting, boolean gameAudioEnabled) {
        this(settings, outputDirectory, diagnostics, notification, saved, exportExecutor, exporting, ClipAudioOptions.legacy(gameAudioEnabled));
    }

    public ClipRecorderSession(ClipSettings settings, Path outputDirectory, Consumer<String> diagnostics,
                               Consumer<ClipNotice> notification, Consumer<Path> saved,
                               Executor exportExecutor, AtomicBoolean exporting, ClipAudioOptions audioOptions) {
        this.settings = settings;
        this.outputDirectory = outputDirectory;
        this.diagnostics = diagnostics;
        this.notification = notification;
        this.saved = saved;
        this.exportExecutor = exportExecutor;
        this.exporting = exporting;
        this.audioOptions = audioOptions;
        ring = new ClipPacketRing((long) settings.seconds() * settings.fps(), settings.maxBufferBytes());
        Thread worker = new Thread(this::run, "betteruc-clip-encoder");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    public ClipSettings settings() { return settings; }
    public ClipAudioOptions audioOptions() { return audioOptions; }
    public void updateAudioLevels(ClipAudioOptions options) {
        if (!audioOptions.sameSources(options)) throw new IllegalArgumentException("Source changes require restarting capture");
        audioOptions = options;
    }
    public boolean ready() { return ready && running; }
    public boolean finished() { return finished; }
    public String failure() { return failure; }
    public double seconds() { return ring.seconds(settings.fps()); }
    public long bufferBytes() { return ring.bytes(); }
    public void beginTimeline(long startNanos) { if (timelineStartNanos == 0) timelineStartNanos = startNanos; }
    public String audioStatus() {
        ClipAudioRecorder current = gameAudio;
        return current != null ? current.status() : audioOptions.label();
    }
    public String details() {
        ClipAudioRecorder current = gameAudio;
        return String.format(Locale.ROOT, "%s | %.1f FPS | %.0f MB | %d ausgelassen | %s",
                encoderName, encodedFps, (bufferBytes() + (current == null ? 0 : current.bytes())) / 1048576.0, skipped,
                current == null ? audioStatus() : current.details())
                + " | Video-Pufferlimit " + settings.maxBufferBytes() / 1048576 + " MiB"
                + (ring.memoryLimited() ? " | RAM-Grenze erreicht: Clips ggf. kürzer" : "");
    }

    public void skipped(long count) { skipped += count; }

    /** Caller transfers the buffer lease; the release callback runs even if the queue is full. */
    public synchronized void submit(ByteBuffer pixels, long pts, Runnable release) {
        if (!ready() || !frames.offer(new Frame(pixels, pts, release))) {
            skipped(1);
            release.run();
        }
    }

    public void requestSave() {
        requestSave(outputDirectory);
    }

    /** Freeze the target when saving is requested, even if the user changes it during export. */
    public void requestSave(Path destination) {
        if (!ready()) { notification.accept(ClipNotice.info("Clip-Puffer ist noch nicht bereit.")); return; }
        if (exporting.get() || !saveRequested.compareAndSet(null, destination.toAbsolutePath().normalize())) {
            notification.accept(ClipNotice.info("Ein Clip wird bereits gespeichert."));
        }
    }

    private void run() {
        try {
            if (!running) return;
            try (HardwareClipEncoder encoder = HardwareClipEncoder.open(settings, diagnostics)) {
                encoderName = encoder.name();
                synchronized (this) {
                    if (running && audioOptions.enabled()) gameAudio = new ClipAudioRecorder(settings.seconds(), audioOptions, diagnostics, notification);
                }
                ready = true;
                long encodedFrames = 0;
                long sampleStart = System.nanoTime();
                boolean memoryLimitReported = false;
                while (running) {
                    Frame frame = frames.poll(50, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        try {
                            if (running) { encoder.encode(frame.pixels(), frame.pts(), ring::add); encodedFrames++; }
                        } finally { frame.release().run(); }
                    }
                    long now = System.nanoTime();
                    if (now - sampleStart >= 1_000_000_000L) {
                        encodedFps = encodedFrames * 1_000_000_000.0 / (now - sampleStart);
                        encodedFrames = 0;
                        sampleStart = now;
                    }
                    if (!memoryLimitReported && ring.memoryLimited()) {
                        memoryLimitReported = true;
                        notification.accept(ClipNotice.warning("Clip-Puffer am RAM-Limit", "Clips ggf. kürzer; Qualität senken."));
                    }
                    if (running) {
                        Path destination = saveRequested.getAndSet(null);
                        if (destination != null) export(encoder, destination);
                    }
                }
            }
        } catch (Throwable error) {
            if (running) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                diagnostics.accept("Clip-Aufnahme gestoppt: " + failure);
                // CaptureClient reports this once when the failed session is collected on the render thread.
            }
        } finally {
            synchronized (this) {
                ready = false;
                running = false;
                if (gameAudio != null) gameAudio.close();
                Frame pending;
                while ((pending = frames.poll()) != null) pending.release().run();
            }
            ring.clear();
            finished = true;
        }
    }

    private void export(HardwareClipEncoder encoder, Path destination) {
        var packets = ring.snapshot();
        if (packets.size() < 2 || ring.seconds(settings.fps()) < 1) {
            notification.accept(ClipNotice.info("Noch zu wenig Videomaterial. Bitte warten."));
            return;
        }
        var header = encoder.header();
        ClipPcmSource audioSlice = null;
        ClipAudioRecorder currentAudio = gameAudio;
        if (currentAudio != null && timelineStartNanos != 0) {
            long start = timelineStartNanos + packets.getFirst().pts() * 1_000_000_000L / settings.fps();
            ClipPacket last = packets.getLast();
            long end = timelineStartNanos + (last.pts() + Math.max(1, last.duration())) * 1_000_000_000L / settings.fps();
            try { audioSlice = currentAudio.slice(start, end, audioOptions); }
            catch (RuntimeException error) { diagnostics.accept("Audio-Zeitraum ungültig; speichere Video ohne Ton: " + error); }
        }
        ClipPcmSource selectedAudio = audioSlice;
        if (!exporting.compareAndSet(false, true)) { notification.accept(ClipNotice.info("Ein Clip wird bereits gespeichert.")); return; }
        boolean hasAudio = selectedAudio != null && selectedAudio.hasCapturedAudio();
        notification.accept(ClipNotice.saving(hasAudio));
        try { exportExecutor.execute(() -> {
            Path temporary = null;
            try {
                Files.createDirectories(destination);
                String name = "clip_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                        + "_" + UUID.randomUUID().toString().substring(0, 8);
                temporary = destination.resolve(name + ".part");
                Path output = destination.resolve(name + ".mp4");
                ClipAudioEncoder.Encoded audio = null;
                if (hasAudio) {
                    try { audio = ClipAudioEncoder.encode(selectedAudio); }
                    catch (Exception | LinkageError error) {
                        diagnostics.accept("AAC fehlgeschlagen; speichere Video ohne Ton: " + error);
                        notification.accept(ClipNotice.saving(false));
                    }
                }
                HardwareClipEncoder.writeMp4(temporary, header, packets, audio);
                Files.move(temporary, output);
                ClipPacket last = packets.getLast();
                double seconds = (last.pts() + Math.max(1, last.duration()) - packets.getFirst().pts()) / (double) settings.fps();
                notification.accept(ClipNotice.saved(seconds, settings, audio != null));
                saved.accept(output);
            } catch (Throwable error) {
                diagnostics.accept("Clip-Export fehlgeschlagen: " + error);
                notification.accept(ClipNotice.exportFailed());
                if (temporary != null) {
                    try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
                }
            } finally { exporting.set(false); }
        }); } catch (RuntimeException error) {
            exporting.set(false);
            diagnostics.accept("Clip-Export konnte nicht gestartet werden: " + error);
            notification.accept(ClipNotice.exportFailed());
        }
    }

    @Override public synchronized void close() {
        ready = false;
        running = false;
        if (gameAudio != null) gameAudio.close();
    }
}
