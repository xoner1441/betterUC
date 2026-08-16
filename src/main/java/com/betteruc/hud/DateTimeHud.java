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
    private static DisplayText cachedDisplay = DisplayText.EMPTY;

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

        DisplayText display = currentDisplay(config);
        if (display.isEmpty()) return;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                config.dateTimeHudX,
                config.dateTimeHudY,
                config.dateTimeHudScale,
                config.dateTimeHudGradientEnabled,
                config.dateTimeHudGradientColor,
                () -> drawBody(
                        context,
                        client,
                        0,
                        0,
                        config.dateTimeHudStyle,
                        config.dateTimeHudCustomFont,
                        config.dateTimeHudColor,
                        display
                )
        );
    }

    public static void drawPreview(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        drawBody(
                context,
                client,
                x,
                y,
                config.dateTimeHudStyle,
                config.dateTimeHudCustomFont,
                config.dateTimeHudColor,
                previewDisplay(config)
        );
    }

    public static int previewWidth(Font font) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        DisplayText display = previewDisplay(config);
        if (display.isEmpty()) return 1;

        if (BetterUCConfig.isModernHudStyle(config.dateTimeHudStyle)) {
            String label = moduleLabel(config);
            int labelGap = label.isBlank() ? 0 : 5;
            int firstLineWidth = font.width(label) + font.width(display.primary()) + labelGap + 23;
            if (!display.hasSecondLine()) {
                return Math.max(58, firstLineWidth);
            }
            return Math.max(58, Math.max(firstLineWidth, font.width(display.secondary()) + 16));
        }
        return Math.max(font.width(display.primary()), font.width(display.secondary())) + 4;
    }

    public static int previewHeight() {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        boolean twoLines = previewDisplay(config).hasSecondLine();
        if (BetterUCConfig.isModernHudStyle(config.dateTimeHudStyle)) {
            return twoLines ? 31 : 18;
        }
        return twoLines ? 24 : 12;
    }

    static DisplayText format(
            LocalDateTime dateTime,
            boolean showDate,
            boolean showTime,
            boolean showSeconds,
            boolean twoLines
    ) {
        if (dateTime == null || (!showDate && !showTime)) return DisplayText.EMPTY;

        String date = showDate ? DATE_FORMAT.format(dateTime) : "";
        String time = showTime
                ? (showSeconds ? TIME_SECONDS_FORMAT : TIME_FORMAT).format(dateTime)
                : "";
        if (showDate && showTime) {
            return twoLines
                    ? new DisplayText(date, time)
                    : new DisplayText(date + " // " + time, "");
        }
        return new DisplayText(showDate ? date : time, "");
    }

    private static DisplayText currentDisplay(BetterUCConfig config) {
        long epochSecond = System.currentTimeMillis() / 1000L;
        int options = optionSignature(config);
        if (epochSecond != cachedEpochSecond || options != cachedOptions) {
            cachedEpochSecond = epochSecond;
            cachedOptions = options;
            cachedDisplay = format(
                    LocalDateTime.now(),
                    config.dateTimeHudShowDate,
                    config.dateTimeHudShowTime,
                    config.dateTimeHudShowSeconds,
                    config.dateTimeHudTwoLines
            );
        }
        return cachedDisplay;
    }

    private static DisplayText previewDisplay(BetterUCConfig config) {
        return format(
                PREVIEW_TIME,
                config.dateTimeHudShowDate,
                config.dateTimeHudShowTime,
                config.dateTimeHudShowSeconds,
                config.dateTimeHudTwoLines
        );
    }

    private static int optionSignature(BetterUCConfig config) {
        int signature = config.dateTimeHudShowDate ? 1 : 0;
        signature |= config.dateTimeHudShowTime ? 1 << 1 : 0;
        signature |= config.dateTimeHudShowSeconds ? 1 << 2 : 0;
        signature |= config.dateTimeHudTwoLines ? 1 << 3 : 0;
        return signature;
    }

    private static void drawBody(
            GuiGraphicsExtractor context,
            Minecraft client,
            int x,
            int y,
            String style,
            String fontId,
            int color,
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
                        moduleLabel(BetterUCConfig.INSTANCE),
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
                        moduleLabel(BetterUCConfig.INSTANCE),
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

    private static String moduleLabel(BetterUCConfig config) {
        if (config.dateTimeHudShowDate && config.dateTimeHudShowTime) return "DATUM & ZEIT";
        return config.dateTimeHudShowDate ? "DATUM" : "UHRZEIT";
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
