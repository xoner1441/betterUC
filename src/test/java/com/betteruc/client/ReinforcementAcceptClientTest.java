package com.betteruc.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static Component clickable(String label, String command) {
        return Component.literal(label)
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
    }
}
