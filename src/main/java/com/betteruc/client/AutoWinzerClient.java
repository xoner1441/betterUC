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

public final class AutoWinzerClient {
    private static final Pattern GRAPE_TARGET_PATTERN = Pattern.compile("\\bernte bitte\\s+(\\d+)\\s+trauben\\s+ab\\b");
    private static final long CLICK_INTERVAL_MS = 180L;

    private static boolean active;
    private static int targetHarvestMenus;
    private static int completedHarvestMenus;
    private static int currentContainerId = -1;
    private static boolean currentContainerHadGrapes;
    private static boolean currentContainerCounted;
    private static long nextClickAtMs;
    private static final Set<Integer> clickedSlotsInContainer = new HashSet<>();

    private AutoWinzerClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        String clean = key(raw);
        if (clean.contains("winzer")) {
            Matcher matcher = GRAPE_TARGET_PATTERN.matcher(clean);
            if (matcher.find()) {
                start(client, parsePositiveInt(matcher.group(1)));
                return;
            }

            if (clean.contains("vielen dank") && clean.contains("trauben")) {
                finish(client);
            }
        }
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client == null || client.player == null || client.gameMode == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        if (targetHarvestMenus <= 0 || completedHarvestMenus >= targetHarvestMenus) {
            finish(client);
            return;
        }

        Screen screen = ClientCompat.currentScreen(client);
        if (!(screen instanceof MenuAccess<?> access)) return;
        if (!isWinzerHarvestMenu(screen)) return;
        if (!(access.getMenu() instanceof AbstractContainerMenu menu)) return;

        if (menu.containerId != currentContainerId) {
            currentContainerId = menu.containerId;
            currentContainerHadGrapes = false;
            currentContainerCounted = false;
            clickedSlotsInContainer.clear();
        }

        long now = System.currentTimeMillis();
        if (nextClickAtMs > now) return;

        Slot grapeSlot = findNextGrapeSlot(client, menu);
        if (grapeSlot == null) {
            markCurrentContainerDone(client);
            return;
        }

        client.gameMode.handleContainerInput(menu.containerId, grapeSlot.index, 0, ContainerInput.PICKUP, client.player);
        clickedSlotsInContainer.add(grapeSlot.index);
        currentContainerHadGrapes = true;
        nextClickAtMs = now + CLICK_INTERVAL_MS;
    }

    public static void reset() {
        active = false;
        targetHarvestMenus = 0;
        completedHarvestMenus = 0;
        currentContainerId = -1;
        currentContainerHadGrapes = false;
        currentContainerCounted = false;
        nextClickAtMs = 0L;
        clickedSlotsInContainer.clear();
    }

    private static void start(Minecraft client, int grapes) {
        if (grapes <= 0) return;

        active = true;
        targetHarvestMenus = grapes;
        completedHarvestMenus = 0;
        currentContainerId = -1;
        currentContainerHadGrapes = false;
        currentContainerCounted = false;
        nextClickAtMs = 0L;
        clickedSlotsInContainer.clear();

        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-Winzer bereit: \u00A7f" + targetHarvestMenus + " Ernte-Fenster"
            ));
        }
    }

    private static void finish(Minecraft client) {
        int total = Math.max(targetHarvestMenus, completedHarvestMenus);
        boolean wasActive = active;
        reset();
        if (wasActive && client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-Winzer abgeschlossen: \u00A7f" + total + " Ernte-Fenster"
            ));
        }
    }

    private static void markCurrentContainerDone(Minecraft client) {
        if (!currentContainerHadGrapes || currentContainerCounted) return;

        currentContainerCounted = true;
        completedHarvestMenus++;
        if (completedHarvestMenus >= targetHarvestMenus) {
            finish(client);
        }
    }

    private static Slot findNextGrapeSlot(Minecraft client, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null || clickedSlotsInContainer.contains(slot.index)) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (!slot.hasItem()) continue;

            ItemStack stack = slot.getItem();
            if (isPurpleDye(stack)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isWinzerHarvestMenu(Screen screen) {
        String title = key(screen.getTitle().getString());
        return title.contains("winzer") && title.contains("trauben") && title.contains("ernten");
    }

    private static boolean isPurpleDye(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && "minecraft".equals(itemId.getNamespace())
                && "purple_dye".equals(itemId.getPath());
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
