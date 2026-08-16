package com.betteruc.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateTimeHudTest {

    private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 8, 16, 16, 42, 37);

    @Test
    void formatsTechnicalSingleLineByDefault() {
        DateTimeHud.DisplayText display = DateTimeHud.format(SAMPLE, true, true, false, false);

        assertEquals("16.08.2026 // 16:42", display.primary());
        assertEquals("", display.secondary());
    }

    @Test
    void formatsSecondsAndTwoLines() {
        DateTimeHud.DisplayText display = DateTimeHud.format(SAMPLE, true, true, true, true);

        assertEquals("16.08.2026", display.primary());
        assertEquals("16:42:37", display.secondary());
    }

    @Test
    void supportsDateOrTimeIndividually() {
        assertEquals("16.08.2026", DateTimeHud.format(SAMPLE, true, false, true, true).primary());
        assertEquals("16:42:37", DateTimeHud.format(SAMPLE, false, true, true, true).primary());
        assertTrue(DateTimeHud.format(SAMPLE, false, false, false, false).isEmpty());
    }
}
