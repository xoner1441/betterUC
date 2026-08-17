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
            if (BetterUCConfig.INSTANCE.toggleSprintEnabled && !ClientCompat.hasScreen(client)) {
                toggleSprintActive = !toggleSprintActive;
            }
        }

        if (!BetterUCConfig.INSTANCE.toggleSprintEnabled) {
            toggleSprintActive = false;
            toggleSprintWasActiveLastTick = false;
            toggleSprintHudActive = false;
            return;
        }

        boolean shouldHoldSprint = toggleSprintActive
                && !ClientCompat.hasScreen(client)
                && client.player != null
                && client.player.isAlive();

        if (toggleSprintActive) {
            // Keep the input latched and let vanilla decide whether sprinting is
            // currently possible. This preserves the toggle intent through
            // temporary interruptions such as crouching, item use, low hunger or
            // collisions and resumes sprinting as soon as Minecraft allows it.
            sprintKey.setDown(shouldHoldSprint);
        } else if (toggleSprintWasActiveLastTick) {
            sprintKey.setDown(false);
        }

        toggleSprintWasActiveLastTick = toggleSprintActive;
        toggleSprintHudActive = toggleSprintActive;
    }
}
