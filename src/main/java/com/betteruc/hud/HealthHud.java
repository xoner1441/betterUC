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

        String style = BetterUCConfig.INSTANCE.healthHudStyle;
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);
        int textWidth = client.textRenderer.getWidth(cachedHealthString);
        int totalWidth = modernStyle ? Math.max(34, textWidth + 27) : 9 + 4 + textWidth;
        float scale = BetterUCConfig.INSTANCE.healthHudScale;

        int startX = BetterUCConfig.INSTANCE.healthHudX >= 0
                ? BetterUCConfig.INSTANCE.healthHudX
                : centerX - ModernHudRenderer.scaledSize(totalWidth, scale) / 2;
        int y = BetterUCConfig.INSTANCE.healthHudY >= 0
                ? BetterUCConfig.INSTANCE.healthHudY
                : centerY + 15;
        int heartColor = BetterUCConfig.INSTANCE.healthHudHeartColor;
        int textColor = BetterUCConfig.INSTANCE.healthHudTextColor;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                startX,
                y,
                scale,
                BetterUCConfig.INSTANCE.healthHudGradientEnabled,
                BetterUCConfig.INSTANCE.healthHudGradientColor,
                () -> {
            if (modernStyle) {
                boolean rightAligned = ModernHudRenderer.isRightAligned(0, totalWidth);
                int heartX = rightAligned ? totalWidth - 16 : 7;
                int textX = rightAligned ? heartX - textWidth - 4 : 19;
                ModernHudRenderer.drawPanel(context, 0, 0, totalWidth, 17, heartColor);
                context.drawGuiTexture(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                        heartX, 4, 9, 9,
                        ModernHudRenderer.hudTextColor(heartColor)
                );
                ModernHudRenderer.drawHudTextWithShadow(context, client.textRenderer, healthText, Math.max(6, textX), 4, textColor);
                return;
            }

            context.drawGuiTexture(
                    net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                    0, 0, 9, 9,
                    ModernHudRenderer.hudTextColor(heartColor)
            );

            if (stylizedStyle) {
                ModernHudRenderer.drawStyledText(context, client.textRenderer, style, BetterUCConfig.INSTANCE.healthHudCustomFont, healthText, 12, 0, textColor);
            } else {
                ModernHudRenderer.drawHudTextWithShadow(context, client.textRenderer, healthText, 11, 0, textColor);
            }
        });
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
