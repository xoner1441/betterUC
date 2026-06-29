package com.betteruc.client;

import com.betteruc.ServerGate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AutoGaertnerClient {
    private static final Pattern FLOWER_TARGET_PATTERN = Pattern.compile("\\bpfluecke\\s+(\\d+)\\s+blumen\\s+an\\b");
    private static final long COMMAND_DELAY_MS = 250L;
    private static final long CLICK_INTERVAL_MS = 180L;

    private static boolean jobActive;
    private static boolean awaitingGardenerArrival;
    private static boolean bushCollectorActive;
    private static int targetFlowers;
    private static int currentContainerId = -1;
    private static int collectedBushes;
    private static long nextClickAtMs;
    private static long lastDropFlowersAtMs;
    private static final Set<Integer> clickedSlotsInContainer = new HashSet<>();

    private AutoGaertnerClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        String clean = key(raw);

        if (bushCollectorActive
                && clean.contains("payday")
                && clean.contains("du bekommst dein gehalt")
                && clean.contains("ausgezahlt")) {
            finishBushCollector(client);
            return;
        }

        if (!clean.contains("gaertner")) return;

        Matcher targetMatcher = FLOWER_TARGET_PATTERN.matcher(clean);
        if (targetMatcher.find()) {
            startFlowerPhase(client, parsePositiveInt(targetMatcher.group(1)));
            return;
        }

        if (clean.contains("bring die blumen")
                && clean.contains("gaertner")
                && clean.contains("dropblumen")) {
            awaitingGardenerArrival = true;
            jobActive = true;
            return;
        }

        if (awaitingGardenerArrival && clean.contains("du bist beim gaertner angekommen")) {
            awaitingGardenerArrival = false;
            sendDropFlowers(client);
            return;
        }

        if (clean.contains("gehe nun zum blumenstand")
                && clean.contains("entferne")
                && clean.contains("verwelkten buesche")) {
            startBushCollector(client);
        }
    }

    public static void tick(Minecraft client) {
        if (!bushCollectorActive) return;
        if (client == null || client.player == null || client.gameMode == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        Screen screen = ClientCompat.currentScreen(client);
        if (!(screen instanceof MenuAccess<?> access)) return;
        if (!isFlowerStandMenu(screen)) return;
        if (!(access.getMenu() instanceof AbstractContainerMenu menu)) return;

        if (menu.containerId != currentContainerId) {
            currentContainerId = menu.containerId;
            clickedSlotsInContainer.clear();
        }

        long now = System.currentTimeMillis();
        if (nextClickAtMs > now) return;

        Slot bushSlot = findNextDeadBushSlot(client, menu);
        if (bushSlot == null) return;

        client.gameMode.handleContainerInput(menu.containerId, bushSlot.index, 0, ContainerInput.PICKUP, client.player);
        clickedSlotsInContainer.add(bushSlot.index);
        collectedBushes++;
        nextClickAtMs = now + CLICK_INTERVAL_MS;
    }

    public static void reset() {
        jobActive = false;
        awaitingGardenerArrival = false;
        bushCollectorActive = false;
        targetFlowers = 0;
        currentContainerId = -1;
        collectedBushes = 0;
        nextClickAtMs = 0L;
        lastDropFlowersAtMs = 0L;
        clickedSlotsInContainer.clear();
    }

    private static void startFlowerPhase(Minecraft client, int flowers) {
        jobActive = true;
        awaitingGardenerArrival = false;
        bushCollectorActive = false;
        targetFlowers = flowers;
        currentContainerId = -1;
        collectedBushes = 0;
        nextClickAtMs = 0L;
        clickedSlotsInContainer.clear();

        if (client != null && client.player != null && targetFlowers > 0) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-G\u00E4rtner bereit: \u00A7f" + targetFlowers + " Blumen"
            ));
        }
    }

    private static void startBushCollector(Minecraft client) {
        jobActive = true;
        awaitingGardenerArrival = false;
        bushCollectorActive = true;
        currentContainerId = -1;
        collectedBushes = 0;
        nextClickAtMs = 0L;
        clickedSlotsInContainer.clear();

        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-G\u00E4rtner sammelt verwelkte B\u00FCsche."
            ));
        }
    }

    private static void finishBushCollector(Minecraft client) {
        int total = collectedBushes;
        boolean wasActive = bushCollectorActive || jobActive;
        reset();
        if (wasActive && client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-G\u00E4rtner abgeschlossen: \u00A7f" + total + " B\u00FCsche"
            ));
        }
    }

    private static void sendDropFlowers(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now - lastDropFlowersAtMs < 3_000L) return;
        lastDropFlowersAtMs = now;
        ClientScheduler.runDelayedOnClient(client, COMMAND_DELAY_MS,
                () -> ServerCommandUtil.send(client, "dropblumen", false));
    }

    private static Slot findNextDeadBushSlot(Minecraft client, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null || clickedSlotsInContainer.contains(slot.index)) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (!slot.hasItem()) continue;

            ItemStack stack = slot.getItem();
            if (isDeadBush(stack)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isFlowerStandMenu(Screen screen) {
        String title = key(screen.getTitle().getString());
        return title.contains("blumenstand");
    }

    private static boolean isDeadBush(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && "minecraft".equals(itemId.getNamespace())
                && "dead_bush".equals(itemId.getPath());
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
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
