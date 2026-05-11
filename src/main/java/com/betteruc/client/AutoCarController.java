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

    private static final int CAR_AUTO_START_DELAY_TICKS = 8;
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
    private Vec3d lastMinecartPos = null;
    private int pendingCarStartTicks = -1;
    private int lastHandledCarControlSyncId = -1;

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
        }

        if (minecart != null) {
            lastMinecartPos = new Vec3d(minecart.getX(), minecart.getY(), minecart.getZ());
        } else if (wasRidingMinecart && lastMinecartPos != null) {
            CarMarkerHud.setMarker(client, lastMinecartPos);
            BetterUCMod.LOGGER.info("Auto-car: Marker gesetzt bei {}", lastMinecartPos);
        }

        if (enabled) {
            if (ridingMinecart && !wasRidingMinecart) {
                ServerCommandUtil.send(client, "car lock");
                pendingCarStartTicks = CAR_AUTO_START_DELAY_TICKS;
                lastHandledCarControlSyncId = -1;
                BetterUCMod.LOGGER.info("Auto-car: Minecart betreten -> /car lock");
            }
            autoClickCarControlScreen(client);
        } else {
            pendingCarStartTicks = -1;
            lastHandledCarControlSyncId = -1;
        }

        if (enabled && pendingCarStartTicks >= 0) {
            if (pendingCarStartTicks == 0) {
                if (ridingMinecart) {
                    ServerCommandUtil.send(client, "car start");
                    BetterUCMod.LOGGER.info("Auto-car: /car start");
                }
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
        pendingCarStartTicks = -1;
        lastHandledCarControlSyncId = -1;
        wasRidingMinecart = false;
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
            return;
        }
        if (client.interactionManager == null || client.player == null) return;

        String title = handledScreen.getTitle().getString();
        if (title == null || !title.toLowerCase(Locale.ROOT).contains("carcontrol")) return;

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler == client.player.playerScreenHandler) return;

        int syncId = handler.syncId;
        if (lastHandledCarControlSyncId == syncId) return;

        int slotId = findCarControlActionSlot(client, handler, Items.EMERALD);
        if (slotId < 0) {
            slotId = findCarControlActionSlot(client, handler, Items.REDSTONE);
        }
        if (slotId < 0) return;

        client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
        client.player.closeHandledScreen();
        lastHandledCarControlSyncId = syncId;
        BetterUCMod.LOGGER.info("Auto-car: CarControl Slot {} geklickt", slotId);
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
