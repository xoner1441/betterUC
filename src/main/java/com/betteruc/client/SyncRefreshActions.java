package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.BetterUCSuppressFlags;
import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class SyncRefreshActions {

    private SyncRefreshActions() {
    }

    public static void requestSelectedFactionRefresh(MinecraftClient client, boolean notify) {
        String factionQuery = BetterUCConfig.getSelectedFactionQuery();
        if (factionQuery.isEmpty()) {
            sendClientMessage(client, "\u00A7cKeine Fraktion ausgewaehlt.");
            return;
        }
        requestFactionRefresh(client, factionQuery, notify);
    }

    public static void requestFactionRefresh(MinecraftClient client, String factionQuery, boolean notify) {
        if (!canRefresh(client, notify)) return;
        String normalized = BetterUCConfig.normalizeFactionQuery(factionQuery);
        if (normalized.isEmpty()) return;

        String commandQuery = BetterUCConfig.memberInfoCommandQueryFor(normalized);
        BetterUCConfig.markFactionSyncRequested(normalized);
        BetterUCSuppressFlags.markSilentMemberRequest();
        ServerCommandUtil.send(client, "memberinfoall " + commandQuery);
        ClientScheduler.runDelayedOnClient(client, BetterUCSuppressFlags.SILENT_MEMBER_TIMEOUT_MS,
                BetterUCSuppressFlags::cleanupStaleSilentMemberState);
        BetterUCMod.LOGGER.info("Manual silent /memberinfoall {} requested ({})", commandQuery, normalized);
        if (notify) {
            sendClientMessage(client, "\u00A7aFraktion wird neu geladen: \u00A7f" + BetterUCConfig.factionLabelForQuery(normalized));
        }
    }

    public static void requestBlacklistRefresh(MinecraftClient client, boolean notify) {
        if (!canRefresh(client, notify)) return;
        BetterUCSuppressFlags.markSilentBlacklistRequest();
        ServerCommandUtil.send(client, "blacklist");
        ClientScheduler.runDelayedOnClient(client, BetterUCSuppressFlags.SILENT_BLACKLIST_TIMEOUT_MS,
                BetterUCSuppressFlags::cleanupStaleSilentBlacklistState);
        BetterUCMod.LOGGER.info("Manual silent /blacklist requested");
        if (notify) {
            sendClientMessage(client, "\u00A7aBlacklist wird neu geladen.");
        }
    }

    public static void requestStatsRefresh(MinecraftClient client, boolean notify) {
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

    private static boolean canRefresh(MinecraftClient client, boolean notify) {
        if (client == null || client.player == null) return false;
        if (ServerGate.isAllowedServer(client)) return true;
        if (notify) {
            sendClientMessage(client, "\u00A7cBetterUCMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel());
        }
        return false;
    }

    private static void sendClientMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
