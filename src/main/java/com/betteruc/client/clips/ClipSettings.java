package com.betteruc.client.clips;

/** Local-only capture presets. Resolution is a bounding box, never an upscale target. */
public record ClipSettings(int width, int height, int fps, int seconds, int bitrate) {
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 300;
    public static final int DEFAULT_SECONDS = 30;
    public static final int AUDIO_RESERVE_SECONDS = 5;
    public static final long MAX_VIDEO_BUFFER_BYTES = 1024L * 1024 * 1024;
    private static final int[] DURATION_PRESETS = {15, 30, 60, 90, 120, 180, 300};

    public ClipSettings {
        if (width < 2 || height < 2 || (width & 1) != 0 || (height & 1) != 0
                || fps < 1 || fps > 60 || seconds < MIN_SECONDS || seconds > MAX_SECONDS || bitrate < 1) {
            throw new IllegalArgumentException("Invalid clip settings");
        }
    }

    public static ClipSettings forViewport(int width, int height, int seconds) {
        return forViewport(width, height, seconds, 1080, 60);
    }

    public static ClipSettings forViewport(int width, int height, int seconds, int resolutionHeight, int fps) {
        if (width < 2 || height < 2) throw new IllegalArgumentException("Invalid viewport");
        int maxHeight = normalizeResolutionHeight(resolutionHeight);
        int maxWidth = maxHeight == 720 ? 1280 : 1920;
        double scale = Math.min(1.0, Math.min((double) maxWidth / width, (double) maxHeight / height));
        int outputWidth = Math.max(2, ((int) (width * scale)) & ~1);
        int outputHeight = Math.max(2, ((int) (height * scale)) & ~1);
        return new ClipSettings(outputWidth, outputHeight, normalizeFps(fps), normalizeSeconds(seconds),
                bitrateForPreset(maxHeight, fps));
    }

    public static int normalizeResolutionHeight(int height) {
        return height == 720 ? 720 : 1080;
    }

    public static int normalizeFps(int fps) {
        return fps == 30 ? 30 : 60;
    }

    public static int bitrateForPreset(int resolutionHeight, int fps) {
        if (normalizeResolutionHeight(resolutionHeight) == 720) {
            return normalizeFps(fps) == 30 ? 8_000_000 : 12_000_000;
        }
        return normalizeFps(fps) == 30 ? 16_000_000 : 25_000_000;
    }

    public static int normalizeSeconds(int seconds) {
        return seconds < MIN_SECONDS ? DEFAULT_SECONDS : Math.min(seconds, MAX_SECONDS);
    }

    public static int parseSeconds(String text) {
        if (text == null || !text.trim().matches("[0-9]{1,3}")) return -1;
        int seconds = Integer.parseInt(text.trim());
        return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS ? seconds : -1;
    }

    public static int nextDurationPreset(int seconds) {
        int current = normalizeSeconds(seconds);
        for (int preset : DURATION_PRESETS) if (preset > current) return preset;
        return DURATION_PRESETS[0];
    }

    public long maxBufferBytes() {
        return maxBufferBytes(Runtime.getRuntime().maxMemory());
    }

    long maxBufferBytes(long maxHeapBytes) {
        // Bound even misbehaving VBR output; leave room for a complete keyframe group.
        // Two rings can coexist during export. Together they must not reserve most of the game heap.
        long budget = Math.min(MAX_VIDEO_BUFFER_BYTES, Math.max(1, maxHeapBytes / 8));
        return Math.min(budget, (long) bitrate / 8 * (seconds + 3) * 2);
    }
}
