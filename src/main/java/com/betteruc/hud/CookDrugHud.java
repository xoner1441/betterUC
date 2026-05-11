package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class CookDrugHud {

    private static final int DEFAULT_SECONDS = 9 * 60;

    private static int secondsRemaining = 0;
    private static long lastTick = 0L;

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void startDefault() {
        start(DEFAULT_SECONDS);
    }

    public static void start(int seconds) {
        secondsRemaining = Math.max(0, seconds);
        lastTick = System.currentTimeMillis();
    }

    public static void clear() {
        secondsRemaining = 0;
        lastTick = 0L;
    }

    public static void tick() {
        if (secondsRemaining <= 0) return;

        long now = System.currentTimeMillis();
        if (lastTick <= 0L) {
            lastTick = now;
            return;
        }

        if (now - lastTick >= 1000L) {
            secondsRemaining = Math.max(0, secondsRemaining - 1);
            lastTick = now;
        }
    }

    private static void render(DrawContext context) {
        if (secondsRemaining <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int x = BetterUCConfig.INSTANCE.methTimerX;
        int y = BetterUCConfig.INSTANCE.methTimerY;

        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        String text = String.format("CookDrug: %02d:%02d", minutes, seconds);

        int textWidth = client.textRenderer.getWidth(text);
        context.fill(x - 4, y - 4, x + textWidth + 4, y + 14, 0xAA000000);
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, 0xFF9ED7FF);
    }
}
