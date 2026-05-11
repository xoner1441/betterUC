package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BankBalanceHud {

    private static final Pattern BANK_BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:ihr\\s+bankguthaben\\s+betr(?:a|ae|\\u00E4)gt\\s*:?|neuer\\s+(?:bank\\s*)?kontostand\\s*:?|neuer\\s+betrag\\s*:?)\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );
    private static final Pattern PREVIOUS_BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:vorheriger\\s+kontostand\\s*:?|alter\\s+betrag\\s*:?)\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );

    private static int currentBankBalance = -1;
    private static final DecimalFormat MONEY_FORMAT = createMoneyFormat();

    public static void register() {
        restoreFromConfig();
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void updateFromChatLine(String raw) {
        if (raw == null || raw.isBlank()) return;

        Matcher balanceMatcher = BANK_BALANCE_PATTERN.matcher(raw);
        if (balanceMatcher.find()) {
            Integer parsed = parseMoneyValue(balanceMatcher.group(1));
            if (parsed != null) {
                setBalanceAndPersist(Math.max(0, parsed));
            }
            return;
        }

        if (currentBankBalance >= 0) return;
        Matcher previousMatcher = PREVIOUS_BALANCE_PATTERN.matcher(raw);
        if (previousMatcher.find()) {
            Integer parsed = parseMoneyValue(previousMatcher.group(1));
            if (parsed != null) {
                setBalanceAndPersist(Math.max(0, parsed));
            }
        }
    }

    public static int getCurrentBankBalance() {
        return currentBankBalance;
    }

    public static String formatMoney(int value) {
        if (value < 0) return String.valueOf(value);
        return MONEY_FORMAT.format(value);
    }

    public static void clear() {
        restoreFromConfig();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!BetterUCConfig.INSTANCE.showBankHud) return;
        if (currentBankBalance < 0) return;

        int x = BetterUCConfig.INSTANCE.bankHudX;
        int y = BetterUCConfig.INSTANCE.bankHudY;
        String text = "Bank: " + formatMoney(currentBankBalance) + "$";
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, BetterUCConfig.INSTANCE.bankHudColor);
    }

    private static Integer parseMoneyValue(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().replace(".", "").replace(" ", "");
        if (normalized.isEmpty()) return null;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void restoreFromConfig() {
        currentBankBalance = Math.max(-1, BetterUCConfig.INSTANCE.lastKnownBankBalance);
    }

    private static void setBalanceAndPersist(int newBalance) {
        if (newBalance < 0) return;
        boolean changed = currentBankBalance != newBalance
                || BetterUCConfig.INSTANCE.lastKnownBankBalance != newBalance;
        currentBankBalance = newBalance;
        BetterUCConfig.INSTANCE.lastKnownBankBalance = newBalance;
        if (changed) {
            BetterUCConfig.save();
        }
    }

    private static DecimalFormat createMoneyFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        format.setGroupingUsed(true);
        return format;
    }
}
