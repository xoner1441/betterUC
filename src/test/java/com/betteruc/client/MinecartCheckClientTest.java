package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartCheckClientTest {

    @Test
    void shortcutRequiresClientMainHandSneakingAndMinecart() {
        assertTrue(MinecartCheckClient.matchesShortcut(true, true, true, true, true));
        assertFalse(MinecartCheckClient.matchesShortcut(false, true, true, true, true));
        assertFalse(MinecartCheckClient.matchesShortcut(true, false, true, true, true));
        assertFalse(MinecartCheckClient.matchesShortcut(true, true, false, true, true));
        assertFalse(MinecartCheckClient.matchesShortcut(true, true, true, false, true));
        assertFalse(MinecartCheckClient.matchesShortcut(true, true, true, true, false));
    }

    @Test
    void repeatGuardOnlyBlocksTheSameMinecartBriefly() {
        assertTrue(MinecartCheckClient.isRepeatedInteraction(42, 1_200L, 42, 1_000L));
        assertFalse(MinecartCheckClient.isRepeatedInteraction(43, 1_200L, 42, 1_000L));
        assertFalse(MinecartCheckClient.isRepeatedInteraction(42, 1_350L, 42, 1_000L));
    }
}
