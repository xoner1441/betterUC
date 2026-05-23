package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class FpsHud {

    private static long sampleWindowStartMs = 0L;
    private static int framesInWindow = 0;
    private static int currentFps = 0;
    private static int cachedFps = -1;
    private static Text cachedText = Text.literal("FPS: 0");

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showFpsHud) return;

        updateFpsSample();
        Text text = getDisplayText();
        int x = BetterUCConfig.INSTANCE.fpsHudX;
        int y = BetterUCConfig.INSTANCE.fpsHudY;
        context.drawTextWithShadow(client.textRenderer, text, x, y, BetterUCConfig.INSTANCE.fpsHudColor);
    }

    private static void updateFpsSample() {
        long now = System.currentTimeMillis();
        if (sampleWindowStartMs == 0L) {
            sampleWindowStartMs = now;
        }

        framesInWindow++;
        long elapsed = now - sampleWindowStartMs;
        if (elapsed >= 1000L) {
            currentFps = (int) Math.round((framesInWindow * 1000.0) / Math.max(1L, elapsed));
            framesInWindow = 0;
            sampleWindowStartMs = now;
        }
    }

    private static Text getDisplayText() {
        int fps = Math.max(0, currentFps);
        if (fps != cachedFps) {
            cachedFps = fps;
            cachedText = Text.literal("FPS: " + fps);
        }
        return cachedText;
    }
}
