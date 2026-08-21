package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ZoomController {

    public static final double MIN_ZOOM_FACTOR = 1.5D;
    public static final double MAX_ZOOM_FACTOR = 20.0D;
    private static final double SCROLL_MULTIPLIER = 1.25D;
    private static final double MIN_PROGRESS = 0.0001D;

    private static boolean zoomRequested;
    private static boolean toggleLatched;
    private static boolean previousRequested;
    private static boolean zoomifyWarningShown;
    private static double animationProgress;
    private static double runtimeZoomFactor = 4.0D;
    private static long lastFrameNanos;

    private ZoomController() {
    }

    public static void tick(Minecraft client, KeyMapping zoomKey) {
        if (zoomKey == null) {
            zoomRequested = false;
            toggleLatched = false;
            return;
        }

        int queuedPresses = 0;
        while (zoomKey.consumeClick()) {
            queuedPresses++;
        }

        BetterUCConfig config = BetterUCConfig.INSTANCE;
        boolean usable = config.zoomEnabled
                && client != null
                && client.player != null
                && !ClientCompat.hasScreen(client);

        if (!usable) {
            zoomRequested = false;
            toggleLatched = false;
        } else if (config.zoomToggleMode) {
            if ((queuedPresses & 1) == 1) {
                toggleLatched = !toggleLatched;
            }
            zoomRequested = toggleLatched;
        } else {
            toggleLatched = false;
            zoomRequested = zoomKey.isDown();
        }

        if (zoomRequested && !previousRequested) {
            runtimeZoomFactor = clampZoomFactor(config.zoomFactor);
            showZoomifyWarning(client);
        }
        previousRequested = zoomRequested;
    }

    public static float applyFov(float originalFov) {
        updateAnimation();
        return zoomedFov(originalFov, runtimeZoomFactor, animationProgress);
    }

    public static boolean handleMouseScroll(long windowHandle, double verticalAmount) {
        Minecraft client = Minecraft.getInstance();
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (!zoomRequested
                || !config.zoomEnabled
                || !config.zoomScrollAdjustEnabled
                || verticalAmount == 0.0D
                || client == null
                || client.player == null
                || ClientCompat.hasScreen(client)
                || client.getWindow().handle() != windowHandle) {
            return false;
        }

        double direction = Math.signum(verticalAmount);
        double nextFactor = runtimeZoomFactor * Math.pow(SCROLL_MULTIPLIER, direction);
        runtimeZoomFactor = roundZoomFactor(clampZoomFactor(nextFactor));
        if (config.zoomRememberLevel) {
            config.zoomFactor = runtimeZoomFactor;
            BetterUCConfig.save();
        }

        if (client.gui != null && client.gui.hud != null) {
            client.gui.hud.setOverlayMessage(
                    Component.literal("Zoom: " + factorLabel(runtimeZoomFactor)),
                    false
            );
        }
        return true;
    }

    public static double mouseSensitivityMultiplier() {
        if (!BetterUCConfig.INSTANCE.zoomSensitivityScalingEnabled || animationProgress <= MIN_PROGRESS) {
            return 1.0D;
        }
        return 1.0D / zoomDivisor(runtimeZoomFactor, animationProgress);
    }

    public static boolean isZoomifyLoaded() {
        return FabricLoader.getInstance().isModLoaded("zoomify");
    }

    public static String configuredFactorLabel() {
        return factorLabel(clampZoomFactor(BetterUCConfig.INSTANCE.zoomFactor));
    }

    public static void reset() {
        zoomRequested = false;
        toggleLatched = false;
        previousRequested = false;
        animationProgress = 0.0D;
        runtimeZoomFactor = clampZoomFactor(BetterUCConfig.INSTANCE.zoomFactor);
        lastFrameNanos = 0L;
    }

    private static void updateAnimation() {
        boolean target = zoomRequested && BetterUCConfig.INSTANCE.zoomEnabled;
        if (!BetterUCConfig.INSTANCE.zoomSmoothEnabled) {
            animationProgress = target ? 1.0D : 0.0D;
            lastFrameNanos = System.nanoTime();
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        double elapsedMs = Math.max(0.0D, Math.min(100.0D, (now - lastFrameNanos) / 1_000_000.0D));
        lastFrameNanos = now;
        double durationMs = Math.max(50.0D, BetterUCConfig.INSTANCE.zoomAnimationDurationMs);
        double step = elapsedMs / durationMs;
        animationProgress = target
                ? Math.min(1.0D, animationProgress + step)
                : Math.max(0.0D, animationProgress - step);
    }

    private static void showZoomifyWarning(Minecraft client) {
        if (zoomifyWarningShown || !isZoomifyLoaded() || client == null || client.player == null) {
            return;
        }
        zoomifyWarningShown = true;
        client.player.sendSystemMessage(Component.literal(
                "\u00A7e[betterUC] Zoomify wurde ebenfalls erkannt. "
                        + "Belege nur einen der beiden Zoom-Keys, um doppeltes Zoomen zu vermeiden."
        ));
    }

    static float zoomedFov(float originalFov, double factor, double progress) {
        double safeFov = Math.max(1.0D, originalFov);
        return (float) Math.max(1.0D, safeFov / zoomDivisor(factor, progress));
    }

    static double zoomDivisor(double factor, double progress) {
        double eased = smoothStep(clamp01(progress));
        return 1.0D + (clampZoomFactor(factor) - 1.0D) * eased;
    }

    static double smoothStep(double value) {
        double clamped = clamp01(value);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    static double clampZoomFactor(double factor) {
        if (!Double.isFinite(factor)) {
            return 4.0D;
        }
        return Math.max(MIN_ZOOM_FACTOR, Math.min(MAX_ZOOM_FACTOR, factor));
    }

    private static double roundZoomFactor(double factor) {
        return Math.round(factor * 10.0D) / 10.0D;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String factorLabel(double factor) {
        return String.format(Locale.GERMANY, "%.1fx", factor);
    }
}
