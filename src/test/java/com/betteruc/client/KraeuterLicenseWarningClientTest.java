package com.betteruc.client;

import com.betteruc.parser.KraeuterLicenseParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class KraeuterLicenseWarningClientTest {
    private static final KraeuterLicenseParser.Status EXPIRING = new KraeuterLicenseParser.Status(
            true, LocalDateTime.of(2026, 10, 4, 1, 47));

    @Test void warnsWithinConfiguredWindowButNotWeeksEarly() {
        assertNull(KraeuterLicenseWarningClient.warningText(EXPIRING,
                LocalDateTime.of(2026, 9, 20, 12, 0), 7));
        String warning = KraeuterLicenseWarningClient.warningText(EXPIRING,
                LocalDateTime.of(2026, 9, 28, 12, 0), 7);
        assertNotNull(warning);
        assertTrue(warning.contains("04.10.2026 01:47"));
        assertTrue(warning.contains("RP"));
    }

    @Test void missingLicenseDoesNotWarnEveryoneAndExpiredDateIsDistinct() {
        assertNull(KraeuterLicenseWarningClient.warningText(new KraeuterLicenseParser.Status(false, null),
                LocalDateTime.of(2026, 9, 28, 12, 0), 7));
        assertTrue(KraeuterLicenseWarningClient.warningText(EXPIRING,
                LocalDateTime.of(2026, 10, 5, 12, 0), 7).contains("abgelaufen"));
    }
}
