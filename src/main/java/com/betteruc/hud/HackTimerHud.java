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

        Text text = getDisplayText();
        int accentColor = secondsRemaining <= 15 ? 0xFFFF4D6D : 0xFF60A5FA;
        String style = BetterUCConfig.INSTANCE.hackTimerHudStyle;
        if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, client.textRenderer, style, text, x, y, accentColor);
            return;
        }
        if (!BetterUCConfig.isModernHudStyle(style)) {
            context.drawTextWithShadow(client.textRenderer, text, x, y, accentColor);
            return;
        }
        ModernHudRenderer.drawModule(
                context,
                client,
                x,
                y,
                "HACK",
                text.getString().replace("Hack: ", ""),
                accentColor
        );
    }

    private static Text getDisplayText() {
        if (secondsRemaining != cachedSeconds) {
            cachedSeconds = secondsRemaining;
            int minutes = secondsRemaining / 60;
            int seconds = secondsRemaining % 60;
            cachedTextString = "Hack: " + twoDigits(minutes) + ":" + twoDigits(seconds);
            cachedText = Text.literal(cachedTextString);
        }
        return cachedText;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
