package com.kartellmod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class KartellSuppressFlags {
    public static boolean suppressMemberOutput = false;
    public static int pendingSilentMemberRequests = 0;
    public static boolean activeSilentMemberCapture = false;
    public static long lastSilentMemberRequestMs = 0L;
    public static final long SILENT_MEMBER_TIMEOUT_MS = 5000L;
    public static boolean suppressBlacklistOutput = false;
    public static int pendingSilentBlacklistRequests = 0;
    public static boolean activeSilentBlacklistCapture = false;
    public static long lastSilentBlacklistRequestMs = 0L;
    public static final long SILENT_BLACKLIST_TIMEOUT_MS = 5000L;
    public static boolean suppressStatsOutput = false;
    public static int pendingSilentStatsRequests = 0;
    public static boolean activeSilentStatsCapture = false;
    public static long lastSilentStatsRequestMs = 0L;
    public static final long SILENT_STATS_TIMEOUT_MS = 5000L;
    public static long lastManualBlacklistCommandMs = 0L;
    public static final long MANUAL_BLACKLIST_COMMAND_WINDOW_MS = 15000L;

    // For /modbl: suppress output and call callback once loading is complete.
    public static boolean suppressModBlOutput = false;
    public static Runnable modBlCallback = null;
    private static final long MODBL_REMOVE_GRACE_MS = 5000L;
    private static final long RECENT_REMOVE_HOLD_MS = 15000L;
    private static final Map<String, Long> pendingModBlReaddUntil = new HashMap<>();
    private static final Map<String, Long> recentBlacklistRemovalsUntil = new HashMap<>();

    public static void markSilentBlacklistRequest() {
        pendingSilentBlacklistRequests++;
        suppressBlacklistOutput = true;
        lastSilentBlacklistRequestMs = System.currentTimeMillis();
    }

    public static void markSilentMemberRequest() {
        pendingSilentMemberRequests++;
        suppressMemberOutput = true;
        lastSilentMemberRequestMs = System.currentTimeMillis();
    }

    public static void markSilentStatsRequest() {
        pendingSilentStatsRequests++;
        suppressStatsOutput = true;
        lastSilentStatsRequestMs = System.currentTimeMillis();
    }

    public static void markManualBlacklistCommand() {
        lastManualBlacklistCommandMs = System.currentTimeMillis();
    }

    public static boolean isManualBlacklistCommandRecent() {
        if (lastManualBlacklistCommandMs <= 0L) return false;
        long age = System.currentTimeMillis() - lastManualBlacklistCommandMs;
        return age <= MANUAL_BLACKLIST_COMMAND_WINDOW_MS;
    }

    public static void clearManualBlacklistCommandMarker() {
        lastManualBlacklistCommandMs = 0L;
    }

    public static boolean beginSilentBlacklistCaptureIfPending() {
        cleanupStaleSilentBlacklistState();
        if (pendingSilentBlacklistRequests <= 0) {
            activeSilentBlacklistCapture = false;
            suppressBlacklistOutput = false;
            return false;
        }
        pendingSilentBlacklistRequests--;
        activeSilentBlacklistCapture = true;
        suppressBlacklistOutput = true;
        return true;
    }

    public static boolean isSilentBlacklistCaptureActive() {
        cleanupStaleSilentBlacklistState();
        return suppressBlacklistOutput && activeSilentBlacklistCapture;
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

    public static boolean beginSilentMemberCaptureIfPending() {
        cleanupStaleSilentMemberState();
        if (pendingSilentMemberRequests <= 0) {
            activeSilentMemberCapture = false;
            suppressMemberOutput = false;
            return false;
        }
        pendingSilentMemberRequests--;
        activeSilentMemberCapture = true;
        suppressMemberOutput = true;
        return true;
    }

    public static boolean isSilentMemberCaptureActive() {
        cleanupStaleSilentMemberState();
        return suppressMemberOutput && activeSilentMemberCapture;
    }

    public static boolean isSilentStatsCaptureActive() {
        cleanupStaleSilentStatsState();
        return suppressStatsOutput && activeSilentStatsCapture;
    }

    public static void finishSilentBlacklistCapture() {
        activeSilentBlacklistCapture = false;
        suppressBlacklistOutput = false;
    }

    public static void finishSilentStatsCapture() {
        activeSilentStatsCapture = false;
        suppressStatsOutput = false;
    }

    public static void finishSilentMemberCapture() {
        activeSilentMemberCapture = false;
        suppressMemberOutput = false;
    }

    public static void clearSilentBlacklistState() {
        pendingSilentMemberRequests = 0;
        activeSilentMemberCapture = false;
        suppressMemberOutput = false;
        lastSilentMemberRequestMs = 0L;
        pendingSilentBlacklistRequests = 0;
        activeSilentBlacklistCapture = false;
        suppressBlacklistOutput = false;
        lastSilentBlacklistRequestMs = 0L;
        pendingSilentStatsRequests = 0;
        activeSilentStatsCapture = false;
        suppressStatsOutput = false;
        lastSilentStatsRequestMs = 0L;
        lastManualBlacklistCommandMs = 0L;
        pendingModBlReaddUntil.clear();
        recentBlacklistRemovalsUntil.clear();
    }

    public static void cleanupStaleSilentBlacklistState() {
        if (lastSilentBlacklistRequestMs <= 0L) return;
        long age = System.currentTimeMillis() - lastSilentBlacklistRequestMs;
        if (age > SILENT_BLACKLIST_TIMEOUT_MS && !activeSilentBlacklistCapture) {
            pendingSilentBlacklistRequests = 0;
            activeSilentBlacklistCapture = false;
            suppressBlacklistOutput = false;
            lastSilentBlacklistRequestMs = 0L;
        }
    }

    public static void cleanupStaleSilentMemberState() {
        if (lastSilentMemberRequestMs <= 0L) return;
        long age = System.currentTimeMillis() - lastSilentMemberRequestMs;
        if (age > SILENT_MEMBER_TIMEOUT_MS && !activeSilentMemberCapture) {
            pendingSilentMemberRequests = 0;
            activeSilentMemberCapture = false;
            suppressMemberOutput = false;
            lastSilentMemberRequestMs = 0L;
        }
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
}
