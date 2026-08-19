package com.betteruc.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondChatManagerTest {

    @Test
    void suppressedMessagesAreEscapedIntoOneLogLine() {
        assertEquals("erste Zeile\\nzweite Zeile\\rende",
                SecondChatManager.singleLineForLog("erste Zeile\nzweite Zeile\rende"));
    }

    @Test
    void recognizesRealAndFormattedReinforcementMessages() {
        assertTrue(SecondChatManager.isReinforcementMessage(
                "Plantage! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung "
                        + "in der N\u00E4he von Plantage! (12 Meter entfernt)"
        ));
        assertTrue(SecondChatManager.isReinforcementMessage(
                "FBI Agent007 kommt zum Verst\u00E4rkungsruf von FABI1441! (40 Meter entfernt)"
        ));
        assertTrue(SecondChatManager.isReinforcementMessage("Route anzeigen | Unterwegs"));
    }

    @Test
    void ignoresStateAllianceChatPrefixesLocationsAndDistances() {
        assertFalse(SecondChatManager.isReinforcementMessage("FBI Bank 120m"));
        assertFalse(SecondChatManager.isReinforcementMessage("Polizei Plantage 85m"));
        assertFalse(SecondChatManager.isReinforcementMessage("Rettungsdienst Krankenhaus 12m"));
        assertFalse(SecondChatManager.isReinforcementMessage("Unterwegs Polizei"));
        assertFalse(SecondChatManager.isReinforcementMessage("Medic FBI"));
        assertFalse(SecondChatManager.isReinforcementMessage("Polizei FABI1441: was geht ab chat"));
        assertFalse(SecondChatManager.isReinforcementMessage("Polizei readax72: d\u00F6ner"));
        assertFalse(SecondChatManager.isReinforcementMessage("FBI tzfy: betteruc"));
        assertFalse(SecondChatManager.isReinforcementMessage(
                "Rettungsdienst Paulphobie: FABI1441 gro\u00DF geredet und nichts"
        ));
        assertFalse(SecondChatManager.isReinforcementMessage(
                "Polizei FABI1441: Bin bei der Plantage, Polizei kommt gleich."
        ));
    }

    @Test
    void formatterMarksEveryGeneratedReinforcementLineExplicitly() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.Result.replaceReinforcement(
                List.of(Component.literal("REINF FABI1441"), Component.literal("FBI Bank 120m"))
        );

        assertNotNull(result);
        assertTrue(result.reinforcement());
        assertTrue(result.replacementMessages().size() > 1);
    }

    @Test
    void recognizesSmallCapsTechnicalHqHeadlines() {
        assertTrue(SecondChatManager.isFormattedHqMessage(
                "ɪɴʜᴀꜰᴛɪᴇʀᴛ // [UC]_ek61 \u2192 NuRisk"
        ));
        assertTrue(SecondChatManager.isFormattedHqMessage(
                "ᴡᴀꜰꜰᴇɴ ᴀʙɴᴀʜᴍᴇ // FishMac_ \u2192 Ehhie"
        ));
    }

    @Test
    void recognizesNewTicketAndLicenseHeadlinesWithTheirContinuationCounts() {
        assertEquals(1, SecondChatManager.formattedHqContinuationLineCount(
                "ᴛɪᴄᴋᴇᴛ ᴀᴜꜱɢᴇꜱᴛᴇʟʟᴛ // Eymenn \u2192 aveey_"
        ));
        assertEquals(1, SecondChatManager.formattedHqContinuationLineCount(
                "ᴛɪᴄᴋᴇᴛ ʙᴇꜱᴛäᴛɪɢᴛ // Eymenn \u2192 aveey_"
        ));
        assertEquals(0, SecondChatManager.formattedHqContinuationLineCount(
                "ꜰüʜʀᴇʀꜱᴄʜᴇɪɴ ᴀʙɴᴀʜᴍᴇ // FABI1441 \u2192 reaax72"
        ));
        assertEquals(0, SecondChatManager.formattedHqContinuationLineCount(
                "ꜰüʜʀᴇʀꜱᴄʜᴇɪɴ ʀüᴄᴋɢᴀʙᴇ // FABI1441 \u2192 reaax72"
        ));
        assertEquals(0, SecondChatManager.formattedHqContinuationLineCount(
                "ᴀᴋᴛᴇɴ ɢᴇʟöꜱᴄʜᴛ // FABI1441 \u2192 reaax72"
        ));
        assertFalse(SecondChatManager.isFormattedHqMessage(
                "ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // Erfolgreich"
        ));
    }

    @Test
    void recognizesFormattedEmergencyCallBlocks() {
        assertEquals(2, SecondChatManager.formattedHqContinuationLineCount(
                "ɴᴏᴛʀᴜꜰ ✦ ardasaatci » ID 221"
        ));
        assertEquals(1, SecondChatManager.formattedHqContinuationLineCount(
                "ɴᴏᴛʀᴜꜰ ᴀɴɢᴇɴᴏᴍᴍᴇɴ ✦ _toobi » ardasaatci"
        ));
        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "HQ: Achtung! Ein Notruf von ardasaatci (221): \"Hilfe\"."
        ));
        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "HQ: _toobi hat den Notruf von ardasaatci angenommen, over. (11m entfernt)"
        ));
    }

    @Test
    void doesNotTreatHqLocationsInNormalFactionChatAsHqMessages() {
        assertFalse(SecondChatManager.isHqOrWpsMessage(
                "Calderón Kartell TikaKorth: Alles klar ich setze nen Vertrag auf "
                        + "und ich würde sagen wir sehen uns am Cop HQ"
        ));
        assertFalse(SecondChatManager.isHqOrWpsMessage(
                "Polizei FABI1441: Ich stehe gerade am FBI HQ"
        ));

        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "HQ: Officer 68mirco hat 1Arrogante_ ein Ticket über 230$ ausgestellt."
        ));
        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "[System] [CHAT] 19:13:44 HQ: Officer 68mirco hat 1Arrogante_ ein Ticket ausgestellt."
        ));
        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "[betterUC Second Chat] HQ: NuRisk wurde von mteii getötet."
        ));
    }

    @Test
    void keepsAdministrativePunishmentsOutOfTheHqFilter() {
        assertFalse(SecondChatManager.isHqOrWpsMessage(
                "Metrickz wurde von [UC]Knosbe für 120min gebannt! Grund: Metagaming"
        ));
        assertFalse(SecondChatManager.isHqOrWpsMessage(
                "Metrickz wurde von [UC]Knosbe zu 300 Checkpoints eingesperrt! Grund: Metagaming"
        ));
        assertFalse(SecondChatManager.isHqOrWpsMessage(
                "Metrickz wurde von [UC]Knosbe gekickt! Grund: Metagaming"
        ));

        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "HQ: Level10Rizzler wurde von FABI1441 eingesperrt."
        ));
        assertTrue(SecondChatManager.isHqOrWpsMessage(
                "Online Spieler mit WantedPunkten!"
        ));
    }

    @Test
    void recognizesWantedInfoHeaderAndOnlyItsExpectedDetails() {
        assertTrue(SecondChatManager.isWantedInfoHeaderMessage(
                "HQ: Fahndungs-Informationen über Moxemilian:"
        ));
        assertTrue(SecondChatManager.isWantedInfoDetailMessage("» WantedPunkte: 55"));
        assertTrue(SecondChatManager.isWantedInfoDetailMessage("» Grund: Versuchter Mord"));
        assertTrue(SecondChatManager.isWantedInfoDetailMessage("» Gefahndet seit: 0 Minuten"));
        assertTrue(SecondChatManager.isWantedInfoDetailMessage("» Beamte-/r: Schbastyyy787"));

        assertFalse(SecondChatManager.isWantedInfoDetailMessage(
                "Polizei FABI1441: Wir treffen uns am Cop HQ"
        ));
        assertFalse(SecondChatManager.isWantedInfoDetailMessage(
                "Metrickz wurde von [UC]Knosbe für 120min gebannt! Grund: Metagaming"
        ));
    }

    @Test
    void keepsTheCompleteWantedInfoBlockInTheHqFilter() {
        assertTrue(SecondChatManager.classifyHqOrWpsBlockMessage(
                "HQ: Fahndungs-Informationen über Moxemilian:"
        ));
        assertTrue(SecondChatManager.classifyHqOrWpsBlockMessage("» WantedPunkte: 55"));
        assertTrue(SecondChatManager.classifyHqOrWpsBlockMessage("» Grund: Versuchter Mord"));
        assertTrue(SecondChatManager.classifyHqOrWpsBlockMessage("» Gefahndet seit: 0 Minuten"));
        assertTrue(SecondChatManager.classifyHqOrWpsBlockMessage("» Beamte-/r: Schbastyyy787"));

        assertFalse(SecondChatManager.classifyHqOrWpsBlockMessage("» WantedPunkte: 55"));
        assertFalse(SecondChatManager.classifyHqOrWpsBlockMessage(
                "Polizei FABI1441: Wir treffen uns am Cop HQ"
        ));
    }
}
