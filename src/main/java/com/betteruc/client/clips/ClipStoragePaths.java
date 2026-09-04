package com.betteruc.client.clips;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local paths only. Never moves existing clips or silently falls back to another destination. */
final class ClipStoragePaths {
    private ClipStoragePaths() {}

    static Path resolve(Path gameDirectory, String customParent) {
        if (customParent == null || customParent.isBlank()) {
            // Preserve the location used by previous beta builds.
            return gameDirectory.toAbsolutePath().normalize().resolve("betteruc-clips");
        }
        Path parent = Path.of(customParent);
        if (!parent.isAbsolute()) throw new IllegalArgumentException("Speicherort muss ein absoluter Ordnerpfad sein.");
        return parent.normalize().resolve("buclips");
    }

    static Path prepareSelection(Path gameDirectory, String customParent) throws IOException {
        Path target = resolve(gameDirectory, customParent);
        if (!Files.isDirectory(target.getParent())) throw new IOException("Übergeordneter Ordner ist nicht verfügbar.");
        Files.createDirectories(target);
        // Check actual write access, without touching any existing clip or user file.
        Path probe = Files.createTempFile(target, ".betteruc-write-test-", ".tmp");
        try { Files.write(probe, new byte[]{0}); }
        finally { Files.deleteIfExists(probe); }
        return target;
    }
}
