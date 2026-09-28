package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BombWireAnnouncerTest {

    @Test
    void recognizesBombEventBoundariesWithVariableLocation() {
        assertTrue(BombWireAnnouncer.isBombStart(
                "\u00A76News: ACHTUNG! Es wurde eine Bombe in der N\u00E4he von Rathaus gefunden!"
        ));
        assertTrue(BombWireAnnouncer.isBombStart(
                "News: ACHTUNG! Es wurde eine Bombe in der N\u00E4he von 125/64/-340 gefunden!"
        ));
        assertTrue(BombWireAnnouncer.isBombEnd(
                "News: Die Bombe konnte erfolgreich entsch\u00E4rft werden!"
        ));
        assertTrue(BombWireAnnouncer.isBombEnd(
                "News: Die Bombe konnte nicht erfolgreich entsch\u00E4rft werden!"
        ));
        assertFalse(BombWireAnnouncer.isBombStart("Spieler: Ich habe eine Bombe gefunden"));
    }

    @Test
    void recognizesBombBossBarForLateJoiners() {
        assertTrue(BombWireAnnouncer.isBombBossBar(
                "\u00A7cBombe \u00A7f| 9 Minuten, 8 Sekunden | \u00A76Ort \u00A7f| Taxi Zentrale"
        ));
        assertTrue(BombWireAnnouncer.isBombBossBar(
                "Bombe | 0 Minuten, 42 Sekunden | Ort | Farm"
        ));
        assertTrue(BombWireAnnouncer.isBombBossBar(
                "Bombe | 1 Minute, 1 Sekunde | Ort | Hafen"
        ));
        assertFalse(BombWireAnnouncer.isBombBossBar(
                "Bankraub | 9 Minuten, 8 Sekunden | Ort | Staatsbank"
        ));
    }

    @Test
    void acceptsOnlyNamedPaperWiresInTheFourSupportedColors() {
        assertEquals(BombWireAnnouncer.WireType.GREEN,
                BombWireAnnouncer.wireType("minecraft:paper", "\u00A7aGr\u00FCner Draht"));
        assertEquals(BombWireAnnouncer.WireType.RED,
                BombWireAnnouncer.wireType("minecraft:paper", "Roter Draht"));
        assertEquals(BombWireAnnouncer.WireType.BLUE,
                BombWireAnnouncer.wireType("minecraft:paper", "Blauer Draht"));
        assertEquals(BombWireAnnouncer.WireType.PURPLE,
                BombWireAnnouncer.wireType("minecraft:paper", "Lila Draht"));
        assertNull(BombWireAnnouncer.wireType("minecraft:paper", "Papier"));
        assertNull(BombWireAnnouncer.wireType("minecraft:book", "Lila Draht"));
    }
}
