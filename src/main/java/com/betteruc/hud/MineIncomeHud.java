package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks mine income that is waiting to be paid out at the next PayDay. */
public final class MineIncomeHud {

    private static final long MESSAGE_DEDUP_WINDOW_MS = 1_200L;
    private static final Pattern TEXT_FORMATTING_PATTERN = Pattern.compile("\\u00A7.");
    private static final Pattern CHAT_TIMESTAMP_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+");
    private static final Pattern MINE_INCOME_PATTERN = Pattern.compile(
            "(?iu)^\\[payday]\\s+du\\s+bekommst\\s+deine\\s+minen?(?:\\s+|-)einnahmen\\s+von\\s+"
                    + "([0-9][0-9.]*)\\s*\\$\\s+am\\s+payday\\s+ausgezahlt\\s*[.!]?\\s*$"
    );
    private static final DecimalFormat MONEY_FORMAT = createMoneyFormat();

    private static long currentIncome;
    private static String lastMessageKey = "";
    private static long lastMessageAtMs;

    private MineIncomeHud() {
    }

    public static void register() {
        restoreFromConfig();
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("betteruc", "mine_income"),
                (context, tickCounter) -> {
                    if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
                }
        );
    }

    public static boolean updateFromChatLine(String raw) {
        Long amount = parseMineIncome(raw);
        if (amount == null) return false;

        String key = normalizedMessageKey(raw);
        long now = System.currentTimeMillis();
        if (key.equals(lastMessageKey) && now - lastMessageAtMs >= 0L
                && now - lastMessageAtMs <= MESSAGE_DEDUP_WINDOW_MS) {
            return true;
        }

        currentIncome = saturatingAdd(currentIncome, amount);
        lastMessageKey = key;
        lastMessageAtMs = now;
        persist();
        return true;
    }

    public static void resetForNewPayday() {
        currentIncome = 0L;
        lastMessageKey = "";
        lastMessageAtMs = 0L;
        persist();
    }

    public static void clear() {
        restoreFromConfig();
        lastMessageKey = "";
        lastMessageAtMs = 0L;
    }

    public static long getCurrentIncome() {
        return currentIncome;
    }

    public static String formatMoney(long value) {
        return MONEY_FORMAT.format(Math.max(0L, value));
    }

    static Long parseMineIncome(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = stripChatPrefix(stripFormatting(raw));
        Matcher matcher = MINE_INCOME_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return null;
        try {
            long amount = Long.parseLong(matcher.group(1).replace(".", ""));
            return amount > 0L ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !BetterUCConfig.INSTANCE.showMineIncomeHud
                || !shouldShow(currentIncome)) return;

        String value = formatMoney(currentIncome) + "$";
        String style = BetterUCConfig.INSTANCE.mineIncomeHudStyle;
        String displayText = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.mineIncomeHudPrefixEnabled,
                BetterUCConfig.INSTANCE.mineIncomeHudPrefix,
                value
        );
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.mineIncomeHudPrefixEnabled,
                BetterUCConfig.INSTANCE.mineIncomeHudPrefix
        );

        ModernHudRenderer.drawScaledWithGradient(
                context,
                BetterUCConfig.INSTANCE.mineIncomeHudX,
                BetterUCConfig.INSTANCE.mineIncomeHudY,
                BetterUCConfig.INSTANCE.mineIncomeHudScale,
                BetterUCConfig.INSTANCE.mineIncomeHudGradientEnabled,
                BetterUCConfig.INSTANCE.mineIncomeHudGradientColor,
                () -> {
                    if (BetterUCConfig.isStylizedHudStyle(style)) {
                        ModernHudRenderer.drawStyledText(
                                context,
                                client,
                                style,
                                BetterUCConfig.INSTANCE.mineIncomeHudCustomFont,
                                displayText,
                                0,
                                0,
                                BetterUCConfig.INSTANCE.mineIncomeHudColor
                        );
                    } else if (!BetterUCConfig.isModernHudStyle(style)) {
                        ModernHudRenderer.drawHudTextWithShadow(
                                context,
                                client.font,
                                displayText,
                                0,
                                0,
                                BetterUCConfig.INSTANCE.mineIncomeHudColor
                        );
                    } else {
                        ModernHudRenderer.drawModule(
                                context,
                                client,
                                0,
                                0,
                                moduleLabel,
                                value,
                                BetterUCConfig.INSTANCE.mineIncomeHudColor
                        );
                    }
                }
        );
    }

    private static void restoreFromConfig() {
        currentIncome = Math.max(0L, BetterUCConfig.INSTANCE.lastKnownMineIncome);
    }

    private static void persist() {
        long safeIncome = Math.max(0L, currentIncome);
        if (BetterUCConfig.INSTANCE.lastKnownMineIncome == safeIncome) return;
        BetterUCConfig.INSTANCE.lastKnownMineIncome = safeIncome;
        BetterUCConfig.save();
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return Math.max(0L, left);
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    static boolean shouldShow(long income) {
        return income > 0L;
    }

    private static String stripFormatting(String raw) {
        return TEXT_FORMATTING_PATTERN.matcher(raw).replaceAll("");
    }

    private static String stripChatPrefix(String raw) {
        String cleaned = CHAT_TIMESTAMP_PATTERN.matcher(raw).replaceFirst("");
        return cleaned.replaceFirst("^\\s*[^\\p{L}\\p{N}\\[]+\\s*", "").trim();
    }

    private static String normalizedMessageKey(String raw) {
        return stripChatPrefix(stripFormatting(raw)).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static DecimalFormat createMoneyFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        format.setGroupingUsed(true);
        return format;
    }
}
