package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CashHud {
    private static final long SIGNED_DELTA_DEDUP_WINDOW_MS = 3500L;
    private static final long RAW_DELTA_DEDUP_WINDOW_MS = 150L;
    private static final Pattern TEXT_FORMATTING_PATTERN = Pattern.compile("\\u00A7.");
    private static final Pattern CHAT_TIMESTAMP_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+");
    private static final String PLAYER_TOKEN = "(?:\\[[^\\]]+\\]\\s*)?[A-Za-z0-9_]{2,16}";
    private static final String OPTIONAL_SERVER_TAG = "(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)?";
    private static final Pattern CASH_STATS_PATTERN = Pattern.compile(
            "^\\s*[-\\u2010-\\u2015\\u2212]?\\s*Geld\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$\\s*[.!]?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CASH_BALANCE_PATTERN = Pattern.compile(
            "(?i)^" + OPTIONAL_SERVER_TAG + "(?:neuer\\s+bargeldbestand|bargeldbestand)\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$\\s*[.!]?\\s*$"
    );
    private static final Pattern CASH_PAYOUT_PATTERN = Pattern.compile(
            "(?i)^" + OPTIONAL_SERVER_TAG + "auszahlung\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$\\s*[.!]?\\s*$"
    );
    private static final Pattern CASH_DEPOSIT_PATTERN = Pattern.compile(
            "(?i)^" + OPTIONAL_SERVER_TAG + "eingezahlt\\s*:?\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$\\s*[.!]?\\s*$"
    );
    private static final Pattern FACTION_BANK_DEPOSIT_PATTERN = Pattern.compile(
            "(?i)^\\[\\s*F-?Bank\\s*]\\s+(.+?)\\s+hat\\s+([0-9][0-9\\.]*)\\s*\\$\\s+(?:auf|in)\\s+die\\s+Fraktionsbank\\s+eingezahlt\\s*[.!]?\\s*$"
    );
    private static final Pattern FACTION_BANK_WITHDRAW_PATTERN = Pattern.compile(
            "(?i)^\\[\\s*F-?Bank\\s*]\\s+(.+?)\\s+hat\\s+([0-9][0-9\\.]*)\\s*\\$\\s+aus\\s+der\\s+Fraktionsbank\\s+genommen\\s*[.!]?\\s*$"
    );
    private static final Pattern PLAYER_MONEY_SENT_PATTERN = Pattern.compile(
            "(?i)^du\\s+hast\\s+(" + PLAYER_TOKEN + ")\\s+([0-9][0-9\\.]*)\\s*\\$\\s+gegeben\\s*[.!]?\\s*$"
    );
    private static final Pattern PLAYER_MONEY_RECEIVED_PATTERN = Pattern.compile(
            "(?i)^(" + PLAYER_TOKEN + ")\\s+hat\\s+dir\\s+([0-9][0-9\\.]*)\\s*\\$\\s+gegeben\\s*[.!]?\\s*$"
    );
    private static final Pattern PLAYER_ITEM_SALE_PATTERN = Pattern.compile(
            "(?iu)^" + OPTIONAL_SERVER_TAG + "du\\s+hast\\s+.+?\\s+f(?:ü|ue)r\\s+([0-9][0-9\\.]*)\\s*\\$\\s+verkauft\\s*[.!]?\\s*$"
    );
    private static final Pattern FANG_COMBO_PATTERN = Pattern.compile(
            "(?iu)^(?:combo!\\s*)?x\\s*[0-9]+\\s+fang-?combo!?\\s*\\+\\s*([0-9][0-9\\.]*)\\s*\\$\\s*[.!]?\\s*$"
    );
    private static final Pattern CASINO_PURCHASE_PATTERN = Pattern.compile(
            "(?iu)^(?:casino|ᴄᴀsɪɴᴏ)\\s*[•·|:>\\-]*\\s*gekauft\\s*:\\s*[0-9][0-9\\.]*\\s*jetons\\s*"
                    + "\\(\\s*-\\s*([0-9][0-9\\.]*)\\s*\\$(?:\\s*,[^)]*)?\\)\\s*[.!]?\\s*$"
    );
    private static final Pattern CASINO_SALE_PATTERN = Pattern.compile(
            "(?iu)^(?:casino|ᴄᴀsɪɴᴏ)\\s*[•·|:>\\-]*\\s*verkauft\\s*:\\s*[0-9][0-9\\.]*\\s*jetons\\s*"
                    + "\\(\\s*\\+\\s*([0-9][0-9\\.]*)\\s*\\$(?:\\s*,[^)]*)?\\)\\s*[.!]?\\s*$"
    );
    private static final Pattern CASH_SIGNED_DELTA_PATTERN = Pattern.compile(
            "^\\s*([+-])\\s*([0-9][0-9\\.]*)\\s*\\$\\s*$"
    );
    private static final Pattern CEMETERY_ENTRY_PATTERN = Pattern.compile(
            "(?iu)^du\\s+bist\\s+nun\\s+f(?:ü|ue)r\\s+\\d+\\s+minute(?:n)?\\s+auf\\s+dem\\s+friedhof[.!]?\\s*$"
    );
    private static final DecimalFormat MONEY_FORMAT = createMoneyFormat();

    private static int currentCash = -1;
    private static int lastSemanticDeltaAmount = -1;
    private static char lastSemanticDeltaSign = '\0';
    private static DeltaSource lastSemanticDeltaSource = DeltaSource.CONTEXT;
    private static long lastSemanticDeltaMs = 0L;
    private static String lastRawDeltaKey = "";
    private static long lastRawDeltaMs = 0L;

    public static void register() {
        restoreFromConfig();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "cash"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    public static void updateFromStatsLine(String raw) {
        if (raw == null || raw.isBlank()) return;
        String cleanedRaw = stripFormatting(raw);
        for (String line : cleanedRaw.split("\\R+")) {
            updateFromCleanLine(stripChatPrefix(line));
        }
    }

    private static void updateFromCleanLine(String raw) {
        if (raw == null || raw.isBlank()) return;

        if (isCemeteryEntryMessage(raw)) {
            resetDeltaDeduplication();
            setCashAndPersist(0);
            return;
        }

        Matcher factionDepositMatcher = FACTION_BANK_DEPOSIT_PATTERN.matcher(raw);
        if (factionDepositMatcher.find() && isCurrentPlayer(factionDepositMatcher.group(1))) {
            Integer parsed = parseMoneyValue(factionDepositMatcher.group(2));
            if (parsed != null) {
                applyDeltaAndPersist('-', parsed, "fbank-deposit:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            }
            return;
        }

        Matcher factionWithdrawMatcher = FACTION_BANK_WITHDRAW_PATTERN.matcher(raw);
        if (factionWithdrawMatcher.find() && isCurrentPlayer(factionWithdrawMatcher.group(1))) {
            Integer parsed = parseMoneyValue(factionWithdrawMatcher.group(2));
            if (parsed != null) {
                applyDeltaAndPersist('+', parsed, "fbank-withdraw:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            }
            return;
        }

        Matcher moneySentMatcher = PLAYER_MONEY_SENT_PATTERN.matcher(raw);
        if (moneySentMatcher.find()) {
            Integer parsed = parseMoneyValue(moneySentMatcher.group(2));
            if (parsed != null) {
                applyDeltaAndPersist('-', parsed, "pay-sent:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            }
            return;
        }

        Matcher moneyReceivedMatcher = PLAYER_MONEY_RECEIVED_PATTERN.matcher(raw);
        if (moneyReceivedMatcher.find()) {
            Integer parsed = parseMoneyValue(moneyReceivedMatcher.group(2));
            if (parsed != null) {
                applyDeltaAndPersist('+', parsed, "pay-received:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            }
            return;
        }

        Integer itemSaleAmount = parsePlayerItemSaleAmount(raw);
        if (itemSaleAmount != null) {
            applyDeltaAndPersist('+', itemSaleAmount, "item-sale:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            return;
        }

        Integer fangComboAmount = parseFangComboCashAmount(raw);
        if (fangComboAmount != null) {
            applyDeltaAndPersist('+', fangComboAmount, "fang-combo:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            return;
        }

        CasinoCashDelta casinoDelta = parseCasinoCashDelta(raw);
        if (casinoDelta != null) {
            applyDeltaAndPersist(
                    casinoDelta.sign(),
                    casinoDelta.amount(),
                    "casino:" + normalizeRawKey(raw),
                    DeltaSource.CONTEXT
            );
            return;
        }

        Integer absoluteCash = parseCashBalanceMessage(raw);
        if (absoluteCash != null) {
            if (currentCash >= 0 && currentCash != absoluteCash) {
                int amount = Math.abs(absoluteCash - currentCash);
                recordSemanticDelta(absoluteCash > currentCash ? '+' : '-', amount, DeltaSource.ABSOLUTE_BALANCE);
            }
            setCashAndPersist(Math.max(0, absoluteCash));
            return;
        }

        Matcher payoutMatcher = CASH_PAYOUT_PATTERN.matcher(raw);
        if (payoutMatcher.find()) {
            Integer parsed = parseMoneyValue(payoutMatcher.group(1));
            if (parsed != null) {
                int amount = Math.abs(parsed);
                applyDeltaAndPersist('+', amount, "payout:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            }
            return;
        }

        Matcher depositMatcher = CASH_DEPOSIT_PATTERN.matcher(raw);
        if (depositMatcher.find()) {
            Integer parsed = parseMoneyValue(depositMatcher.group(1));
            if (parsed != null) {
                int amount = Math.abs(parsed);
                applyDeltaAndPersist('-', amount, "deposit:" + normalizeRawKey(raw), DeltaSource.CONTEXT);
            }
            return;
        }

        Matcher signedDeltaMatcher = CASH_SIGNED_DELTA_PATTERN.matcher(raw);
        if (signedDeltaMatcher.find()) {
            Integer parsed = parseMoneyValue(signedDeltaMatcher.group(2));
            if (parsed != null) {
                char sign = signedDeltaMatcher.group(1).charAt(0);
                applyDeltaAndPersist(
                        sign,
                        parsed,
                        "signed:" + normalizeRawKey(raw),
                        DeltaSource.SIGNED_LINE
                );
            }
            return;
        }

    }

    private static boolean isCurrentPlayer(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || name == null) return false;
        return name.trim().equalsIgnoreCase(client.player.getName().getString());
    }

    private static String stripFormatting(String raw) {
        return TEXT_FORMATTING_PATTERN.matcher(raw).replaceAll("");
    }

    private static String stripChatPrefix(String raw) {
        if (raw == null) return "";
        String cleaned = CHAT_TIMESTAMP_PATTERN.matcher(raw).replaceFirst("");
        cleaned = cleaned.replaceFirst("^\\s*[»>]+\\s*", "");
        return cleaned.trim();
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

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
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
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, displayText, 0, 0, BetterUCConfig.INSTANCE.cashHudColor);
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

    static CasinoCashDelta parseCasinoCashDelta(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = stripChatPrefix(stripFormatting(raw));

        Matcher purchaseMatcher = CASINO_PURCHASE_PATTERN.matcher(cleaned);
        if (purchaseMatcher.matches()) {
            Integer amount = parseMoneyValue(purchaseMatcher.group(1));
            return amount == null || amount <= 0 ? null : new CasinoCashDelta('-', amount);
        }

        Matcher saleMatcher = CASINO_SALE_PATTERN.matcher(cleaned);
        if (saleMatcher.matches()) {
            Integer amount = parseMoneyValue(saleMatcher.group(1));
            return amount == null || amount <= 0 ? null : new CasinoCashDelta('+', amount);
        }
        return null;
    }

    static Integer parsePlayerItemSaleAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = stripChatPrefix(stripFormatting(raw));
        Matcher matcher = PLAYER_ITEM_SALE_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return null;

        Integer amount = parseMoneyValue(matcher.group(1));
        return amount == null || amount <= 0 ? null : amount;
    }

    static Integer parseFangComboCashAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = stripChatPrefix(stripFormatting(raw));
        Matcher matcher = FANG_COMBO_PATTERN.matcher(cleaned);
        if (!matcher.matches()) return null;

        Integer amount = parseMoneyValue(matcher.group(1));
        return amount == null || amount <= 0 ? null : amount;
    }

    static boolean isCemeteryEntryMessage(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String cleaned = stripChatPrefix(stripFormatting(raw));
        return CEMETERY_ENTRY_PATTERN.matcher(cleaned).matches();
    }

    static Integer parseCashBalanceMessage(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = stripChatPrefix(stripFormatting(raw));
        Matcher balanceMatcher = CASH_BALANCE_PATTERN.matcher(cleaned);
        if (balanceMatcher.matches()) {
            return parseMoneyValue(balanceMatcher.group(1));
        }
        Matcher statsMatcher = CASH_STATS_PATTERN.matcher(cleaned);
        if (!statsMatcher.matches()) return null;
        return parseMoneyValue(statsMatcher.group(1));
    }

    record CasinoCashDelta(char sign, int amount) {
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

    private static void applyDeltaAndPersist(char sign, int amount, String rawKey, DeltaSource source) {
        if (amount <= 0 || currentCash < 0) return;
        if (isDuplicateRawDelta(rawKey)) return;
        if (isDuplicateSemanticDelta(sign, amount, source)) return;

        if (sign == '+') {
            addCashAndPersist(amount);
        } else {
            subtractCashAndPersist(amount);
        }
        recordRawDelta(rawKey);
        recordSemanticDelta(sign, amount, source);
    }

    private static void recordSemanticDelta(char sign, int amount, DeltaSource source) {
        if (amount <= 0) return;
        lastSemanticDeltaSign = sign;
        lastSemanticDeltaAmount = amount;
        lastSemanticDeltaSource = source;
        lastSemanticDeltaMs = System.currentTimeMillis();
    }

    private static boolean isDuplicateSemanticDelta(char sign, int amount, DeltaSource source) {
        if (amount <= 0) return false;
        long age = System.currentTimeMillis() - lastSemanticDeltaMs;
        return age >= 0L
                && age <= SIGNED_DELTA_DEDUP_WINDOW_MS
                && lastSemanticDeltaSign == sign
                && lastSemanticDeltaAmount == amount
                && isComplementarySourcePair(lastSemanticDeltaSource, source);
    }

    static boolean isComplementarySourcePair(DeltaSource previous, DeltaSource current) {
        if (previous == null || current == null || previous == current) return false;
        return previous == DeltaSource.SIGNED_LINE || current == DeltaSource.SIGNED_LINE;
    }

    private static boolean isDuplicateRawDelta(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;
        long age = System.currentTimeMillis() - lastRawDeltaMs;
        return age >= 0L
                && age <= RAW_DELTA_DEDUP_WINDOW_MS
                && rawKey.equals(lastRawDeltaKey);
    }

    private static void recordRawDelta(String rawKey) {
        lastRawDeltaKey = rawKey == null ? "" : rawKey;
        lastRawDeltaMs = System.currentTimeMillis();
    }

    private static void resetDeltaDeduplication() {
        lastSemanticDeltaAmount = -1;
        lastSemanticDeltaSign = '\0';
        lastSemanticDeltaSource = DeltaSource.CONTEXT;
        lastSemanticDeltaMs = 0L;
        lastRawDeltaKey = "";
        lastRawDeltaMs = 0L;
    }

    private static String normalizeRawKey(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static DecimalFormat createMoneyFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        format.setGroupingUsed(true);
        return format;
    }

    enum DeltaSource {
        SIGNED_LINE,
        CONTEXT,
        ABSOLUTE_BALANCE
    }
}
