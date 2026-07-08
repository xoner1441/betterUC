package com.betteruc.hud;

import com.betteruc.client.ServerCommandUtil;
import com.betteruc.config.BetterUCConfig;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ProductionTimerHud {

    private static final long NAVI_RETRY_WINDOW_MS = 12_000L;
    private static final Pattern PRODUCTION_TIME_PATTERN = Pattern.compile(
            "(?i)fertig\\s+in\\s*(?:(\\d+)\\s*m(?:in(?:uten)?)?\\s*)?(?:(\\d+)\\s*s(?:ek(?:unden)?)?)?"
    );
    private static final Pattern PRODUCTION_LABEL_PATTERN = Pattern.compile(
            "(?i)produktion\\s+gestartet\\s*[:\\-\\u2010-\\u2015]?\\s*(.+?)\\s*(?:[\\-\\u2010-\\u2015]\\s*)?fertig\\s+in",
            Pattern.DOTALL
    );

    private static long endAtMs = 0L;
    private static long startAtMs = 0L;
    private static long totalDurationMs = 0L;
    private static String productLabel = "";
    private static int targetX = 0;
    private static int targetY = 0;
    private static int targetZ = 0;
    private static String pendingNaviCommand = "";
    private static long pendingNaviUntilMs = 0L;
    private static int cachedSeconds = -1;
    private static String cachedTimerValue = "00:00";
    private static Component cachedText = Component.literal("");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "production_timer"), (context, tickCounter) -> render(context));
    }

    public static void clear() {
        endAtMs = 0L;
        startAtMs = 0L;
        totalDurationMs = 0L;
        productLabel = "";
        pendingNaviCommand = "";
        pendingNaviUntilMs = 0L;
        cachedSeconds = -1;
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (client == null || client.player == null || raw == null || raw.isBlank()) return;
        if (!BetterUCConfig.INSTANCE.showProductionTimerHud) return;

        String clean = cleanChat(raw);
        String folded = foldForMatch(clean);
        if (!folded.contains("produktion gestartet") || !folded.contains("fertig in")) {
            return;
        }

        Matcher matcher = PRODUCTION_TIME_PATTERN.matcher(clean);
        if (!matcher.find()) return;

        int minutes = parseNumber(matcher.group(1));
        int seconds = parseNumber(matcher.group(2));
        int totalSeconds = minutes * 60 + seconds;
        if (totalSeconds <= 0) return;

        start(
                totalSeconds,
                extractProductLabel(clean),
                (int) Math.floor(client.player.getX()),
                (int) Math.floor(client.player.getY()),
                (int) Math.floor(client.player.getZ())
        );
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (endAtMs > 0L && now >= endAtMs) {
            finishTimer();
        }

        if (!pendingNaviCommand.isBlank()) {
            if (now > pendingNaviUntilMs || client == null || client.player == null) {
                pendingNaviCommand = "";
                pendingNaviUntilMs = 0L;
                return;
            }
            if (ServerCommandUtil.sendAutomatic(client, pendingNaviCommand)) {
                pendingNaviCommand = "";
                pendingNaviUntilMs = 0L;
            }
        }
    }

    private static void start(int seconds, String label, int x, int y, int z) {
        startAtMs = System.currentTimeMillis();
        totalDurationMs = seconds * 1000L;
        endAtMs = startAtMs + totalDurationMs;
        productLabel = sanitizeLabel(label);
        targetX = x;
        targetY = y;
        targetZ = z;
        pendingNaviCommand = "";
        pendingNaviUntilMs = 0L;
        cachedSeconds = -1;
    }

    private static void finishTimer() {
        endAtMs = 0L;
        startAtMs = 0L;
        totalDurationMs = 0L;
        cachedSeconds = -1;
        pendingNaviCommand = "navi " + targetX + "/" + targetY + "/" + targetZ;
        pendingNaviUntilMs = System.currentTimeMillis() + NAVI_RETRY_WINDOW_MS;
    }

    private static void render(GuiGraphicsExtractor context) {
        if (endAtMs <= 0L) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showProductionTimerHud) return;

        int x = BetterUCConfig.INSTANCE.productionTimerX;
        int y = BetterUCConfig.INSTANCE.productionTimerY;
        String timerValue = timerValue();
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.productionTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.productionTimerHudPrefix
        );
        Component text = displayText(timerValue);
        String style = BetterUCConfig.INSTANCE.productionTimerHudStyle;
        int color = BetterUCConfig.INSTANCE.productionTimerHudColor;

        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.productionTimerHudScale,
                BetterUCConfig.INSTANCE.productionTimerHudGradientEnabled,
                BetterUCConfig.INSTANCE.productionTimerHudGradientColor,
                () -> {
                    if (BetterUCConfig.isStylizedHudStyle(style)) {
                        ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.productionTimerHudCustomFont, text, 0, 0, color);
                    } else if (!BetterUCConfig.isModernHudStyle(style)) {
                        ModernHudRenderer.drawHudTextWithShadow(context, client.font, text, 0, 0, color);
                    } else {
                        ModernHudRenderer.drawProgressModule(context, client, 0, 0, moduleLabel, timerValue, progress(), color);
                    }
                });
    }

    private static float progress() {
        if (startAtMs <= 0L || totalDurationMs <= 0L) return 0.0F;
        long elapsedMs = System.currentTimeMillis() - startAtMs;
        return elapsedMs / (float) totalDurationMs;
    }

    private static String timerValue() {
        long remainingMs = Math.max(0L, endAtMs - System.currentTimeMillis());
        int seconds = Math.max(0, (int) Math.ceil(remainingMs / 1000.0D));
        if (seconds != cachedSeconds) {
            cachedSeconds = seconds;
            int minutes = seconds / 60;
            int rest = seconds % 60;
            cachedTimerValue = twoDigits(minutes) + ":" + twoDigits(rest);
        }
        return cachedTimerValue;
    }

    private static Component displayText(String timerValue) {
        String value = productLabel.isBlank() ? timerValue : productLabel + " " + timerValue;
        String display = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.productionTimerHudPrefixEnabled,
                BetterUCConfig.INSTANCE.productionTimerHudPrefix,
                value
        );
        if (!display.equals(cachedText.getString())) {
            cachedText = Component.literal(display);
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

    private static String cleanChat(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceFirst("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+", "")
                .replaceFirst("^\\s*[^\\p{L}\\p{N}\\[]+\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String extractProductLabel(String clean) {
        Matcher matcher = PRODUCTION_LABEL_PATTERN.matcher(clean);
        if (!matcher.find()) return "";
        return matcher.group(1);
    }

    private static String foldForMatch(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2015', '-')
                .replace('\u2212', '-')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String sanitizeLabel(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^\\p{L}\\p{N} _\\-]", "")
                .trim();
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
