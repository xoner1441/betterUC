package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class HealthHud {

    private static int cachedHearts = Integer.MIN_VALUE;
    private static String cachedHealthString = "";
    private static Component cachedHealthText = Component.literal("");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "health"), (context, tickCounter) -> render(context));
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showHealthHud) return;

        Player player = client.player;
        int health = (int) Math.ceil(player.getHealth());
        int fullHearts = health / 2;
        Component healthText = getHealthText(fullHearts);

        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;

        String style = BetterUCConfig.INSTANCE.healthHudStyle;
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);
        int textWidth = client.font.width(cachedHealthString);
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
                context.blitSprite(
                        net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/full"),
                        heartX, 4, 9, 9,
                        ModernHudRenderer.hudTextColor(heartColor)
                );
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, healthText, Math.max(6, textX), 4, textColor);
                return;
            }

            context.blitSprite(
                    net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/full"),
                    0, 0, 9, 9,
                    ModernHudRenderer.hudTextColor(heartColor)
            );

            if (stylizedStyle) {
                ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.healthHudCustomFont, healthText, 12, 0, textColor);
            } else {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, healthText, 11, 0, textColor);
            }
        });
    }

    private static Component getHealthText(int fullHearts) {
        if (fullHearts != cachedHearts) {
            cachedHearts = fullHearts;
            cachedHealthString = String.valueOf(fullHearts);
            cachedHealthText = Component.literal(cachedHealthString);
        }
        return cachedHealthText;
    }
}
