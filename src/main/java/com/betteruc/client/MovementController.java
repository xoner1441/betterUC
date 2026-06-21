package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MovementController {

    private static boolean toggleSprintActive = false;
    private static boolean toggleSprintWasActiveLastTick = false;
    private static boolean toggleSprintHudActive = false;
    private static float zoomProgress = 0.0f;

    private MovementController() {
    }

    public static boolean isToggleSprintHudActive() {
        return toggleSprintHudActive;
    }

    public static double applyZoomFov(double baseFov) {
        if (zoomProgress <= 0.0f) return baseFov;
        float cfg = BetterUCConfig.INSTANCE.zoomFovMultiplier;
        float multiplier = Math.max(0.05f, Math.min(cfg, 1.0f));
        double factor = 1.0 - (1.0 - multiplier) * zoomProgress;
        return baseFov * factor;
    }

    public static void tick(Minecraft client) {
        handleToggleSprint(client);
        tickZoom(client);
    }

    public static void reset(Minecraft client) {
        toggleSprintActive = false;
        toggleSprintWasActiveLastTick = false;
        toggleSprintHudActive = false;
        zoomProgress = 0.0f;

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
            if (BetterUCConfig.INSTANCE.toggleSprintEnabled && client.screen == null) {
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
                && client.screen == null
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

    private static void tickZoom(Minecraft client) {
        int keyCode = BetterUCConfig.INSTANCE.zoomKeyCode;
        boolean zoomDown = BetterUCConfig.INSTANCE.zoomEnabled
                && keyCode > 0
                && client.screen == null
                && GLFW.glfwGetKey(client.getWindow().handle(), keyCode) == GLFW.GLFW_PRESS;
        zoomProgress = zoomDown ? 1.0f : 0.0f;
    }
}
