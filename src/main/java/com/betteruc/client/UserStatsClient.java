package com.betteruc.client;

import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.CashHud;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserStatsClient {
    private static final String STAT_PREFIX = "^\\s*[-\\u2010-\\u2015\\u2212]?\\s*";
    private static final Pattern HOUSE_PATTERN = Pattern.compile(STAT_PREFIX + "Haus\\s*:?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACTION_PATTERN = Pattern.compile(STAT_PREFIX + "Fraktion\\s*:?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WARNS_PATTERN = Pattern.compile(STAT_PREFIX + "Verwarnungen\\s*:?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOYALTY_PATTERN = Pattern.compile(STAT_PREFIX + "Treuebonus\\s*:?\\s*([0-9][0-9.]*)\\s*(?:Punkte?)?.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYTIME_PATTERN = Pattern.compile(STAT_PREFIX + "Spielzeit\\s*:?\\s*([0-9][0-9.]*)\\s*(?:Stunden?)?.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOTEPOINTS_PATTERN = Pattern.compile(STAT_PREFIX + "Votepoints\\s*:?\\s*([0-9][0-9.]*).*$", Pattern.CASE_INSENSITIVE);

    private static final long MIN_UPLOAD_INTERVAL_MS = 2500L;
    private static Integer lastBankMoney = null;
    private static Integer lastCashMoney = null;
    private static String lastFactionDisplay = "";
    private static String lastHouses = "";
    private static String lastWarns = "";
    private static Integer lastLoyaltyBonus = null;
    private static Integer lastPlayTimeHours = null;
    private static Integer lastVotepoints = null;
    private static long lastUploadMs = 0L;
    private static boolean uploadQueued = false;

    private UserStatsClient() {
    }

    public static void clear() {
        lastBankMoney = null;
        lastCashMoney = null;
        lastFactionDisplay = "";
        lastHouses = "";
        lastWarns = "";
        lastLoyaltyBonus = null;
        lastPlayTimeHours = null;
        lastVotepoints = null;
        uploadQueued = false;
    }

    public static void handleChatLine(MinecraftClient client, String raw) {
        if (client == null || client.player == null || raw == null || raw.isBlank()) return;

        boolean changed = false;
        int bankMoney = BankBalanceHud.getCurrentBankBalance();
        if (bankMoney >= 0 && !Integer.valueOf(bankMoney).equals(lastBankMoney)) {
            lastBankMoney = bankMoney;
            changed = true;
        }

        int cashMoney = CashHud.getCurrentCash();
        if (cashMoney >= 0 && !Integer.valueOf(cashMoney).equals(lastCashMoney)) {
            lastCashMoney = cashMoney;
            changed = true;
        }

        String trimmed = raw.trim();
        Matcher matcher = HOUSE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String value = cleanText(matcher.group(1));
            if (!value.equals(lastHouses)) {
                lastHouses = value;
                changed = true;
            }
        }

        matcher = FACTION_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String value = cleanText(matcher.group(1));
            if (!value.equals(lastFactionDisplay)) {
                lastFactionDisplay = value;
                changed = true;
            }
        }

        matcher = WARNS_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String value = cleanText(matcher.group(1));
            if (!value.equals(lastWarns)) {
                lastWarns = value;
                changed = true;
            }
        }

        changed |= updateNumber(LOYALTY_PATTERN.matcher(trimmed), StatNumber.LOYALTY);
        changed |= updateNumber(PLAYTIME_PATTERN.matcher(trimmed), StatNumber.PLAYTIME);
        changed |= updateNumber(VOTEPOINTS_PATTERN.matcher(trimmed), StatNumber.VOTEPOINTS);

        if (changed) {
            requestUpload(client);
        }
    }

    private static boolean updateNumber(Matcher matcher, StatNumber statNumber) {
        if (!matcher.find()) return false;
        Integer parsed = parseNumber(matcher.group(1));
        if (parsed == null) return false;

        switch (statNumber) {
            case LOYALTY -> {
                if (parsed.equals(lastLoyaltyBonus)) return false;
                lastLoyaltyBonus = parsed;
                return true;
            }
            case PLAYTIME -> {
                if (parsed.equals(lastPlayTimeHours)) return false;
                lastPlayTimeHours = parsed;
                return true;
            }
            case VOTEPOINTS -> {
                if (parsed.equals(lastVotepoints)) return false;
                lastVotepoints = parsed;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void requestUpload(MinecraftClient client) {
        long now = System.currentTimeMillis();
        long waitMs = MIN_UPLOAD_INTERVAL_MS - (now - lastUploadMs);
        if (waitMs <= 0L) {
            uploadNow(client);
            return;
        }

        if (uploadQueued) return;
        uploadQueued = true;
        ClientScheduler.runDelayedOnClient(client, waitMs, () -> {
            uploadQueued = false;
            uploadNow(client);
        });
    }

    private static void uploadNow(MinecraftClient client) {
        if (client == null || client.player == null) return;
        lastUploadMs = System.currentTimeMillis();
        UserPanelClient.uploadStats(client, currentStatsJson());
    }

    public static void uploadCurrentStats(MinecraftClient client) {
        uploadNow(client);
    }

    private static JsonObject currentStatsJson() {
        JsonObject stats = new JsonObject();
        int bankMoney = BankBalanceHud.getCurrentBankBalance();
        if (bankMoney >= 0) lastBankMoney = bankMoney;
        int cashMoney = CashHud.getCurrentCash();
        if (cashMoney >= 0) lastCashMoney = cashMoney;
        if (lastBankMoney != null) stats.addProperty("bankMoney", lastBankMoney);
        if (lastCashMoney != null) stats.addProperty("cashMoney", lastCashMoney);
        if (!lastFactionDisplay.isBlank()) stats.addProperty("factionDisplay", lastFactionDisplay);
        if (!lastHouses.isBlank()) stats.addProperty("houses", lastHouses);
        if (!lastWarns.isBlank()) stats.addProperty("warns", lastWarns);
        if (lastLoyaltyBonus != null) stats.addProperty("loyaltyBonus", lastLoyaltyBonus);
        if (lastPlayTimeHours != null) stats.addProperty("playTimeHours", lastPlayTimeHours);
        if (lastVotepoints != null) stats.addProperty("votepoints", lastVotepoints);
        return stats;
    }

    private static String cleanText(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^\\p{L}\\p{N}_ .,:/+()\\-]", "").trim();
    }

    private static Integer parseNumber(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Math.max(0, Integer.parseInt(digits));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum StatNumber {
        LOYALTY,
        PLAYTIME,
        VOTEPOINTS
    }
}
