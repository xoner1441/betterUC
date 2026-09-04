package com.betteruc.client.clips;

/**
 * Continuous PCM inside active playback, absolute placement after real playback pauses.
 * Loopback may produce no packets at all while the selected application/device is silent.
 * Neither scheduler delays nor per-packet QPC jitter alone prove such a pause.
 */
final class ClipLoopbackClock {
    private static final long MIN_OBSERVED_IDLE = 20_000_000L;
    private static final long MIN_RESUME_GAP = 10_000_000L;
    private ClipAudioSampleClock segment = new ClipAudioSampleClock();
    private ClipAudioSampleClock.Statistics completed = new ClipAudioSampleClock.Statistics(0, 0, 0);
    private boolean started;
    private long endNanos;
    private long emptySince = Long.MIN_VALUE;
    private long emptyUntil = Long.MIN_VALUE;
    private long resumes;

    void noPacket(long nowNanos) {
        if (emptySince == Long.MIN_VALUE) emptySince = nowNanos;
        emptyUntil = nowNanos;
    }
    /** Ask WASAPI for queued frames only when an invalid QPC needs an initial/resume anchor. */
    boolean needsQueueAnchor() { return !started || observedIdle(); }
    private boolean observedIdle() {
        return emptySince != Long.MIN_VALUE && emptyUntil - emptySince >= MIN_OBSERVED_IDLE;
    }
    long timestamp(int frames, long devicePosition, long reportedNanos, boolean timestampValid,
                   boolean discontinuity, long arrivalNanos, long queuedFrames) {
        boolean valid = timestampValid && Math.abs(reportedNanos - arrivalNanos) <= 2_000_000_000L;
        // CurrentPadding includes this packet. A backlog must not be mistaken for recent sound.
        long pending = Math.max(frames, Math.min(queuedFrames, 2L * ClipAudioBuffer.SAMPLE_RATE));
        long fallbackStart = arrivalNanos - nanos(pending);
        long candidate = valid ? reportedNanos : fallbackStart;
        if (started && candidate - endNanos >= MIN_RESUME_GAP
                && (observedIdle() || valid && discontinuity && candidate - endNanos > 10_000_000_000L)) {
            completed = completed.plus(segment.statistics());
            segment = new ClipAudioSampleClock();
            resumes++;
            started = false;
        }
        // For invalid first packets, feed a queue-adjusted arrival to the sample clock's fallback.
        // Subsequent invalid packets stay contiguous; they never use per-packet worker timing.
        long clockArrival = !started && !valid ? fallbackStart + nanos(frames) : arrivalNanos;
        long result = segment.timestamp(frames, devicePosition, reportedNanos, valid, discontinuity, clockArrival);
        started = true;
        endNanos = result + nanos(frames);
        emptySince = emptyUntil = Long.MIN_VALUE;
        return result;
    }
    private static long nanos(long frames) { return Math.round(frames * (1_000_000_000.0 / ClipAudioBuffer.SAMPLE_RATE)); }
    String diagnostics(String label) {
        return completed.plus(segment.statistics()).describe(label) + " / " + resumes + " fortgesetzte Tonabschnitte";
    }
}
