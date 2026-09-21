package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.hud.BankBalanceHud;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class AutoBuyClient {
    public static final int MAX_REQUEST_AMOUNT = 4096;

    private static final String QUANTITY_MENU_TITLE = "menge waehlen";
    private static final String PAYMENT_MENU_TITLE = "zahlungsmethode";
    private static final String PURCHASE_SUCCESS_MESSAGE = "verkaeufer vielen dank fuer ihren einkauf";
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(?:^|\\s)preis\\s*:?\\s*([0-9][0-9.]*)\\s*\\$");
    private static final long CLICK_INTERVAL_MS = 140L;
    private static final long REOPEN_DELAY_MS = 650L;
    private static final long USER_SELECTION_TIMEOUT_MS = 120_000L;
    private static final long AUTOMATION_TIMEOUT_MS = 25_000L;

    private enum Phase {
        IDLE,
        WAITING_FOR_PRODUCT,
        SETTING_QUANTITY,
        WAITING_FOR_PAYMENT,
        WAITING_FOR_SUCCESS,
        REOPENING_SHOP,
        WAITING_FOR_SHOP,
        WAITING_FOR_QUANTITY
    }

    enum PaymentMethod { CASH, CARD }

    private static Phase phase = Phase.IDLE;
    private static int requestedAmount;
    private static int remainingAmount;
    private static int pendingBatch;
    private static int pendingPurchasePrice;
    private static PaymentMethod pendingPaymentMethod;
    private static PaymentMethod processPaymentMethod;
    private static int incrementClicksSent;
    private static int lastContainerId = -1;
    private static String productItemId = "";
    private static String productNameKey = "";
    private static int productShopSlotIndex = -1;
    private static String shopTitleKey = "";
    private static boolean specialLimitNotified;
    private static boolean paymentChoiceNotified;
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
        pendingPurchasePrice = 0;
        pendingPaymentMethod = null;
        processPaymentMethod = null;
        incrementClicksSent = 0;
        lastContainerId = -1;
        productItemId = "";
        productNameKey = "";
        productShopSlotIndex = -1;
        shopTitleKey = "";
        specialLimitNotified = false;
        paymentChoiceNotified = false;
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
        if (phase != Phase.WAITING_FOR_PAYMENT && phase != Phase.WAITING_FOR_SUCCESS) return;
        if (!key(raw).contains(PURCHASE_SUCCESS_MESSAGE)) return;

        if (pendingPaymentMethod == PaymentMethod.CARD && pendingPurchasePrice > 0) {
            boolean updated = BankBalanceHud.applyPurchaseDebit(pendingPurchasePrice,
                    "auto-buy-card:" + System.nanoTime());
            if (!updated) {
                message(client, "\u00A7e[betterUC] Kartenzahlung erkannt, aber der Bankstand ist noch unbekannt. "
                        + "\u00A77Bitte einmal den Bankstand abrufen.");
            }
        }

        remainingAmount = Math.max(0, remainingAmount - pendingBatch);
        int completed = requestedAmount - remainingAmount;
        pendingBatch = 0;
        pendingPurchasePrice = 0;
        pendingPaymentMethod = null;

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

        if (phase == Phase.WAITING_FOR_PAYMENT) {
            if (menu != null && isPaymentMenu(title)) {
                processPaymentMenu(client, menu, now);
            }
            return;
        }

        if (phase == Phase.SETTING_QUANTITY) {
            if (menu == null || !isQuantityMenu(title)) return;
            processQuantityMenu(client, menu, now);
        }
    }

    public static void rememberProductSelection(Minecraft client, Screen screen, Slot slot) {
        if (phase != Phase.WAITING_FOR_PRODUCT || client == null || client.player == null
                || screen == null || slot == null || !slot.hasItem()) {
            return;
        }
        if (slot.container == client.player.getInventory()) return;

        String selectedItemId = itemId(slot.getItem());
        if (selectedItemId.isEmpty()) return;

        productItemId = selectedItemId;
        productNameKey = key(slot.getItem().getHoverName().getString());
        productShopSlotIndex = slot.index;
        shopTitleKey = key(screen.getTitle().getString());
    }

    public static void rememberPaymentSelection(Minecraft client, Screen screen, Slot slot) {
        if (phase != Phase.WAITING_FOR_PAYMENT || client == null || client.player == null
                || screen == null || slot == null || !slot.hasItem()
                || !isPaymentMenu(key(screen.getTitle().getString()))) return;
        if (slot.container == client.player.getInventory()) return;
        PaymentMethod method = paymentMethod(slot.getItem());
        if (method == null) return;
        selectPayment(client, method, paymentPrice(slot.getItem()));
    }

    public static void reset() {
        phase = Phase.IDLE;
        requestedAmount = 0;
        remainingAmount = 0;
        pendingBatch = 0;
        pendingPurchasePrice = 0;
        pendingPaymentMethod = null;
        processPaymentMethod = null;
        incrementClicksSent = 0;
        lastContainerId = -1;
        productItemId = "";
        productNameKey = "";
        productShopSlotIndex = -1;
        shopTitleKey = "";
        specialLimitNotified = false;
        paymentChoiceNotified = false;
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
            productNameKey = key(controls.selected.getItem().getHoverName().getString());
        } else if (!productItemId.equals(selectedId)) {
            fail(client, "Im Shop wurde ein anderes Produkt ge\u00F6ffnet.");
            return;
        }
        notifySpecialLimit(client);

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
        phase = Phase.WAITING_FOR_PAYMENT;
        nextActionAtMs = 0L;
        phaseDeadlineMs = now + AUTOMATION_TIMEOUT_MS;
    }

    private static void processPaymentMenu(Minecraft client, AbstractContainerMenu menu, long now) {
        PaymentMethod preferred = processPaymentMethod;
        if (preferred == null) {
            if (!paymentChoiceNotified) {
                paymentChoiceNotified = true;
                message(client, "\u00A7b[betterUC] Wähle Bar- oder Kartenzahlung. "
                        + "\u00A77Die Auswahl gilt für alle Batches dieses /abuy.");
                phaseDeadlineMs = now + USER_SELECTION_TIMEOUT_MS;
            }
            return;
        }
        Slot payment = findPaymentSlot(client, menu, preferred);
        if (payment == null || now < nextActionAtMs) return;
        selectPayment(client, preferred, paymentPrice(payment.getItem()));
        click(client, menu, payment);
    }

    private static void selectPayment(Minecraft client, PaymentMethod method, int price) {
        pendingPaymentMethod = method;
        pendingPurchasePrice = price;
        processPaymentMethod = method;
        phase = Phase.WAITING_FOR_SUCCESS;
        phaseDeadlineMs = System.currentTimeMillis() + AUTOMATION_TIMEOUT_MS;
        if (method == PaymentMethod.CARD && price <= 0) {
            message(client, "\u00A7e[betterUC] Kartenpreis konnte nicht gelesen werden; "
                    + "das Bank-HUD wird für diesen Kauf nicht automatisch angepasst.");
        }
    }

    private static Slot findPaymentSlot(Minecraft client, AbstractContainerMenu menu, PaymentMethod method) {
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (paymentMethod(slot.getItem()) == method) return slot;
        }
        return null;
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
        List<Slot> matchingSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (!productItemId.equals(itemId(slot.getItem()))) continue;

            if (slot.index == productShopSlotIndex) return slot;
            matchingSlots.add(slot);
        }

        if (!productNameKey.isEmpty()) {
            List<Slot> namedMatches = matchingSlots.stream()
                    .filter(slot -> productNameKey.equals(key(slot.getItem().getHoverName().getString())))
                    .toList();
            if (namedMatches.size() == 1) return namedMatches.get(0);
        }

        return matchingSlots.size() == 1 ? matchingSlots.get(0) : null;
    }

    private static AbstractContainerMenu menu(Screen screen) {
        if (!(screen instanceof MenuAccess<?> access)) return null;
        return access.getMenu() instanceof AbstractContainerMenu menu ? menu : null;
    }

    private static boolean isQuantityMenu(String title) {
        return title.contains(QUANTITY_MENU_TITLE);
    }

    private static boolean isPaymentMenu(String title) { return title.contains(PAYMENT_MENU_TITLE); }

    static PaymentMethod paymentMethod(String itemId, String hoverName) {
        String name = key(hoverName);
        if ("minecraft:gold_ingot".equals(itemId) && name.contains("bar bezahlen")) return PaymentMethod.CASH;
        if ("minecraft:paper".equals(itemId) && name.contains("mit karte zahlen")) return PaymentMethod.CARD;
        return null;
    }

    private static PaymentMethod paymentMethod(ItemStack stack) {
        return paymentMethod(itemId(stack), stack == null ? "" : stack.getHoverName().getString());
    }

    static int parsePaymentPrice(Iterable<String> lines) {
        if (lines == null) return 0;
        for (String line : lines) {
            Matcher matcher = PRICE_PATTERN.matcher(line == null ? "" : line.replaceAll("§.", ""));
            if (!matcher.find()) continue;
            String digits = matcher.group(1).replace(".", "");
            try {
                long value = Long.parseLong(digits);
                if (value > 0 && value <= Integer.MAX_VALUE) return (int) value;
            } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private static int paymentPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0;
        return parsePaymentPrice(lore.lines().stream().map(Component::getString).toList());
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
