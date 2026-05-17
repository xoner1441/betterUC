package com.betteruc.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserSmokeTest {

    @Test
    void blacklistEntryParsesReasonStatsAndVogelfrei() {
        BlacklistParser.Entry entry = BlacklistParser.parseEntry("» TestSpieler | Gangzone, Leadermord (Vogelfrei) | 80 Kills | 6000$");

        assertNotNull(entry);
        assertEquals("TestSpieler", entry.name());
        assertEquals("Gangzone + Leadermord (Vogelfrei)", entry.normalizedReason());
        assertEquals(80, entry.kills());
        assertEquals(6000, entry.price());
        assertTrue(entry.hasVogelfrei());
    }

    @Test
    void blacklistRealtimeMessagesExtractPlayerName() {
        assertEquals("TestSpieler", BlacklistParser.parseRealtimeAdd("[Blacklist] TestSpieler wurde auf die Blacklist gesetzt"));
        assertEquals("TestSpieler", BlacklistParser.parseRealtimeRemove("[Blacklist] TestSpieler wurde von der Blacklist entfernt"));
        assertNull(BlacklistParser.parseRealtimeAdd("normale Chatnachricht"));
    }

    @Test
    void blacklistEntryKeepsRpStageInReason() {
        BlacklistParser.Entry entry = BlacklistParser.parseEntry("» TestSpieler | Gangzone + Leadermord (2/3) | 80 Kills | 6000$");

        assertNotNull(entry);
        assertEquals("Gangzone + Leadermord (2/3)", entry.normalizedReason());
        assertEquals("Gangzone + Leadermord (2/3)| 80 Kills | 6000$", BlacklistParser.rewriteFormattedRest(entry.rest()));
    }

    @Test
    void blacklistEntryKeepsOriginalRestForInfoCommand() {
        BlacklistParser.Entry entry = BlacklistParser.parseEntry(
                "> Eckiges | Gangzone (Vogelfrei) + Fraktionsschaedigung | 23.4.2026 14:4 | 30 Kills | 6500$ (AFK)"
        );

        assertNotNull(entry);
        assertEquals("Eckiges", entry.name());
        assertEquals(
                "Gangzone (Vogelfrei) + Fraktionsschaedigung | 23.4.2026 14:4 | 30 Kills | 6500$ (AFK)",
                entry.rest().trim()
        );
    }

    @Test
    void memberInfoParsesHeaderAndStructuredLine() {
        MemberInfoParser.Header header = MemberInfoParser.parseHeader("=== Mitglieder von Calderon Kartell (2/50) ===");
        MemberInfoParser.ParsedLine line = MemberInfoParser.parseLine("1 | SpielerEins | online", null);

        assertNotNull(header);
        assertEquals("Calderon Kartell", header.factionName());
        assertEquals(2, header.expectedEntries());
        assertEquals(MemberInfoParser.Type.NAMES, line.type());
        assertEquals("SpielerEins", line.names().get(0));
    }

    @Test
    void statsClassifierRecognizesCommonStatsLines() {
        assertTrue(StatsLineClassifier.isHeader("=== Statistiken ==="));
        assertTrue(StatsLineClassifier.isDetailLine("- Geld: 197925$"));
        assertTrue(StatsLineClassifier.isImplicitDetailLine("12:34:56 - K/D: 1.45"));
    }
}
