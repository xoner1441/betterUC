package com.betteruc.hud;

import com.betteruc.BetterUCClient;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class ToggleSprintHud {

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.toggleSprintEnabled) return;

        boolean isOn = BetterUCClient.isToggleSprintHudActive();
        int color = BetterUCConfig.INSTANCE.toggleSprintHudColor;

        int x = BetterUCConfig.INSTANCE.toggleSprintHudX;
        int y = BetterUCConfig.INSTANCE.toggleSprintHudY;
        String style = BetterUCConfig.INSTANCE.toggleSprintHudStyle;
        String value = isOn ? "ON" : "OFF";
        String text = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.toggleSprintHudPrefixEnabled,
                BetterUCConfig.INSTANCE.toggleSprintHudPrefix,
                value
        );
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.toggleSprintHudPrefixEnabled,
                BetterUCConfig.INSTANCE.toggleSprintHudPrefix
        );
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.toggleSprintHudScale,
                BetterUCConfig.INSTANCE.toggleSprintHudGradientEnabled,
                BetterUCConfig.INSTANCE.toggleSprintHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client, style, BetterUCConfig.INSTANCE.toggleSprintHudCustomFont, text, 0, 0, color);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.textRenderer, text, 0, 0, color);
            } else {
                ModernHudRenderer.drawModule(
                        context,
                        client,
                        0,
                        0,
                        moduleLabel,
                        value,
                        color,
                        isOn ? ModernHudRenderer.TEXT_PRIMARY : ModernHudRenderer.TEXT_DIM
                );
            }
        });
    }
}
