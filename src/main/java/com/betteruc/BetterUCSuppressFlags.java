package com.betteruc;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BetterUCSuppressFlags {
    public static boolean suppressStatsOutput = false;
    public static int pendingSilentStatsRequests = 0;
    public static boolean activeSilentStatsCapture = false;
    public static long lastSilentStatsRequestMs = 0L;
    public static long forceHideStatsOutputUntilMs = 0L;
    public static long forceHideDashStatsOutputUntilMs = 0L;
    public static final long SILENT_STATS_TIMEOUT_MS = 18000L;
    public static final long SILENT_STATS_DASH_TAIL_TIMEOUT_MS = 22000L;
    // For /modbl: suppress output and call callback once loading is complete.
    public static boolean suppressModBlOutput = false;
    public static Runnable modBlCallback = null;
    private static String pendingBlacklistInfoLookupKey = "";
    private static boolean pendingBlacklistInfoMatched = false;
    private static boolean bypassNextBlacklistInfoLocalMessage = false;
    private static final long MODBL_REMOVE_GRACE_MS = 5000L;
    private static final long RECENT_REMOVE_HOLD_MS = 15000L;
    private static final Map<String, Long> pendingModBlReaddUntil = new HashMap<>();
    private static final Map<String, Long> recentBlacklistRemovalsUntil = new HashMap<>();

    public static void markSilentStatsRequest() {
        long now = System.currentTimeMillis();
        pendingSilentStatsRequests++;
        suppressStatsOutput = true;
        lastSilentStatsRequestMs = now;
        forceHideStatsOutputUntilMs = Math.max(forceHideStatsOutputUntilMs, now + SILENT_STATS_TIMEOUT_MS);
        forceHideDashStatsOutputUntilMs = Math.max(forceHideDashStatsOutputUntilMs, now + SILENT_STATS_DASH_TAIL_TIMEOUT_MS);
    }

    public static void beginBlacklistInfoLookup(String playerName) {
        pendingBlacklistInfoLookupKey = normalizePlayerKey(playerName);
        pendingBlacklistInfoMatched = false;
    }

    public static boolean completeBlacklistInfoLookupIfMatches(String playerName) {
        if (pendingBlacklistInfoLookupKey.isEmpty() || pendingBlacklistInfoMatched) return false;
        if (!pendingBlacklistInfoLookupKey.equals(normalizePlayerKey(playerName))) return false;
        pendingBlacklistInfoMatched = true;
        pendingBlacklistInfoLookupKey = "";
        return true;
    }

    public static void clearBlacklistInfoLookup() {
        pendingBlacklistInfoLookupKey = "";
        pendingBlacklistInfoMatched = false;
    }

    public static void markNextBlacklistInfoLocalMessageBypass() {
        bypassNextBlacklistInfoLocalMessage = true;
    }

    public static boolean consumeBlacklistInfoLocalMessageBypass() {
        if (!bypassNextBlacklistInfoLocalMessage) return false;
        bypassNextBlacklistInfoLocalMessage = false;
        return true;
    }

    public static boolean beginSilentStatsCaptureIfPending() {
        cleanupStaleSilentStatsState();
        if (pendingSilentStatsRequests <= 0) {
            activeSilentStatsCapture = false;
            suppressStatsOutput = false;
            return false;
        }
        pendingSilentStatsRequests--;
        activeSilentStatsCapture = true;
        suppressStatsOutput = true;
        return true;
    }

    public static boolean isSilentStatsCaptureActive() {
        cleanupStaleSilentStatsState();
        return suppressStatsOutput && activeSilentStatsCapture;
    }

    public static void finishSilentStatsCapture() {
        activeSilentStatsCapture = false;
        suppressStatsOutput = false;
    }

    public static void clearSilentBlacklistState() {
        pendingSilentStatsRequests = 0;
        activeSilentStatsCapture = false;
        suppressStatsOutput = false;
        lastSilentStatsRequestMs = 0L;
        forceHideStatsOutputUntilMs = 0L;
        forceHideDashStatsOutputUntilMs = 0L;
        clearBlacklistInfoLookup();
        bypassNextBlacklistInfoLocalMessage = false;
        pendingModBlReaddUntil.clear();
        recentBlacklistRemovalsUntil.clear();
    }

    public static void cleanupStaleSilentStatsState() {
        if (lastSilentStatsRequestMs <= 0L) return;
        long age = System.currentTimeMillis() - lastSilentStatsRequestMs;
        if (age > SILENT_STATS_TIMEOUT_MS && !activeSilentStatsCapture) {
            pendingSilentStatsRequests = 0;
            activeSilentStatsCapture = false;
            suppressStatsOutput = false;
            lastSilentStatsRequestMs = 0L;
        }
    }

    public static void markPendingModBlReadd(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        pendingModBlReaddUntil.put(playerName.toLowerCase(Locale.ROOT),
                System.currentTimeMillis() + MODBL_REMOVE_GRACE_MS);
    }

    public static boolean shouldIgnoreRealtimeRemove(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        cleanupStalePendingModBlReadd();
        String key = playerName.toLowerCase(Locale.ROOT);
        Long until = pendingModBlReaddUntil.get(key);
        return until != null && until >= System.currentTimeMillis();
    }

    public static void clearPendingModBlReadd(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        pendingModBlReaddUntil.remove(playerName.toLowerCase(Locale.ROOT));
    }

    public static void markRecentBlacklistRemove(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        recentBlacklistRemovalsUntil.put(playerName.toLowerCase(Locale.ROOT),
                System.currentTimeMillis() + RECENT_REMOVE_HOLD_MS);
    }

    public static void clearRecentBlacklistRemove(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        recentBlacklistRemovalsUntil.remove(playerName.toLowerCase(Locale.ROOT));
    }

    public static boolean isRecentlyRemovedBlacklist(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        cleanupStaleRecentBlacklistRemovals();
        Long until = recentBlacklistRemovalsUntil.get(playerName.toLowerCase(Locale.ROOT));
        return until != null && until >= System.currentTimeMillis();
    }

    private static void cleanupStalePendingModBlReadd() {
        long now = System.currentTimeMillis();
        pendingModBlReaddUntil.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    private static void cleanupStaleRecentBlacklistRemovals() {
        long now = System.currentTimeMillis();
        recentBlacklistRemovalsUntil.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    private static String normalizePlayerKey(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }
}
