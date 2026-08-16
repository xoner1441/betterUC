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
}
