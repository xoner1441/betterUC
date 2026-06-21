package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.BetterUCSuppressFlags;
import com.betteruc.ServerGate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SyncRefreshActions {

    private SyncRefreshActions() {
    }

    public static void requestStatsRefresh(Minecraft client, boolean notify) {
        if (!canRefresh(client, notify)) return;
        BetterUCSuppressFlags.markSilentStatsRequest();
        ServerCommandUtil.send(client, "stats");
        ClientScheduler.runDelayedOnClient(client, BetterUCSuppressFlags.SILENT_STATS_TIMEOUT_MS,
                BetterUCSuppressFlags::cleanupStaleSilentStatsState);
        BetterUCMod.LOGGER.info("Manual silent /stats requested");
        if (notify) {
            sendClientMessage(client, "\u00A7aStats werden neu geladen.");
        }
    }

    private static boolean canRefresh(Minecraft client, boolean notify) {
        if (client == null || client.player == null) return false;
        if (ServerGate.isAllowedServer(client)) return true;
        if (notify) {
            sendClientMessage(client, "\u00A7cBetterUCMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel());
        }
        return false;
    }

    private static void sendClientMessage(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }
}
