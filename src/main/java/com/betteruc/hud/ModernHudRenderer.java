package com.betteruc.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class ModernHudRenderer {

    public static final int TEXT_PRIMARY = 0xFFF8FAFC;
    public static final int TEXT_MUTED = 0xFFCBD5E1;
    public static final int TEXT_DIM = 0xFF94A3B8;

    private static final int PANEL_BG = 0xCC101318;
    private static final int PANEL_INNER = 0xB81A2029;
    private static final int PANEL_BORDER = 0x70313A47;
    private static final int PANEL_HIGHLIGHT = 0x24FFFFFF;
    private static final int MIN_MODULE_WIDTH = 58;

    private ModernHudRenderer() {
    }

    public static void drawModule(
            DrawContext context,
            MinecraftClient client,
            int x,
            int y,
            String label,
            String value,
            int accentColor
    ) {
        drawModule(context, client, x, y, label, value, accentColor, TEXT_PRIMARY);
    }

    public static void drawModule(
            DrawContext context,
            MinecraftClient client,
            int x,
            int y,
            String label,
            String value,
            int accentColor,
            int valueColor
    ) {
        TextRenderer renderer = client.textRenderer;
        String safeLabel = safe(label);
        String safeValue = safe(value);
        int labelWidth = renderer.getWidth(safeLabel);
        int valueWidth = renderer.getWidth(safeValue);
        int width = Math.max(MIN_MODULE_WIDTH, labelWidth + valueWidth + 28);
        int height = 18;

        drawPanel(context, x, y, width, height, accentColor);
        context.drawTextWithShadow(renderer, Text.literal(safeLabel), x + 8, y + 5, withAlpha(accentColor, 0xFF));
        context.drawTextWithShadow(renderer, Text.literal(safeValue), x + width - valueWidth - 7, y + 5, valueColor);
    }

    public static void drawTwoLineModule(
            DrawContext context,
            MinecraftClient client,
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
            DrawContext context,
            MinecraftClient client,
            int x,
            int y,
            String label,
            String primary,
            String secondary,
            int accentColor,
            int secondaryColor
    ) {
        TextRenderer renderer = client.textRenderer;
        String safeLabel = safe(label);
        String safePrimary = safe(primary);
        String safeSecondary = safe(secondary);
        int width = Math.max(MIN_MODULE_WIDTH,
                Math.max(
                        renderer.getWidth(safeLabel) + renderer.getWidth(safePrimary) + 28,
                        renderer.getWidth(safeSecondary) + 16
                ));
        int height = safeSecondary.isEmpty() ? 20 : 31;

        drawPanel(context, x, y, width, height, accentColor);
        context.drawTextWithShadow(renderer, Text.literal(safeLabel), x + 8, y + 5, withAlpha(accentColor, 0xFF));
        context.drawTextWithShadow(
                renderer,
                Text.literal(safePrimary),
                x + width - renderer.getWidth(safePrimary) - 7,
                y + 5,
                TEXT_PRIMARY
        );
        if (!safeSecondary.isEmpty()) {
            context.drawTextWithShadow(renderer, Text.literal(safeSecondary), x + 8, y + 17, secondaryColor);
        }
    }

    public static void drawProgressModule(
            DrawContext context,
            MinecraftClient client,
            int x,
            int y,
            String label,
            String value,
            float progress,
            int accentColor
    ) {
        TextRenderer renderer = client.textRenderer;
        String safeLabel = safe(label);
        String safeValue = safe(value);
        int width = Math.max(86, renderer.getWidth(safeLabel) + renderer.getWidth(safeValue) + 28);
        int height = 24;

        drawPanel(context, x, y, width, height, accentColor);
        context.drawTextWithShadow(renderer, Text.literal(safeLabel), x + 8, y + 5, withAlpha(accentColor, 0xFF));
        context.drawTextWithShadow(
                renderer,
                Text.literal(safeValue),
                x + width - renderer.getWidth(safeValue) - 7,
                y + 5,
                TEXT_PRIMARY
        );

        int barX = x + 8;
        int barY = y + 18;
        int barWidth = width - 16;
        context.fill(barX, barY, barX + barWidth, barY + 2, 0x66313A47);
        int filledWidth = Math.round(barWidth * clamp01(progress));
        if (filledWidth > 0) {
            context.fill(barX, barY, barX + filledWidth, barY + 2, withAlpha(accentColor, 0xEE));
        }
    }

    public static void drawPanel(DrawContext context, int x, int y, int width, int height, int accentColor) {
        int safeWidth = Math.max(8, width);
        int safeHeight = Math.max(8, height);

        fillSoftRect(context, x, y, safeWidth, safeHeight, PANEL_BG);
        fillSoftRect(context, x + 1, y + 1, safeWidth - 2, safeHeight - 2, PANEL_INNER);
        drawBorder(context, x, y, safeWidth, safeHeight, PANEL_BORDER);

        context.fill(x + 2, y + 2, x + safeWidth - 2, y + 3, PANEL_HIGHLIGHT);
        context.fill(x + 2, y + 3, x + 4, y + safeHeight - 3, withAlpha(accentColor, 0xF2));
        context.fill(x + 4, y + 3, x + 5, y + safeHeight - 3, withAlpha(accentColor, 0x44));
    }

    private static void fillSoftRect(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
