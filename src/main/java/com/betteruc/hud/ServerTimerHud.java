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

/** Displays the latest timer announced by UnicaCity's server-side timer system. */
public final class ServerTimerHud {

    private static final Pattern TIMER_PATTERN = Pattern.compile(
            "(?i)timer\\s*#\\s*(\\d+)\\s+lauft\\s*[-\\u2010-\\u2015]?\\s*"
                    + "(?:(\\d+)\\s*h(?:ours?|ours?|stunden?)?\\s*)?"
                    + "(?:(\\d+)\\s*m(?:in(?:uten?)?)?\\s*)?"
                    + "(?:(\\d+)\\s*s(?:ek(?:unden?)?)?\\s*)?"
                    + "[,]?\\s*fallig\\s+um\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+uhr"
    );

    private static long startAtMs;
    private static long endAtMs;
    private static long totalDurationMs;
    private static int timerNumber;
    private static String dueTime = "";
    private static int cachedSeconds = -1;
    private static String cachedValue = "";
    private static Component cachedText = Component.literal("");

    private ServerTimerHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("betteruc", "server_timer"),
                (context, tickCounter) -> {
                    if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
                }
        );
    }

    public static void clear() {
        startAtMs = 0L;
        endAtMs = 0L;
        totalDurationMs = 0L;
        timerNumber = 0;
        dueTime = "";
        cachedSeconds = -1;
        cachedValue = "";
    }

    public static void handleChatLine(String raw) {
        TimerAnnouncement announcement = parseTimerLine(raw);
        if (announcement == null) return;

        long now = System.currentTimeMillis();
        startAtMs = now;
        totalDurationMs = announcement.totalSeconds() * 1000L;
        endAtMs = now + totalDurationMs;
        timerNumber = announcement.number();
        dueTime = announcement.dueTime();
        cachedSeconds = -1;
    }

    public static void tick() {
        if (endAtMs > 0L && System.currentTimeMillis() >= endAtMs) {
            clear();
        }
    }

    public static TimerAnnouncement parseTimerLine(String raw) {
        if (raw == null || raw.isBlank()) return null;

        Matcher matcher = TIMER_PATTERN.matcher(normalize(raw));
        if (!matcher.find()) return null;

        int number = parseNumber(matcher.group(1));
        int hours = parseNumber(matcher.group(2));
        int minutes = parseNumber(matcher.group(3));
        int seconds = parseNumber(matcher.group(4));
        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
        if (number <= 0 || totalSeconds <= 0) return null;

        return new TimerAnnouncement(number, totalSeconds, normalizeClock(matcher.group(5)));
    }

    private static void render(GuiGraphicsExtractor context) {
        if (endAtMs <= 0L || !BetterUCConfig.INSTANCE.showServerTimerHud) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String value = timerValue();
        String label = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.serverTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.serverTimerHudPrefix
        );
        Component text = displayText(value);
        String style = BetterUCConfig.INSTANCE.serverTimerHudStyle;
        int color = BetterUCConfig.INSTANCE.serverTimerHudColor;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                BetterUCConfig.INSTANCE.serverTimerHudX,
                BetterUCConfig.INSTANCE.serverTimerHudY,
                BetterUCConfig.INSTANCE.serverTimerHudScale,
                BetterUCConfig.INSTANCE.serverTimerHudGradientEnabled,
                BetterUCConfig.INSTANCE.serverTimerHudGradientColor,
                () -> {
                    if (BetterUCConfig.isStylizedHudStyle(style)) {
                        ModernHudRenderer.drawStyledText(
                                context, client.font, style,
                                BetterUCConfig.INSTANCE.serverTimerHudCustomFont,
                                text, 0, 0, color
                        );
                    } else if (!BetterUCConfig.isModernHudStyle(style)) {
                        ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, 0, 0, color);
                    } else {
                        ModernHudRenderer.drawProgressModule(
                                context, client, 0, 0, label, value, progress(), color
                        );
                    }
                }
        );
    }

    private static String timerValue() {
        long remainingMs = Math.max(0L, endAtMs - System.currentTimeMillis());
        int seconds = Math.max(0, (int) Math.ceil(remainingMs / 1000.0D));
        if (seconds != cachedSeconds) {
            cachedSeconds = seconds;
            cachedValue = "#" + timerNumber + " | " + formatDuration(seconds) + " | " + dueTime;
        }
        return cachedValue;
    }

    private static Component displayText(String value) {
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.serverTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.serverTimerHudPrefix,
                value
        );
        if (!display.equals(cachedText.getString())) {
            cachedText = Component.literal(display);
        }
        return cachedText;
    }

    private static float progress() {
        if (startAtMs <= 0L || totalDurationMs <= 0L) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F,
                (System.currentTimeMillis() - startAtMs) / (float) totalDurationMs));
    }

    static String formatDuration(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        int seconds = safe % 60;
        if (hours > 0) {
            return twoDigits(hours) + ":" + twoDigits(minutes) + ":" + twoDigits(seconds);
        }
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    private static String normalize(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeClock(String clock) {
        if (clock == null) return "";
        String[] parts = clock.split(":");
        if (parts.length < 2) return clock;
        int hour = parseNumber(parts[0]);
        int minute = parseNumber(parts[1]);
        int second = parts.length >= 3 ? parseNumber(parts[2]) : 0;
        return twoDigits(hour) + ":" + twoDigits(minute) + ":" + twoDigits(second);
    }

    private static int parseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    public record TimerAnnouncement(int number, int totalSeconds, String dueTime) {
    }
}
