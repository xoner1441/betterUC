package com.betteruc.client;

import com.betteruc.ServerGate;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class AutoMoneyTransportClient {
    private static final String START_MESSAGE =
            "geldtransport bringe das geld zum automaten und benutze dropmoney";
    private static final String ARRIVAL_MESSAGE =
            "navi hier kannst du einzahlen dropmoney";
    private static final long JOB_TIMEOUT_MS = 30L * 60L * 1_000L;
    private static final long COMMAND_RETRY_WINDOW_MS = 10_000L;

    private static long armedUntilMs;
    private static long pendingDropUntilMs;
    private static boolean pendingDrop;

    private AutoMoneyTransportClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (!AutomationController.isMoneyTransportEnabled()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (updateJobState(raw, now)) {
            pendingDrop = true;
            pendingDropUntilMs = now + COMMAND_RETRY_WINDOW_MS;
            tick(client);
        }
    }

    public static void tick(Minecraft client) {
        if (!AutomationController.isMoneyTransportEnabled()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        expire(now);
        if (!pendingDrop) return;
        if (client == null || client.player == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        if (ServerCommandUtil.sendAutomatic(client, "dropmoney")) {
            reset();
        }
    }

    public static void reset() {
        armedUntilMs = 0L;
        pendingDropUntilMs = 0L;
        pendingDrop = false;
    }

    static boolean isStartMessage(String raw) {
        return START_MESSAGE.equals(key(raw));
    }

    static boolean isArrivalMessage(String raw) {
        return ARRIVAL_MESSAGE.equals(key(raw));
    }

    static boolean updateJobState(String raw, long now) {
        expire(now);
        String clean = key(raw);
        if (START_MESSAGE.equals(clean)) {
            armedUntilMs = now + JOB_TIMEOUT_MS;
            pendingDrop = false;
            pendingDropUntilMs = 0L;
            return false;
        }
        if (armedUntilMs > now && ARRIVAL_MESSAGE.equals(clean)) {
            armedUntilMs = 0L;
            return true;
        }
        return false;
    }

    private static void expire(long now) {
        if (armedUntilMs > 0L && now > armedUntilMs) {
            armedUntilMs = 0L;
        }
        if (pendingDrop && now > pendingDropUntilMs) {
            pendingDrop = false;
            pendingDropUntilMs = 0L;
        }
    }

    private static String key(String value) {
        return value == null ? "" : value
                .replaceAll("\u00A7.", "")
                .toLowerCase(Locale.ROOT)
                .replace("\u00E4", "ae")
                .replace("\u00F6", "oe")
                .replace("\u00FC", "ue")
                .replace("\u00DF", "ss")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
