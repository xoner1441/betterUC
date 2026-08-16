package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.betteruc.config.BetterUCConfig;
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
}
