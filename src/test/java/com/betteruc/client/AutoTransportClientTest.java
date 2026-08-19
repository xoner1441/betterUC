package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoTransportClientTest {

    @Test
    void recognizesTheTransportJobSequence() {
        assertTrue(AutoTransportClient.isStartMessage(
                "[Transport] Wähle jetzt dein Lieferziel."
        ));
        assertTrue(AutoTransportClient.isTargetMessage(
                "[Transport] Ziel gesetzt: Feuerwerksladen"
        ));
        assertTrue(AutoTransportClient.isArrivalMessage(
                "[Navi] Hier kannst du abliefern – /droptransport"
        ));
    }

    @Test
    void ignoresUnrelatedNavigationAndIncompleteTargets() {
        assertFalse(AutoTransportClient.isTargetMessage("[Transport] Ziel gesetzt:"));
        assertFalse(AutoTransportClient.isArrivalMessage(
                "[Navi] Hier kannst du einzahlen - /dropmoney"
        ));
        assertFalse(AutoTransportClient.isArrivalMessage(
                "Polizei FABI1441: Hier kannst du abliefern - /droptransport"
        ));
    }

    @Test
    void parsesCrateCountsWithoutTreatingOtherNumbersAsTransportCargo() {
        assertEquals(3, AutoTransportClient.parseCrateLine("Kisten: 3"));
        assertEquals(1, AutoTransportClient.parseCrateLine("Noch 1 Kiste"));
        assertEquals(12, AutoTransportClient.parseCrateLine("§eKisten §7» §f12"));
        assertEquals(-1, AutoTransportClient.parseCrateLine("Jobrotation: 8/10"));
    }
}
