package com.betteruc.client;

import com.betteruc.ServerGate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ServerCommandUtil {

    private static final long AUTOMATIC_COMMAND_JOIN_GRACE_MS = 12_000L;
    private static final long AUTOMATIC_COMMAND_MIN_GAP_MS = 1_250L;
    private static final String DOMAIN_LOCK_MESSAGE =
            "\u00A7cBetterUCMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel();
    private static long joinedAllowedServerAtMs = 0L;
    private static long lastAutomaticCommandMs = 0L;

    private ServerCommandUtil() {
    }

    public static void markJoined(Minecraft client) {
        joinedAllowedServerAtMs = client == null ? 0L : System.currentTimeMillis();
        lastAutomaticCommandMs = 0L;
    }

    public static void markDisconnected() {
        joinedAllowedServerAtMs = 0L;
        lastAutomaticCommandMs = 0L;
    }

    public static boolean ensureAllowedServerForManualCommand(Minecraft client) {
        if (ServerGate.isAllowedServer(client)) return true;
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(DOMAIN_LOCK_MESSAGE));
        }
        return false;
    }

    public static boolean send(Minecraft client, String command) {
        return send(client, command, false);
    }

    public static boolean send(Minecraft client, String command, boolean notifyIfBlocked) {
        if (client == null || client.player == null || command == null || command.isBlank()) return false;
        if (!ServerGate.isAllowedServer(client)) {
            if (notifyIfBlocked) {
                client.player.sendSystemMessage(Component.literal(DOMAIN_LOCK_MESSAGE));
            }
            return false;
        }
        client.player.connection.sendCommand(command);
        return true;
    }

    public static boolean isAutomaticSendReady(Minecraft client) {
        if (client == null || client.player == null || commandJoinGraceRemainingMs(client) > 0L) return false;
        if (!ServerGate.isAllowedServer(client)) return false;
        long now = System.currentTimeMillis();
        return now - lastAutomaticCommandMs >= AUTOMATIC_COMMAND_MIN_GAP_MS;
    }

    public static boolean sendAutomatic(Minecraft client, String command) {
        if (!isAutomaticSendReady(client)) return false;
        boolean sent = send(client, command, false);
        if (sent) {
            lastAutomaticCommandMs = System.currentTimeMillis();
        }
        return sent;
    }

    private static long commandJoinGraceRemainingMs(Minecraft client) {
        if (!ServerGate.isAllowedServer(client)) return AUTOMATIC_COMMAND_JOIN_GRACE_MS;
        if (joinedAllowedServerAtMs <= 0L) {
            joinedAllowedServerAtMs = System.currentTimeMillis();
            return AUTOMATIC_COMMAND_JOIN_GRACE_MS;
        }
        long elapsed = System.currentTimeMillis() - joinedAllowedServerAtMs;
        return Math.max(0L, AUTOMATIC_COMMAND_JOIN_GRACE_MS - elapsed);
    }
}
