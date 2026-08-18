package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrustedChatCommandsTest {

    @AfterEach
    void clearTrust() {
        TrustedChatCommands.clear();
    }

    @Test
    void remembersOnlyExistingRunCommandActions() {
        Component message = Component.empty()
                .append(Component.literal("Unterwegs").withStyle(
                        style -> style.withClickEvent(new ClickEvent.RunCommand("/reinfaccept request-42"))
                ))
                .append(Component.literal(" https://betteruc.de").withStyle(
                        style -> style.withClickEvent(new ClickEvent.OpenUrl(
                                java.net.URI.create("https://betteruc.de")
                        ))
                ));

        TrustedChatCommands.remember(message);

        assertTrue(TrustedChatCommands.isTrusted("reinfaccept request-42"));
        assertTrue(TrustedChatCommands.isTrusted("/reinfaccept request-42"));
        assertFalse(TrustedChatCommands.isTrusted("pay Spieler 5000"));
    }

    @Test
    void plainCommandTextNeverBecomesTrusted() {
        TrustedChatCommands.remember(Component.literal("Nutze /pay Spieler 5000"));
        assertFalse(TrustedChatCommands.isTrusted("pay Spieler 5000"));
    }
}
