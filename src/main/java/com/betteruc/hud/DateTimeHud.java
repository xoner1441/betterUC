package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class DateTimeHud {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_SECONDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final LocalDateTime PREVIEW_TIME = LocalDateTime.of(2026, 8, 16, 16, 42, 37);
    private static long cachedEpochSecond = Long.MIN_VALUE;
    private static int cachedOptions = Integer.MIN_VALUE;
    private static DisplaySet cachedDisplays = DisplaySet.EMPTY;

    private DateTimeHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("betteruc", "date_time"),
                (context, tickCounter) -> {
                    if (ModernHudRenderer.shouldRenderGameplayHud()) {
                        render(context);
                    }
                }
        );
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (client.player == null || !config.showDateTimeHud) return;

        DisplaySet displays = currentDisplays(config);
        if (config.dateTimeHudSeparate) {
            drawScaledDisplay(context, client, config.dateTimeHudX, config.dateTimeHudY,
                    config.dateTimeHudScale, "DATUM", displays.date());
            drawScaledDisplay(context, client, config.dateTimeHudTimeX, config.dateTimeHudTimeY,
                    config.dateTimeHudTimeScale, "UHRZEIT", displays.time());
        } else {
            drawScaledDisplay(
                    context,
                    client,
                    config.dateTimeHudX,
                    config.dateTimeHudY,
                    config.dateTimeHudScale,
                    combinedLabel(config),
                    displays.combined()
            );
        }
    }

    public static void drawPreview(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        DisplaySet displays = previewDisplays(config);
        if (config.dateTimeHudSeparate) {
            drawBody(context, client, x, y, config.dateTimeHudStyle, config.dateTimeHudCustomFont,
                    config.dateTimeHudColor, "DATUM", displays.date());
            drawBody(context, client, x, y + previewHeight(false) + 2, config.dateTimeHudStyle,
                    config.dateTimeHudCustomFont, config.dateTimeHudColor, "UHRZEIT", displays.time());
        } else {
            drawBody(context, client, x, y, config.dateTimeHudStyle, config.dateTimeHudCustomFont,
                    config.dateTimeHudColor, combinedLabel(config), displays.combined());
        }
    }

    public static void drawLayoutPreview(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            boolean timeElement
    ) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        DisplaySet displays = previewDisplays(config);
        DisplayText display = timeElement ? displays.time() : layoutPrimaryDisplay(config, displays);
        String label = timeElement ? "UHRZEIT" : layoutPrimaryLabel(config);
        drawBody(context, client, x, y, config.dateTimeHudStyle, config.dateTimeHudCustomFont,
                config.dateTimeHudColor, label, display);
    }

    public static int previewWidth(Font font, boolean timeElement) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        DisplaySet displays = previewDisplays(config);
        DisplayText display = timeElement ? displays.time() : layoutPrimaryDisplay(config, displays);
        String label = timeElement ? "UHRZEIT" : layoutPrimaryLabel(config);
        return displayWidth(font, config.dateTimeHudStyle, label, display);
    }

    public static int previewHeight(boolean timeElement) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        DisplaySet displays = previewDisplays(config);
        DisplayText display = timeElement ? displays.time() : layoutPrimaryDisplay(config, displays);
        return displayHeight(config.dateTimeHudStyle, display);
    }

    private static int displayWidth(Font font, String style, String label, DisplayText display) {
        if (display.isEmpty()) return 1;

        if (BetterUCConfig.isModernHudStyle(style)) {
            int labelGap = label.isBlank() ? 0 : 5;
            int firstLineWidth = font.width(label) + font.width(display.primary()) + labelGap + 23;
            if (!display.hasSecondLine()) {
                return Math.max(58, firstLineWidth);
            }
            return Math.max(58, Math.max(firstLineWidth, font.width(display.secondary()) + 16));
        }
        return Math.max(font.width(display.primary()), font.width(display.secondary())) + 4;
    }

    private static int displayHeight(String style, DisplayText display) {
        boolean twoLines = display.hasSecondLine();
        if (BetterUCConfig.isModernHudStyle(style)) {
            return twoLines ? 31 : 18;
        }
        return twoLines ? 24 : 12;
    }

    static DisplayText format(
            LocalDateTime dateTime,
            boolean showDate,
            boolean showTime,
            boolean showSeconds,
            boolean separate
    ) {
        if (dateTime == null || (!showDate && !showTime)) return DisplayText.EMPTY;

        String date = showDate ? DATE_FORMAT.format(dateTime) : "";
        String time = showTime
                ? (showSeconds ? TIME_SECONDS_FORMAT : TIME_FORMAT).format(dateTime)
                : "";
        if (showDate && showTime) {
            return separate
                    ? new DisplayText(date, time)
                    : new DisplayText(date + " // " + time, "");
        }
        return new DisplayText(showDate ? date : time, "");
    }

    private static DisplaySet currentDisplays(BetterUCConfig config) {
        long epochSecond = System.currentTimeMillis() / 1000L;
        int options = optionSignature(config);
        if (epochSecond != cachedEpochSecond || options != cachedOptions) {
            cachedEpochSecond = epochSecond;
            cachedOptions = options;
            cachedDisplays = displays(LocalDateTime.now(), config);
        }
        return cachedDisplays;
    }

    private static DisplaySet previewDisplays(BetterUCConfig config) {
        return displays(PREVIEW_TIME, config);
    }

    private static DisplaySet displays(LocalDateTime dateTime, BetterUCConfig config) {
        return new DisplaySet(
                format(dateTime, config.dateTimeHudShowDate, config.dateTimeHudShowTime,
                        config.dateTimeHudShowSeconds, false),
                format(dateTime, config.dateTimeHudShowDate, false, false, false),
                format(dateTime, false, config.dateTimeHudShowTime, config.dateTimeHudShowSeconds, false)
        );
    }

    private static int optionSignature(BetterUCConfig config) {
        int signature = config.dateTimeHudShowDate ? 1 : 0;
        signature |= config.dateTimeHudShowTime ? 1 << 1 : 0;
        signature |= config.dateTimeHudShowSeconds ? 1 << 2 : 0;
        return signature;
    }

    private static void drawScaledDisplay(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            float scale,
            String label,
            DisplayText display
    ) {
        if (display.isEmpty()) return;
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                scale,
                config.dateTimeHudGradientEnabled,
                config.dateTimeHudGradientColor,
                () -> drawBody(context, client, 0, 0, config.dateTimeHudStyle,
                        config.dateTimeHudCustomFont, config.dateTimeHudColor, label, display)
        );
    }

    private static void drawBody(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String style,
            String fontId,
            int color,
            String label,
            DisplayText display
    ) {
        if (display.isEmpty()) return;

        if (BetterUCConfig.isModernHudStyle(style)) {
            if (display.hasSecondLine()) {
                ModernHudRenderer.drawTwoLineModule(
                        context,
                        client,
                        x,
                        y,
                        label,
                        display.primary(),
                        display.secondary(),
                        color,
                        color
                );
            } else {
                ModernHudRenderer.drawModule(
                        context,
                        client,
                        x,
                        y,
                        label,
                        display.primary(),
                        color
                );
            }
            return;
        }

        if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, client, style, fontId, display.primary(), x, y, color);
            if (display.hasSecondLine()) {
                ModernHudRenderer.drawStyledText(context, client, style, fontId, display.secondary(), x, y + 11, color);
            }
            return;
        }

        ModernHudRenderer.drawHudTextWithShadow(context, client.font, display.primary(), x, y, color);
        if (display.hasSecondLine()) {
            ModernHudRenderer.drawHudTextWithShadow(context, client.font, display.secondary(), x, y + 10, color);
        }
    }

    private static DisplayText layoutPrimaryDisplay(BetterUCConfig config, DisplaySet displays) {
        return config.dateTimeHudSeparate ? displays.date() : displays.combined();
    }

    private static String layoutPrimaryLabel(BetterUCConfig config) {
        return config.dateTimeHudSeparate ? "DATUM" : combinedLabel(config);
    }

    private static String combinedLabel(BetterUCConfig config) {
        if (config.dateTimeHudShowDate && config.dateTimeHudShowTime) return "DATUM & ZEIT";
        return config.dateTimeHudShowDate ? "DATUM" : "UHRZEIT";
    }

    private record DisplaySet(DisplayText combined, DisplayText date, DisplayText time) {
        private static final DisplaySet EMPTY = new DisplaySet(DisplayText.EMPTY, DisplayText.EMPTY, DisplayText.EMPTY);
    }

    record DisplayText(String primary, String secondary) {
        private static final DisplayText EMPTY = new DisplayText("", "");

        DisplayText {
            primary = primary == null ? "" : primary;
            secondary = secondary == null ? "" : secondary;
        }

        boolean hasSecondLine() {
            return !secondary.isEmpty();
        }

        boolean isEmpty() {
            return primary.isEmpty() && secondary.isEmpty();
        }
    }
}
