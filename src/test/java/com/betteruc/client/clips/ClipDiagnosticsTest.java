package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClipDiagnosticsTest {
    @Test void originalNativeFailureSurvivesLaterClassInitializationErrorsAndCaptureToggles() {
        var diagnostics = new ClipDiagnostics();
        diagnostics.recordFailure(new ExceptionInInitializerError(new UnsatisfiedLinkError("jniavutil.dll: missing dependency")));
        diagnostics.record("Clip-Aufnahme ausgeschaltet.");
        diagnostics.recordFailure(new NoClassDefFoundError("Could not initialize class avutil"));
        String report = diagnostics.report("Aus");
        assertTrue(report.contains("jniavutil.dll: missing dependency"));
        assertTrue(report.contains("Could not initialize class avutil"));
        assertTrue(report.contains("java.version:"));
        assertTrue(report.contains("org/bytedeco/ffmpeg/windows-x86_64/avutil-60.dll:"));
    }

    @Test void summaryIncludesRootCauseButNoChatControlCharacters() {
        String summary = ClipDiagnostics.summary(new ExceptionInInitializerError(new UnsatisfiedLinkError("missing\0DLL\npath")));
        assertEquals("UnsatisfiedLinkError: missing DLL path", summary);
        assertTrue(ClipDiagnostics.summary(new IllegalStateException("x".repeat(2000))).length() < 520);
    }

    @Test void keepsOnlyBoundedRecentHistory() {
        var diagnostics = new ClipDiagnostics();
        diagnostics.record("discard-this-event");
        for (int i = 0; i < 45; i++) diagnostics.record("entry " + i + ": " + "x".repeat(5000));
        String report = diagnostics.report("Aus");
        assertFalse(report.contains("discard-this-event"));
        assertTrue(report.contains("entry 44:"));
        assertTrue(report.length() < 100_000);
    }
}
