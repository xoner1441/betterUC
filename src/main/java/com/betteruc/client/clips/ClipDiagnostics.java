package com.betteruc.client.clips;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Small local error history survives capture toggles; copied only at the user's request. */
public final class ClipDiagnostics {
    static final ClipDiagnostics SHARED = new ClipDiagnostics();
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private String firstFailure = "";
    private String latestFailure = "";

    synchronized void record(String event) {
        if (events.size() == 40) events.removeFirst();
        events.addLast(limit(event, 2000));
    }

    synchronized void recordFailure(Throwable error) {
        latestFailure = stackTrace(error);
        if (firstFailure.isEmpty()) firstFailure = latestFailure;
    }

    static String stackTrace(Throwable error) {
        var text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        return limit(text.toString(), 16000);
    }

    static String summary(Throwable error) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable root = error;
        while (root.getCause() != null && seen.add(root) && !seen.contains(root.getCause())) root = root.getCause();
        String text = root.getClass().getSimpleName() + ": " + (root.getMessage() == null ? "ohne Detailtext" : root.getMessage());
        return limit(text.replaceAll("[\\p{Cntrl}]", " "), 500);
    }

    public synchronized String report(String status) {
        var text = new StringBuilder("betterUC Clip-Diagnose\nStatus: ").append(status).append('\n');
        for (String property : new String[]{"os.name", "os.version", "os.arch", "java.version", "java.vendor",
                "java.home", "org.bytedeco.javacpp.cachedir", "org.bytedeco.javacpp.platform"}) {
            text.append(property).append(": ").append(System.getProperty(property, "(Standard)")).append('\n');
        }
        text.append("FFmpeg-Laufzeit:\n").append(ClipEncoderRuntime.diagnosticSummary()).append('\n');
        text.append("\nErster Fehler seit Spielstart:\n").append(firstFailure.isEmpty() ? "Kein Fehler erfasst.\n" : firstFailure);
        if (!latestFailure.equals(firstFailure)) text.append("\nLetzter Fehler:\n").append(latestFailure);
        text.append("\nLetzte Clip-Meldungen:\n");
        for (String event : events) text.append(event).append('\n');
        return text.toString();
    }

    private static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + " [gekürzt]";
    }
}
