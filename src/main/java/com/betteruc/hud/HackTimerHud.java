package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class HackTimerHud {

    public static int secondsRemaining = 0;
    private static long lastTick = 0;
    private static int cachedSeconds = -1;
    private static String cachedTextString = "";
    private static Text cachedText = Text.literal("");

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void start(int seconds) {
        secondsRemaining = seconds;
        lastTick = System.currentTimeMillis();
    }

    public static void tick() {
        if (secondsRemaining <= 0) return;
        long now = System.currentTimeMillis();
        if (now - lastTick >= 1000) {
            secondsRemaining--;
            lastTick = now;
        }
    }

    private static void render(DrawContext context) {
        if (secondsRemaining <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int x = BetterUCConfig.INSTANCE.hackTimerX;
        int y = BetterUCConfig.INSTANCE.hackTimerY;

        String timerValue = getTimerValue();
        Text text = getDisplayText(timerValue);
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.hackTimerHudPrefix
        );
        int accentColor = secondsRemaining <= 15 ? 0xFFFF4D6D : 0xFF60A5FA;
        String style = BetterUCConfig.INSTANCE.hackTimerHudStyle;
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.hackTimerHudScale,
                BetterUCConfig.INSTANCE.hackTimerHudGradientEnabled,
                BetterUCConfig.INSTANCE.hackTimerHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client.textRenderer, style, BetterUCConfig.INSTANCE.hackTimerHudCustomFont, text, 0, 0, accentColor);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.textRenderer, text, 0, 0, accentColor);
            } else {
                ModernHudRenderer.drawModule(
                        context,
                        client,
                        0,
                        0,
                        moduleLabel,
                        timerValue,
                        accentColor
                );
            }
        });
    }

    private static String getTimerValue() {
        if (secondsRemaining != cachedSeconds) {
            cachedSeconds = secondsRemaining;
            int minutes = secondsRemaining / 60;
            int seconds = secondsRemaining % 60;
            cachedTextString = twoDigits(minutes) + ":" + twoDigits(seconds);
        }
        return cachedTextString;
    }

    private static Text getDisplayText(String timerValue) {
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.hackTimerHudPrefix,
                timerValue
        );
        if (!display.equals(cachedText.getString())) {
            cachedText = Text.literal(display);
        }
        return cachedText;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
