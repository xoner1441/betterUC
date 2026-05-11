package com.betteruc.client;

import com.betteruc.ServerGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ServerCommandUtil {

    private static final String DOMAIN_LOCK_MESSAGE =
            "\u00A7cBetterUCMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel();

    private ServerCommandUtil() {
    }

    public static boolean ensureAllowedServerForManualCommand(MinecraftClient client) {
        if (ServerGate.isAllowedServer(client)) return true;
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(DOMAIN_LOCK_MESSAGE), false);
        }
        return false;
    }

    public static boolean send(MinecraftClient client, String command) {
        return send(client, command, false);
    }

    public static boolean send(MinecraftClient client, String command, boolean notifyIfBlocked) {
        if (client == null || client.player == null || command == null || command.isBlank()) return false;
        if (!ServerGate.isAllowedServer(client)) {
            if (notifyIfBlocked) {
                client.player.sendMessage(Text.literal(DOMAIN_LOCK_MESSAGE), false);
            }
            return false;
        }
        client.player.networkHandler.sendChatCommand(command);
        return true;
    }
}
