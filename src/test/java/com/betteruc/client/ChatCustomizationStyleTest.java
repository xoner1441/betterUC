package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.betteruc.config.BetterUCConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class ChatCustomizationStyleTest {

    @Test
    void convertsOnlyActionLabelToSmallCapsWithTechnicalSeparators() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "Beamter FishMac_ hat Ehhie die Waffen abgenommen.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL
        );

        assertNotNull(result);
        assertEquals(
                "ᴡᴀꜰꜰᴇɴ ᴀʙɴᴀʜᴍᴇ // FishMac_ \u2192 Ehhie",
                result.replacementMessages().get(0).getString()
        );
    }

    @Test
    void keepsClassicNormalStyleSelectable() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "Beamter FishMac_ hat Ehhie die Waffen abgenommen.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_NORMAL,
                BetterUCConfig.CHAT_SEPARATOR_CLASSIC
        );

        assertNotNull(result);
        assertEquals(
                "waffen abnahme \u25C6 FishMac_ \u00BB Ehhie",
                result.replacementMessages().get(0).getString()
        );
    }

    @Test
    void foldsSmallCapsBackForChatClassification() {
        assertEquals("waffen abnahme", SmallCapsText.fold("ᴡᴀꜰꜰᴇɴ ᴀʙɴᴀʜᴍᴇ"));
        assertEquals("inhaftiert", SmallCapsText.fold("ɪɴʜᴀꜰᴛɪᴇʀᴛ"));
    }

    @Test
    void appliesBoldSegmentGradientsWithoutBoldingPlayerNames() {
        ChatCustomizationFormatter.clearPending();
        ChatCustomizationFormatter.Result first = ChatCustomizationFormatter.transform(
                "HQ: NuRisk wurde von [UC]_ek61 getötet.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );
        assertNotNull(first);

        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "HQ: Fahndungsgrund: Terrorismus | Fahndungszeit: 20 Minuten.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );
        assertNotNull(result);

        String action = "ɢᴇᴛöᴛᴇᴛ";
        String actor = "[UC]_ek61";
        List<Component> headline = result.replacementMessages().get(0).toFlatList();
        assertEquals(0xFF5555, headline.get(0).getStyle().getColor().getValue());
        assertEquals(0xAA0000, headline.get(action.length() - 1).getStyle().getColor().getValue());
        assertTrue(headline.get(0).getStyle().isBold());

        int actorStart = action.length() + 1;
        assertEquals(0x55FFFF, headline.get(actorStart).getStyle().getColor().getValue());
        assertEquals(0x5555FF, headline.get(actorStart + actor.length() - 1).getStyle().getColor().getValue());
        assertFalse(headline.get(actorStart).getStyle().isBold());

        int targetStart = actorStart + actor.length() + 1;
        assertEquals(0x5555FF, headline.get(targetStart).getStyle().getColor().getValue());
        assertEquals(0xAA55FF, headline.get(headline.size() - 1).getStyle().getColor().getValue());
        assertFalse(headline.get(targetStart).getStyle().isBold());

        List<Component> reason = result.replacementMessages().get(1).toFlatList();
        assertEquals(0x55AAFF, reason.get(1).getStyle().getColor().getValue());
        assertEquals(0x5555FF, reason.get(reason.size() - 1).getStyle().getColor().getValue());
        assertTrue(reason.get(1).getStyle().isBold());

        List<Component> duration = result.replacementMessages().get(2).toFlatList();
        assertEquals(0x55AAFF, duration.get(1).getStyle().getColor().getValue());
        assertEquals(0x5555FF, duration.get(duration.size() - 1).getStyle().getColor().getValue());
        assertTrue(duration.get(1).getStyle().isBold());
    }

    @Test
    void gradientToggleOffKeepsLegacySolidStyles() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "Beamter FishMac_ hat Ehhie die Waffen abgenommen.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                false
        );

        assertNotNull(result);
        List<Component> segments = result.replacementMessages().get(0).toFlatList();
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), segments.get(0).getStyle().getColor());
        assertFalse(segments.get(0).getStyle().isBold());
    }

    @Test
    void formatsLicenseSeizureWithInvisibleNameMarkersAndLogPrefix() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[System] [CHAT] 19:13:44 Beamter FABI1441 hat \u200Creaax72\u200C's Führerschein abgenommen.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertEquals("ꜰüʜʀᴇʀꜱᴄʜᴇɪɴ ᴀʙɴᴀʜᴍᴇ // FABI1441 → reaax72",
                result.replacementMessages().get(0).getString());
    }

    @Test
    void formatsLicenseReturnAsPositiveActionAndPreservesUcPrefix() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "Beamter [UC]FABI1441 hat reaax72’s Führerschein zurückgegeben.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertEquals("ꜰüʜʀᴇʀꜱᴄʜᴇɪɴ ʀüᴄᴋɢᴀʙᴇ // [UC]FABI1441 → reaax72",
                result.replacementMessages().get(0).getString());
        List<Component> segments = result.replacementMessages().get(0).toFlatList();
        String action = "ꜰüʜʀᴇʀꜱᴄʜᴇɪɴ ʀüᴄᴋɢᴀʙᴇ";
        assertEquals(0x55FF55, segments.get(0).getStyle().getColor().getValue());
        assertEquals(0x00AA00, segments.get(action.length() - 1).getStyle().getColor().getValue());
        assertTrue(segments.get(0).getStyle().isBold());
    }

    @Test
    void formatsTicketAndCorrelatesFollowingRecordsDeletion() {
        ChatCustomizationFormatter.clearPending();
        ChatCustomizationFormatter.Result issued = ChatCustomizationFormatter.transform(
                "HQ: Officer 68mirco hat 1Arrogante_ ein Ticket über 230$ ausgestellt. Bestätigung ausstehend, over.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(issued);
        assertEquals("ᴛɪᴄᴋᴇᴛ ᴀᴜꜱɢᴇꜱᴛᴇʟʟᴛ // 68mirco → 1Arrogante_",
                issued.replacementMessages().get(0).getString());
        assertEquals("» 230$ | Bestätigung ausstehend",
                issued.replacementMessages().get(1).getString());
        assertEquals(0xFFAA00,
                issued.replacementMessages().get(0).toFlatList().get(0).getStyle().getColor().getValue());

        ChatCustomizationFormatter.Result confirmed = ChatCustomizationFormatter.transform(
                "[betterUC Second Chat] HQ: Officer 68mirco hat 1Arrogante_ ihre Akten gelöscht, over.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(confirmed);
        assertEquals("ᴛɪᴄᴋᴇᴛ ʙᴇꜱᴛäᴛɪɢᴛ // 68mirco → 1Arrogante_",
                confirmed.replacementMessages().get(0).getString());
        assertEquals("» 230$ | Akten gelöscht",
                confirmed.replacementMessages().get(1).getString());
        assertEquals(0x55FF55,
                confirmed.replacementMessages().get(0).toFlatList().get(0).getStyle().getColor().getValue());
        ChatCustomizationFormatter.clearPending();
    }

    @Test
    void keepsUnrelatedRecordsDeletionAsStandaloneAction() {
        ChatCustomizationFormatter.clearPending();
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "HQ: Officer 68mirco hat 1Arrogante_ ihre Akten gelöscht, over.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertEquals(1, result.replacementMessages().size());
        assertEquals("ᴀᴋᴛᴇɴ ɢᴇʟöꜱᴄʜᴛ // 68mirco → 1Arrogante_",
                result.replacementMessages().get(0).getString());
        ChatCustomizationFormatter.clearPending();
    }

    @Test
    void formatsPersonalPlantageBurnAsCompactSuccessMessage() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[System] [CHAT] 23:52:56 Du hast erfolgreich eine Pulver Plant verbrannt.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertFalse(result.hq());
        assertEquals(1, result.replacementMessages().size());
        assertEquals("ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // Erfolgreich",
                result.replacementMessages().get(0).getString());
        List<Component> segments = result.replacementMessages().get(0).toFlatList();
        assertEquals(0xFF3B30, segments.get(0).getStyle().getColor().getValue());
        assertTrue(segments.get(0).getStyle().isBold());
    }

    @Test
    void formatsPersonalKraeuterPlantageBurnAsCompactSuccessMessage() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[System] [CHAT] 23:52:56 Du hast erfolgreich eine Kräuter Plant verbrannt.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertFalse(result.hq());
        assertEquals(1, result.replacementMessages().size());
        assertEquals("ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // Erfolgreich",
                result.replacementMessages().get(0).getString());
    }

    @Test
    void formatsPersonalBluetenharzPlantageBurnAsCompactSuccessMessage() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[System] [CHAT] 16:20:16 Du hast erfolgreich eine Blütenharz Plant verbrannt.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertFalse(result.hq());
        assertEquals(1, result.replacementMessages().size());
        assertEquals("ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // Erfolgreich",
                result.replacementMessages().get(0).getString());
    }

    @Test
    void formatsHqPlantageBurnWithAgentAndFireDetailProfile() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[betterUC Second Chat] HQ: Agent lara412 hat erfolgreich eine Pulver Plantage verbrannt, over.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertTrue(result.hq());
        assertEquals(2, result.replacementMessages().size());
        assertEquals("ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // lara412",
                result.replacementMessages().get(0).getString());
        assertEquals("» Pulver-Plantage erfolgreich zerstört",
                result.replacementMessages().get(1).getString());

        List<Component> headline = result.replacementMessages().get(0).toFlatList();
        assertEquals(0xFF3B30, headline.get(0).getStyle().getColor().getValue());
        assertTrue(headline.get(0).getStyle().isBold());
        List<Component> detail = result.replacementMessages().get(1).toFlatList();
        assertEquals(0xFFD75A, detail.get(1).getStyle().getColor().getValue());
        assertTrue(detail.get(1).getStyle().isBold());
    }

    @Test
    void formatsHqKraeuterPlantageBurnWithMatchingDetail() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[betterUC Second Chat] HQ: Agent lara412 hat erfolgreich eine Kräuter Plantage verbrannt, over.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertTrue(result.hq());
        assertEquals(2, result.replacementMessages().size());
        assertEquals("ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // lara412",
                result.replacementMessages().get(0).getString());
        assertEquals("» Kräuter-Plantage erfolgreich zerstört",
                result.replacementMessages().get(1).getString());
    }

    @Test
    void formatsHqBluetenharzPlantageBurnFromOfficerWithMatchingDetail() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "16:20:16 HQ: Officer 36Flo hat erfolgreich eine Blütenharz Plantage verbrannt, over.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertTrue(result.hq());
        assertEquals(2, result.replacementMessages().size());
        assertEquals("ᴘʟᴀɴᴛᴀɢᴇ ᴠᴇʀʙʀᴀɴɴᴛ // 36Flo",
                result.replacementMessages().get(0).getString());
        assertEquals("» Blütenharz-Plantage erfolgreich zerstört",
                result.replacementMessages().get(1).getString());
    }

    @Test
    void formatsIncomingEmergencyCallAsOneThreeLineHqBlock() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "00:02:02 HQ: Achtung! Ein Notruf von ardasaatci (221): \"hilfe eymen sagt mir er will kämpfen\".\n"
                        + "HQ: Der nächste Punkt ist Tellerwäscher. Die nächsten Personen sind Eymenn (3m), _toobi (36m).",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertTrue(result.hq());
        assertEquals(3, result.replacementMessages().size());
        assertEquals("ɴᴏᴛʀᴜꜰ ✦ ardasaatci » ID 221",
                result.replacementMessages().get(0).getString());
        assertEquals("» hilfe eymen sagt mir er will kämpfen",
                result.replacementMessages().get(1).getString());
        assertEquals("» Tellerwäscher · Eymenn 3m · _toobi 36m",
                result.replacementMessages().get(2).getString());
        assertEquals(0xFF3B30,
                result.replacementMessages().get(0).toFlatList().get(0).getStyle().getColor().getValue());
    }

    @Test
    void formatsIncomingEmergencyCallWithServerNaehesteWording() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "[betterUC Second Chat] HQ: Achtung! Ein Notruf von FourJvst (858): \"Hilfe\"\\n"
                        + "HQ: Der näheste Punkt ist Altstadt. Die nähesten Personen sind "
                        + "reaax72 (350m), _toobi (805m).",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertTrue(result.hq());
        assertEquals(3, result.replacementMessages().size());
        assertEquals("ɴᴏᴛʀᴜꜰ ✦ FourJvst » ID 858",
                result.replacementMessages().get(0).getString());
        assertEquals("» Hilfe", result.replacementMessages().get(1).getString());
        assertEquals("» Altstadt · reaax72 350m · _toobi 805m",
                result.replacementMessages().get(2).getString());
    }

    @Test
    void formatsAcceptedEmergencyCallWithOfficerCallerAndDistance() {
        ChatCustomizationFormatter.Result result = ChatCustomizationFormatter.transform(
                "HQ: _toobi hat den Notruf von ardasaatci angenommen, over. (11m entfernt)",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true
        );

        assertNotNull(result);
        assertTrue(result.hq());
        assertEquals(2, result.replacementMessages().size());
        assertEquals("ɴᴏᴛʀᴜꜰ ᴀɴɢᴇɴᴏᴍᴍᴇɴ ✦ _toobi » ardasaatci",
                result.replacementMessages().get(0).getString());
        assertEquals("» 11m entfernt", result.replacementMessages().get(1).getString());
        assertEquals(0x55FF55,
                result.replacementMessages().get(0).toFlatList().get(0).getStyle().getColor().getValue());
    }

    @Test
    void keepsHqAndPayGradientProfilesIndependent() {
        ChatCustomizationFormatter.GradientPalette palette =
                ChatCustomizationFormatter.GradientPalette.defaults()
                        .withHqAction(0xFF112233, 0xFF223344)
                        .withPayAction(0xFFAA5500, 0xFFCC7700)
                        .withPayOutgoing(0xFF010203, 0xFF040506);

        ChatCustomizationFormatter.Result hq = ChatCustomizationFormatter.transform(
                "Beamter FishMac_ hat Ehhie die Waffen abgenommen.",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true,
                palette
        );
        ChatCustomizationFormatter.Result pay = ChatCustomizationFormatter.transform(
                "Du hast Ehhie 230$ gegeben!",
                true,
                false,
                BetterUCConfig.CHAT_ACTION_TEXT_SMALL_CAPS,
                BetterUCConfig.CHAT_SEPARATOR_TECHNICAL,
                true,
                palette
        );

        assertNotNull(hq);
        assertNotNull(pay);
        assertEquals(0x112233,
                hq.replacementMessages().get(0).toFlatList().get(0).getStyle().getColor().getValue());
        assertEquals(0xAA5500,
                pay.replacementMessages().get(0).toFlatList().get(0).getStyle().getColor().getValue());
        assertEquals(0x010203,
                pay.replacementMessages().get(1).toFlatList().get(1).getStyle().getColor().getValue());
    }
}
