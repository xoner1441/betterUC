package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyHudDiagnosticsTest {

    @Test
    void serverMoneyFormatsAreDiagnosticCandidates() {
        assertTrue(MoneyHudDiagnostics.isSuspiciousCandidate(
                "19:18:51 Neuer Geldbetrag 12,500$ nach Sonderzahlung", false
        ));
    }

    @Test
    void ordinaryPlayerChatCannotFillDiagnostics() {
        assertFalse(MoneyHudDiagnostics.isSuspiciousCandidate(
                "19:18:51 FBI pixel412: Neuer Geldbetrag 999.999$", true
        ));
        assertTrue(MoneyHudDiagnostics.isSuspiciousCandidate(
                "19:18:51 [F-Bank] FABI1441 überwies 8.000$", true
        ));
    }

    @Test
    void unrelatedDollarMentionsAreIgnored() {
        assertFalse(MoneyHudDiagnostics.isSuspiciousCandidate(
                "19:18:51 Das Fahrzeug kostet 20.000$", false
        ));
    }
}
