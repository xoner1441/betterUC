package com.betteruc.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementAcceptClientTest {

    @Test
    void selectsUnderwegsInsteadOfTheEarlierRouteAction() {
        Component actionLine = Component.empty()
                .append(clickable("Route anzeigen", "/navi 10/20/30"))
                .append(Component.literal(" | "))
                .append(clickable("Unterwegs", "/reinfaccept request-42"));

        assertEquals(
                "/reinfaccept request-42",
                ReinforcementAcceptClient.findRunCommandForLabel(actionLine, "Unterwegs")
        );
    }

    @Test
    void doesNotFallBackToRouteWhenUnderwegsHasNoClickAction() {
        Component actionLine = Component.empty()
                .append(clickable("Route anzeigen", "/navi 10/20/30"))
                .append(Component.literal(" | Unterwegs"));

        assertNull(ReinforcementAcceptClient.findRunCommandForLabel(actionLine, "Unterwegs"));
    }

    @Test
    void restrictsAcceptanceToSurvivalOnlyWhenConfigured() {
        assertTrue(ReinforcementAcceptClient.isGameModeAllowed(false, GameType.CREATIVE));
        assertTrue(ReinforcementAcceptClient.isGameModeAllowed(false, null));
        assertTrue(ReinforcementAcceptClient.isGameModeAllowed(true, GameType.SURVIVAL));
        assertFalse(ReinforcementAcceptClient.isGameModeAllowed(true, GameType.CREATIVE));
        assertFalse(ReinforcementAcceptClient.isGameModeAllowed(true, GameType.ADVENTURE));
        assertFalse(ReinforcementAcceptClient.isGameModeAllowed(true, GameType.SPECTATOR));
        assertFalse(ReinforcementAcceptClient.isGameModeAllowed(true, null));
    }

    private static Component clickable(String label, String command) {
        return Component.literal(label)
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
    }
}
