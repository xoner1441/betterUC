package com.betteruc.client;

import com.betteruc.ServerGate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

public final class AutoBuyClient {
    public static final int MAX_REQUEST_AMOUNT = 4096;

    private static final String QUANTITY_MENU_TITLE = "menge waehlen";
    private static final String PURCHASE_SUCCESS_MESSAGE = "verkaeufer vielen dank fuer ihren einkauf";
    private static final long CLICK_INTERVAL_MS = 140L;
    private static final long REOPEN_DELAY_MS = 650L;
    private static final long USER_SELECTION_TIMEOUT_MS = 120_000L;
    private static final long AUTOMATION_TIMEOUT_MS = 25_000L;

    private enum Phase {
        IDLE,
        WAITING_FOR_PRODUCT,
        SETTING_QUANTITY,
        WAITING_FOR_SUCCESS,
        REOPENING_SHOP,
        WAITING_FOR_SHOP,
        WAITING_FOR_QUANTITY
    }

    private static Phase phase = Phase.IDLE;
    private static int requestedAmount;
    private static int remainingAmount;
    private static int pendingBatch;
    private static int incrementClicksSent;
    private static int lastContainerId = -1;
    private static String productItemId = "";
    private static String shopTitleKey = "";
    private static boolean specialLimitNotified;
    private static long nextActionAtMs;
    private static long phaseDeadlineMs;

    private AutoBuyClient() {
    }

    public static int start(Minecraft client, int amount) {
        if (client == null || client.player == null) return 0;
        if (!ServerCommandUtil.ensureAllowedServerForManualCommand(client)) return 0;
        if (!AutomationController.isAutoBuyEnabled()) {
            message(client, "\u00A7c[betterUC] Auto-Kauf ist im ClickGUI deaktiviert.");
            return 0;
        }

        requestedAmount = Math.max(1, Math.min(amount, MAX_REQUEST_AMOUNT));
        remainingAmount = requestedAmount;
        pendingBatch = 0;
        incrementClicksSent = 0;
        lastContainerId = -1;
        productItemId = "";
        shopTitleKey = "";
        specialLimitNotified = false;
        phase = Phase.WAITING_FOR_PRODUCT;
        nextActionAtMs = 0L;
        phaseDeadlineMs = System.currentTimeMillis() + USER_SELECTION_TIMEOUT_MS;

        message(client, "\u00A7a[betterUC] Auto-Kauf bereit: \u00A7f" + requestedAmount
                + "\u00A7a St\u00FCck. \u00A77\u00D6ffne jetzt /buy und w\u00E4hle das Produkt.");
        return 1;
    }

    public static int cancel(Minecraft client, boolean notify) {
        boolean wasActive = isActive();
        reset();
        if (notify && client != null && client.player != null) {
            message(client, wasActive
                    ? "\u00A7e[betterUC] Auto-Kauf abgebrochen."
                    : "\u00A77[betterUC] Es ist kein Auto-Kauf aktiv.");
        }
        return wasActive ? 1 : 0;
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (phase != Phase.WAITING_FOR_SUCCESS) return;
        if (!key(raw).contains(PURCHASE_SUCCESS_MESSAGE)) return;

        remainingAmount = Math.max(0, remainingAmount - pendingBatch);
        int completed = requestedAmount - remainingAmount;
        pendingBatch = 0;

        if (remainingAmount <= 0) {
            message(client, "\u00A7a[betterUC] Auto-Kauf abgeschlossen: \u00A7f"
                    + completed + "\u00A7a St\u00FCck.");
            reset();
            return;
        }

        message(client, "\u00A77[betterUC] Auto-Kauf: \u00A7f" + completed + "/"
                + requestedAmount + "\u00A77 gekauft.");
        phase = Phase.REOPENING_SHOP;
        nextActionAtMs = System.currentTimeMillis() + REOPEN_DELAY_MS;
        phaseDeadlineMs = System.currentTimeMillis() + AUTOMATION_TIMEOUT_MS;
        lastContainerId = -1;
        incrementClicksSent = 0;
    }

    public static void tick(Minecraft client) {
        if (!AutomationController.isAutoBuyEnabled()) {
            reset();
            return;
        }
        if (!isActive()) return;
        if (client == null || client.player == null || client.gameMode == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (phaseDeadlineMs > 0L && now > phaseDeadlineMs) {
            fail(client, "Zeit\u00FCberschreitung. Nutze /abuy erneut.");
            return;
        }

        Screen screen = ClientCompat.currentScreen(client);
        AbstractContainerMenu menu = menu(screen);
        String title = screen == null ? "" : key(screen.getTitle().getString());

        if (phase == Phase.WAITING_FOR_PRODUCT) {
            if (menu != null && !isQuantityMenu(title)) {
                shopTitleKey = title;
            }
            if (menu != null && isQuantityMenu(title)) {
                beginQuantitySelection(client, menu);
            }
            return;
        }

        if (phase == Phase.REOPENING_SHOP) {
            if (now < nextActionAtMs) return;
            if (!ServerCommandUtil.sendAutomatic(client, "buy")) return;
            phase = Phase.WAITING_FOR_SHOP;
            phaseDeadlineMs = now + AUTOMATION_TIMEOUT_MS;
            return;
        }

        if (phase == Phase.WAITING_FOR_SHOP) {
            if (menu == null || isQuantityMenu(title)) return;
            if (!shopTitleKey.isEmpty() && !title.equals(shopTitleKey)) return;
            Slot productSlot = findProductSlot(client, menu);
            if (productSlot == null) return;
            click(client, menu, productSlot);
            phase = Phase.WAITING_FOR_QUANTITY;
            nextActionAtMs = now + CLICK_INTERVAL_MS;
            phaseDeadlineMs = now + AUTOMATION_TIMEOUT_MS;
            return;
        }

        if (phase == Phase.WAITING_FOR_QUANTITY) {
            if (menu != null && isQuantityMenu(title)) {
                beginQuantitySelection(client, menu);
            }
            return;
        }

        if (phase == Phase.SETTING_QUANTITY) {
            if (menu == null || !isQuantityMenu(title)) return;
            processQuantityMenu(client, menu, now);
        }
    }

    public static void reset() {
        phase = Phase.IDLE;
        requestedAmount = 0;
        remainingAmount = 0;
        pendingBatch = 0;
        incrementClicksSent = 0;
        lastContainerId = -1;
        productItemId = "";
        shopTitleKey = "";
        specialLimitNotified = false;
        nextActionAtMs = 0L;
        phaseDeadlineMs = 0L;
    }

    private static void beginQuantitySelection(Minecraft client, AbstractContainerMenu menu) {
        QuantityControls controls = findQuantityControls(client, menu);
        if (controls == null) return;

        String selectedId = itemId(controls.selected.getItem());
        if (selectedId.isEmpty()) {
            fail(client, "Das ausgew\u00E4hlte Produkt konnte nicht erkannt werden.");
            return;
        }
        if (productItemId.isEmpty()) {
            productItemId = selectedId;
            notifySpecialLimit(client);
        } else if (!productItemId.equals(selectedId)) {
            fail(client, "Im Shop wurde ein anderes Produkt ge\u00F6ffnet.");
            return;
        }

        phase = Phase.SETTING_QUANTITY;
        lastContainerId = menu.containerId;
        incrementClicksSent = 0;
        nextActionAtMs = System.currentTimeMillis() + CLICK_INTERVAL_MS;
        phaseDeadlineMs = System.currentTimeMillis() + AUTOMATION_TIMEOUT_MS;
    }

    private static void processQuantityMenu(Minecraft client, AbstractContainerMenu menu, long now) {
        QuantityControls controls = findQuantityControls(client, menu);
        if (controls == null || now < nextActionAtMs) return;

        if (menu.containerId != lastContainerId) {
            lastContainerId = menu.containerId;
            incrementClicksSent = 0;
        }

        int target = Math.min(batchLimit(), remainingAmount);
        int requiredIncrementClicks = Math.max(0, target - 1);
        if (incrementClicksSent < requiredIncrementClicks) {
            click(client, menu, controls.increment);
            incrementClicksSent++;
            nextActionAtMs = now + CLICK_INTERVAL_MS;
            phaseDeadlineMs = now + AUTOMATION_TIMEOUT_MS;
            return;
        }

        pendingBatch = target;
        click(client, menu, controls.confirm);
        phase = Phase.WAITING_FOR_SUCCESS;
        nextActionAtMs = 0L;
        phaseDeadlineMs = now + AUTOMATION_TIMEOUT_MS;
    }

    private static QuantityControls findQuantityControls(Minecraft client, AbstractContainerMenu menu) {
        List<Slot> shopSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            shopSlots.add(slot);
        }

        Slot decrement = shopSlots.stream()
                .filter(slot -> "minecraft:redstone".equals(itemId(slot.getItem())))
                .min(Comparator.comparingInt(slot -> slot.index))
                .orElse(null);
        if (decrement == null) return null;

        int controlRow = decrement.index / 9;
        List<Slot> emeralds = shopSlots.stream()
                .filter(slot -> "minecraft:emerald".equals(itemId(slot.getItem())))
                .sorted(Comparator.comparingInt(slot -> slot.index))
                .toList();

        Slot increment = emeralds.stream()
                .filter(slot -> slot.index / 9 == controlRow && slot.index > decrement.index)
                .max(Comparator.comparingInt(slot -> slot.index))
                .orElse(null);
        Slot confirm = shopSlots.stream()
                .filter(slot -> "minecraft:green_concrete".equals(itemId(slot.getItem())))
                .filter(slot -> slot.index / 9 > controlRow)
                .min(Comparator.comparingInt(slot -> slot.index))
                .orElse(null);
        if (increment == null || confirm == null) return null;

        double midpoint = (decrement.index + increment.index) / 2.0D;
        Slot selected = shopSlots.stream()
                .filter(slot -> slot.index / 9 == controlRow)
                .filter(slot -> slot.index > decrement.index && slot.index < increment.index)
                .min(Comparator.comparingDouble(slot -> Math.abs(slot.index - midpoint)))
                .orElse(null);
        if (selected == null) return null;

        return new QuantityControls(decrement, selected, increment, confirm);
    }

    private static Slot findProductSlot(Minecraft client, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (productItemId.equals(itemId(slot.getItem()))) return slot;
        }
        return null;
    }

    private static AbstractContainerMenu menu(Screen screen) {
        if (!(screen instanceof MenuAccess<?> access)) return null;
        return access.getMenu() instanceof AbstractContainerMenu menu ? menu : null;
    }

    private static boolean isQuantityMenu(String title) {
        return title.contains(QUANTITY_MENU_TITLE);
    }

    private static void click(Minecraft client, AbstractContainerMenu menu, Slot slot) {
        client.gameMode.handleContainerInput(
                menu.containerId,
                slot.index,
                0,
                ContainerInput.PICKUP,
                client.player
        );
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static boolean shouldShowCancelButton(Screen screen) {
        return isActive() && screen instanceof MenuAccess<?>;
    }

    private static int batchLimit() {
        return "minecraft:arrow".equals(productItemId) ? 25 : 64;
    }

    private static void notifySpecialLimit(Minecraft client) {
        if (specialLimitNotified || !"minecraft:arrow".equals(productItemId)) return;
        specialLimitNotified = true;
        message(client, "\u00A77[betterUC] Munition erkannt: Eink\u00E4ufe werden in 25er-Batches aufgeteilt.");
    }

    private static void fail(Minecraft client, String reason) {
        message(client, "\u00A7c[betterUC] Auto-Kauf abgebrochen: \u00A7f" + reason);
        reset();
    }

    private static void message(Minecraft client, String text) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
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

    private record QuantityControls(Slot decrement, Slot selected, Slot increment, Slot confirm) {
    }
}
