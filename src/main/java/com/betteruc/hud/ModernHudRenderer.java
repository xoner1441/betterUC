package com.betteruc.hud;

import com.betteruc.client.BetterUCFontManager;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ModernHudRenderer {

    public static final int TEXT_PRIMARY = 0xFFF8FAFC;
    public static final int TEXT_MUTED = 0xFFCBD5E1;
    public static final int TEXT_DIM = 0xFF94A3B8;

    private static final int PANEL_BG = 0xCC101318;
    private static final int PANEL_INNER = 0xB81A2029;
    private static final int PANEL_BORDER = 0x70313A47;
    private static final int PANEL_HIGHLIGHT = 0x24FFFFFF;
    private static final int MIN_MODULE_WIDTH = 58;
    private static final RenderTransform DEFAULT_TRANSFORM = new RenderTransform(0.0F, 1.0F);
    private static final ThreadLocal<RenderTransform> ACTIVE_TRANSFORM = ThreadLocal.withInitial(() -> DEFAULT_TRANSFORM);
    private static final ThreadLocal<GradientStyle> ACTIVE_GRADIENT = new ThreadLocal<>();

    private ModernHudRenderer() {
    }

    public static void drawScaled(
            GuiGraphicsExtractor context,
            int x,
            int y,
            float scale,
            Runnable drawAction
    ) {
        if (drawAction == null) return;

        float safeScale = BetterUCConfig.normalizeHudScale(scale);
        RenderTransform previousTransform = ACTIVE_TRANSFORM.get();
        context.pose().pushMatrix();
        try {
            ACTIVE_TRANSFORM.set(new RenderTransform(x, safeScale));
            context.pose().translate(x, y);
            context.pose().scale(safeScale, safeScale);
            drawAction.run();
        } finally {
            ACTIVE_TRANSFORM.set(previousTransform);
            context.pose().popMatrix();
        }
    }

    public static void drawScaledWithGradient(
            GuiGraphicsExtractor context,
            int x,
            int y,
            float scale,
            boolean gradientEnabled,
            int gradientColor,
            Runnable drawAction
    ) {
        withHudGradient(gradientEnabled, gradientColor, () -> drawScaled(context, x, y, scale, drawAction));
    }

    public static void drawScaledAround(
            GuiGraphicsExtractor context,
            int x,
            int y,
            float scale,
            Runnable drawAction
    ) {
        if (drawAction == null) return;

        float safeScale = BetterUCConfig.normalizeHudScale(scale);
        RenderTransform previousTransform = ACTIVE_TRANSFORM.get();
        context.pose().pushMatrix();
        try {
            ACTIVE_TRANSFORM.set(new RenderTransform(x * (1.0F - safeScale), safeScale));
            context.pose().translate(x, y);
            context.pose().scale(safeScale, safeScale);
            context.pose().translate(-x, -y);
            drawAction.run();
        } finally {
            ACTIVE_TRANSFORM.set(previousTransform);
            context.pose().popMatrix();
        }
    }

    public static void drawScaledAroundWithGradient(
            GuiGraphicsExtractor context,
            int x,
            int y,
            float scale,
            boolean gradientEnabled,
            int gradientColor,
            Runnable drawAction
    ) {
        withHudGradient(gradientEnabled, gradientColor, () -> drawScaledAround(context, x, y, scale, drawAction));
    }

    public static int scaledSize(int baseSize, float scale) {
        return Math.max(1, (int) Math.ceil(baseSize * BetterUCConfig.normalizeHudScale(scale)));
    }

    public static void withHudGradient(boolean enabled, int gradientColor, Runnable drawAction) {
        if (drawAction == null) return;

        GradientStyle previousGradient = ACTIVE_GRADIENT.get();
        ACTIVE_GRADIENT.set(new GradientStyle(enabled, gradientColor));
        try {
            drawAction.run();
        } finally {
            if (previousGradient == null) {
                ACTIVE_GRADIENT.remove();
            } else {
                ACTIVE_GRADIENT.set(previousGradient);
            }
        }
    }

    public static int hudTextColor(int baseColor) {
        return hudGradientColor(baseColor, 0.45F);
    }

    public static void drawHudTextWithShadow(
            GuiGraphicsExtractor context,
            Font renderer,
            Component text,
            int x,
            int y,
            int baseColor
    ) {
        Component safeText = text == null ? Component.literal("") : text;
        if (!hudGradientEnabled()) {
            context.text(renderer, safeText, x, y, withAlpha(baseColor, 0xFF));
            return;
        }
        drawTextLeftToRight(context, renderer, safeText, x, y, baseColor, true);
    }

    public static void drawHudTextWithShadow(
            GuiGraphicsExtractor context,
            Font renderer,
            String text,
            int x,
            int y,
            int baseColor
    ) {
        drawHudTextWithShadow(context, renderer, Component.literal(safe(text)), x, y, baseColor);
    }

    public static void drawModule(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String label,
            String value,
            int accentColor
    ) {
        drawModule(context, client, x, y, label, value, accentColor, TEXT_PRIMARY);
    }

    public static void drawModule(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String label,
            String value,
            int accentColor,
            int valueColor
    ) {
        Font renderer = client.font;
        String safeLabel = safe(label);
        String safeValue = safe(value);
        int labelWidth = renderer.width(safeLabel);
        int valueWidth = renderer.width(safeValue);
        int gap = safeLabel.isEmpty() ? 0 : 5;
        int width = Math.max(MIN_MODULE_WIDTH, labelWidth + valueWidth + gap + 23);
        int height = 18;
        boolean rightAligned = isRightAligned(x, width);

        drawPanel(context, x, y, width, height, accentColor);
        if (rightAligned) {
            int rightTextX = x + width - 8;
            int valueX = rightTextX - valueWidth;
            if (!safeLabel.isEmpty()) {
                int labelX = valueX - labelWidth - gap;
                drawHudTextWithShadow(context, renderer, safeLabel, labelX, y + 5, accentColor);
            }
            context.text(renderer, Component.literal(safeValue), valueX, y + 5, valueColor);
        } else {
            if (!safeLabel.isEmpty()) {
                drawHudTextWithShadow(context, renderer, safeLabel, x + 8, y + 5, accentColor);
            }
            context.text(renderer, Component.literal(safeValue), x + width - valueWidth - 7, y + 5, valueColor);
        }
    }

    public static void drawTwoLineModule(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String label,
            String primary,
            String secondary,
            int accentColor
    ) {
        drawTwoLineModule(context, client, x, y, label, primary, secondary, accentColor, TEXT_MUTED);
    }

    public static void drawTwoLineModule(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String label,
            String primary,
            String secondary,
            int accentColor,
            int secondaryColor
    ) {
        Font renderer = client.font;
        String safeLabel = safe(label);
        String safePrimary = safe(primary);
        String safeSecondary = safe(secondary);
        int labelWidth = renderer.width(safeLabel);
        int primaryWidth = renderer.width(safePrimary);
        int labelGap = safeLabel.isEmpty() ? 0 : 5;
        int width = Math.max(MIN_MODULE_WIDTH,
                Math.max(
                        labelWidth + primaryWidth + labelGap + 23,
                        renderer.width(safeSecondary) + 16
                ));
        int height = safeSecondary.isEmpty() ? 20 : 31;
        boolean rightAligned = isRightAligned(x, width);

        drawPanel(context, x, y, width, height, accentColor);
        if (rightAligned) {
            int rightTextX = x + width - 8;
            if (!safeLabel.isEmpty()) {
                int labelX = rightTextX - primaryWidth - labelWidth - labelGap;
                drawHudTextWithShadow(context, renderer, safeLabel, labelX, y + 5, accentColor);
            }
            context.text(renderer, Component.literal(safePrimary), rightTextX - primaryWidth, y + 5, TEXT_PRIMARY);
            if (!safeSecondary.isEmpty()) {
                context.text(renderer, Component.literal(safeSecondary), rightTextX - renderer.width(safeSecondary), y + 17, secondaryColor);
            }
        } else {
            if (!safeLabel.isEmpty()) {
                drawHudTextWithShadow(context, renderer, safeLabel, x + 8, y + 5, accentColor);
            }
            context.text(
                    renderer,
                    Component.literal(safePrimary),
                    x + width - renderer.width(safePrimary) - 7,
                    y + 5,
                    TEXT_PRIMARY
            );
            if (!safeSecondary.isEmpty()) {
                context.text(renderer, Component.literal(safeSecondary), x + 8, y + 17, secondaryColor);
            }
        }
    }

    public static void drawProgressModule(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String label,
            String value,
            float progress,
            int accentColor
    ) {
        Font renderer = client.font;
        String safeLabel = safe(label);
        String safeValue = safe(value);
        int labelWidth = renderer.width(safeLabel);
        int valueWidth = renderer.width(safeValue);
        int gap = safeLabel.isEmpty() ? 0 : 5;
        int width = Math.max(86, labelWidth + valueWidth + gap + 23);
        int height = 24;
        boolean rightAligned = isRightAligned(x, width);

        drawPanel(context, x, y, width, height, accentColor);
        if (rightAligned) {
            int rightTextX = x + width - 8;
            if (!safeLabel.isEmpty()) {
                int labelX = rightTextX - valueWidth - labelWidth - gap;
                drawHudTextWithShadow(context, renderer, safeLabel, labelX, y + 5, accentColor);
            }
            context.text(renderer, Component.literal(safeValue), rightTextX - valueWidth, y + 5, TEXT_PRIMARY);
        } else {
            if (!safeLabel.isEmpty()) {
                drawHudTextWithShadow(context, renderer, safeLabel, x + 8, y + 5, accentColor);
            }
            context.text(
                    renderer,
                    Component.literal(safeValue),
                    x + width - valueWidth - 7,
                    y + 5,
                    TEXT_PRIMARY
            );
        }

        int barX = x + 8;
        int barY = y + 18;
        int barWidth = width - 16;
        context.fill(barX, barY, barX + barWidth, barY + 2, 0x66313A47);
        int filledWidth = Math.round(barWidth * clamp01(progress));
        if (filledWidth > 0) {
            if (rightAligned) {
                fillHorizontalGradient(context, barX + barWidth - filledWidth, barY, filledWidth, 2, accentColor, 0xEE);
            } else {
                fillHorizontalGradient(context, barX, barY, filledWidth, 2, accentColor, 0xEE);
            }
        }
    }

    public static void drawStyledText(
            GuiGraphicsExtractor context,
            Minecraft client,
            String hudStyle,
            String text,
            int x,
            int y,
            int color
    ) {
        drawStyledText(context, client.font, hudStyle, BetterUCConfig.INSTANCE.customHudFont, Component.literal(safe(text)), x, y, color);
    }

    public static void drawStyledText(
            GuiGraphicsExtractor context,
            Minecraft client,
            String hudStyle,
            String fontId,
            String text,
            int x,
            int y,
            int color
    ) {
        drawStyledText(context, client.font, hudStyle, fontId, Component.literal(safe(text)), x, y, color);
    }

    public static void drawStyledText(
            GuiGraphicsExtractor context,
            Font renderer,
            String hudStyle,
            Component text,
            int x,
            int y,
            int color
    ) {
        drawStyledText(context, renderer, hudStyle, BetterUCConfig.INSTANCE.customHudFont, text, x, y, color);
    }

    public static void drawStyledText(
            GuiGraphicsExtractor context,
            Font renderer,
            String hudStyle,
            String fontId,
            Component text,
            int x,
            int y,
            int color
    ) {
        if (BetterUCConfig.isCustomHudStyle(hudStyle)) {
            drawCustomText(context, renderer, fontId, text, x, y, color);
            return;
        }
        drawCartoonText(context, renderer, text, x, y, color);
    }

    public static void drawCartoonText(
            GuiGraphicsExtractor context,
            Minecraft client,
            String text,
            int x,
            int y,
            int color
    ) {
        drawCartoonText(context, client.font, Component.literal(safe(text)), x, y, color);
    }

    public static void drawCartoonText(
            GuiGraphicsExtractor context,
            Font renderer,
            Component text,
            int x,
            int y,
            int color
    ) {
        Component safeText = text == null ? Component.literal("") : text;
        drawOutlinedText(context, renderer, safeText, x, y, color);
    }

    private static void drawCustomText(
            GuiGraphicsExtractor context,
            Font renderer,
            String fontId,
            Component text,
            int x,
            int y,
            int color
    ) {
        Component safeText = BetterUCFontManager.applyCustomHudFont(text == null ? Component.literal("") : text, fontId);
        drawOutlinedText(context, renderer, safeText, x, y, color);
    }

    private static void drawOutlinedText(
            GuiGraphicsExtractor context,
            Font renderer,
            Component text,
            int x,
            int y,
            int color
    ) {
        Component safeText = text == null ? Component.literal("") : text;
        if (hudGradientEnabled()) {
            drawOutlinedTextLeftToRight(context, renderer, safeText, x, y, color);
            return;
        }
        int solidColor = hudGradientColor(color, 0.45F);
        int outline = 0xFF24132E;
        int shadow = 0x99000000;

        context.text(renderer, safeText, x + 2, y + 2, shadow, false);
        context.text(renderer, safeText, x - 1, y, outline, false);
        context.text(renderer, safeText, x + 1, y, outline, false);
        context.text(renderer, safeText, x, y - 1, outline, false);
        context.text(renderer, safeText, x, y + 1, outline, false);
        context.text(renderer, safeText, x - 1, y - 1, outline, false);
        context.text(renderer, safeText, x + 1, y - 1, outline, false);
        context.text(renderer, safeText, x - 1, y + 1, outline, false);
        context.text(renderer, safeText, x + 1, y + 1, outline, false);
        context.text(renderer, safeText, x, y - 1, brighten(solidColor), false);
        context.text(renderer, safeText, x, y, solidColor, false);
    }

    public static void drawPanel(GuiGraphicsExtractor context, int x, int y, int width, int height, int accentColor) {
        int safeWidth = Math.max(8, width);
        int safeHeight = Math.max(8, height);
        boolean rightAligned = isRightAligned(x, safeWidth);

        fillSoftRect(context, x, y, safeWidth, safeHeight, PANEL_BG);
        fillSoftRect(context, x + 1, y + 1, safeWidth - 2, safeHeight - 2, PANEL_INNER);
        drawBorder(context, x, y, safeWidth, safeHeight, PANEL_BORDER);

        if (hudGradientEnabled()) {
            fillHorizontalGradient(context, x + 2, y + 2, safeWidth - 4, 1, accentColor, 0xA8);
        } else {
            context.fill(x + 2, y + 2, x + safeWidth - 2, y + 3, PANEL_HIGHLIGHT);
        }
        if (rightAligned) {
            context.fill(x + safeWidth - 4, y + 3, x + safeWidth - 2, y + safeHeight - 3, withAlpha(accentColor, 0xF2));
            context.fill(x + safeWidth - 5, y + 3, x + safeWidth - 4, y + safeHeight - 3, withAlpha(accentColor, 0x44));
        } else {
            context.fill(x + 2, y + 3, x + 4, y + safeHeight - 3, withAlpha(accentColor, 0xF2));
            context.fill(x + 4, y + 3, x + 5, y + safeHeight - 3, withAlpha(accentColor, 0x44));
        }
    }

    public static boolean isRightAligned(int x, int width) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        RenderTransform transform = ACTIVE_TRANSFORM.get();
        float safeWidth = Math.max(1, width) * transform.scale();
        float screenX = transform.originX() + (x * transform.scale());
        return screenX + safeWidth / 2.0F >= client.getWindow().getGuiScaledWidth() / 2.0F;
    }

    private static void fillSoftRect(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private static void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static void fillHorizontalGradient(GuiGraphicsExtractor context, int x, int y, int width, int height, int baseColor, int alpha) {
        int safeWidth = Math.max(1, width);
        for (int column = 0; column < safeWidth; column++) {
            float progress = safeWidth <= 1 ? 0.0F : column / (float) (safeWidth - 1);
            context.fill(x + column, y, x + column + 1, y + Math.max(1, height), withAlpha(hudGradientColor(baseColor, progress), alpha));
        }
    }

    private static void drawOutlinedTextLeftToRight(
            GuiGraphicsExtractor context,
            Font renderer,
            Component text,
            int x,
            int y,
            int baseColor
    ) {
        Component safeText = text == null ? Component.literal("") : text;
        int outline = 0xFF24132E;
        int shadow = 0x99000000;

        context.text(renderer, safeText, x + 2, y + 2, shadow, false);
        context.text(renderer, safeText, x - 1, y, outline, false);
        context.text(renderer, safeText, x + 1, y, outline, false);
        context.text(renderer, safeText, x, y - 1, outline, false);
        context.text(renderer, safeText, x, y + 1, outline, false);
        context.text(renderer, safeText, x - 1, y - 1, outline, false);
        context.text(renderer, safeText, x + 1, y - 1, outline, false);
        context.text(renderer, safeText, x - 1, y + 1, outline, false);
        context.text(renderer, safeText, x + 1, y + 1, outline, false);
        drawTextLeftToRight(context, renderer, safeText, x, y - 1, brighten(baseColor), false);
        drawTextLeftToRight(context, renderer, safeText, x, y, baseColor, false);
    }

    private static void drawTextLeftToRight(
            GuiGraphicsExtractor context,
            Font renderer,
            Component text,
            int x,
            int y,
            int baseColor,
            boolean shadow
    ) {
        Component safeText = text == null ? Component.literal("") : text;
        String raw = safeText.getString();
        if (raw.isEmpty()) return;

        int totalWidth = Math.max(1, renderer.width(safeText));
        int currentX = x;
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            String part = new String(Character.toChars(codePoint));
            Component partText = Component.literal(part).setStyle(safeText.getStyle());
            int partWidth = Math.max(1, renderer.width(partText));
            float progress = totalWidth <= partWidth ? 0.0F : (currentX - x + partWidth / 2.0F) / (float) totalWidth;
            int color = hudGradientColor(baseColor, progress);
            if (shadow) {
                context.text(renderer, partText, currentX, y, color);
            } else {
                context.text(renderer, partText, currentX, y, color, false);
            }
            currentX += partWidth;
            offset += Character.charCount(codePoint);
        }
    }

    private static int hudGradientColor(int baseColor, float progress) {
        int start = withAlpha(baseColor, 0xFF);
        if (!hudGradientEnabled()) {
            return start;
        }
        int end = withAlpha(activeGradientColor(), 0xFF);
        float safeProgress = clamp01(progress);
        int red = lerp((start >> 16) & 0xFF, (end >> 16) & 0xFF, safeProgress);
        int green = lerp((start >> 8) & 0xFF, (end >> 8) & 0xFF, safeProgress);
        int blue = lerp(start & 0xFF, end & 0xFF, safeProgress);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static boolean hudGradientEnabled() {
        GradientStyle style = ACTIVE_GRADIENT.get();
        return style != null && style.enabled();
    }

    private static int activeGradientColor() {
        GradientStyle style = ACTIVE_GRADIENT.get();
        return style == null ? BetterUCConfig.DEFAULT_HUD_GRADIENT_COLOR : style.color();
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    private static int brighten(int color) {
        int alpha = color & 0xFF000000;
        int red = Math.min(255, ((color >> 16) & 0xFF) + 56);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 56);
        int blue = Math.min(255, (color & 0xFF) + 56);
        return alpha | (red << 16) | (green << 8) | blue;
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record RenderTransform(float originX, float scale) {
    }

    private record GradientStyle(boolean enabled, int color) {
    }
}
