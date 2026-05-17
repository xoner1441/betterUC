package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
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

    public static void tick(MinecraftClient client) {
        handleToggleSprint(client);
        tickZoom(client);
    }

    public static void reset(MinecraftClient client) {
        toggleSprintActive = false;
        toggleSprintWasActiveLastTick = false;
        toggleSprintHudActive = false;
        zoomProgress = 0.0f;

        if (client != null && client.options != null && client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }
    }

    private static void handleToggleSprint(MinecraftClient client) {
        KeyBinding sprintKey = client.options.sprintKey;
        if (sprintKey == null || sprintKey.isUnbound()) {
            toggleSprintActive = false;
            toggleSprintWasActiveLastTick = false;
            toggleSprintHudActive = false;
            return;
        }

        while (sprintKey.wasPressed()) {
            if (BetterUCConfig.INSTANCE.toggleSprintEnabled && client.currentScreen == null) {
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
                && client.currentScreen == null
                && client.player != null
                && client.player.isAlive()
                && client.player.getHungerManager().getFoodLevel() > 6
                && !client.player.isSneaking();

        if (toggleSprintActive) {
            sprintKey.setPressed(shouldForceSprint);
        } else if (toggleSprintWasActiveLastTick) {
            sprintKey.setPressed(false);
        }

        toggleSprintWasActiveLastTick = toggleSprintActive;
        toggleSprintHudActive = toggleSprintActive;
    }

    private static void tickZoom(MinecraftClient client) {
        int keyCode = BetterUCConfig.INSTANCE.zoomKeyCode;
        boolean zoomDown = BetterUCConfig.INSTANCE.zoomEnabled
                && keyCode > 0
                && client.currentScreen == null
                && GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
        zoomProgress = zoomDown ? 1.0f : 0.0f;
    }
}
