package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MaskTimerHud {

    private static final long SHORT_TIME_WARNING_MS = 2L * 60L * 1000L;
    private static final int WARNING_COLOR = 0xFFFFB020;
    private static final Pattern START_PATTERN = Pattern.compile(
            "(?i)du\\s+bist\\s+nun\\s+fur\\s+(\\d+)\\s+minute(?:n)?\\s+maskiert"
    );

    private static long startAtMs = 0L;
    private static long endAtMs = 0L;
    private static long totalDurationMs = 0L;
    private static boolean warning = false;
    private static int cachedSeconds = -1;
    private static boolean cachedWarning = false;
    private static String cachedTimerValue = "00:00";
    private static Component cachedText = Component.literal("");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "mask_timer"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    public static void clear() {
        startAtMs = 0L;
        endAtMs = 0L;
        totalDurationMs = 0L;
        warning = false;
        cachedSeconds = -1;
        cachedWarning = false;
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (client == null || client.player == null || raw == null || raw.isBlank()) return;

        String folded = fold(raw);
        if (!folded.contains("masken")) return;

        if (folded.contains("du bist nun nicht mehr maskiert")) {
            clear();
            return;
        }

        if (folded.contains("du bist nur noch fur eine kurze zeit maskiert")) {
            markWarning();
            return;
        }

        Matcher matcher = START_PATTERN.matcher(folded);
        if (!matcher.find()) return;

        int minutes = parseNumber(matcher.group(1));
        if (minutes > 0) {
            start(minutes * 60);
        }
    }

    public static void tick() {
        if (endAtMs > 0L && System.currentTimeMillis() >= endAtMs) {
            clear();
        }
    }

    private static void start(int seconds) {
        startAtMs = System.currentTimeMillis();
        totalDurationMs = seconds * 1000L;
        endAtMs = startAtMs + totalDurationMs;
        warning = false;
        cachedSeconds = -1;
        cachedWarning = false;
    }

    private static void markWarning() {
        if (endAtMs <= 0L) return;

        long now = System.currentTimeMillis();
        long remainingMs = Math.max(0L, endAtMs - now);
        if (remainingMs > SHORT_TIME_WARNING_MS) {
            endAtMs = now + SHORT_TIME_WARNING_MS;
        }
        warning = true;
        cachedSeconds = -1;
    }

    private static void render(GuiGraphicsExtractor context) {
        if (endAtMs <= 0L) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !BetterUCConfig.INSTANCE.showMaskTimerHud) return;

        int x = BetterUCConfig.INSTANCE.maskTimerX;
        int y = BetterUCConfig.INSTANCE.maskTimerY;
        String timerValue = timerValue();
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.maskTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.maskTimerHudPrefix
        );
        String modernLabel = warning
                ? (moduleLabel.isBlank() ? "L\u00E4uft bald ab" : moduleLabel + " l\u00E4uft ab")
                : moduleLabel;
        Component text = displayText(timerValue);
        String style = BetterUCConfig.INSTANCE.maskTimerHudStyle;
        int color = warning ? WARNING_COLOR : BetterUCConfig.INSTANCE.maskTimerHudColor;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.maskTimerHudScale,
                BetterUCConfig.INSTANCE.maskTimerHudGradientEnabled,
                BetterUCConfig.INSTANCE.maskTimerHudGradientColor,
                () -> {
                    if (BetterUCConfig.isStylizedHudStyle(style)) {
                        ModernHudRenderer.drawStyledText(context, client.font, style,
                                BetterUCConfig.INSTANCE.maskTimerHudCustomFont, text, 0, 0, color);
                    } else if (!BetterUCConfig.isModernHudStyle(style)) {
                        ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, 0, 0, color);
                    } else {
                        ModernHudRenderer.drawProgressModule(
                                context, client, 0, 0, modernLabel, timerValue, progress(), color);
                    }
                });
    }

    private static float progress() {
        if (startAtMs <= 0L || totalDurationMs <= 0L) return 0.0F;
        long elapsedMs = System.currentTimeMillis() - startAtMs;
        return Math.max(0.0F, Math.min(1.0F, elapsedMs / (float) totalDurationMs));
    }

    private static String timerValue() {
        long remainingMs = Math.max(0L, endAtMs - System.currentTimeMillis());
        int seconds = Math.max(0, (int) Math.ceil(remainingMs / 1000.0D));
        if (seconds != cachedSeconds) {
            cachedSeconds = seconds;
            cachedTimerValue = twoDigits(seconds / 60) + ":" + twoDigits(seconds % 60);
        }
        return cachedTimerValue;
    }

    private static Component displayText(String timerValue) {
        String value = warning ? timerValue + " - l\u00E4uft bald ab" : timerValue;
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.maskTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.maskTimerHudPrefix,
                value
        );
        if (!display.equals(cachedText.getString()) || cachedWarning != warning) {
            cachedText = Component.literal(display);
            cachedWarning = warning;
        }
        return cachedText;
    }

    private static int parseNumber(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String fold(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
