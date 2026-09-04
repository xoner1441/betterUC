package com.betteruc.client.clips;

/**
 * Continuous audio sample clock. QPC anchors the stream to video, but packet-to-packet
 * timestamp jitter must not chop up otherwise contiguous PCM. Device positions preserve real loss.
 * All positions are in the negotiated capture format's sample frames (IAudioCaptureClient::GetBuffer).
 */
final class ClipAudioSampleClock {
    private static final long MAX_POSITION_GAP = 10L * ClipAudioBuffer.SAMPLE_RATE;
    private boolean started;
    private long originNanos;
    private long nextFrame;
    private boolean deviceKnown;
    private long nextDeviceFrame;
    private long timestampErrors;
    private long jitterPackets;
    private long missingFrames;

    long timestamp(int frames, long devicePosition, long reportedNanos, boolean timestampValid,
                   boolean discontinuity, long arrivalNanos) {
        if (frames <= 0 || frames > ClipAudioBuffer.SAMPLE_RATE * 2) throw new IllegalArgumentException("Invalid audio packet size");
        // Drivers may return success but an unusable QPC value. Never anchor to an unrelated epoch.
        timestampValid &= Math.abs(reportedNanos - arrivalNanos) <= 2_000_000_000L;
        if (!timestampValid) timestampErrors++;
        if (!started) {
            originNanos = timestampValid ? reportedNanos : arrivalNanos - nanos(frames);
            started = true;
        } else {
            long expectedNanos = originNanos + nanos(nextFrame);
            long gap = 0;
            if (timestampValid && deviceKnown && devicePosition >= nextDeviceFrame) {
                long deviceGap = devicePosition - nextDeviceFrame;
                long clockGap = ClipAudioBuffer.sampleOffset(reportedNanos - expectedNanos);
                // Require a loss flag or corroborating time gap; don't mistake a broken device
                // counter or a converter's different frame units for missing audio samples.
                if (deviceGap <= MAX_POSITION_GAP && (discontinuity
                        || deviceGap > 0 && Math.abs(clockGap - deviceGap) <= 48)) gap = deviceGap;
            } else if (timestampValid && discontinuity) {
                // Counter reset/reconnect: use the absolute clock only for a genuine forward gap.
                long clockGap = ClipAudioBuffer.sampleOffset(reportedNanos - expectedNanos);
                if (clockGap > 480 && clockGap <= MAX_POSITION_GAP) gap = clockGap;
            }
            nextFrame += gap;
            missingFrames += gap;
            if (timestampValid && Math.abs(reportedNanos - (originNanos + nanos(nextFrame))) > 25_000L) jitterPackets++;
        }
        long result = originNanos + nanos(nextFrame);
        nextFrame += frames;
        if (timestampValid && devicePosition >= 0) {
            deviceKnown = true;
            nextDeviceFrame = devicePosition + frames;
        } else if (deviceKnown) {
            // A bad timestamp must not use the worker's arrival time for each queued packet.
            nextDeviceFrame += frames;
        }
        return result;
    }

    private static long nanos(long frames) { return Math.round(frames * (1_000_000_000.0 / ClipAudioBuffer.SAMPLE_RATE)); }
    record Statistics(long timestampErrors, long jitterPackets, long missingFrames) {
        Statistics plus(Statistics other) {
            return new Statistics(timestampErrors + other.timestampErrors, jitterPackets + other.jitterPackets,
                    missingFrames + other.missingFrames);
        }
        String describe(String label) {
            return label + "-Takt: " + timestampErrors + " ungültige Zeitstempel, " + jitterPackets
                    + " geglättete Paketzeitstempel, " + missingFrames + " fehlende Samples";
        }
    }
    Statistics statistics() { return new Statistics(timestampErrors, jitterPackets, missingFrames); }
    String diagnostics() { return statistics().describe("Mikrofon"); }
}
