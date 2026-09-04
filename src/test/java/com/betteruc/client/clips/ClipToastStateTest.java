package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipToastStateTest {
    private final ClipSettings settings = ClipSettings.forViewport(1920, 1080, 30);

    private ClipToastState state() {
        var state = new ClipToastState();
        state.advance(0, true);
        return state;
    }

    @Test void recordingSlidesInAndDisappearsAfterFourVisibleSeconds() {
        var state = state();
        state.show(ClipNotice.recording(settings, "Ton aus"));
        assertEquals(0, state.visible().getFirst().visibility());
        state.advance(110, true);
        assertEquals(0.5, state.visible().getFirst().visibility(), 0.001);
        state.advance(220, true);
        assertEquals(1, state.visible().getFirst().visibility());
        state.advance(3_890, true);
        assertEquals(0.5, state.visible().getFirst().visibility(), 0.001);
        state.advance(4_000, true);
        assertTrue(state.visible().isEmpty());
    }

    @Test void menusFocusLossAndHiddenHudDoNotConsumeDisplayTime() {
        var state = state();
        state.show(ClipNotice.info("Test"));
        state.advance(100, true);
        state.advance(120, false);
        state.advance(60_000, false);
        state.advance(60_100, true);
        assertEquals(100, state.visible().getFirst().elapsedMs());
        state.advance(60_320, true);
        assertEquals(320, state.visible().getFirst().elapsedMs());
    }

    @Test void savingNeverExpiresAndRepeatedEventsDoNotRestartTheAnimation() {
        var state = state();
        state.show(ClipNotice.saving(true));
        state.advance(90_000, true);
        assertTrue(state.saving());
        assertEquals(1, state.visible().getFirst().visibility());
        state.show(ClipNotice.saving(false));
        assertEquals(90_000, state.visible().getFirst().elapsedMs());
        assertEquals("Video ohne Ton", state.visible().getFirst().notice().detail());
    }

    @Test void completionReplacesProgressAndShowsActualLengthAndFormat() {
        var state = state();
        state.show(ClipNotice.saving(false));
        state.advance(10_000, true);
        state.show(ClipNotice.saved(27.4, settings, false));
        assertFalse(state.saving());
        assertEquals(1, state.visible().size());
        var notice = state.visible().getFirst().notice();
        assertEquals(ClipNotice.Kind.SAVED, notice.kind());
        assertEquals("27,4 s · 1920x1080 · 60 FPS", notice.detail());
        assertTrue(notice.footer().contains("Ohne Ton"));
        assertEquals(0xFF4ADE80, notice.accent());
        state.advance(14_000, true);
        assertTrue(state.visible().isEmpty());
    }

    @Test void recordingErrorAndSaveProgressHaveSeparateLanes() {
        var state = state();
        state.show(ClipNotice.saving(true));
        state.show(ClipNotice.recordingFailed("GPU"));
        assertEquals(2, state.visible().size());
        assertTrue(state.saving());
        state.advance(6_000, true);
        assertEquals(1, state.visible().size());
        assertTrue(state.saving());
    }

    @Test void failureReplacesProgressAndUsesLongerRedNotice() {
        var state = state();
        state.show(ClipNotice.saving(true));
        state.show(ClipNotice.exportFailed());
        assertFalse(state.saving());
        assertEquals(0xFFFF5555, state.visible().getFirst().notice().accent());
        state.advance(4_000, true);
        assertEquals(1, state.visible().size());
        state.advance(6_000, true);
        assertTrue(state.visible().isEmpty());
    }

    @Test void rapidHotkeysAndRecordingRestartsDoNotObscureSaveResults() {
        var state = state();
        state.show(ClipNotice.recording(settings, "Ton aus"));
        state.show(ClipNotice.saving(false));
        for (int i = 0; i < 100; i++) state.show(ClipNotice.info("Wird bereits gespeichert"));
        assertEquals(1, state.visible().size());
        state.show(ClipNotice.saved(29, settings, false));
        state.show(ClipNotice.recording(settings, "Ton aus"));
        assertEquals(1, state.visible().size());
        assertEquals(ClipNotice.Kind.SAVED, state.visible().getFirst().notice().kind());
    }

    @Test void captureCanBeClearedWithoutHidingAnOngoingExport() {
        var state = state();
        state.show(ClipNotice.saving(true));
        state.show(ClipNotice.warning("Ton", "Fehler"));
        state.clearCapture();
        assertEquals(1, state.visible().size());
        assertTrue(state.saving());
        state.show(ClipNotice.saved(30, settings, true));
        assertEquals(ClipNotice.Kind.SAVED, state.visible().getFirst().notice().kind());
    }

    @Test void ordinaryNoticesCannotReplaceAnError() {
        var state = state();
        state.show(ClipNotice.recordingFailed("Encoder"));
        state.show(ClipNotice.info("Noch nicht bereit"));
        assertEquals(ClipNotice.Kind.RECORDING_FAILED, state.visible().getFirst().notice().kind());
    }

    @Test void clipCardsFitBelowTheTaxCardWithAGap() {
        int taxBottom = 10 + 50;
        int first = ClipToastHud.stackY(50 + 6, 0);
        int second = ClipToastHud.stackY(50 + 6, 1);
        assertEquals(taxBottom + 6, first);
        assertEquals(first + ClipToastHud.HEIGHT + ClipToastHud.GAP, second);
        assertEquals(10, ClipToastHud.stackY(0, 0));
    }
}
