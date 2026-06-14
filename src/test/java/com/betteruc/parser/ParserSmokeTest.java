package com.betteruc.parser;

import com.betteruc.client.ChatCustomizationFormatter;
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
        assertTrue(StatsLineClassifier.isDetailLine("- Immobilien [Details]"));
        assertTrue(StatsLineClassifier.isDetailLine("02:55:59 - Immobilien [Details]"));
        assertTrue(StatsLineClassifier.isDetailLine("\u00A78- \u00A76Immobilien \u00A77[\u00A7cDetails\u00A77]"));
        assertTrue(StatsLineClassifier.isDetailLine("03:06:58 \u00A78- \u00A76Immobilien \u00A77[\u00A7cDetails\u00A77]"));
        assertTrue(StatsLineClassifier.isStandaloneKdStatsLine("14:04:31    - K/D: 1.48"));
        assertTrue(StatsLineClassifier.isStandaloneKdStatsLine("\u00A7714:04:31 \u00A78- \u00A7eK/D: \u00A7c1.48"));
    }

    @Test
    void statsClassifierDoesNotEatMemberInfoPlayerLines() {
        assertFalse(StatsLineClassifier.isDetailLine("- FABI1441"));
        assertFalse(StatsLineClassifier.isDetailLine("02:23:31 - FABI1441"));
        assertFalse(StatsLineClassifier.isImplicitDetailLine("- JxsNothing"));
        assertFalse(StatsLineClassifier.isImplicitDetailLine("\u00BB FABI1441"));
        assertFalse(StatsLineClassifier.isDetailLine("\u00A78- \u00A7bFABI1441"));
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

    @Test
    void chatCustomizationCombinesKilledHqPairWithTimestamp() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result first = ChatCustomizationFormatter.transform(
                "00:08:09 \u00BB HQ: coderXD wurde von pixel361 get\u00F6tet.",
                true,
                false
        );
        assertNotNull(first);
        assertTrue(first.cancelOriginal());
        assertTrue(first.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result second = ChatCustomizationFormatter.transform(
                "00:08:09 \u00BB HQ Fahndungsgrund: Terrorismus | Fahndungszeit: 20 Minuten.",
                true,
                false
        );
        assertNotNull(second);
        assertEquals(3, second.replacementMessages().size());
        assertEquals("get\u00F6tet \u25C6 pixel361 \u00BB coderXD", second.replacementMessages().get(0).getString());
        assertEquals("\u00BB Terrorismus", second.replacementMessages().get(1).getString());
        assertEquals("\u00BB 20 Minuten", second.replacementMessages().get(2).getString());
    }

    @Test
    void chatCustomizationCombinesJailedHqPairWithTimestamp() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result first = ChatCustomizationFormatter.transform(
                "23:20:20 \u00BB HQ: cznr wurde von _ek61 eingesperrt.",
                true,
                false
        );
        assertNotNull(first);
        assertTrue(first.cancelOriginal());
        assertTrue(first.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result second = ChatCustomizationFormatter.transform(
                "23:20:20 \u00BB HQ Fahndungsgrund: Pfandnahme + Stellung + Gute F\u00FChrung + Drogenabgabe 15g | Fahndungszeit: 0 Minuten.",
                true,
                false
        );
        assertNotNull(second);
        assertEquals(3, second.replacementMessages().size());
        assertEquals("inhaftiert \u25C6 _ek61 \u00BB cznr", second.replacementMessages().get(0).getString());
        assertEquals("\u00BB Pfandnahme + Stellung + Gute F\u00FChrung + DA 15g", second.replacementMessages().get(1).getString());
        assertEquals("\u00BB 0 Minuten", second.replacementMessages().get(2).getString());
    }

    @Test
    void chatCustomizationFormatsWeaponSeizureWithoutFollowingReason() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "12:32:54 \u00BB Beamter _ek61 hat WantKS0E die Waffen abgenommen.",
                true,
                false
        );
        assertNotNull(result);
        assertEquals(1, result.replacementMessages().size());
        assertEquals("waffen abnahme \u25C6 _ek61 \u00BB WantKS0E", result.replacementMessages().get(0).getString());

        ChatCustomizationFormatter.Result reason = ChatCustomizationFormatter.transform(
                "12:33:03 \u00BB HQ Fahndungsgrund: Versuchter Mord | Fahndungszeit: 2 Minuten.",
                true,
                false
        );
        assertNull(reason);
    }

    @Test
    void chatCustomizationFormatsDrugSeizureWithoutFollowingReason() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "23:20:20 \u00BB FBI _ek61 hat cznr Drogen abgenommen.",
                true,
                false
        );
        assertNotNull(result);
        assertEquals(1, result.replacementMessages().size());
        assertEquals("drogen abnahme \u25C6 _ek61 \u00BB cznr", result.replacementMessages().get(0).getString());

        ChatCustomizationFormatter.Result reason = ChatCustomizationFormatter.transform(
                "23:20:20 \u00BB HQ Fahndungsgrund: Pfandnahme + Stellung + Gute F\u00FChrung + Drogenabgabe 15g | Fahndungszeit: 0 Minuten.",
                true,
                false
        );
        assertNull(reason);
    }

    @Test
    void chatCustomizationHandlesTaggedWantedPairAndPipeVariant() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result first = ChatCustomizationFormatter.transform(
                "01:41:43 \u00BB HQ: Gesuchter: [VIP] blausaphir. Grund: Versuchter Mord",
                true,
                false
        );
        assertNotNull(first);
        assertTrue(first.cancelOriginal());
        assertTrue(first.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result second = ChatCustomizationFormatter.transform(
                "01:41:43 \u00BB HQ: [VIP] blausaphir's momentanes WantedLevel: 55",
                true,
                false
        );
        assertNotNull(second);
        assertEquals(3, second.replacementMessages().size());
        assertEquals("gesucht \u25C6 blausaphir", second.replacementMessages().get(0).getString());
        assertEquals("\u00BB Versuchter Mord", second.replacementMessages().get(1).getString());
        assertEquals("\u00BB 55 Wanteds", second.replacementMessages().get(2).getString());
    }

    @Test
    void chatCustomizationHandlesTaggedKilledPairWithBrokenPipeSeparator() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result first = ChatCustomizationFormatter.transform(
                "09:08:09 \u00BB HQ: [35] coderXD wurde von [FBI] pixel361 getoetet.",
                true,
                false
        );
        assertNotNull(first);
        assertTrue(first.cancelOriginal());
        assertTrue(first.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result second = ChatCustomizationFormatter.transform(
                "09:08:09 \u00BB HQ: Fahndungsgrund: Terrorismus \u00A6 Fahndungszeit: 1 Minute.",
                true,
                false
        );
        assertNotNull(second);
        assertEquals(3, second.replacementMessages().size());
        assertEquals("get\u00F6tet \u25C6 pixel361 \u00BB coderXD", second.replacementMessages().get(0).getString());
        assertEquals("\u00BB Terrorismus", second.replacementMessages().get(1).getString());
        assertEquals("\u00BB 1 Minute", second.replacementMessages().get(2).getString());
    }

    @Test
    void chatCustomizationHandlesMultipleReasonsAndHours() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result first = ChatCustomizationFormatter.transform(
                "22:45:07 \u00BB HQ: Notsituation wurde von UncJonas get\u00F6tet.",
                true,
                false
        );
        assertNotNull(first);
        assertTrue(first.cancelOriginal());
        assertTrue(first.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result second = ChatCustomizationFormatter.transform(
                "22:45:26 \u00BB HQ: Fahndungsgrund: Versuchter Mord + Widerstand gegen Beamte | Fahndungszeit: 1 Stunden.",
                true,
                false
        );
        assertNotNull(second);
        assertEquals(3, second.replacementMessages().size());
        assertEquals("get\u00F6tet \u25C6 UncJonas \u00BB Notsituation", second.replacementMessages().get(0).getString());
        assertEquals("\u00BB Versuchter Mord + Widerstand gegen Beamte", second.replacementMessages().get(1).getString());
        assertEquals("\u00BB 1 Stunden", second.replacementMessages().get(2).getString());
    }

    @Test
    void chatCustomizationHandlesRankedWantedChangeAndDeletedRecords() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result change = ChatCustomizationFormatter.transform(
                "22:26:34 \u00BB HQ: Captain hustexYD hat faul681s WantedPunkte ver\u00E4ndert!",
                true,
                false
        );
        assertNotNull(change);
        assertTrue(change.cancelOriginal());
        assertTrue(change.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result reason = ChatCustomizationFormatter.transform(
                "22:26:34 \u00BB HQ Neuer Grund: Pfandnahme + Widerstand gegen Beamte [65 > 60 WantedPunkte]",
                true,
                false
        );
        assertNotNull(reason);
        assertEquals(3, reason.replacementMessages().size());
        assertEquals("ver\u00E4ndert \u25C6 hustexYD \u00BB faul681", reason.replacementMessages().get(0).getString());
        assertEquals("\u00BB Pfandnahme + Widerstand gegen Beamte", reason.replacementMessages().get(1).getString());
        assertEquals("\u00BB 65 \u00BB 60 Wanteds", reason.replacementMessages().get(2).getString());

        ChatCustomizationFormatter.Result deleted = ChatCustomizationFormatter.transform(
                "22:30:58 \u00BB HQ: Lieutenant WzrU hat Crizock404HD's Akten gel\u00F6scht, over.",
                true,
                false
        );
        assertNotNull(deleted);
        assertEquals(2, deleted.replacementMessages().size());
        assertEquals("gel\u00F6scht \u25C6 WzrU \u00BB Crizock404HD", deleted.replacementMessages().get(0).getString());
    }

    @Test
    void chatCustomizationCompactsLongWantedChangeReasonLists() {
        ChatCustomizationFormatter.clearPending();

        ChatCustomizationFormatter.Result change = ChatCustomizationFormatter.transform(
                "01:33:29 \u00BB HQ: _ek61 hat QingVons WantedPunkte ver\u00E4ndert!",
                true,
                false
        );
        assertNotNull(change);
        assertTrue(change.cancelOriginal());
        assertTrue(change.replacementMessages().isEmpty());

        ChatCustomizationFormatter.Result reason = ChatCustomizationFormatter.transform(
                "01:33:29 \u00BB HQ Neuer Grund: Versuchter Mord + Drogenabgabe 15g + Stellung +Gute F\u00FChrung [55 > 30 WantedPunkte]",
                true,
                false
        );
        assertNotNull(reason);
        assertEquals(3, reason.replacementMessages().size());
        assertEquals("ver\u00E4ndert \u25C6 _ek61 \u00BB QingVon", reason.replacementMessages().get(0).getString());
        assertEquals("\u00BB Versuchter Mord + DA 15g + Stellung + Gute F\u00FChrung", reason.replacementMessages().get(1).getString());
        assertEquals("\u00BB 55 \u00BB 30 Wanteds", reason.replacementMessages().get(2).getString());
    }
}
