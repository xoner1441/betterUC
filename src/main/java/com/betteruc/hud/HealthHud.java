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

    private static final Identifier HEART_TEXTURE = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Component EMPTY_TEXT = Component.literal("");
    private static final Component PREVIEW_HEALTH_TEXT = Component.literal("10");
    private static final Component PREVIEW_ABSORPTION_TEXT = Component.literal("2");
    private static int cachedHealthHalfUnits = Integer.MIN_VALUE;
    private static int cachedAbsorptionHalfUnits = Integer.MIN_VALUE;
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
        int healthHalfUnits = displayedHalfHeartUnits(player.getHealth());
        int absorptionHalfUnits = BetterUCConfig.INSTANCE.showHealthAbsorption
                ? displayedHalfHeartUnits(player.getAbsorptionAmount())
                : 0;
        Component healthText = getHealthText(healthHalfUnits);
        Component absorptionText = getAbsorptionText(absorptionHalfUnits);

        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int centerY = client.getWindow().getGuiScaledHeight() / 2;

        String style = BetterUCConfig.INSTANCE.healthHudStyle;
        int normalWidth = getHudWidth(client.font, style, healthText, EMPTY_TEXT);
        int totalWidth = getHudWidth(client.font, style, healthText, absorptionText);
        float scale = BetterUCConfig.INSTANCE.healthHudScale;

        int startX = BetterUCConfig.INSTANCE.healthHudX >= 0
                ? BetterUCConfig.INSTANCE.healthHudX - centeredGrowthOffset(normalWidth, totalWidth, scale)
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
                        textColor,
                        BetterUCConfig.INSTANCE.healthHudAbsorptionColor
                )
        );
    }

    private static Component getHealthText(int halfHeartUnits) {
        if (halfHeartUnits != cachedHealthHalfUnits) {
            cachedHealthHalfUnits = halfHeartUnits;
            cachedHealthText = Component.literal(formatHeartCount(halfHeartUnits));
        }
        return cachedHealthText;
    }

    private static Component getAbsorptionText(int halfHeartUnits) {
        if (halfHeartUnits != cachedAbsorptionHalfUnits) {
            cachedAbsorptionHalfUnits = halfHeartUnits;
            cachedAbsorptionText = halfHeartUnits > 0
                    ? Component.literal(formatHeartCount(halfHeartUnits))
                    : Component.literal("");
        }
        return cachedAbsorptionText;
    }

    static int displayedHalfHeartUnits(float healthPoints) {
        if (!Float.isFinite(healthPoints) || healthPoints <= 0.0F) {
            return 0;
        }
        return Math.max(0, (int) Math.ceil(healthPoints));
    }

    static String formatHeartCount(int halfHeartUnits) {
        int safeUnits = Math.max(0, halfHeartUnits);
        int wholeHearts = safeUnits / 2;
        return (safeUnits & 1) == 0 ? Integer.toString(wholeHearts) : wholeHearts + ",5";
    }

    public static int getPreviewWidth(Font renderer, String style) {
        Component absorption = BetterUCConfig.INSTANCE.showHealthAbsorption ? PREVIEW_ABSORPTION_TEXT : EMPTY_TEXT;
        return getHudWidth(renderer, style, PREVIEW_HEALTH_TEXT, absorption);
    }

    public static int getBasePreviewWidth(Font renderer, String style) {
        return getHudWidth(renderer, style, PREVIEW_HEALTH_TEXT, EMPTY_TEXT);
    }

    public static int getPreviewCenterOffset(Font renderer, String style, float scale) {
        return centeredGrowthOffset(
                getBasePreviewWidth(renderer, style),
                getPreviewWidth(renderer, style),
                scale
        );
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
        Component absorption = BetterUCConfig.INSTANCE.showHealthAbsorption ? PREVIEW_ABSORPTION_TEXT : EMPTY_TEXT;
        drawValue(
                context,
                client,
                x,
                y,
                style,
                fontId,
                PREVIEW_HEALTH_TEXT,
                absorption,
                width,
                heartColor,
                textColor,
                BetterUCConfig.INSTANCE.healthHudAbsorptionColor
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

    private static int centeredGrowthOffset(int normalWidth, int expandedWidth, float scale) {
        int normalScaledWidth = ModernHudRenderer.scaledSize(normalWidth, scale);
        int expandedScaledWidth = ModernHudRenderer.scaledSize(expandedWidth, scale);
        return Math.max(0, expandedScaledWidth - normalScaledWidth) / 2;
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
            int textColor,
            int absorptionColor
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
                    drawAbsorption(context, renderer, absorptionText, absorptionHeartX, y + 4, absorptionTextX, y + 4,
                            false, style, fontId, absorptionColor);
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
                        fontId,
                        absorptionColor
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
                    fontId,
                    absorptionColor
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
            String fontId,
            int absorptionColor
    ) {
        ModernHudRenderer.withHudGradient(false, absorptionColor, () -> {
            drawHeart(context, heartX, heartY, absorptionColor);
            if (stylizedStyle) {
                ModernHudRenderer.drawStyledText(context, renderer, style, fontId, text, textX, textY, absorptionColor);
            } else {
                ModernHudRenderer.drawHudTextWithShadow(context, renderer, text, textX, textY, absorptionColor);
            }
        });
    }
}
