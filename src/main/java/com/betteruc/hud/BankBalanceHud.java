package com.betteruc.hud;

import com.betteruc.client.ClientScheduler;
import com.betteruc.client.ServerCommandUtil;
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

public class BankBalanceHud {

    private static final long BANK_DELTA_DEDUP_WINDOW_MS = 1200L;
    private static final long AUTO_BANK_COMMAND_DEDUP_WINDOW_MS = 2000L;
    private static final long AUTO_BANK_COMMAND_DELAY_MS = 150L;
    private static final long AUTO_BANK_COMMAND_GAP_MS = 400L;
    private static final Pattern TEXT_FORMATTING_PATTERN = Pattern.compile("\\u00A7.");
    private static final Pattern CHAT_TIMESTAMP_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}:\\d{2}\\s+");
    private static final Pattern BANK_BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:ihr\\s+bankguthaben\\s+betr(?:a|ae|\\u00E4)gt\\s*:?|neuer\\s+(?:bank\\s*)?kontostand\\s*:?|neuer\\s+betrag\\s*:?)\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );
    private static final Pattern PERSONAL_BANK_BALANCE_PATTERN = Pattern.compile(
            "(?i)ihr\\s+bankguthaben\\s+betr(?:a|ae|\\u00E4)gt\\s*:?\\s*[+-]?[0-9][0-9\\.]*\\s*\\$"
    );
    private static final Pattern PREVIOUS_BALANCE_PATTERN = Pattern.compile(
            "(?i)(?:vorheriger\\s+kontostand\\s*:?|alter\\s+betrag\\s*:?)\\s*([+-]?[0-9][0-9\\.]*)\\s*\\$"
    );
    private static final Pattern BANK_TRANSFER_SENT_PATTERN = Pattern.compile(
            "(?i)\\bdu\\s+hast\\s+(.+?)\\s+([0-9][0-9\\.]*)\\s*\\$\\s+(?:\\u00FCberwiesen|ueberwiesen)\\s*!?"
    );
    private static final Pattern BANK_TRANSFER_RECEIVED_PATTERN = Pattern.compile(
            "(?i)\\b(.+?)\\s+hat\\s+dir\\s+([0-9][0-9\\.]*)\\s*\\$\\s+(?:\\u00FCberwiesen|ueberwiesen)\\s*!?"
    );
    private static final Pattern BATTLE_PASS_REWARD_PATTERN = Pattern.compile(
            "(?i)\\[battle\\s+pass]\\s*\\+\\s*([0-9][0-9\\.]*)\\s*\\$\\s+erhalten\\s*\\.?"
    );

    private static int currentBankBalance = -1;
    private static long lastBalanceUpdateMs = 0L;
    private static String lastBankDeltaKey = "";
    private static long lastBankDeltaMs = 0L;
    private static long lastAutoBankFollowupMs = 0L;
    private static final DecimalFormat MONEY_FORMAT = createMoneyFormat();

    public static void register() {
        restoreFromConfig();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "bank_balance"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    public static void updateFromChatLine(String raw) {
        if (raw == null || raw.isBlank()) return;
        String cleanedRaw = stripFormatting(raw);
        for (String line : cleanedRaw.split("\\R+")) {
            updateFromCleanLine(stripChatPrefix(line));
        }
    }

    private static void updateFromCleanLine(String raw) {
        if (raw == null || raw.isBlank()) return;

        Matcher transferSentMatcher = BANK_TRANSFER_SENT_PATTERN.matcher(raw);
        if (transferSentMatcher.find()) {
            Integer parsed = parseMoneyValue(transferSentMatcher.group(2));
            if (parsed != null) {
                subtractBalanceAndPersist(parsed, "bank-transfer-sent:" + normalizeRawKey(raw));
            }
            return;
        }

        Matcher transferReceivedMatcher = BANK_TRANSFER_RECEIVED_PATTERN.matcher(raw);
        if (transferReceivedMatcher.find()) {
            Integer parsed = parseMoneyValue(transferReceivedMatcher.group(2));
            if (parsed != null) {
                addBalanceAndPersist(parsed, "bank-transfer-received:" + normalizeRawKey(raw));
            }
            return;
        }

        Matcher battlePassRewardMatcher = BATTLE_PASS_REWARD_PATTERN.matcher(raw);
        if (battlePassRewardMatcher.find()) {
            Integer parsed = parseMoneyValue(battlePassRewardMatcher.group(1));
            if (parsed != null) {
                addBalanceAndPersist(parsed, "battle-pass-reward:" + normalizeRawKey(raw));
            }
            return;
        }

        Matcher balanceMatcher = BANK_BALANCE_PATTERN.matcher(raw);
        if (balanceMatcher.find()) {
            Integer parsed = parseMoneyValue(balanceMatcher.group(1));
            if (parsed != null) {
                setBalanceAndPersist(Math.max(0, parsed));
                requestBankFollowupsAfterPersonalBalance(raw);
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

    public static long getBalanceAgeMs() {
        if (lastBalanceUpdateMs <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, System.currentTimeMillis() - lastBalanceUpdateMs);
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
        if (!BetterUCConfig.INSTANCE.showBankHud) return;
        if (currentBankBalance < 0) return;

        int x = BetterUCConfig.INSTANCE.bankHudX;
        int y = BetterUCConfig.INSTANCE.bankHudY;
        String value = formatMoney(currentBankBalance) + "$";
        String style = BetterUCConfig.INSTANCE.bankHudStyle;
        String displayText = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.bankHudPrefixEnabled,
                BetterUCConfig.INSTANCE.bankHudPrefix,
                value
        );
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.bankHudPrefixEnabled,
                BetterUCConfig.INSTANCE.bankHudPrefix
        );
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.bankHudScale,
                BetterUCConfig.INSTANCE.bankHudGradientEnabled,
                BetterUCConfig.INSTANCE.bankHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client, style, BetterUCConfig.INSTANCE.bankHudCustomFont, displayText, 0, 0, BetterUCConfig.INSTANCE.bankHudColor);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, displayText, 0, 0, BetterUCConfig.INSTANCE.bankHudColor);
            } else {
                ModernHudRenderer.drawModule(
                        context,
                        client,
                        0,
                        0,
                        moduleLabel,
                        value,
                        BetterUCConfig.INSTANCE.bankHudColor
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
        currentBankBalance = Math.max(-1, BetterUCConfig.INSTANCE.lastKnownBankBalance);
        lastBalanceUpdateMs = 0L;
    }

    private static String stripFormatting(String raw) {
        return TEXT_FORMATTING_PATTERN.matcher(raw).replaceAll("");
    }

    private static String stripChatPrefix(String raw) {
        if (raw == null) return "";
        String cleaned = CHAT_TIMESTAMP_PATTERN.matcher(raw).replaceFirst("");
        cleaned = cleaned.replaceFirst("^\\s*[^\\p{L}\\p{N}\\[]+\\s*", "");
        return cleaned.trim();
    }

    private static String normalizeRawKey(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void setBalanceAndPersist(int newBalance) {
        if (newBalance < 0) return;
        boolean changed = currentBankBalance != newBalance
                || BetterUCConfig.INSTANCE.lastKnownBankBalance != newBalance;
        currentBankBalance = newBalance;
        lastBalanceUpdateMs = System.currentTimeMillis();
        BetterUCConfig.INSTANCE.lastKnownBankBalance = newBalance;
        if (changed) {
            BetterUCConfig.save();
        }
        RichTaxAlertHud.onBankBalanceUpdated(Minecraft.getInstance(), newBalance);
    }

    private static void subtractBalanceAndPersist(int amount, String rawKey) {
        if (amount <= 0 || currentBankBalance < 0) return;
        if (isDuplicateBankDelta(rawKey)) return;
        setBalanceAndPersist(Math.max(0, currentBankBalance - amount));
        recordBankDelta(rawKey);
    }

    private static void addBalanceAndPersist(int amount, String rawKey) {
        if (amount <= 0 || currentBankBalance < 0) return;
        if (isDuplicateBankDelta(rawKey)) return;
        setBalanceAndPersist(currentBankBalance + amount);
        recordBankDelta(rawKey);
    }

    private static boolean isDuplicateBankDelta(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return false;
        long age = System.currentTimeMillis() - lastBankDeltaMs;
        return age >= 0L
                && age <= BANK_DELTA_DEDUP_WINDOW_MS
                && rawKey.equals(lastBankDeltaKey);
    }

    private static void recordBankDelta(String rawKey) {
        lastBankDeltaKey = rawKey == null ? "" : rawKey;
        lastBankDeltaMs = System.currentTimeMillis();
    }

    private static void requestBankFollowupsAfterPersonalBalance(String raw) {
        boolean requestFactionBank = BetterUCConfig.INSTANCE.autoFactionBankOnBalanceEnabled;
        boolean requestAtmInfo = BetterUCConfig.INSTANCE.autoAtmInfoOnBalanceEnabled;
        if (!requestFactionBank && !requestAtmInfo) return;
        if (!PERSONAL_BANK_BALANCE_PATTERN.matcher(raw).find()) return;

        long now = System.currentTimeMillis();
        if (now - lastAutoBankFollowupMs < AUTO_BANK_COMMAND_DEDUP_WINDOW_MS) return;
        lastAutoBankFollowupMs = now;

        Minecraft client = Minecraft.getInstance();
        long nextDelayMs = AUTO_BANK_COMMAND_DELAY_MS;
        if (requestFactionBank) {
            ClientScheduler.runDelayedOnClient(client, nextDelayMs,
                    () -> ServerCommandUtil.send(client, "fbank"));
            nextDelayMs += AUTO_BANK_COMMAND_GAP_MS;
        }
        if (requestAtmInfo) {
            ClientScheduler.runDelayedOnClient(client, nextDelayMs,
                    () -> ServerCommandUtil.send(client, "atminfo"));
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
