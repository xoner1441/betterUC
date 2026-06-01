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
        String text = "ToggleSprint: " + (isOn ? "ON" : "OFF");
        if (BetterUCConfig.isCartoonHudStyle(style)) {
            ModernHudRenderer.drawCartoonText(context, client, text, x, y, color);
            return;
        }
        if (!BetterUCConfig.isModernHudStyle(style)) {
            context.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(text),
                    x,
                    y,
                    color
            );
            return;
        }
        ModernHudRenderer.drawModule(
                context,
                client,
                x,
                y,
                "SPRINT",
                isOn ? "ON" : "OFF",
                color,
                isOn ? ModernHudRenderer.TEXT_PRIMARY : ModernHudRenderer.TEXT_DIM
        );
    }
}
