package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class HealthHud {

    public static final int ABSORPTION_COLOR = 0xFFFFC83D;

    private static final Identifier HEART_TEXTURE = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Component PREVIEW_HEALTH_TEXT = Component.literal("10");
    private static final Component PREVIEW_ABSORPTION_TEXT = Component.literal("2");
    private static int cachedHearts = Integer.MIN_VALUE;
    private static int cachedAbsorptionHearts = Integer.MIN_VALUE;
    private static String cachedHealthString = "";
    private static Component cachedHealthText = Component.literal("");
    private static Component cachedAbsorptionText = Component.literal("");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "health"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showHealthHud) return;

        Player player = client.player;
        int health = (int) Math.ceil(player.getHealth());
        int fullHearts = health / 2;
        int absorptionHearts = Math.max(0, (int) Math.ceil(player.getAbsorptionAmount() / 2.0F));
        Component healthText = getHealthText(fullHearts);
        Component absorptionText = getAbsorptionText(absorptionHearts);

        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;

        String style = BetterUCConfig.INSTANCE.healthHudStyle;
        int totalWidth = getHudWidth(client.font, style, healthText, absorptionText);
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
                () -> drawValue(
                        context,
                        client,
                        0,
                        0,
                        style,
                        BetterUCConfig.INSTANCE.healthHudCustomFont,
                        healthText,
                        absorptionText,
                        totalWidth,
                        heartColor,
                        textColor
                )
        );
    }

    private static Component getHealthText(int fullHearts) {
        if (fullHearts != cachedHearts) {
            cachedHearts = fullHearts;
            cachedHealthString = String.valueOf(fullHearts);
            cachedHealthText = Component.literal(cachedHealthString);
        }
        return cachedHealthText;
    }

    private static Component getAbsorptionText(int absorptionHearts) {
        if (absorptionHearts != cachedAbsorptionHearts) {
            cachedAbsorptionHearts = absorptionHearts;
            cachedAbsorptionText = absorptionHearts > 0
                    ? Component.literal(String.valueOf(absorptionHearts))
                    : Component.literal("");
        }
        return cachedAbsorptionText;
    }

    public static int getPreviewWidth(Font renderer, String style) {
        return getHudWidth(renderer, style, PREVIEW_HEALTH_TEXT, PREVIEW_ABSORPTION_TEXT);
    }

    public static void drawPreview(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String style,
            String fontId,
            int heartColor,
            int textColor
    ) {
        int width = getPreviewWidth(client.font, style);
        drawValue(
                context,
                client,
                x,
                y,
                style,
                fontId,
                PREVIEW_HEALTH_TEXT,
                PREVIEW_ABSORPTION_TEXT,
                width,
                heartColor,
                textColor
        );
    }

    private static int getHudWidth(Font renderer, String style, Component healthText, Component absorptionText) {
        int healthWidth = renderer.width(healthText);
        int absorptionWidth = absorptionText.getString().isEmpty()
                ? 0
                : 18 + renderer.width(absorptionText);
        if (BetterUCConfig.isModernHudStyle(style)) {
            return Math.max(34, healthWidth + 27 + absorptionWidth);
        }
        return 13 + healthWidth + absorptionWidth;
    }

    private static void drawValue(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String style,
            String fontId,
            Component healthText,
            Component absorptionText,
            int totalWidth,
            int heartColor,
            int textColor
    ) {
        Font renderer = client.font;
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);
        boolean hasAbsorption = !absorptionText.getString().isEmpty();
        int healthWidth = renderer.width(healthText);
        int absorptionWidth = renderer.width(absorptionText);

        if (modernStyle) {
            boolean rightAligned = ModernHudRenderer.isRightAligned(x, totalWidth);
            ModernHudRenderer.drawPanel(context, x, y, totalWidth, 17, heartColor);
            if (rightAligned) {
                int cursor = x + totalWidth - 7;
                if (hasAbsorption) {
                    int absorptionHeartX = cursor - 9;
                    int absorptionTextX = absorptionHeartX - absorptionWidth - 4;
                    drawAbsorption(context, renderer, absorptionText, absorptionHeartX, y + 4, absorptionTextX, y + 4, false, style, fontId);
                    cursor = absorptionTextX - 6;
                }
                int heartX = cursor - 9;
                int textX = Math.max(x + 6, heartX - healthWidth - 4);
                drawHeart(context, heartX, y + 4, heartColor);
                ModernHudRenderer.drawHudTextWithShadow(context, renderer, healthText, textX, y + 4, textColor);
                return;
            }

            int heartX = x + 7;
            int textX = x + 19;
            drawHeart(context, heartX, y + 4, heartColor);
            ModernHudRenderer.drawHudTextWithShadow(context, renderer, healthText, textX, y + 4, textColor);
            if (hasAbsorption) {
                int absorptionHeartX = textX + healthWidth + 6;
                drawAbsorption(
                        context,
                        renderer,
                        absorptionText,
                        absorptionHeartX,
                        y + 4,
                        absorptionHeartX + 12,
                        y + 4,
                        false,
                        style,
                        fontId
                );
            }
            return;
        }

        drawHeart(context, x, y, heartColor);
        int healthTextX = x + 11;
        if (stylizedStyle) {
            ModernHudRenderer.drawStyledText(context, renderer, style, fontId, healthText, healthTextX + 1, y, textColor);
        } else {
            ModernHudRenderer.drawHudTextWithShadow(context, renderer, healthText, healthTextX, y, textColor);
        }
        if (hasAbsorption) {
            int absorptionHeartX = healthTextX + healthWidth + 7;
            drawAbsorption(
                    context,
                    renderer,
                    absorptionText,
                    absorptionHeartX,
                    y,
                    absorptionHeartX + (stylizedStyle ? 12 : 11),
                    y,
                    stylizedStyle,
                    style,
                    fontId
            );
        }
    }

    private static void drawHeart(GuiGraphicsExtractor context, int x, int y, int color) {
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                HEART_TEXTURE,
                x,
                y,
                9,
                9,
                ModernHudRenderer.hudTextColor(color)
        );
    }

    private static void drawAbsorption(
            GuiGraphicsExtractor context,
            Font renderer,
            Component text,
            int heartX,
            int heartY,
            int textX,
            int textY,
            boolean stylizedStyle,
            String style,
            String fontId
    ) {
        ModernHudRenderer.withHudGradient(false, ABSORPTION_COLOR, () -> {
            drawHeart(context, heartX, heartY, ABSORPTION_COLOR);
            if (stylizedStyle) {
                ModernHudRenderer.drawStyledText(context, renderer, style, fontId, text, textX, textY, ABSORPTION_COLOR);
            } else {
                ModernHudRenderer.drawHudTextWithShadow(context, renderer, text, textX, textY, ABSORPTION_COLOR);
            }
        });
    }
}
