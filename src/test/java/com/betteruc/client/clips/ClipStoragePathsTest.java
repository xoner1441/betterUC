package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClipStoragePathsTest {
    @TempDir Path directory;

    @Test void defaultKeepsExistingBetaLocationWithoutCreatingAnything() {
        Path expected = directory.resolve("betteruc-clips");
        assertEquals(expected, ClipStoragePaths.resolve(directory, ""));
        assertEquals(expected, ClipStoragePaths.resolve(directory, null));
        assertEquals(expected, ClipStoragePaths.resolve(directory, " "));
        assertFalse(Files.exists(expected));
    }

    @Test void customParentUsesBuclipsAndPreservesSpacesAndUnicode() throws Exception {
        Path parent = Files.createDirectory(directory.resolve("Meine Clips äöü"));
        assertEquals(parent.resolve("buclips"), ClipStoragePaths.resolve(directory, parent.toString()));
        assertEquals(parent.resolve("buclips"), ClipStoragePaths.resolve(directory, parent.resolve("..")
                .resolve(parent.getFileName()).toString()));
    }

    @Test void relativeAndInvalidPathsAreRejectedInsteadOfFallingBack() {
        assertThrows(IllegalArgumentException.class, () -> ClipStoragePaths.resolve(directory, "relative-clips"));
        assertThrows(IllegalArgumentException.class, () -> ClipStoragePaths.resolve(directory, "invalid\0path"));
    }

    @Test void writeProbeLeavesExistingFilesUntouchedAndNoTemporaryFile() throws Exception {
        Path target = Files.createDirectory(directory.resolve("buclips"));
        Path clip = target.resolve("existing.mp4");
        Files.writeString(clip, "existing content");
        assertEquals(target, ClipStoragePaths.prepareSelection(directory, directory.toString()));
        assertEquals("existing content", Files.readString(clip));
        try (var entries = Files.list(target)) { assertEquals(1, entries.count()); }
    }

    @Test void missingParentIsNotCreatedAndCannotSwitchStorage() {
        Path missing = directory.resolve("missing-drive-or-folder");
        assertThrows(IOException.class, () -> ClipStoragePaths.prepareSelection(directory, missing.toString()));
        assertFalse(Files.exists(missing));
        assertFalse(Files.exists(directory.resolve("betteruc-clips")));
    }

    @Test void existingFileCalledBuclipsIsNeverReplaced() throws Exception {
        Path existing = directory.resolve("buclips");
        Files.writeString(existing, "do not overwrite");
        assertThrows(IOException.class, () -> ClipStoragePaths.prepareSelection(directory, directory.toString()));
        assertEquals("do not overwrite", Files.readString(existing));
    }

    @Test void resetDoesNotMoveOrDeleteCustomClips() throws Exception {
        Path custom = ClipStoragePaths.prepareSelection(directory, directory.toString());
        Path clip = Files.createFile(custom.resolve("clip.mp4"));
        assertEquals(directory.resolve("betteruc-clips"), ClipStoragePaths.prepareSelection(directory, ""));
        assertTrue(Files.exists(clip));
    }
}
