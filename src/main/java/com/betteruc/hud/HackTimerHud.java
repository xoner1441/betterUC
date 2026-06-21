package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class HackTimerHud {

    public static int secondsRemaining = 0;
    private static long lastTick = 0;
    private static int cachedSeconds = -1;
    private static String cachedTextString = "";
    private static Component cachedText = Component.literal("");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "hack_timer"), (context, tickCounter) -> render(context));
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

    private static void render(GuiGraphicsExtractor context) {
        if (secondsRemaining <= 0) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int x = BetterUCConfig.INSTANCE.hackTimerX;
        int y = BetterUCConfig.INSTANCE.hackTimerY;

        String timerValue = getTimerValue();
        Component text = getDisplayText(timerValue);
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
                ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.hackTimerHudCustomFont, text, 0, 0, accentColor);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, 0, 0, accentColor);
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

    private static Component getDisplayText(String timerValue) {
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.hackTimerHudPrefix,
                timerValue
        );
        if (!display.equals(cachedText.getString())) {
            cachedText = Component.literal(display);
        }
        return cachedText;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
