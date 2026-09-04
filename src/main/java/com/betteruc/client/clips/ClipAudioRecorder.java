package com.betteruc.client.clips;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Independent, bounded source workers: a missing microphone must not stop output or video. */
final class ClipAudioRecorder implements AutoCloseable {
    enum Kind { GAME, SYSTEM, MICROPHONE }
    interface Factory { ClipAudioCaptureSource open(Kind kind, String device) throws Exception; }
    private static final Semaphore OUTPUT_SLOT = new Semaphore(1), INPUT_SLOT = new Semaphore(1);
    private final List<Track> tracks;
    private final ClipAudioOptions options;

    ClipAudioRecorder(int seconds, ClipAudioOptions options, Consumer<String> diagnostics, Consumer<ClipNotice> notices) {
        this(seconds, options, diagnostics, notices, (kind, device) -> kind == Kind.GAME
                ? WindowsProcessAudioCapture.openCurrentProcess()
                : WindowsProcessAudioCapture.openEndpoint(kind == Kind.MICROPHONE, device));
    }
    ClipAudioRecorder(int seconds, ClipAudioOptions options, Consumer<String> diagnostics, Consumer<ClipNotice> notices, Factory factory) {
        this.options = options;
        var selected = new ArrayList<Track>();
        if (options.mode() != ClipAudioOptions.Mode.OFF) selected.add(new Track(seconds,
                options.mode() == ClipAudioOptions.Mode.GAME ? Kind.GAME : Kind.SYSTEM,
                options.outputDevice(), diagnostics, notices, factory));
        if (options.microphone()) selected.add(new Track(seconds, Kind.MICROPHONE,
                options.inputDevice(), diagnostics, notices, factory));
        tracks = List.copyOf(selected);
        tracks.forEach(Track::start);
    }
    String status() { return options.label() + (tracks.stream().anyMatch(t -> t.failed) ? " (Fehler)" : ""); }
    String details() { return String.join(" / ", tracks.stream().map(t -> t.label() + ": " + t.status).toList()); }
    long bytes() { return tracks.stream().mapToLong(t -> t.buffer.bytes()).sum(); }
    ClipAudioMix slice(long start, long end, ClipAudioOptions levels) {
        return new ClipAudioMix(tracks.stream().map(t -> new ClipAudioMix.Track(t.buffer.slice(start, end),
                t.kind == Kind.MICROPHONE ? levels.microphoneVolume() : levels.outputVolume())).toList());
    }
    public void close() { tracks.forEach(Track::close); }

    private static final class Track {
        private final ClipAudioBuffer buffer;
        private final Kind kind;
        private final String device;
        private final Consumer<String> diagnostics;
        private final Consumer<ClipNotice> notices;
        private final Factory factory;
        private volatile boolean running = true;
        private volatile boolean failed;
        private volatile String status = "startet";
        Track(int seconds, Kind kind, String device, Consumer<String> diagnostics, Consumer<ClipNotice> notices, Factory factory) {
            buffer = new ClipAudioBuffer(seconds);
            this.kind = kind; this.device = device;
            this.diagnostics = diagnostics; this.notices = notices; this.factory = factory;
        }
        String label() { return switch (kind) { case GAME -> "Spielton"; case SYSTEM -> "Ausgabeton"; case MICROPHONE -> "Mikrofon"; }; }
        void start() {
            Thread thread = new Thread(this::run, "betteruc-clip-audio-" + kind.name().toLowerCase(java.util.Locale.ROOT));
            thread.setDaemon(true);
            thread.start();
        }
        void run() {
            Semaphore slot = kind == Kind.MICROPHONE ? INPUT_SLOT : OUTPUT_SLOT;
            boolean acquired = false;
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
                while (running && !(acquired = slot.tryAcquire(25, TimeUnit.MILLISECONDS)) && System.nanoTime() < deadline) { }
                if (!running) return;
                if (!acquired) throw new IllegalStateException("Vorherige Tonaufnahme wird noch beendet; Aufnahme neu starten");
                try (var source = factory.open(kind, device)) {
                    if (!running) return;
                    status = "aktiv";
                    diagnostics.accept(label() + " aktiv: 48 kHz Stereo, lokal, kein automatischer Geräte-Fallback");
                    long nextDiagnostics = 0;
                    try {
                        while (running) {
                            source.awaitSamples();
                            WindowsProcessAudioCapture.Samples samples;
                            while (running && (samples = source.read()) != null) {
                                synchronized (this) { if (running) buffer.add(samples.startNanos(), samples.pcm()); }
                            }
                            long now = System.nanoTime();
                            if (now >= nextDiagnostics) {
                                String detail = source.diagnostics();
                                status = detail.isEmpty() ? "aktiv" : "aktiv | " + detail;
                                nextDiagnostics = now + TimeUnit.SECONDS.toNanos(2);
                            }
                        }
                    } finally {
                        String detail = source.diagnostics();
                        if (!detail.isEmpty()) diagnostics.accept(detail); // Counters only; never microphone PCM/speech.
                    }
                }
            } catch (Throwable error) {
                if (running) {
                    failed = true;
                    status = "nicht verfügbar: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                    diagnostics.accept(label() + " fehlgeschlagen: " + error);
                    notices.accept(ClipNotice.warning(label() + " nicht verfügbar", "Diese Tonquelle fehlt; Video läuft weiter."));
                }
            } finally { if (acquired) slot.release(); }
        }
        synchronized void close() { running = false; buffer.clear(); }
    }
}
