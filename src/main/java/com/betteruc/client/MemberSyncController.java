package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.BetterUCSuppressFlags;
import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.PaydayHud;
import net.minecraft.client.MinecraftClient;

public final class MemberSyncController {

    private static final int RETRY_DELAY_TICKS = 20 * 6;

    private int memberInfoDelay = -1;
    private int reloadTickCounter = -1;
    private String pendingRetryFactionQuery = "";
    private boolean pendingRetryUsed = false;
    private int pendingRetryTicks = -1;
    private long pendingRetryRequestMs = 0L;

    public void onJoin() {
        memberInfoDelay = 0;
        scheduleReload();
        clearRetry();
    }

    public void reset() {
        memberInfoDelay = -1;
        reloadTickCounter = -1;
        clearRetry();
    }

    public void requestNow() {
        memberInfoDelay = 0;
    }

    public void scheduleReload() {
        reloadTickCounter = BetterUCConfig.INSTANCE.reloadIntervalMinutes * 60 * 20;
    }

    public void tick(MinecraftClient client) {
        tickMemberReload(client);
        tickReloadCycle(client);
        tickRetry(client);
    }

    public void requestSilentRefresh(MinecraftClient client, String factionQuery) {
        String normalized = BetterUCConfig.normalizeFactionQuery(factionQuery);
        if (normalized.isEmpty()) return;
        sendMemberInfoRequest(client, normalized, false);
    }

    private void tickMemberReload(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (PaydayHud.isPausedByAfk()) return;

        if (memberInfoDelay == 0) {
            String factionQuery = BetterUCConfig.getSelectedFactionQuery();
            if (factionQuery.isEmpty()) {
                BetterUCConfig.rebuildRemoteFactionUnion();
                memberInfoDelay = -1;
                ClientScheduler.runDelayedOnClient(client, 1000, BetterUCSuppressFlags::cleanupStaleSilentMemberState);
                return;
            }

            sendMemberInfoRequest(client, factionQuery, false);
            memberInfoDelay = -1;
        } else if (memberInfoDelay > 0) {
            memberInfoDelay--;
        }
    }

    private void tickReloadCycle(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (PaydayHud.isPausedByAfk()) return;

        if (reloadTickCounter > 0) {
            reloadTickCounter--;
        } else if (reloadTickCounter == 0) {
            requestNow();
            scheduleReload();
            BetterUCMod.LOGGER.info("Auto-Reload ausgeloest (Intervall: {} Minuten)",
                    BetterUCConfig.INSTANCE.reloadIntervalMinutes);
        }
    }

    private void tickRetry(MinecraftClient client) {
        if (pendingRetryTicks < 0) return;
        if (!ServerGate.isAllowedServer(client) || PaydayHud.isPausedByAfk()) return;

        if (pendingRetryTicks > 0) {
            pendingRetryTicks--;
            return;
        }

        if (!pendingRetryUsed && shouldRetryPendingRequest()) {
            String query = pendingRetryFactionQuery;
            pendingRetryUsed = true;
            clearRetry();
            sendMemberInfoRequest(client, query, true);
            return;
        }

        clearRetry();
    }

    private void sendMemberInfoRequest(MinecraftClient client, String factionQuery, boolean retry) {
        if (client == null || client.player == null) return;
        if (!ServerGate.isAllowedServer(client)) return;

        String normalized = BetterUCConfig.normalizeFactionQuery(factionQuery);
        if (normalized.isEmpty()) return;

        String commandQuery = BetterUCConfig.memberInfoCommandQueryFor(normalized);
        BetterUCConfig.markFactionSyncRequested(normalized);
        BetterUCSuppressFlags.markSilentMemberRequest();
        ServerCommandUtil.send(client, "memberinfoall " + commandQuery);
        BetterUCMod.LOGGER.info("{} /memberinfoall {} ({})",
                retry ? "Retry-sent" : "Auto-sent",
                commandQuery,
                normalized
        );

        pendingRetryFactionQuery = normalized;
        pendingRetryRequestMs = BetterUCConfig.INSTANCE.lastFactionSyncRequestMs;
        pendingRetryTicks = RETRY_DELAY_TICKS;
        if (!retry) {
            pendingRetryUsed = false;
        }

        ClientScheduler.runDelayedOnClient(client, BetterUCSuppressFlags.SILENT_MEMBER_TIMEOUT_MS,
                BetterUCSuppressFlags::cleanupStaleSilentMemberState);
    }

    private boolean shouldRetryPendingRequest() {
        if (pendingRetryFactionQuery.isBlank()) return false;
        String lastQuery = BetterUCConfig.normalizeFactionQuery(BetterUCConfig.INSTANCE.lastFactionSyncQuery);
        boolean sameQuery = pendingRetryFactionQuery.equals(lastQuery);
        boolean noResponseAfterRequest = BetterUCConfig.INSTANCE.lastFactionSyncMs < pendingRetryRequestMs;
        boolean emptyResponse = sameQuery && BetterUCConfig.INSTANCE.lastFactionSyncMemberCount <= 0;
        return noResponseAfterRequest || emptyResponse;
    }

    private void clearRetry() {
        pendingRetryFactionQuery = "";
        pendingRetryUsed = false;
        pendingRetryTicks = -1;
        pendingRetryRequestMs = 0L;
    }
}
