package com.betteruc.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondChatManagerTest {

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
}
