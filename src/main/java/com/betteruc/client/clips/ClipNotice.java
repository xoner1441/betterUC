package com.betteruc.client.clips;

import java.util.Locale;

/** Typed recorder events: presentation must not depend on matching translated chat messages. */
public record ClipNotice(Kind kind, String title, String detail, String footer) {
    public enum Kind {
        RECORDING, INFO, WARNING, RECORDING_FAILED, SAVING, SAVED, EXPORT_FAILED
    }

    public boolean exportEvent() {
        return kind == Kind.SAVING || kind == Kind.SAVED || kind == Kind.EXPORT_FAILED;
    }

    public boolean important() {
        return kind == Kind.WARNING || kind == Kind.RECORDING_FAILED || kind == Kind.EXPORT_FAILED;
    }

    public int accent() {
        return switch (kind) {
            case SAVED -> 0xFF4ADE80;
            case RECORDING_FAILED, EXPORT_FAILED -> 0xFFFF5555;
            case WARNING -> 0xFFFBBF24;
            default -> 0xFF38BDF8;
        };
    }

    public long durationMs() { return important() ? 6_000 : 4_000; }

    public static ClipNotice recording(ClipSettings settings, String audioStatus) {
        return new ClipNotice(Kind.RECORDING, "Clip-Aufnahme aktiv",
                settings.width() + "x" + settings.height() + " · " + settings.fps() + " FPS",
                audioStatus + " · Puffer " + settings.seconds() + " s");
    }

    public static ClipNotice info(String detail) {
        return new ClipNotice(Kind.INFO, "betterUC Clips", detail, "Details: /buclip");
    }

    public static ClipNotice warning(String title, String detail) {
        return new ClipNotice(Kind.WARNING, title, detail, "Details: /buclip");
    }

    public static ClipNotice recordingFailed(String reason) {
        return new ClipNotice(Kind.RECORDING_FAILED, "Aufnahme nicht verfügbar", reason, "Details: /buclip");
    }

    public static ClipNotice saving(boolean audio) {
        return new ClipNotice(Kind.SAVING, "Clip wird gespeichert …",
                audio ? "Video mit Ton" : "Video ohne Ton", "Lokal · bitte kurz warten");
    }

    public static ClipNotice saved(double seconds, ClipSettings settings, boolean audio) {
        return new ClipNotice(Kind.SAVED, "Clip gespeichert",
                String.format(Locale.GERMANY, "%.1f s · %dx%d · %d FPS", seconds, settings.width(), settings.height(), settings.fps()),
                (audio ? "Mit Ton" : "Ohne Ton") + " · Ordner: /buclip folder");
    }

    public static ClipNotice exportFailed() {
        return new ClipNotice(Kind.EXPORT_FAILED, "Speichern fehlgeschlagen",
                "Clip konnte nicht gesichert werden.", "Details stehen in latest.log");
    }
}
