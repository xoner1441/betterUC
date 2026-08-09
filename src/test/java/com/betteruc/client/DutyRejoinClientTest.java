package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DutyRejoinClientTest {

    @Test
    void recognizesTheDutyReturnPrompt() {
        assertTrue(DutyRejoinClient.matchesPrompt(
                "[Dienst] Du warst zu lange offline. Willst du den Dienst wieder antreten? [Annehmen] [Ablehnen]"));
    }

    @Test
    void toleratesFormattingWhitespaceAndAccents() {
        assertTrue(DutyRejoinClient.matchesPrompt(
                "  [DIENST]  Du warst zu lange offline.\nWillst du den Dienst wieder antreten?  [Annehmen] "));
    }

    @Test
    void ignoresOtherDutyMessages() {
        assertFalse(DutyRejoinClient.matchesPrompt("[Dienst] Du hast den Dienst wieder angetreten."));
        assertFalse(DutyRejoinClient.matchesPrompt("Du warst zu lange offline."));
    }
}
