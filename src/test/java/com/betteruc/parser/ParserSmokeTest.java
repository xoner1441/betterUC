package com.betteruc.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void statsClassifierRecognizesCommonStatsLines() {
        assertTrue(StatsLineClassifier.isHeader("=== Statistiken ==="));
        assertTrue(StatsLineClassifier.isDetailLine("- Geld: 197925$"));
        assertTrue(StatsLineClassifier.isImplicitDetailLine("12:34:56 - K/D: 1.45"));
        assertTrue(StatsLineClassifier.isDetailLine(" - K / D : 1,48 "));
        assertTrue(StatsLineClassifier.isDetailLine("06:53:37    - K/D: 1.48"));
        assertTrue(StatsLineClassifier.isStandaloneKdStatsLine("14:04:31    - K/D: 1.48"));
        assertTrue(StatsLineClassifier.isStandaloneKdStatsLine("\u00A7714:04:31 \u00A78- \u00A7eK/D: \u00A7c1.48"));
    }

    @Test
    void statsClassifierDoesNotEatMemberInfoPlayerLines() {
        assertFalse(StatsLineClassifier.isDetailLine("- FABI1441"));
        assertFalse(StatsLineClassifier.isDetailLine("02:23:31 - FABI1441"));
        assertFalse(StatsLineClassifier.isImplicitDetailLine("- JxsNothing"));
        assertFalse(StatsLineClassifier.isImplicitDetailLine("\u00BB FABI1441"));
    }

    @Test
    void statsFactionValueMapsKnownFactionsAndRanks() {
        assertEquals("zivilist", FactionStatsParser.queryFromStatsValue("Zivilist"));
        assertEquals("zivilist", FactionStatsParser.queryFromStatsValue("Zivilist Rang 0"));
        assertEquals("kartell", FactionStatsParser.queryFromStatsValue("Calder\u00F3n Kartell Leader"));
        assertEquals("news", FactionStatsParser.queryFromStatsValue("News"));
        assertEquals("ordo", FactionStatsParser.queryFromStatsValue("Ordo Absolutus"));
        assertEquals("soeldner", FactionStatsParser.queryFromStatsValue("S\u00F6ldner"));
    }
}
