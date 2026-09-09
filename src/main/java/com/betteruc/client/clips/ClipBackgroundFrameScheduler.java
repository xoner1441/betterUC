package com.betteruc.client.clips;

/** Supplies a sparse still-frame timeline while an unfocused game renders no new frames. */
final class ClipBackgroundFrameScheduler {
    static final long INTERVAL_NANOS = 1_000_000_000L;

    private ClipBackgroundFrameScheduler() {}

    static long duePts(boolean backgrounded, boolean frameAvailable, long timelineStartNanos,
                       long lastFrameWallNanos, long nowNanos, long lastVideoPts, int fps) {
        if (!backgrounded || !frameAvailable || timelineStartNanos <= 0 || lastFrameWallNanos <= 0
                || nowNanos < lastFrameWallNanos || nowNanos - lastFrameWallNanos < INTERVAL_NANOS) return -1;
        long elapsed = Math.max(0, nowNanos - timelineStartNanos);
        long seconds = elapsed / 1_000_000_000L;
        long remainder = elapsed % 1_000_000_000L;
        long timelinePts = seconds * fps + remainder * fps / 1_000_000_000L;
        return Math.max(lastVideoPts + 1, timelinePts);
    }
}
