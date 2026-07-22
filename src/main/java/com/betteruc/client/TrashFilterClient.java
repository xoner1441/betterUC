package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import java.text.Normalizer;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class TrashFilterClient {
    private static final long CLOSE_LOCK_MS = 5_000L;

    private static int currentContainerId = -1;
    private static boolean selectedItemSeen;
    private static long closeLockedUntilMs;

    private TrashFilterClient() {
    }

    public static void tick(Minecraft client) {
        if (!BetterUCConfig.INSTANCE.trashFilterEnabled || client == null || client.player == null) {
            reset();
            return;
        }

        Screen screen = ClientCompat.currentScreen(client);
        if (!(screen instanceof MenuAccess<?> access) || !isTrashScreen(screen)) {
            reset();
            return;
        }
        if (!(access.getMenu() instanceof AbstractContainerMenu menu)) {
            reset();
            return;
        }

        if (menu.containerId != currentContainerId) {
            currentContainerId = menu.containerId;
            selectedItemSeen = false;
            closeLockedUntilMs = 0L;
            logTopRow(screen, menu);
        }

        boolean hasSelectedItem = hasSelectedItem(screen, menu);
        if (hasSelectedItem && !selectedItemSeen && BetterUCConfig.INSTANCE.trashFilterCloseLockEnabled) {
            closeLockedUntilMs = System.currentTimeMillis() + CLOSE_LOCK_MS;
        }
        selectedItemSeen = hasSelectedItem;
    }

    public static boolean shouldHighlight(Screen screen, Slot slot) {
        if (!BetterUCConfig.INSTANCE.trashFilterEnabled || !isTrashScreen(screen) || slot == null) return false;

        if (!isTopTrashSlot(screen, slot)) return false;
        return isSelected(slot.getItem());
    }

    public static boolean shouldPreventClose(Screen screen) {
        return BetterUCConfig.INSTANCE.trashFilterEnabled
                && BetterUCConfig.INSTANCE.trashFilterCloseLockEnabled
                && isTrashScreen(screen)
                && closeLockedUntilMs > System.currentTimeMillis();
    }

    public static void reset() {
        currentContainerId = -1;
        selectedItemSeen = false;
        closeLockedUntilMs = 0L;
    }

    private static boolean hasSelectedItem(Screen screen, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null) continue;
            if (!isTopTrashSlot(screen, slot)) continue;
            if (isSelected(slot.getItem())) return true;
        }
        return false;
    }

    private static boolean isTopTrashSlot(Screen screen, Slot slot) {
        if (slot == null || !(screen instanceof MenuAccess<?> access)) return false;

        int topY = Integer.MAX_VALUE;
        for (Slot candidate : access.getMenu().slots) {
            if (candidate != null) topY = Math.min(topY, candidate.y);
        }
        return topY != Integer.MAX_VALUE && Math.abs(slot.y - topY) <= 2;
    }

    private static void logTopRow(Screen screen, AbstractContainerMenu menu) {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (!isTopTrashSlot(screen, slot) || slot.getItem().isEmpty()) continue;

            ItemStack stack = slot.getItem();
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!items.isEmpty()) items.append(", ");
            items.append('#').append(index)
                    .append(" y=").append(slot.y)
                    .append(' ').append(id);
        }
        BetterUCMod.LOGGER.info(
                "Muelleimer-Filter: oberste Slot-Reihe erkannt [{}]",
                items.isEmpty() ? "leer" : items
        );
    }

    private static boolean isSelected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !"minecraft".equals(id.getNamespace())) return false;

        return switch (id.getPath()) {
            case "rotten_flesh" -> BetterUCConfig.INSTANCE.trashFilterRottenFlesh;
            case "paper" -> BetterUCConfig.INSTANCE.trashFilterPaper;
            case "potato" -> BetterUCConfig.INSTANCE.trashFilterPotato;
            case "carrot" -> BetterUCConfig.INSTANCE.trashFilterCarrot;
            case "apple" -> BetterUCConfig.INSTANCE.trashFilterApple;
            case "chest" -> BetterUCConfig.INSTANCE.trashFilterChest;
            case "trapped_chest" -> BetterUCConfig.INSTANCE.trashFilterTrappedChest;
            case "ender_chest" -> BetterUCConfig.INSTANCE.trashFilterEnderChest;
            default -> false;
        };
    }

    private static boolean isTrashScreen(Screen screen) {
        if (screen == null || screen.getTitle() == null) return false;
        String title = normalize(screen.getTitle().getString());
        return title.equals("mulleimer") || title.equals("muelleimer");
    }

    private static String normalize(String value) {
        if (value == null) return "";

        String normalized = Normalizer.normalize(
                value.replaceAll("\\u00A7.", ""),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");

        return normalized
                .toLowerCase(Locale.ROOT)
                .replace("ä", "a")
                .replace("ö", "o")
                .replace("ü", "u")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }
}
