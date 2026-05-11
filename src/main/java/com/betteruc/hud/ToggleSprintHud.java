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
        String text = "ToggleSprint: " + (isOn ? "ON" : "OFF");
        int color = BetterUCConfig.INSTANCE.toggleSprintHudColor;

        int x = BetterUCConfig.INSTANCE.toggleSprintHudX;
        int y = BetterUCConfig.INSTANCE.toggleSprintHudY;
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, color);
    }
}
