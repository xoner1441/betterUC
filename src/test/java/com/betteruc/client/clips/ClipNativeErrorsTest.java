package com.betteruc.client.clips;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

class ClipNativeErrorsTest {
    @Test void ignoresUnusedBufferTailAfterTerminator() {
        assertEquals("Invalid argument", ClipNativeErrors.decode("Invalid argument\0garbage\0more".getBytes(StandardCharsets.UTF_8)));
        assertEquals("", ClipNativeErrors.decode(new byte[256]));
        assertEquals("bounded", ClipNativeErrors.decode("bounded".getBytes(StandardCharsets.UTF_8)));
    }

    @Test @EnabledOnOs(OS.WINDOWS) void actualNativeErrorIsShortAndContainsNoNulCharacters() {
        assertEquals("Invalid argument (-22)", ClipNativeErrors.describe(-22));
        String unsupported = ClipNativeErrors.describe(-40);
        assertFalse(unsupported.contains("\0"));
        assertTrue(unsupported.length() < 100);
        assertTrue(unsupported.endsWith("(-40)"));
    }
}
