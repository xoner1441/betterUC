package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class HealthHud {

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showHealthHud) return;

        PlayerEntity player = client.player;
        int health = (int) Math.ceil(player.getHealth());
        int fullHearts = health / 2;

        int centerX = client.getWindow().getScaledWidth() / 2;
        int centerY = client.getWindow().getScaledHeight() / 2;

        String healthText = String.valueOf(fullHearts);
        int textWidth = client.textRenderer.getWidth(healthText);
        int totalWidth = 9 + 2 + textWidth;

        int startX = BetterUCConfig.INSTANCE.healthHudX >= 0
                ? BetterUCConfig.INSTANCE.healthHudX
                : centerX - totalWidth / 2;
        int y = BetterUCConfig.INSTANCE.healthHudY >= 0
                ? BetterUCConfig.INSTANCE.healthHudY
                : centerY + 15;
        int heartColor = BetterUCConfig.INSTANCE.healthHudHeartColor;
        int textColor = BetterUCConfig.INSTANCE.healthHudTextColor;

        context.drawGuiTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                startX, y, 9, 9,
                heartColor
        );

        context.drawText(client.textRenderer, Text.literal(healthText), startX + 11, y, textColor, true);
    }
}
