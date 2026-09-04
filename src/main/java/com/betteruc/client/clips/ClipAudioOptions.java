package com.betteruc.client.clips;

import com.betteruc.config.BetterUCConfig;

/** Local, explicit audio consent. Empty mode migrates the old game-only setting without broadening it. */
public record ClipAudioOptions(Mode mode, boolean microphone, String outputDevice, String inputDevice,
                               int outputVolume, int microphoneVolume) {
    public enum Mode {
        OFF("Aus"), GAME("Nur Minecraft"), SYSTEM("Gesamter Ausgabeton");
        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return label; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public ClipAudioOptions {
        if (mode == null) mode = Mode.OFF;
        outputDevice = outputDevice == null ? "" : outputDevice;
        inputDevice = inputDevice == null ? "" : inputDevice;
        outputVolume = Math.clamp(outputVolume, 0, 100);
        microphoneVolume = Math.clamp(microphoneVolume, 0, 100);
    }
    public static ClipAudioOptions fromConfig(BetterUCConfig config) {
        Mode mode = Mode.OFF;
        String value = config.clipAudioMode;
        if (value == null || value.isEmpty()) mode = config.clipsGameAudioEnabled ? Mode.GAME : Mode.OFF;
        else {
            try { mode = Mode.valueOf(value); } catch (IllegalArgumentException ignored) { /* Fail closed. */ }
        }
        return new ClipAudioOptions(mode, config.clipsMicrophoneEnabled, config.clipOutputDevice,
                config.clipInputDevice, config.clipOutputVolume, config.clipMicrophoneVolume);
    }
    public static ClipAudioOptions legacy(boolean game) {
        return new ClipAudioOptions(game ? Mode.GAME : Mode.OFF, false, "", "", 100, 100);
    }
    public boolean enabled() { return mode != Mode.OFF || microphone; }
    public boolean sameSources(ClipAudioOptions other) {
        return other != null && mode == other.mode && microphone == other.microphone
                && outputDevice.equals(other.outputDevice) && inputDevice.equals(other.inputDevice);
    }
    public String label() {
        return (mode == Mode.OFF ? "Ton aus" : mode == Mode.GAME ? "Spielton" : "Ausgabeton")
                + (microphone ? (mode == Mode.OFF ? " / Mikrofon an" : " + Mikrofon") : "");
    }
}
