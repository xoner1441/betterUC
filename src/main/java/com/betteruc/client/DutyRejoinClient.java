package com.betteruc.client;

import net.minecraft.client.Minecraft;

import java.text.Normalizer;
import java.util.Locale;

public final class DutyRejoinClient {

    private static final long PENDING_EXPIRY_MS = 30_000L;
    private static final long DUPLICATE_WINDOW_MS = 30_000L;

    private static boolean pending;
    private static long pendingSinceMs;
    private static long lastSentAtMs;

    private DutyRejoinClient() {
    }

    public static void handleChatLine(String raw) {
        if (!matchesPrompt(raw)) return;

        long now = System.currentTimeMillis();
        if (lastSentAtMs > 0L && now - lastSentAtMs < DUPLICATE_WINDOW_MS) return;

        pending = true;
        pendingSinceMs = now;
    }

    public static void tick(Minecraft client) {
        if (!pending) return;

        long now = System.currentTimeMillis();
        if (now - pendingSinceMs > PENDING_EXPIRY_MS) {
            pending = false;
            return;
        }

        if (ServerCommandUtil.sendAutomatic(client, "rejoinduty")) {
            pending = false;
            lastSentAtMs = now;
        }
    }

    public static void reset() {
        pending = false;
        pendingSinceMs = 0L;
        lastSentAtMs = 0L;
    }

    static boolean matchesPrompt(String raw) {
        String normalized = normalize(raw);
        return normalized.contains("[dienst]")
                && normalized.contains("du warst zu lange offline")
                && normalized.contains("willst du den dienst wieder antreten");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
