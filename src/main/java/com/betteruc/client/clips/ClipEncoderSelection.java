package com.betteruc.client.clips;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Driver failures are local to a candidate, never a reason to skip AMD/Intel. No CPU fallback. */
final class ClipEncoderSelection {
    private record Candidate(String codec, String label) {}
    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate("h264_nvenc", "NVIDIA"), new Candidate("h264_amf", "AMD"),
            new Candidate("h264_qsv", "Intel"));

    @FunctionalInterface interface Attempt<T> { T open(String codec) throws Exception; }
    static final class MissingEncoderException extends IOException {
        MissingEncoderException() { super("Encoder fehlt in der mitgelieferten Bibliothek"); }
    }

    private ClipEncoderSelection() {}

    static <T> T open(Attempt<T> attempt, Consumer<String> diagnostics) throws IOException {
        var failures = new ArrayList<String>();
        for (var candidate : CANDIDATES) {
            try {
                T result = Objects.requireNonNull(attempt.open(candidate.codec()));
                diagnostics.accept("Hardware-Encoder bereit: " + candidate.codec() + " (" + candidate.label() + ")");
                return result;
            } catch (Exception | LinkageError error) {
                String status = error instanceof MissingEncoderException ? "fehlt in Test-JAR"
                        : error instanceof LinkageError ? "Bibliothek nicht ladbar" : "Start fehlgeschlagen";
                failures.add(candidate.label() + ": " + status);
                String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                diagnostics.accept(candidate.label() + " / " + candidate.codec() + ": " + detail);
            }
        }
        // Do not present the first (usually NVIDIA) native failure as THE error on an AMD PC.
        throw new IOException("Hardware-Aufnahme nicht verfügbar. " + String.join("; ", failures)
                + ". Technische Details stehen in latest.log (Clips). Kein CPU-Fallback.");
    }
}
