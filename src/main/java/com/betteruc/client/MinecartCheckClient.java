package com.betteruc.client;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;

/**
 * Provides a small shortcut for checking a vehicle without having to type the
 * command manually.
 */
public final class MinecartCheckClient {

    private static final String CHECK_COMMAND = "checkkfz";
    private static final long REPEAT_GUARD_MS = 350L;
    private static final long FOLLOW_UP_INTERACTION_DELAY_MS = 450L;

    private static int lastMinecartId = Integer.MIN_VALUE;
    private static long lastUseAtMs;

    private MinecartCheckClient() {
    }

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!matchesShortcut(
                    level.isClientSide(),
                    hand == InteractionHand.MAIN_HAND,
                    player.isShiftKeyDown(),
                    entity instanceof AbstractMinecart)) {
                return InteractionResult.PASS;
            }

            Minecraft client = Minecraft.getInstance();
            if (client.player != player) return InteractionResult.PASS;

            long now = System.currentTimeMillis();
            int minecartId = entity.getId();
            if (isRepeatedInteraction(minecartId, now, lastMinecartId, lastUseAtMs)) {
                return InteractionResult.PASS;
            }

            if (ServerCommandUtil.send(client, CHECK_COMMAND, true)) {
                lastMinecartId = minecartId;
                lastUseAtMs = now;
                Vec3 hitOffset = hitResult.getLocation().subtract(
                        entity.getX(), entity.getY(), entity.getZ());
                ClientScheduler.runDelayedOnClient(client, FOLLOW_UP_INTERACTION_DELAY_MS, () -> {
                    if (client.player != player
                            || client.getConnection() == null
                            || entity.isRemoved()
                            || entity.level() != client.level) {
                        return;
                    }

                    // /checkkfz expects a regular second right-click. Sending it
                    // directly keeps it non-sneaking even while Shift is held.
                    client.getConnection().send(new ServerboundInteractPacket(
                            entity.getId(),
                            InteractionHand.MAIN_HAND,
                            hitOffset,
                            false));
                });
            }

            // The original click must not reach the server before /checkkfz is active.
            return InteractionResult.SUCCESS;
        });
    }

    static boolean matchesShortcut(boolean clientSide, boolean mainHand, boolean sneaking, boolean minecart) {
        return clientSide && mainHand && sneaking && minecart;
    }

    static boolean isRepeatedInteraction(int minecartId, long now, int previousMinecartId, long previousUseAt) {
        long elapsed = now - previousUseAt;
        return minecartId == previousMinecartId && elapsed >= 0L && elapsed < REPEAT_GUARD_MS;
    }
}
