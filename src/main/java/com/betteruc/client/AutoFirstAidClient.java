package com.betteruc.client;

import com.betteruc.ServerGate;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AutoFirstAidClient {
    private static final String ACCEPTED_MESSAGE = "erste hilfe du hast die erste hilfe angenommen";
    private static final String RESPAWNED_MESSAGE = "friedhof du lebst nun wieder";

    private static boolean armed;
    private static boolean waitingForManualMenuToClose;
    private static boolean firstAidMenuWasOpen;
    private static boolean clickedCurrentMenu;
    private static int currentContainerId = -1;

    private AutoFirstAidClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        String clean = key(raw);
        if (clean.contains(RESPAWNED_MESSAGE)) {
            reset();
            return;
        }
        if (!AutomationController.isFirstAidEnabled() || !clean.contains(ACCEPTED_MESSAGE)) return;

        armed = true;
        waitingForManualMenuToClose = isFirstAidMenu(ClientCompat.currentScreen(client));
    }

    public static void tick(Minecraft client) {
        if (!AutomationController.isFirstAidEnabled()) {
            reset();
            return;
        }
        if (!armed) return;
        if (client == null || client.player == null || client.gameMode == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        Screen screen = ClientCompat.currentScreen(client);
        if (!(screen instanceof MenuAccess<?> access)
                || !isFirstAidMenu(screen)
                || !(access.getMenu() instanceof AbstractContainerMenu menu)) {
            firstAidMenuWasOpen = false;
            clickedCurrentMenu = false;
            currentContainerId = -1;
            waitingForManualMenuToClose = false;
            return;
        }

        if (waitingForManualMenuToClose) return;
        if (!firstAidMenuWasOpen || currentContainerId != menu.containerId) {
            firstAidMenuWasOpen = true;
            clickedCurrentMenu = false;
            currentContainerId = menu.containerId;
        }
        if (clickedCurrentMenu) return;

        Slot acceptSlot = findAcceptSlot(client, menu);
        if (acceptSlot == null) return;

        client.gameMode.handleContainerInput(
                menu.containerId,
                acceptSlot.index,
                0,
                ContainerInput.PICKUP,
                client.player
        );
        clickedCurrentMenu = true;
    }

    public static void reset() {
        armed = false;
        waitingForManualMenuToClose = false;
        firstAidMenuWasOpen = false;
        clickedCurrentMenu = false;
        currentContainerId = -1;
    }

    private static Slot findAcceptSlot(Minecraft client, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (isAcceptItem(slot.getItem())) return slot;
        }
        return null;
    }

    private static boolean isFirstAidMenu(Screen screen) {
        return screen != null && key(screen.getTitle().getString()).contains("erste hilfe annehmen");
    }

    private static boolean isAcceptItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && "minecraft".equals(itemId.getNamespace())
                && "lime_stained_glass_pane".equals(itemId.getPath());
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
