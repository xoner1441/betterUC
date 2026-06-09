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

public class CashHud {
    private static final Pattern TEXT_FORMATTING_PATTERN = Pattern.compile("\\u00A7.");
    private static final Pattern CASH_STATS_PATTERN = Pattern.compile(
            "^\\s*[-\\u2010-\\u2015\\u2212]?\\s*Geld\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CASH_BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:neuer\\s+bargeldbestand|bargeldbestand)\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );
    private static final Pattern CASH_PAYOUT_PATTERN = Pattern.compile(
            "(?i)auszahlung\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );
    private static final Pattern CASH_DEPOSIT_PATTERN = Pattern.compile(
            "(?i)eingezahlt\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );
    private static final Pattern FACTION_BANK_DEPOSIT_PATTERN = Pattern.compile(
            "(?i)\\[\\s*F-?Bank\\s*]\\s+(.+?)\\s+hat\\s+([0-9][0-9\\.]*)\\s*\\$\\s+(?:auf|in)\\s+die\\s+Fraktionsbank\\s+eingezahlt\\b"
    );
    private static final Pattern FACTION_BANK_WITHDRAW_PATTERN = Pattern.compile(
            "(?i)\\[\\s*F-?Bank\\s*]\\s+(.+?)\\s+hat\\s+([0-9][0-9\\.]*)\\s*\\$\\s+aus\\s+der\\s+Fraktionsbank\\s+genommen\\b"
    );
    private static final Pattern PLAYER_MONEY_SENT_PATTERN = Pattern.compile(
            "(?i)\\bdu\\s+hast\\s+(.+?)\\s+([0-9][0-9\\.]*)\\s*\\$\\s+gegeben\\s*!?"
    );
    private static final Pattern PLAYER_MONEY_RECEIVED_PATTERN = Pattern.compile(
            "(?i)\\b(.+?)\\s+hat\\s+dir\\s+([0-9][0-9\\.]*)\\s*\\$\\s+gegeben\\s*!?"
    );
    private static final Pattern CASH_SIGNED_DELTA_PATTERN = Pattern.compile(
            "^\\s*([+-])\\s*([0-9][0-9\\.]*)\\s*\\$\\s*$"
    );
    private static final DecimalFormat MONEY_FORMAT = createMoneyFormat();

    private static int currentCash = -1;

    public static void register() {
        restoreFromConfig();
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void updateFromStatsLine(String raw) {
        if (raw == null || raw.isBlank()) return;
        raw = stripFormatting(raw);

        Matcher factionDepositMatcher = FACTION_BANK_DEPOSIT_PATTERN.matcher(raw);
        if (factionDepositMatcher.find() && isCurrentPlayer(factionDepositMatcher.group(1))) {
            Integer parsed = parseMoneyValue(factionDepositMatcher.group(2));
            if (parsed != null) {
                subtractCashAndPersist(parsed);
            }
            return;
        }

        Matcher factionWithdrawMatcher = FACTION_BANK_WITHDRAW_PATTERN.matcher(raw);
        if (factionWithdrawMatcher.find() && isCurrentPlayer(factionWithdrawMatcher.group(1))) {
            Integer parsed = parseMoneyValue(factionWithdrawMatcher.group(2));
            if (parsed != null) {
                addCashAndPersist(parsed);
            }
            return;
        }

        Matcher moneySentMatcher = PLAYER_MONEY_SENT_PATTERN.matcher(raw);
        if (moneySentMatcher.find()) {
            Integer parsed = parseMoneyValue(moneySentMatcher.group(2));
            if (parsed != null) {
                subtractCashAndPersist(parsed);
            }
            return;
        }

        Matcher moneyReceivedMatcher = PLAYER_MONEY_RECEIVED_PATTERN.matcher(raw);
        if (moneyReceivedMatcher.find()) {
            Integer parsed = parseMoneyValue(moneyReceivedMatcher.group(2));
            if (parsed != null) {
                addCashAndPersist(parsed);
            }
            return;
        }

        Matcher payoutMatcher = CASH_PAYOUT_PATTERN.matcher(raw);
        if (payoutMatcher.find()) {
            Integer parsed = parseMoneyValue(payoutMatcher.group(1));
            if (parsed != null) {
                addCashAndPersist(Math.abs(parsed));
            }
            return;
        }

        Matcher depositMatcher = CASH_DEPOSIT_PATTERN.matcher(raw);
        if (depositMatcher.find()) {
            Integer parsed = parseMoneyValue(depositMatcher.group(1));
            if (parsed != null) {
                subtractCashAndPersist(Math.abs(parsed));
            }
            return;
        }

        Matcher signedDeltaMatcher = CASH_SIGNED_DELTA_PATTERN.matcher(raw);
        if (signedDeltaMatcher.find()) {
            Integer parsed = parseMoneyValue(signedDeltaMatcher.group(2));
            if (parsed != null) {
                if ("+".equals(signedDeltaMatcher.group(1))) {
                    addCashAndPersist(parsed);
                } else {
                    subtractCashAndPersist(parsed);
                }
            }
            return;
        }

        Matcher matcher = CASH_STATS_PATTERN.matcher(raw);
        if (!matcher.find()) {
            matcher = CASH_BALANCE_PATTERN.matcher(raw);
            if (!matcher.find()) return;
        }

        Integer parsed = parseMoneyValue(matcher.group(1));
        if (parsed != null) {
            setCashAndPersist(Math.max(0, parsed));
        }
    }

    private static boolean isCurrentPlayer(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || name == null) return false;
        return name.trim().equalsIgnoreCase(client.player.getName().getString());
    }

    private static String stripFormatting(String raw) {
        return TEXT_FORMATTING_PATTERN.matcher(raw).replaceAll("");
    }

    public static int getCurrentCash() {
        return currentCash;
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
        if (!BetterUCConfig.INSTANCE.showCashHud) return;
        if (currentCash < 0) return;

        int x = BetterUCConfig.INSTANCE.cashHudX;
        int y = BetterUCConfig.INSTANCE.cashHudY;
        String value = formatMoney(currentCash) + "$";
        String style = BetterUCConfig.INSTANCE.cashHudStyle;
        String displayText = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.cashHudPrefixEnabled,
                BetterUCConfig.INSTANCE.cashHudPrefix,
                value
        );
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.cashHudPrefixEnabled,
                BetterUCConfig.INSTANCE.cashHudPrefix
        );

        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.cashHudScale,
                BetterUCConfig.INSTANCE.cashHudGradientEnabled,
                BetterUCConfig.INSTANCE.cashHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client, style, BetterUCConfig.INSTANCE.cashHudCustomFont, displayText, 0, 0, BetterUCConfig.INSTANCE.cashHudColor);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.textRenderer, displayText, 0, 0, BetterUCConfig.INSTANCE.cashHudColor);
            } else {
                ModernHudRenderer.drawModule(
                        context,
                        client,
                        0,
                        0,
                        moduleLabel,
                        value,
                        BetterUCConfig.INSTANCE.cashHudColor
                );
            }
        });
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
        currentCash = Math.max(-1, BetterUCConfig.INSTANCE.lastKnownCash);
    }

    private static void setCashAndPersist(int newCash) {
        if (newCash < 0) return;
        boolean changed = currentCash != newCash
                || BetterUCConfig.INSTANCE.lastKnownCash != newCash;
        currentCash = newCash;
        BetterUCConfig.INSTANCE.lastKnownCash = newCash;
        if (changed) {
            BetterUCConfig.save();
        }
    }

    private static void addCashAndPersist(int amount) {
        if (amount <= 0 || currentCash < 0) return;
        setCashAndPersist(currentCash + amount);
    }

    private static void subtractCashAndPersist(int amount) {
        if (amount <= 0 || currentCash < 0) return;
        setCashAndPersist(Math.max(0, currentCash - amount));
    }

    private static DecimalFormat createMoneyFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        format.setGroupingUsed(true);
        return format;
    }
}
