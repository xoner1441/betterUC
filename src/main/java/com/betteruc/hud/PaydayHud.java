package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaydayHud {

    private static final int PAYDAY_DURATION_MINUTES = 60;
    private static final Set<Integer> SERVER_COUNTDOWN_STAGES = Set.of(10, 5, 3, 2, 1);
    private static final Pattern SERVER_COUNTDOWN_PATTERN = Pattern.compile(
            "(?iu)\\bInfo\\s*:\\s*Du\\s+hast\\s+in\\s+(\\d+)\\s+Minute(?:n)?\\s+deinen\\s+PayDay\\b"
    );

    private static int currentMinutes = -1;
    private static int totalMinutes = -1;
    private static long lastMinuteUpdateMs = 0L;
    private static boolean pausedByAfk = false;
    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "payday"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    public static void updateFromStats(int current, int total) {
        if (current < 0 || total <= 0) return;
        currentMinutes = Math.min(current, total);
        totalMinutes = total;
        lastMinuteUpdateMs = System.currentTimeMillis();
    }

    /**
     * Uses the server's PayDay countdown as an authoritative correction for the
     * locally advanced HUD. The server currently announces 10, 5, 3, 2 and 1
     * remaining minute(s).
     */
    public static boolean updateFromCountdownMessage(String raw) {
        int remainingMinutes = parseCountdownRemainingMinutes(raw);
        if (remainingMinutes < 0) return false;

        totalMinutes = PAYDAY_DURATION_MINUTES;
        currentMinutes = PAYDAY_DURATION_MINUTES - remainingMinutes;
        lastMinuteUpdateMs = System.currentTimeMillis();
        return true;
    }

    static int parseCountdownRemainingMinutes(String raw) {
        if (raw == null || raw.isBlank()) return -1;

        Matcher matcher = SERVER_COUNTDOWN_PATTERN.matcher(raw);
        if (!matcher.find()) return -1;

        try {
            int minutes = Integer.parseInt(matcher.group(1));
            return SERVER_COUNTDOWN_STAGES.contains(minutes) ? minutes : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static int currentMinutesForTesting() {
        return currentMinutes;
    }

    static int totalMinutesForTesting() {
        return totalMinutes;
    }

    public static void resetForNewPayday() {
        if (totalMinutes <= 0) {
            totalMinutes = 60;
        }
        currentMinutes = 0;
        lastMinuteUpdateMs = System.currentTimeMillis();
        pausedByAfk = false;
    }

    public static void setPausedByAfk(boolean paused) {
        if (pausedByAfk == paused) return;
        pausedByAfk = paused;

        // Avoid catching up paused time when AFK ends.
        if (!pausedByAfk) {
            lastMinuteUpdateMs = System.currentTimeMillis();
        }
    }

    public static boolean isPausedByAfk() {
        return pausedByAfk;
    }

    public static void clear() {
        currentMinutes = -1;
        totalMinutes = -1;
        lastMinuteUpdateMs = 0L;
        pausedByAfk = false;
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showPaydayHud) return;
        if (currentMinutes < 0 || totalMinutes <= 0) return;

        tickMinuteProgress();

        int x = BetterUCConfig.INSTANCE.paydayHudX;
        int y = BetterUCConfig.INSTANCE.paydayHudY;
        float progress = totalMinutes <= 0 ? 0.0F : currentMinutes / (float) totalMinutes;
        String value = currentMinutes + "/" + totalMinutes + " min";
        String style = BetterUCConfig.INSTANCE.paydayHudStyle;
        String fullValue = currentMinutes + "/" + totalMinutes + " Minuten";
        String text = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.paydayHudPrefixEnabled,
                BetterUCConfig.INSTANCE.paydayHudPrefix,
                fullValue
        );
        if (pausedByAfk) {
            text += " (AFK)";
        }
        String displayText = text;
        String baseModuleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.paydayHudPrefixEnabled,
                BetterUCConfig.INSTANCE.paydayHudPrefix
        );
        String moduleLabel = pausedByAfk && !baseModuleLabel.isBlank()
                ? baseModuleLabel + " AFK"
                : baseModuleLabel;
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.paydayHudScale,
                BetterUCConfig.INSTANCE.paydayHudGradientEnabled,
                BetterUCConfig.INSTANCE.paydayHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client, style, BetterUCConfig.INSTANCE.paydayHudCustomFont, displayText, 0, 0, BetterUCConfig.INSTANCE.paydayHudColor);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, displayText, 0, 0, BetterUCConfig.INSTANCE.paydayHudColor);
            } else {
                ModernHudRenderer.drawProgressModule(
                        context,
                        client,
                        0,
                        0,
                        moduleLabel,
                        value,
                        progress,
                        BetterUCConfig.INSTANCE.paydayHudColor
                );
            }
        });
    }

    private static void tickMinuteProgress() {
        if (currentMinutes < 0 || totalMinutes <= 0) return;
        if (pausedByAfk) return;

        long now = System.currentTimeMillis();
        if (lastMinuteUpdateMs <= 0L) {
            lastMinuteUpdateMs = now;
            return;
        }

        long elapsed = now - lastMinuteUpdateMs;
        if (elapsed < 60_000L) return;

        int addMinutes = (int) (elapsed / 60_000L);
        if (addMinutes <= 0) return;

        currentMinutes = Math.min(totalMinutes, currentMinutes + addMinutes);
        lastMinuteUpdateMs += addMinutes * 60_000L;
    }

}
