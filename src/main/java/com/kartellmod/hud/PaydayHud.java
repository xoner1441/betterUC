package com.kartellmod.hud;

import com.kartellmod.config.KartellConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class PaydayHud {

    private static int currentMinutes = -1;
    private static int totalMinutes = -1;
    private static long lastMinuteUpdateMs = 0L;
    private static boolean pausedByAfk = false;

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void updateFromStats(int current, int total) {
        if (current < 0 || total <= 0) return;
        currentMinutes = Math.min(current, total);
        totalMinutes = total;
        lastMinuteUpdateMs = System.currentTimeMillis();
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

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!KartellConfig.INSTANCE.showPaydayHud) return;
        if (currentMinutes < 0 || totalMinutes <= 0) return;

        tickMinuteProgress();

        String text = "Payday: " + currentMinutes + "/" + totalMinutes + " Minuten";
        if (pausedByAfk) {
            text += " (AFK)";
        }
        int x = KartellConfig.INSTANCE.paydayHudX;
        int y = KartellConfig.INSTANCE.paydayHudY;
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, KartellConfig.INSTANCE.paydayHudColor);
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
