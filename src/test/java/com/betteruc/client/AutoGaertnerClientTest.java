package com.betteruc.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoGaertnerClientTest {

    @Test
    void associatesOnlyARecentlyClickedPotWithTheOpenedMenu() {
        AutoGaertnerClient.reset();
        BlockPos pot = new BlockPos(10, 64, -5);

        AutoGaertnerClient.recordPotInteraction(pot, 1_000L);
        assertEquals(pot, AutoGaertnerClient.consumeRecentPotInteraction(3_500L));
        assertNull(AutoGaertnerClient.consumeRecentPotInteraction(3_600L));

        AutoGaertnerClient.recordPotInteraction(pot, 10_000L);
        assertNull(AutoGaertnerClient.consumeRecentPotInteraction(13_001L));
    }

    @Test
    void completedPotMarkersAreClearedWithTheAutomationState() {
        AutoGaertnerClient.reset();
        BlockPos pot = new BlockPos(4, 70, 8);
        AutoGaertnerClient.markPotCompleted(pot);
        assertTrue(AutoGaertnerClient.completedPotPositions().contains(pot));

        AutoGaertnerClient.reset();
        assertTrue(AutoGaertnerClient.completedPotPositions().isEmpty());
    }
}
