package com.betteruc.hud;

import com.betteruc.client.JobTimerClient;
import com.betteruc.config.BetterUCConfig;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shows every active side-job cooldown as a vertically expanding list. */
public final class JobTimerHud {
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
        List<JobTimerClient.JobState> active = JobTimerClient.activeCooldowns(now);
        String label = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.jobTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.jobTimerHudPrefix
        );
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
                    int row = 0;
                    if (active.isEmpty()) {
                        drawRow(context, client, style, label, "Alle bereit", 0, true, color);
                        return;
                    }
                    for (JobTimerClient.JobState job : active) {
                        long seconds = JobTimerClient.remainingSeconds(job, now);
                        String value = job.name() + " " + JobTimerClient.formatDuration(seconds);
                        drawRow(context, client, style, label, value, row, row == 0, color);
                        row++;
                    }
                }
        );
    }

    private static void drawRow(
            GuiGraphicsExtractor context,
            Minecraft client,
            String style,
            String label,
            String value,
            int row,
            boolean first,
            int color
    ) {
        int y = row * (BetterUCConfig.isModernHudStyle(style) ? 20 : 11);
        String rowLabel = first ? label : "";
        String display = first
                ? BetterUCConfig.prefixedHudText(
                        BetterUCConfig.INSTANCE.jobTimerHudPrefixEnabled,
                        BetterUCConfig.INSTANCE.jobTimerHudPrefix,
                        value
                )
                : value;
        Component text = Component.literal(display);
        if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, client.font, style,
                    BetterUCConfig.INSTANCE.jobTimerHudCustomFont, text, 0, y, color);
        } else if (!BetterUCConfig.isModernHudStyle(style)) {
            ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, 0, y, color);
        } else {
            ModernHudRenderer.drawModule(context, client, 0, y, rowLabel, value, color);
        }
    }
}
