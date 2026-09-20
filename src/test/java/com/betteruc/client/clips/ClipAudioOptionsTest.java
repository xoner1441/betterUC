package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipAudioOptionsTest {
    @Test void offsetParserAcceptsExactSignedMillisecondsWithinRange() {
        assertEquals(20, ClipAudioOptions.parseOffsetMs("+20").orElseThrow());
        assertEquals(-20, ClipAudioOptions.parseOffsetMs(" -20 ").orElseThrow());
        assertEquals(0, ClipAudioOptions.parseOffsetMs("0").orElseThrow());
        assertEquals(500, ClipAudioOptions.parseOffsetMs("500").orElseThrow());
        assertTrue(ClipAudioOptions.parseOffsetMs("501").isEmpty());
        assertTrue(ClipAudioOptions.parseOffsetMs("-501").isEmpty());
        assertTrue(ClipAudioOptions.parseOffsetMs("").isEmpty());
        assertTrue(ClipAudioOptions.parseOffsetMs("-").isEmpty());
    }

    @Test void outOfRangePersistedValuesAreClampedAtRuntime() {
        assertEquals(ClipAudioOptions.MAX_OFFSET_MS,
                new ClipAudioOptions(ClipAudioOptions.Mode.GAME, false, "", "", 100, 100, 10_000).offsetMs());
        assertEquals(-ClipAudioOptions.MAX_OFFSET_MS,
                new ClipAudioOptions(ClipAudioOptions.Mode.GAME, false, "", "", 100, 100, -10_000).offsetMs());
    }
}
