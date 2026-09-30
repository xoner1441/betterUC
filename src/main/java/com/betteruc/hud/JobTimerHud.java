package com.betteruc.hud;

import com.betteruc.client.JobTimerClient;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shows the next side-job cooldown; /jobtimer lists every known job. */
public final class JobTimerHud {
    private static int cachedSeconds = -1;
    private static int cachedCount = -1;
    private static String cachedJob = "";
    private static String cachedValue = "";
    private static Component cachedText = Component.empty();

    private JobTimerHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("betteruc", "job_timer"),
                (context, tickCounter) -> {
                    if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
                }
        );
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !BetterUCConfig.INSTANCE.showJobTimerHud || !JobTimerClient.hasData()) return;

        long now = System.currentTimeMillis();
        String value = value(now);
        String label = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.jobTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.jobTimerHudPrefix
        );
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.jobTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.jobTimerHudPrefix,
                value
        );
        if (!display.equals(cachedText.getString())) cachedText = Component.literal(display);
        String style = BetterUCConfig.INSTANCE.jobTimerHudStyle;
        int color = BetterUCConfig.INSTANCE.jobTimerHudColor;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                BetterUCConfig.INSTANCE.jobTimerHudX,
                BetterUCConfig.INSTANCE.jobTimerHudY,
                BetterUCConfig.INSTANCE.jobTimerHudScale,
                BetterUCConfig.INSTANCE.jobTimerHudGradientEnabled,
                BetterUCConfig.INSTANCE.jobTimerHudGradientColor,
                () -> {
                    if (BetterUCConfig.isStylizedHudStyle(style)) {
                        ModernHudRenderer.drawStyledText(context, client.font, style,
                                BetterUCConfig.INSTANCE.jobTimerHudCustomFont, cachedText, 0, 0, color);
                    } else if (!BetterUCConfig.isModernHudStyle(style)) {
                        ModernHudRenderer.drawHudTextWithShadow(context, client.font, cachedText, 0, 0, color);
                    } else {
                        ModernHudRenderer.drawModule(context, client, 0, 0, label, value, color);
                    }
                }
        );
    }

    private static String value(long now) {
        JobTimerClient.JobState next = JobTimerClient.nextCooldown(now);
        int count = JobTimerClient.activeCooldownCount(now);
        if (next == null) return "Alle bereit";
        int seconds = (int) Math.min(Integer.MAX_VALUE, JobTimerClient.remainingSeconds(next, now));
        if (seconds != cachedSeconds || count != cachedCount || !next.name().equals(cachedJob)) {
            cachedSeconds = seconds;
            cachedCount = count;
            cachedJob = next.name();
            cachedValue = next.name() + " " + JobTimerClient.formatDuration(seconds)
                    + (count > 1 ? " (+" + (count - 1) + ")" : "");
        }
        return cachedValue;
    }
}
