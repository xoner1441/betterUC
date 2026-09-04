package com.betteruc.client.clips;

import java.util.ArrayList;
import java.util.List;

/** Two bounded notification lanes. Only visible game time counts towards toast expiry. */
final class ClipToastState {
    record Visible(ClipNotice notice, long elapsedMs) {
        double visibility() {
            double enter = elapsedMs / 220.0;
            double leave = notice.kind() == ClipNotice.Kind.SAVING ? 1 : (notice.durationMs() - elapsedMs) / 220.0;
            double value = Math.clamp(Math.min(enter, leave), 0, 1);
            return value * value * (3 - 2 * value);
        }
        double remaining() { return Math.clamp(1 - elapsedMs / (double) notice.durationMs(), 0, 1); }
    }
    private record Entry(ClipNotice notice, long shownAt) {}
    private Entry capture;
    private Entry export;
    private long visibleTime;
    private long previousTime = -1;
    private boolean previouslyVisible;

    void advance(long now, boolean visible) {
        if (previousTime >= 0 && visible && previouslyVisible) visibleTime += Math.max(0, now - previousTime);
        previousTime = now;
        previouslyVisible = visible;
        capture = unexpired(capture);
        export = unexpired(export);
    }

    void show(ClipNotice notice) {
        if (notice.exportEvent()) {
            if (notice.kind() == ClipNotice.Kind.SAVING && saving()) {
                export = new Entry(notice, export.shownAt());
                return;
            }
            export = new Entry(notice, visibleTime);
            if (capture != null && !capture.notice().important()) capture = null;
        } else {
            // Repeated hotkeys or a recording restart must not obscure save progress/results or errors.
            if (!notice.important() && (export != null || capture != null && capture.notice().important())) return;
            capture = new Entry(notice, visibleTime);
        }
    }

    boolean saving() { return export != null && export.notice().kind() == ClipNotice.Kind.SAVING; }
    void clearCapture() { capture = null; }

    List<Visible> visible() {
        var result = new ArrayList<Visible>(2);
        if (export != null) result.add(new Visible(export.notice(), visibleTime - export.shownAt()));
        if (capture != null) result.add(new Visible(capture.notice(), visibleTime - capture.shownAt()));
        return List.copyOf(result);
    }

    private Entry unexpired(Entry entry) {
        return entry == null || entry.notice().kind() != ClipNotice.Kind.SAVING
                && visibleTime - entry.shownAt() >= entry.notice().durationMs() ? null : entry;
    }
}
