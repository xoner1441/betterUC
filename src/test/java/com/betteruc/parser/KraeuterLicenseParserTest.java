package com.betteruc.parser;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class KraeuterLicenseParserTest {
    @Test void parsesExactServerLogFormat() {
        String line = "[02:32:36] [System] [CHAT] 02:32:36    - Kräuter-Lizenz: Vorhanden bis 04.10.2026 01:47";
        assertEquals(new KraeuterLicenseParser.Status(true, LocalDateTime.of(2026, 10, 4, 1, 47)),
                KraeuterLicenseParser.parse(line));
        assertEquals(new KraeuterLicenseParser.Status(true, LocalDateTime.of(2026, 10, 4, 1, 47)),
                KraeuterLicenseParser.parse("- Kräuter-Lizenz: Vorhanden bis 04.10.2026 01:47"));
    }

    @Test void ignoresOtherLicensesAndMissingOrInvalidDates() {
        assertNull(KraeuterLicenseParser.parse("- Waffenschein: Vorhanden"));
        assertEquals(new KraeuterLicenseParser.Status(false, null),
                KraeuterLicenseParser.parse("- Kräuter-Lizenz: Nicht vorhanden"));
        assertNull(KraeuterLicenseParser.parse("- Kräuter-Lizenz: Vorhanden bis 31.02.2026 01:47"));
        assertNull(KraeuterLicenseParser.parse(null));
    }
}
