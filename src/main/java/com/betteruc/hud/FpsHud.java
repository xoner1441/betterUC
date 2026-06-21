package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FpsHud {

    private static long sampleWindowStartMs = 0L;
    private static int framesInWindow = 0;
    private static int currentFps = 0;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "fps"), (context, tickCounter) -> render(context));
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showFpsHud) return;

        updateFpsSample();
        int x = BetterUCConfig.INSTANCE.fpsHudX;
        int y = BetterUCConfig.INSTANCE.fpsHudY;
        int fps = Math.max(0, currentFps);
        String style = BetterUCConfig.INSTANCE.fpsHudStyle;
        String value = String.valueOf(fps);
        String displayText = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.fpsHudPrefixEnabled,
                BetterUCConfig.INSTANCE.fpsHudPrefix,
                value
        );
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.fpsHudPrefixEnabled,
                BetterUCConfig.INSTANCE.fpsHudPrefix
        );
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.fpsHudScale,
                BetterUCConfig.INSTANCE.fpsHudGradientEnabled,
                BetterUCConfig.INSTANCE.fpsHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client, style, BetterUCConfig.INSTANCE.fpsHudCustomFont, displayText, 0, 0, BetterUCConfig.INSTANCE.fpsHudColor);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, displayText, 0, 0, BetterUCConfig.INSTANCE.fpsHudColor);
            } else {
                ModernHudRenderer.drawModule(
                        context,
                        client,
                        0,
                        0,
                        moduleLabel,
                        value,
                        BetterUCConfig.INSTANCE.fpsHudColor
                );
            }
        });
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

}
