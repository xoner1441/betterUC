package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.parser.KraeuterLicenseParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Optional, personal reminder based solely on the server's own /licenses response. */
public final class KraeuterLicenseWarningClient {
    private static final long JOIN_DELAY_MS = 20_000L;
    private static final long REFRESH_INTERVAL_MS = 12L * 60L * 60L * 1_000L;
    private static final ZoneId SERVER_ZONE = ZoneId.of("Europe/Berlin");
    private static long nextRefreshAtMs;
    private static LocalDateTime knownExpiry;
    private static boolean knownMissing;
    private static String pendingWarning;
    private static String pendingKey;
    private static String lastWarnedKey;

    private KraeuterLicenseWarningClient() { }

    public static void onJoin() {
        knownExpiry = null;
        knownMissing = false;
        pendingWarning = null;
        pendingKey = null;
        nextRefreshAtMs = BetterUCConfig.INSTANCE.kraeuterLicenseWarningEnabled
                ? System.currentTimeMillis() + JOIN_DELAY_MS : 0L;
    }

    public static void onDisconnect() {
        nextRefreshAtMs = 0L;
        knownExpiry = null;
        knownMissing = false;
        pendingWarning = null;
        pendingKey = null;
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || !ServerGate.isAllowedServer(client)) return;
        if (!BetterUCConfig.INSTANCE.kraeuterLicenseWarningEnabled) {
            nextRefreshAtMs = 0L;
            pendingWarning = null;
            pendingKey = null;
            return;
        }
        if (pendingWarning != null) {
            client.player.sendSystemMessage(Component.literal(pendingWarning));
            lastWarnedKey = pendingKey;
            pendingWarning = null;
            pendingKey = null;
        }
        long now = System.currentTimeMillis();
        if (nextRefreshAtMs == 0L) nextRefreshAtMs = now + 1_000L;
        if (now >= nextRefreshAtMs && ServerCommandUtil.sendAutomatic(client, "licenses")) {
            nextRefreshAtMs = now + REFRESH_INTERVAL_MS;
        }
    }

    public static void requestNow(Minecraft client) {
        if (ServerCommandUtil.send(client, "licenses", true)) {
            nextRefreshAtMs = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
        }
    }

    public static String statusLabel() {
        if (knownMissing) return "Nicht vorhanden";
        return knownExpiry == null ? "Noch nicht geprüft" : "Bis " + KraeuterLicenseParser.format(knownExpiry);
    }

    public static void handleServerChatLine(Minecraft client, String line) {
        if (client == null || client.player == null || !BetterUCConfig.INSTANCE.kraeuterLicenseWarningEnabled) return;
        KraeuterLicenseParser.Status status = KraeuterLicenseParser.parse(line);
        if (status == null) return;
        knownExpiry = status.expiresAt();
        knownMissing = !status.present();
        pendingWarning = null;
        pendingKey = null;
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        String warning = warningText(status, now,
                BetterUCConfig.INSTANCE.kraeuterLicenseWarningDays);
        if (warning == null) return;
        String key = client.player.getUUID() + "|" + status.expiresAt() + "|" + now.toLocalDate();
        if (!key.equals(lastWarnedKey)) {
            pendingWarning = warning;
            pendingKey = key;
        }
    }

    static String warningText(KraeuterLicenseParser.Status status, LocalDateTime now, int warningDays) {
        if (status == null || !status.present() || status.expiresAt() == null) return null;
        LocalDateTime expiry = status.expiresAt();
        String date = KraeuterLicenseParser.format(expiry);
        if (!expiry.isAfter(now)) {
            return "§c[betterUC Lizenz] §fDeine Kräuter-Lizenz ist seit " + date
                    + " abgelaufen. Für die Verlängerung ist das RP erforderlich.";
        }
        int days = Math.clamp(warningDays, 1, 14);
        if (expiry.isAfter(now.plusDays(days))) return null;
        return "§6[betterUC Lizenz] §eDeine Kräuter-Lizenz läuft am " + date
                + " ab. Plane das RP zur Verlängerung rechtzeitig ein.";
    }
}
