package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipEncoderSelectionTest {
    @Test void amdIsUsedAfterNvidiaFailsWithoutTryingIntelOrSoftware() throws Exception {
        var attempted = new ArrayList<String>();
        var messages = new ArrayList<String>();
        String encoder = ClipEncoderSelection.open(name -> {
            attempted.add(name);
            if (name.equals("h264_nvenc")) throw new IOException("Operation not permitted");
            return name;
        }, messages::add);
        assertEquals("h264_amf", encoder);
        assertEquals(List.of("h264_nvenc", "h264_amf"), attempted);
        assertTrue(messages.getLast().contains("h264_amf (AMD)"));
        assertTrue(messages.getFirst().contains("Operation not permitted"));
    }

    @Test void workingNvidiaKeepsItsExistingPriority() throws Exception {
        var attempted = new ArrayList<String>();
        assertEquals("h264_nvenc", ClipEncoderSelection.open(name -> {
            attempted.add(name);
            return name;
        }, ignored -> {}));
        assertEquals(List.of("h264_nvenc"), attempted);
    }

    @Test void intelCanStillBeSelectedAfterMissingOrBrokenOtherBackends() throws Exception {
        var attempted = new ArrayList<String>();
        assertEquals("h264_qsv", ClipEncoderSelection.open(name -> {
            attempted.add(name);
            if (name.equals("h264_nvenc")) throw new UnsatisfiedLinkError("NVIDIA unavailable");
            if (name.equals("h264_amf")) throw new ClipEncoderSelection.MissingEncoderException();
            return name;
        }, ignored -> {}));
        assertEquals(List.of("h264_nvenc", "h264_amf", "h264_qsv"), attempted);
    }

    @Test void allFailuresAreSummarizedByVendorAndNativeDetailsRemainInTheLog() {
        var attempted = new ArrayList<String>();
        var messages = new ArrayList<String>();
        IOException error = assertThrows(IOException.class, () -> ClipEncoderSelection.open(name -> {
            attempted.add(name);
            if (name.equals("h264_amf")) throw new ClipEncoderSelection.MissingEncoderException();
            throw new IOException("Operation not permitted");
        }, messages::add));
        assertEquals(3, attempted.size());
        assertTrue(error.getMessage().contains("NVIDIA: Start fehlgeschlagen"));
        assertTrue(error.getMessage().contains("AMD: fehlt in Test-JAR"));
        assertTrue(error.getMessage().contains("Intel: Start fehlgeschlagen"));
        assertTrue(error.getMessage().contains("latest.log"));
        assertTrue(error.getMessage().contains("Kein CPU-Fallback"));
        assertFalse(error.getMessage().contains("Operation not permitted"));
        assertEquals(3, messages.size());
        assertTrue(messages.getFirst().contains("Operation not permitted"));
    }

    @Test void linkageAndEmptyErrorsAreStillExplainedWithoutSoftwareFallback() {
        var messages = new ArrayList<String>();
        IOException error = assertThrows(IOException.class, () -> ClipEncoderSelection.open(name -> {
            if (name.equals("h264_amf")) throw new UnsatisfiedLinkError();
            throw new IOException();
        }, messages::add));
        assertTrue(error.getMessage().contains("AMD: Bibliothek nicht ladbar"));
        assertEquals(3, messages.size());
        assertTrue(messages.stream().anyMatch(m -> m.contains("UnsatisfiedLinkError")));
    }
}
