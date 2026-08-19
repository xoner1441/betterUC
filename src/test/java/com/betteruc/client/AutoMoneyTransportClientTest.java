package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMoneyTransportClientTest {

    @Test
    void recognizesTheTwoExactMoneyTransportMessages() {
        assertTrue(AutoMoneyTransportClient.isStartMessage(
                "[Geldtransport] Bringe das Geld zum Automaten und benutze /dropmoney"
        ));
        assertTrue(AutoMoneyTransportClient.isArrivalMessage(
                "[Navi] Hier kannst du einzahlen - /dropmoney"
        ));
    }

    @Test
    void ignoresSimilarUnrelatedMessages() {
        assertFalse(AutoMoneyTransportClient.isStartMessage(
                "[Geldtransport] Bringe das Geld zum Automaten"
        ));
        assertFalse(AutoMoneyTransportClient.isArrivalMessage(
                "[Navi] Hier kannst du dein Fahrzeug abstellen"
        ));
        assertFalse(AutoMoneyTransportClient.isArrivalMessage(
                "Polizei FABI1441: Hier kannst du einzahlen - /dropmoney"
        ));
    }

    @Test
    void requiresARecentStartAndConsumesTheArrivalOnce() {
        AutoMoneyTransportClient.reset();
        long start = 1_000L;

        assertFalse(AutoMoneyTransportClient.updateJobState(
                "[Navi] Hier kannst du einzahlen - /dropmoney", start
        ));
        assertFalse(AutoMoneyTransportClient.updateJobState(
                "[Geldtransport] Bringe das Geld zum Automaten und benutze /dropmoney", start
        ));
        assertTrue(AutoMoneyTransportClient.updateJobState(
                "[Navi] Hier kannst du einzahlen - /dropmoney", start + 1_000L
        ));
        assertFalse(AutoMoneyTransportClient.updateJobState(
                "[Navi] Hier kannst du einzahlen - /dropmoney", start + 2_000L
        ));
    }

    @Test
    void expiresTheArmedJobAfterThirtyMinutes() {
        AutoMoneyTransportClient.reset();
        long start = 1_000L;
        AutoMoneyTransportClient.updateJobState(
                "[Geldtransport] Bringe das Geld zum Automaten und benutze /dropmoney", start
        );

        assertFalse(AutoMoneyTransportClient.updateJobState(
                "[Navi] Hier kannst du einzahlen - /dropmoney", start + 30L * 60L * 1_000L + 1L
        ));
    }
}
