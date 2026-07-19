package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import java.text.Normalizer;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class DealerTimerHud {

    private static final long TIMER_DURATION_MS = 15_000L;
    private static long endAtMs = 0L;
    private static boolean playedEndSound = false;
    private static int cachedSeconds = -1;
    private static String cachedTimerValue = "00:15";
    private static Component cachedText = Component.literal("");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "dealer_timer"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    public static void clear() {
        endAtMs = 0L;
        playedEndSound = false;
        cachedSeconds = -1;
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (client == null || client.player == null || raw == null || raw.isBlank()) return;
        if (!BetterUCConfig.INSTANCE.showDealerTimerHud) return;

        String folded = fold(raw);
        if (!folded.contains("dealer")
                || !folded.contains("du hast")
                || !folded.contains("krauter")
                || !folded.contains("schwarzgeld")
                || !folded.contains("verkauft")) {
            return;
        }

        start();
    }

    public static void tick() {
        if (endAtMs <= 0L) return;

        long now = System.currentTimeMillis();
        if (now < endAtMs) return;

        if (!playedEndSound) {
            playedEndSound = true;
            playEndSound(Minecraft.getInstance());
        }
        endAtMs = 0L;
    }

    private static void start() {
        endAtMs = System.currentTimeMillis() + TIMER_DURATION_MS;
        playedEndSound = false;
        cachedSeconds = -1;
    }

    private static void playEndSound(Minecraft client) {
        if (client == null || client.player == null) return;
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.55F, 1.85F);
    }

    private static void render(GuiGraphicsExtractor context) {
        if (endAtMs <= 0L) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showDealerTimerHud) return;

        int x = BetterUCConfig.INSTANCE.dealerTimerX;
        int y = BetterUCConfig.INSTANCE.dealerTimerY;
        String timerValue = timerValue();
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.dealerTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.dealerTimerHudPrefix
        );
        Component text = displayText(timerValue);
        String style = BetterUCConfig.INSTANCE.dealerTimerHudStyle;
        int color = BetterUCConfig.INSTANCE.dealerTimerHudColor;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.dealerTimerHudScale,
                BetterUCConfig.INSTANCE.dealerTimerHudGradientEnabled,
                BetterUCConfig.INSTANCE.dealerTimerHudGradientColor,
                () -> {
                    if (BetterUCConfig.isStylizedHudStyle(style)) {
                        ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.dealerTimerHudCustomFont, text, 0, 0, color);
                    } else if (!BetterUCConfig.isModernHudStyle(style)) {
                        ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, 0, 0, color);
                    } else {
                        ModernHudRenderer.drawModule(context, client, 0, 0, moduleLabel, timerValue, color);
                    }
                });
    }

    private static String timerValue() {
        long remainingMs = Math.max(0L, endAtMs - System.currentTimeMillis());
        int seconds = Math.max(0, (int) Math.ceil(remainingMs / 1000.0D));
        if (seconds != cachedSeconds) {
            cachedSeconds = seconds;
            cachedTimerValue = "00:" + (seconds < 10 ? "0" + seconds : String.valueOf(seconds));
        }
        return cachedTimerValue;
    }

    private static Component displayText(String timerValue) {
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.dealerTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.dealerTimerHudPrefix,
                timerValue
        );
        if (!display.equals(cachedText.getString())) {
            cachedText = Component.literal(display);
        }
        return cachedText;
    }

    private static String fold(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/$]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
