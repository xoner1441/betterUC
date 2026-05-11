package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public final class MovementController {

    private static final float ZOOM_SMOOTH_LERP = 0.30f;
    private static final float ZOOM_SNAP_EPSILON = 0.01f;
    private static final double FULLBRIGHT_GAMMA_VALUE = 1.0;
    private static final int FULLBRIGHT_REAPPLY_INTERVAL_TICKS = 20;

    private static boolean toggleSprintActive = false;
    private static boolean toggleSprintWasActiveLastTick = false;
    private static boolean toggleSprintHudActive = false;
    private static float zoomProgress = 0.0f;
    private static Double fullbrightPreviousGamma = null;
    private static Double fullbrightPreviousDarknessScale = null;
    private static Boolean fullbrightPreviousAo = null;
    private static Boolean fullbrightPreviousEntityShadows = null;
    private static boolean fullbrightApplied = false;
    private static int fullbrightReapplyTicks = 0;

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
        handleFullbright(client);
    }

    public static void reset(MinecraftClient client) {
        toggleSprintActive = false;
        toggleSprintWasActiveLastTick = false;
        toggleSprintHudActive = false;
        zoomProgress = 0.0f;
        restoreFullbright(client);
        fullbrightApplied = false;
        fullbrightPreviousGamma = null;
        fullbrightPreviousDarknessScale = null;
        fullbrightPreviousAo = null;
        fullbrightPreviousEntityShadows = null;
        fullbrightReapplyTicks = 0;

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
        float target = zoomDown ? 1.0f : 0.0f;
        if (BetterUCConfig.INSTANCE.zoomInstant) {
            zoomProgress = target;
            return;
        }

        zoomProgress += (target - zoomProgress) * ZOOM_SMOOTH_LERP;
        if (Math.abs(target - zoomProgress) < ZOOM_SNAP_EPSILON) {
            zoomProgress = target;
        }
    }

    private static void handleFullbright(MinecraftClient client) {
        if (client.options == null) return;

        if (BetterUCConfig.INSTANCE.fullbrightEnabled) {
            if (!fullbrightApplied) {
                fullbrightPreviousGamma = client.options.getGamma().getValue();
                fullbrightPreviousDarknessScale = client.options.getDarknessEffectScale().getValue();
                fullbrightPreviousAo = client.options.getAo().getValue();
                fullbrightPreviousEntityShadows = client.options.getEntityShadows().getValue();
                applyFullbrightVisualOptions(client);
                fullbrightApplied = true;
                fullbrightReapplyTicks = FULLBRIGHT_REAPPLY_INTERVAL_TICKS;
                return;
            }

            if (fullbrightReapplyTicks <= 0) {
                applyFullbrightVisualOptions(client);
                fullbrightReapplyTicks = FULLBRIGHT_REAPPLY_INTERVAL_TICKS;
            } else {
                fullbrightReapplyTicks--;
            }
            return;
        }

        restoreFullbright(client);
    }

    private static void restoreFullbright(MinecraftClient client) {
        if (client == null || client.options == null) return;
        if (!fullbrightApplied && fullbrightPreviousGamma == null && fullbrightPreviousDarknessScale == null
                && fullbrightPreviousAo == null && fullbrightPreviousEntityShadows == null) return;

        if (fullbrightPreviousGamma != null) client.options.getGamma().setValue(fullbrightPreviousGamma);
        if (fullbrightPreviousDarknessScale != null) client.options.getDarknessEffectScale().setValue(fullbrightPreviousDarknessScale);
        if (fullbrightPreviousAo != null) client.options.getAo().setValue(fullbrightPreviousAo);
        if (fullbrightPreviousEntityShadows != null) client.options.getEntityShadows().setValue(fullbrightPreviousEntityShadows);

        fullbrightApplied = false;
        fullbrightPreviousGamma = null;
        fullbrightPreviousDarknessScale = null;
        fullbrightPreviousAo = null;
        fullbrightPreviousEntityShadows = null;
        fullbrightReapplyTicks = 0;
    }

    private static void applyFullbrightVisualOptions(MinecraftClient client) {
        client.options.getGamma().setValue(FULLBRIGHT_GAMMA_VALUE);
        client.options.getDarknessEffectScale().setValue(0.0);
        client.options.getAo().setValue(false);
        client.options.getEntityShadows().setValue(false);
    }
}
