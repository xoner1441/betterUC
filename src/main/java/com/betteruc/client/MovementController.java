package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class MovementController {

    private static boolean toggleSprintActive = false;
    private static boolean toggleSprintWasActiveLastTick = false;
    private static boolean toggleSprintHudActive = false;

    private MovementController() {
    }

    public static boolean isToggleSprintHudActive() {
        return toggleSprintHudActive;
    }

    public static void tick(Minecraft client) {
        handleToggleSprint(client);
    }

    public static void reset(Minecraft client) {
        toggleSprintActive = false;
        toggleSprintWasActiveLastTick = false;
        toggleSprintHudActive = false;

        if (client != null && client.options != null && client.options.keySprint != null) {
            client.options.keySprint.setDown(false);
        }
    }

    private static void handleToggleSprint(Minecraft client) {
        KeyMapping sprintKey = client.options.keySprint;
        if (sprintKey == null || sprintKey.isUnbound()) {
            toggleSprintActive = false;
            toggleSprintWasActiveLastTick = false;
            toggleSprintHudActive = false;
            return;
        }

        while (sprintKey.consumeClick()) {
            if (BetterUCConfig.INSTANCE.toggleSprintEnabled && client.gui.screen() == null) {
                toggleSprintActive = !toggleSprintActive;
            }
        }

        if (!BetterUCConfig.INSTANCE.toggleSprintEnabled) {
            toggleSprintActive = false;
            toggleSprintWasActiveLastTick = false;
            toggleSprintHudActive = false;
            return;
        }

        boolean shouldForceSprint = BetterUCConfig.INSTANCE.toggleSprintEnabled
                && toggleSprintActive
                && client.gui.screen() == null
                && client.player != null
                && client.player.isAlive()
                && client.player.getFoodData().getFoodLevel() > 6
                && !client.player.isShiftKeyDown();

        if (toggleSprintActive) {
            sprintKey.setDown(shouldForceSprint);
        } else if (toggleSprintWasActiveLastTick) {
            sprintKey.setDown(false);
        }

        toggleSprintWasActiveLastTick = toggleSprintActive;
        toggleSprintHudActive = toggleSprintActive;
    }
}
