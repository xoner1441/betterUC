package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class HackTimerHud {

    public static int secondsRemaining = 0;
    private static long lastTick = 0;

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

        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        String text = String.format("Hack: %02d:%02d", minutes, seconds);

        int textWidth = client.textRenderer.getWidth(text);
        context.fill(x - 4, y - 4, x + textWidth + 4, y + 14, 0xAA000000);
        context.drawTextWithShadow(client.textRenderer, net.minecraft.text.Text.literal(text), x, y, 0xFFFFFFFF);
    }
}
