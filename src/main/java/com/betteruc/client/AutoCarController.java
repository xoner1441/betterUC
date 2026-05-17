package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.CarMarkerHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoCarController {

    private static final int CAR_AUTO_LOCK_DELAY_TICKS = 10;
    private static final int CAR_AUTO_START_DELAY_TICKS = 10;
    private static final int CAR_CONTROL_CLICK_DELAY_TICKS = 0;
    private static final int PARK_MARKER_MIN_SETTLE_TICKS = 6;
    private static final int PARK_MARKER_MAX_SETTLE_TICKS = 50;
    private static final int PARK_MARKER_STABLE_TICKS = 5;
    private static final double PARK_MARKER_STABLE_DISTANCE_SQ = 0.0009;
    private static final Pattern NAVI_COORDS_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s)/?navi\\s+(-?\\d+)\\s*/\\s*(-?\\d+)\\s*/\\s*(-?\\d+)(?:\\b|$)"
    );
    private static final Pattern AXIS_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\b([XYZ])\\s*[:=]\\s*([\\-−]?\\d+)"
    );
    private static final long CAR_FIND_RESULT_WINDOW_MS = 20_000L;
    private static final long AUTO_NAVI_COOLDOWN_MS = 2_000L;
    private static final long AUTO_NAVI_DUPLICATE_WINDOW_MS = 45_000L;

    private static long lastCarFindCommandMs = 0L;
    private static long lastAutoNaviMs = 0L;
    private static String lastAutoNaviKey = "";
    private static Integer pendingFindX = null;
    private static Integer pendingFindY = null;
    private static Integer pendingFindZ = null;

    private boolean wasRidingMinecart = false;
    private AbstractMinecartEntity lastMinecartEntity = null;
    private Vec3d lastMinecartPos = null;
    private boolean pendingParkMarker = false;
    private int pendingParkMarkerTicks = -1;
    private int pendingParkStableTicks = 0;
    private Vec3d pendingParkLastObservedPos = null;
    private int pendingCarLockTicks = -1;
    private int pendingCarStartTicks = -1;
    private int lastHandledCarControlSyncId = -1;
    private int pendingCarControlSyncId = -1;
    private int pendingCarControlSlotId = -1;
    private int pendingCarControlClickTicks = -1;

    public void tick(MinecraftClient client) {
        if (client.player == null) return;
        if (!ServerGate.isAllowedServer(client)) {
            resetTransientState();
            return;
        }

        boolean enabled = BetterUCConfig.INSTANCE.carAutomationEnabled;
        AbstractMinecartEntity minecart = client.player.getVehicle() instanceof AbstractMinecartEntity
                ? (AbstractMinecartEntity) client.player.getVehicle()
                : null;
        boolean ridingMinecart = minecart != null;

        if (ridingMinecart && !wasRidingMinecart) {
            CarMarkerHud.clear();
            clearPendingParkMarker();
        }

        if (minecart != null) {
            lastMinecartEntity = minecart;
            lastMinecartPos = getMinecartPos(minecart);
        } else if (wasRidingMinecart && lastMinecartPos != null) {
            beginPendingParkMarker();
        }

        if (!ridingMinecart) {
            tickPendingParkMarker(client);
        }

        if (enabled) {
            if (ridingMinecart && !wasRidingMinecart) {
                pendingCarLockTicks = CAR_AUTO_LOCK_DELAY_TICKS;
                pendingCarStartTicks = -1;
                lastHandledCarControlSyncId = -1;
                resetPendingCarControlClick();
                BetterUCMod.LOGGER.info("Auto-car: Minecart betreten -> /car lock in {} Ticks", pendingCarLockTicks);
            }
            autoClickCarControlScreen(client);
        } else {
            pendingCarLockTicks = -1;
            pendingCarStartTicks = -1;
            lastHandledCarControlSyncId = -1;
            resetPendingCarControlClick();
        }

        if (enabled && pendingCarLockTicks >= 0) {
            if (!ridingMinecart) {
                pendingCarLockTicks = -1;
            } else if (pendingCarLockTicks == 0) {
                ServerCommandUtil.send(client, "car lock");
                pendingCarStartTicks = CAR_AUTO_START_DELAY_TICKS;
                pendingCarLockTicks = -1;
                BetterUCMod.LOGGER.info(
                        "Auto-car: /car lock -> /car start in {} Ticks",
                        pendingCarStartTicks
                );
            } else {
                pendingCarLockTicks--;
            }
        }

        if (enabled && pendingCarStartTicks >= 0) {
            if (!ridingMinecart) {
                pendingCarStartTicks = -1;
            } else if (pendingCarStartTicks == 0) {
                ServerCommandUtil.send(client, "car start");
                BetterUCMod.LOGGER.info("Auto-car: /car start");
                pendingCarStartTicks = -1;
            } else {
                pendingCarStartTicks--;
            }
        }

        wasRidingMinecart = ridingMinecart;
    }

    public void reset() {
        resetTransientState();
        lastMinecartPos = null;
        CarMarkerHud.clear();
        clearFindAutomationState();
    }

    public static void markCarFindCommand() {
        lastCarFindCommandMs = System.currentTimeMillis();
        lastAutoNaviMs = 0L;
        lastAutoNaviKey = "";
        pendingFindX = null;
        pendingFindY = null;
        pendingFindZ = null;
    }

    public static void handleIncomingChatForCarFind(MinecraftClient client, String raw) {
        if (client == null || client.player == null) return;
        if (raw == null || raw.isBlank()) return;
        if (!BetterUCConfig.INSTANCE.carAutomationEnabled) return;
        if (!ServerGate.isAllowedServer(client)) return;

        long now = System.currentTimeMillis();
        if (now - lastCarFindCommandMs > CAR_FIND_RESULT_WINDOW_MS) return;
        if (now - lastAutoNaviMs < AUTO_NAVI_COOLDOWN_MS) return;

        Matcher matcher = NAVI_COORDS_PATTERN.matcher(raw);
        if (matcher.find()) {
            Integer x = parseCoordinateValue(matcher.group(1));
            Integer y = parseCoordinateValue(matcher.group(2));
            Integer z = parseCoordinateValue(matcher.group(3));
            if (x != null && y != null && z != null) {
                maybeSendNavi(client, x, y, z, now);
            }
            return;
        }

        Matcher axisMatcher = AXIS_TOKEN_PATTERN.matcher(raw);
        boolean sawAxisToken = false;
        while (axisMatcher.find()) {
            sawAxisToken = true;
            String axis = axisMatcher.group(1);
            Integer value = parseCoordinateValue(axisMatcher.group(2));
            if (value == null) continue;

            if ("X".equalsIgnoreCase(axis)) {
                pendingFindX = value;
            } else if ("Y".equalsIgnoreCase(axis)) {
                pendingFindY = value;
            } else if ("Z".equalsIgnoreCase(axis)) {
                pendingFindZ = value;
            }
        }
        if (!sawAxisToken) return;

        if (pendingFindX == null || pendingFindY == null || pendingFindZ == null) return;
        maybeSendNavi(client, pendingFindX, pendingFindY, pendingFindZ, now);
    }

    private static Integer parseCoordinateValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().replace('−', '-');
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void maybeSendNavi(MinecraftClient client, int x, int y, int z, long now) {
        String key = x + "/" + y + "/" + z;

        if (key.equals(lastAutoNaviKey) && now - lastAutoNaviMs < AUTO_NAVI_DUPLICATE_WINDOW_MS) {
            return;
        }

        String command = "navi " + key;
        if (!ServerCommandUtil.send(client, command)) return;

        lastAutoNaviMs = now;
        lastAutoNaviKey = key;
        lastCarFindCommandMs = 0L;
        pendingFindX = null;
        pendingFindY = null;
        pendingFindZ = null;
        BetterUCMod.LOGGER.info("Auto-car: /car find Treffer erkannt -> /{}", command);
    }

    private void resetTransientState() {
        pendingCarLockTicks = -1;
        pendingCarStartTicks = -1;
        lastHandledCarControlSyncId = -1;
        resetPendingCarControlClick();
        wasRidingMinecart = false;
        lastMinecartEntity = null;
        clearPendingParkMarker();
    }

    private void beginPendingParkMarker() {
        pendingParkMarker = true;
        pendingParkMarkerTicks = 0;
        pendingParkStableTicks = 0;
        pendingParkLastObservedPos = lastMinecartPos;
        BetterUCMod.LOGGER.info("Auto-car: Parkmarker wartet auf Minecart-Stillstand bei {}", lastMinecartPos);
    }

    private void tickPendingParkMarker(MinecraftClient client) {
        if (!pendingParkMarker || lastMinecartPos == null) return;

        Vec3d currentPos = getTrackedMinecartPos();
        if (currentPos != null) {
            lastMinecartPos = currentPos;
            if (pendingParkLastObservedPos != null
                    && pendingParkLastObservedPos.squaredDistanceTo(currentPos) <= PARK_MARKER_STABLE_DISTANCE_SQ) {
                pendingParkStableTicks++;
            } else {
                pendingParkStableTicks = 0;
            }
            pendingParkLastObservedPos = currentPos;
        }

        pendingParkMarkerTicks++;
        boolean waitedLongEnough = pendingParkMarkerTicks >= PARK_MARKER_MIN_SETTLE_TICKS;
        boolean minecartLooksStopped = pendingParkStableTicks >= PARK_MARKER_STABLE_TICKS;
        boolean timedOut = pendingParkMarkerTicks >= PARK_MARKER_MAX_SETTLE_TICKS;
        if (!timedOut && (!waitedLongEnough || !minecartLooksStopped)) return;

        CarMarkerHud.setMarker(client, lastMinecartPos);
        BetterUCMod.LOGGER.info(
                "Auto-car: Marker gesetzt bei {} ({} Ticks, {})",
                lastMinecartPos,
                pendingParkMarkerTicks,
                timedOut ? "Timeout" : "stabil"
        );
        clearPendingParkMarker();
        lastMinecartEntity = null;
    }

    private Vec3d getTrackedMinecartPos() {
        if (lastMinecartEntity == null || lastMinecartEntity.isRemoved()) return null;
        return getMinecartPos(lastMinecartEntity);
    }

    private Vec3d getMinecartPos(AbstractMinecartEntity minecart) {
        return new Vec3d(minecart.getX(), minecart.getY(), minecart.getZ());
    }

    private void clearPendingParkMarker() {
        pendingParkMarker = false;
        pendingParkMarkerTicks = -1;
        pendingParkStableTicks = 0;
        pendingParkLastObservedPos = null;
    }

    private static void clearFindAutomationState() {
        lastCarFindCommandMs = 0L;
        lastAutoNaviMs = 0L;
        lastAutoNaviKey = "";
        pendingFindX = null;
        pendingFindY = null;
        pendingFindZ = null;
    }

    private void autoClickCarControlScreen(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            lastHandledCarControlSyncId = -1;
            resetPendingCarControlClick();
            return;
        }
        if (client.interactionManager == null || client.player == null) return;

        String title = handledScreen.getTitle().getString();
        if (title == null || !title.toLowerCase(Locale.ROOT).contains("carcontrol")) {
            resetPendingCarControlClick();
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler == client.player.playerScreenHandler) {
            resetPendingCarControlClick();
            return;
        }

        int syncId = handler.syncId;
        if (lastHandledCarControlSyncId == syncId) return;

        int slotId = findCarControlActionSlot(client, handler, Items.EMERALD);
        if (slotId < 0) {
            slotId = findCarControlActionSlot(client, handler, Items.REDSTONE);
        }
        if (slotId < 0) {
            resetPendingCarControlClick();
            return;
        }

        if (pendingCarControlSyncId != syncId || pendingCarControlSlotId != slotId) {
            pendingCarControlSyncId = syncId;
            pendingCarControlSlotId = slotId;
            pendingCarControlClickTicks = CAR_CONTROL_CLICK_DELAY_TICKS;
            BetterUCMod.LOGGER.info(
                    "Auto-car: CarControl Slot {} erkannt -> Klick in {} Ticks",
                    slotId,
                    pendingCarControlClickTicks
            );
            if (pendingCarControlClickTicks > 0) {
                return;
            }
        }

        if (pendingCarControlClickTicks > 0) {
            pendingCarControlClickTicks--;
            return;
        }

        client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
        client.player.closeHandledScreen();
        lastHandledCarControlSyncId = syncId;
        resetPendingCarControlClick();
        BetterUCMod.LOGGER.info("Auto-car: CarControl Slot {} geklickt", slotId);
    }

    private void resetPendingCarControlClick() {
        pendingCarControlSyncId = -1;
        pendingCarControlSlotId = -1;
        pendingCarControlClickTicks = -1;
    }

    private int findCarControlActionSlot(MinecraftClient client, ScreenHandler handler, Item item) {
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot == null || slot.inventory == client.player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }
}
