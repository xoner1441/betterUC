package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public class HealthHud {

    private static int cachedHearts = Integer.MIN_VALUE;
    private static String cachedHealthString = "";
    private static Text cachedHealthText = Text.literal("");

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
        Text healthText = getHealthText(fullHearts);

        int centerX = client.getWindow().getScaledWidth() / 2;
        int centerY = client.getWindow().getScaledHeight() / 2;

        boolean modernStyle = BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.healthHudStyle);
        int textWidth = client.textRenderer.getWidth(cachedHealthString);
        int totalWidth = modernStyle ? Math.max(34, textWidth + 27) : 9 + 2 + textWidth;

        int startX = BetterUCConfig.INSTANCE.healthHudX >= 0
                ? BetterUCConfig.INSTANCE.healthHudX
                : centerX - totalWidth / 2;
        int y = BetterUCConfig.INSTANCE.healthHudY >= 0
                ? BetterUCConfig.INSTANCE.healthHudY
                : centerY + 15;
        int heartColor = BetterUCConfig.INSTANCE.healthHudHeartColor;
        int textColor = BetterUCConfig.INSTANCE.healthHudTextColor;

        if (modernStyle) {
            ModernHudRenderer.drawPanel(context, startX, y, totalWidth, 17, heartColor);
            context.drawGuiTexture(
                    net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                    startX + 7, y + 4, 9, 9,
                    heartColor
            );
            context.drawText(client.textRenderer, healthText, startX + 19, y + 4, textColor, true);
            return;
        }

        context.drawGuiTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                startX, y, 9, 9,
                heartColor
        );

        context.drawText(client.textRenderer, healthText, startX + 11, y, textColor, true);
    }

    private static Text getHealthText(int fullHearts) {
        if (fullHearts != cachedHearts) {
            cachedHearts = fullHearts;
            cachedHealthString = String.valueOf(fullHearts);
            cachedHealthText = Text.literal(cachedHealthString);
        }
        return cachedHealthText;
    }
}
