package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatTabConfig;
import com.betteruc.config.SecondChatWindowConfig;
import com.betteruc.hud.SecondChatHud;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SecondChatOverlay {

    private static final int TAB_HEIGHT = 17;
    private static final int MENU_WIDTH = 146;
    private static final int MENU_ITEM_HEIGHT = 19;
    private static final int BACKGROUND = 0xE6121821;
    private static final int BACKGROUND_HOVER = 0xF0222D3B;
    private static final int BORDER = 0xFF334155;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int DRAG_THRESHOLD = 4;
    private static String openMenuWindowId;
    private static String openMenuTabId;
    private static int openMenuX;
    private static int openMenuY;
    private static TabDragState tabDrag;
    private static List<TabHitbox> tabHitboxes = List.of();
    private static List<MenuButtonHitbox> menuButtons = List.of();
    private static List<MenuItemHitbox> menuItems = List.of();

    private SecondChatOverlay() {
    }

    public static void render(
            Screen chatScreen,
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY
    ) {
        Minecraft client = Minecraft.getInstance();
        List<TabHitbox> rebuiltTabs = new ArrayList<>();
        List<MenuButtonHitbox> rebuiltMenus = new ArrayList<>();
        List<MenuItemHitbox> rebuiltItems = new ArrayList<>();

        renderWindowStrip(
                client,
                context,
                SecondChatManager.PRIMARY_WINDOW_ID,
                SecondChatHud.configuredBounds(SecondChatManager.PRIMARY_WINDOW_ID),
                true,
                mouseX,
                mouseY,
                rebuiltTabs,
                rebuiltMenus,
                rebuiltItems
        );
        if (BetterUCConfig.INSTANCE.secondChatEnabled) {
            for (SecondChatWindowConfig window : SecondChatManager.windows()) {
                renderWindowStrip(
                        client,
                        context,
                        window.id,
                        SecondChatHud.configuredBounds(window.id),
                        false,
                        mouseX,
                        mouseY,
                        rebuiltTabs,
                        rebuiltMenus,
                        rebuiltItems
                );
            }
        }

        tabHitboxes = List.copyOf(rebuiltTabs);
        menuButtons = List.copyOf(rebuiltMenus);
        menuItems = List.copyOf(rebuiltItems);
        updateTabDrag(mouseX, mouseY, ClientCompat.isLeftMouseDown(client));
        renderTabDragFeedback(client, context, mouseX, mouseY);
        SecondChatHud.renderTooltip(context, mouseX, mouseY);
    }

    public static boolean mouseClicked(Screen chatScreen, double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) {
            return false;
        }

        if (button == 1) {
            tabDrag = null;
            for (TabHitbox hitbox : tabHitboxes) {
                if (hitbox.box.contains(mouseX, mouseY)) {
                    openMenuWindowId = hitbox.windowId;
                    openMenuTabId = hitbox.tabId;
                    openMenuX = hitbox.box.x;
                    openMenuY = hitbox.box.y + hitbox.box.height + 2;
                    return true;
                }
            }
            closeMenu();
            return false;
        }

        for (MenuButtonHitbox hitbox : menuButtons) {
            if (hitbox.box.contains(mouseX, mouseY)) {
                tabDrag = null;
                boolean close = hitbox.windowId.equals(openMenuWindowId) && openMenuTabId == null;
                if (close) {
                    closeMenu();
                } else {
                    openMenuWindowId = hitbox.windowId;
                    openMenuTabId = null;
                }
                return true;
            }
        }

        if (openMenuWindowId != null) {
            for (MenuItemHitbox hitbox : menuItems) {
                if (hitbox.box.contains(mouseX, mouseY)) {
                    tabDrag = null;
                    closeMenu();
                    handleMenuAction(chatScreen, hitbox.windowId, hitbox.tabId, hitbox.action);
                    return true;
                }
            }
        }

        if (SecondChatHud.mouseClicked(mouseX, mouseY)) {
            tabDrag = null;
            closeMenu();
            return true;
        }

        for (TabHitbox hitbox : tabHitboxes) {
            if (hitbox.box.contains(mouseX, mouseY)) {
                int unread = SecondChatManager.unreadCount(hitbox.tabId);
                long firstUnreadAt = SecondChatManager.firstUnreadAt(hitbox.tabId);
                SecondChatManager.selectTab(hitbox.windowId, hitbox.tabId);
                if (unread > 0) {
                    SecondChatHud.jumpToFirstUnread(hitbox.windowId, hitbox.tabId, firstUnreadAt);
                }
                tabDrag = SecondChatManager.MAIN_TAB_ID.equals(hitbox.tabId)
                        ? null
                        : new TabDragState(hitbox.tabId, mouseX, mouseY);
                closeMenu();
                return true;
            }
        }

        tabDrag = null;
        closeMenu();
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || tabDrag == null) {
            return false;
        }
        if (!tabDrag.dragging) {
            double deltaX = mouseX - tabDrag.startX;
            double deltaY = mouseY - tabDrag.startY;
            tabDrag.dragging = deltaX * deltaX + deltaY * deltaY
                    >= DRAG_THRESHOLD * DRAG_THRESHOLD;
        }
        return true;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || tabDrag == null) {
            return false;
        }
        TabDragState released = tabDrag;
        tabDrag = null;
        if (!released.dragging) {
            return true;
        }

        TabHitbox target = dropTargetAt(mouseX, mouseY);
        if (target == null || released.tabId.equals(target.tabId)) {
            return true;
        }
        SecondChatTabConfig moving = SecondChatManager.findTab(released.tabId);
        if (moving == null) {
            return true;
        }
        if (!moving.windowId.equals(target.windowId)) {
            SecondChatManager.moveTabToWindow(moving.id, target.windowId);
        }
        if (!SecondChatManager.MAIN_TAB_ID.equals(target.tabId)) {
            SecondChatManager.reorderTab(moving.id, target.tabId);
        }
        return true;
    }

    private static void updateTabDrag(double mouseX, double mouseY, boolean leftMouseDown) {
        if (tabDrag == null) {
            return;
        }
        if (leftMouseDown) {
            mouseDragged(mouseX, mouseY, 0);
        } else {
            mouseReleased(mouseX, mouseY, 0);
        }
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (verticalAmount == 0.0D) return false;
        SecondChatHud.Bounds primary = SecondChatHud.configuredBounds(SecondChatManager.PRIMARY_WINDOW_ID);
        if (primary.contains(mouseX, mouseY)
                && SecondChatManager.activeTab(SecondChatManager.PRIMARY_WINDOW_ID) != null) {
            return SecondChatHud.scrollWindow(SecondChatManager.PRIMARY_WINDOW_ID, verticalAmount);
        }
        for (SecondChatWindowConfig window : SecondChatManager.windows()) {
            if (SecondChatHud.configuredBounds(window.id).contains(mouseX, mouseY)) {
                return SecondChatHud.scrollWindow(window.id, verticalAmount);
            }
        }
        return false;
    }

    private static void renderWindowStrip(
            Minecraft client,
            GuiGraphicsExtractor context,
            String windowId,
            SecondChatHud.Bounds panel,
            boolean includeMain,
            int mouseX,
            int mouseY,
            List<TabHitbox> tabTargets,
            List<MenuButtonHitbox> menuTargets,
            List<MenuItemHitbox> itemTargets
    ) {
        List<TabSpec> tabs = new ArrayList<>();
        if (includeMain) {
            tabs.add(new TabSpec(SecondChatManager.MAIN_TAB_ID, "Chat"));
        }
        for (SecondChatTabConfig tab : SecondChatManager.tabsForWindow(windowId)) {
            tabs.add(new TabSpec(tab.id, tab.name));
        }
        if (tabs.isEmpty()) {
            return;
        }

        int stripX = panel.x();
        int stripY = Math.max(4, panel.y() - TAB_HEIGHT - 2);
        int stripWidth = panel.panelWidth();
        int menuX = stripX + stripWidth - TAB_HEIGHT;
        Box menuButton = new Box(menuX, stripY, TAB_HEIGHT, TAB_HEIGHT);
        menuTargets.add(new MenuButtonHitbox(windowId, menuButton));

        int availableWidth = Math.max(40, stripWidth - TAB_HEIGHT - 2);
        int naturalWidth = 0;
        for (TabSpec tab : tabs) {
            naturalWidth += Math.max(38, client.font.width(tab.label) + 16);
        }
        double scale = naturalWidth > availableWidth ? availableWidth / (double) naturalWidth : 1.0D;
        int tabX = stripX;
        String activeId = includeMain
                ? BetterUCConfig.INSTANCE.secondChatActiveTabId
                : activeId(windowId);
        for (int i = 0; i < tabs.size(); i++) {
            TabSpec tab = tabs.get(i);
            int unread = SecondChatManager.unreadCount(tab.id);
            String displayLabel = unread > 0 ? tab.label + " (" + Math.min(99, unread) + ")" : tab.label;
            int natural = Math.max(38, client.font.width(displayLabel) + 16);
            int tabWidth = Math.max(28, (int) Math.floor(natural * scale));
            if (i == tabs.size() - 1) {
                tabWidth = Math.max(28, stripX + availableWidth - tabX);
            }
            boolean active = tab.id.equals(activeId);
            boolean hovered = contains(tabX, stripY, tabWidth, TAB_HEIGHT, mouseX, mouseY);
            boolean attention = SecondChatManager.needsAttention(tab.id);
            int fill = active ? 0xF0202B38
                    : attention ? 0xE07C2D12
                    : hovered ? BACKGROUND_HOVER : BACKGROUND;
            context.fill(tabX, stripY, tabX + tabWidth, stripY + TAB_HEIGHT, fill);
            drawTabOutline(context, tabX, stripY, tabWidth, TAB_HEIGHT, active
                    ? forceOpaque(BetterUCConfig.INSTANCE.secondChatAccentColor)
                    : BORDER);
            String label = fitText(client, displayLabel, tabWidth - 10);
            int labelX = tabX + Math.max(5, (tabWidth - client.font.width(label)) / 2);
            context.text(client.font, label, labelX, stripY + 5,
                    active ? TEXT : attention ? 0xFFFFD54A : unread > 0 ? 0xFF38BDF8 : MUTED);
            tabTargets.add(new TabHitbox(windowId, tab.id, new Box(tabX, stripY, tabWidth, TAB_HEIGHT)));
            tabX += tabWidth;
        }

        boolean menuOpen = windowId.equals(openMenuWindowId);
        boolean menuHovered = menuButton.contains(mouseX, mouseY);
        context.fill(menuX, stripY, menuX + TAB_HEIGHT, stripY + TAB_HEIGHT,
                menuHovered || menuOpen ? BACKGROUND_HOVER : BACKGROUND);
        drawOutline(context, menuX, stripY, TAB_HEIGHT, TAB_HEIGHT, menuOpen
                ? forceOpaque(BetterUCConfig.INSTANCE.secondChatAccentColor)
                : BORDER);
        for (int line = 0; line < 3; line++) {
            int lineY = stripY + 5 + line * 3;
            context.fill(menuX + 5, lineY, menuX + 12, lineY + 1, TEXT);
        }

        if (menuOpen) {
            String targetTabId = openMenuTabId;
            int targetX = targetTabId == null
                    ? menuX + TAB_HEIGHT - MENU_WIDTH
                    : openMenuX;
            int targetY = targetTabId == null
                    ? stripY + TAB_HEIGHT + 2
                    : openMenuY;
            renderMenu(
                    client,
                    context,
                    windowId,
                    targetTabId,
                    targetX,
                    targetY,
                    mouseX,
                    mouseY,
                    itemTargets
            );
        }
    }

    private static void renderTabDragFeedback(
            Minecraft client,
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY
    ) {
        if (tabDrag == null || !tabDrag.dragging) {
            return;
        }
        TabHitbox target = dropTargetAt(mouseX, mouseY);
        if (target != null && !tabDrag.tabId.equals(target.tabId)) {
            drawOutline(
                    context,
                    target.box.x,
                    target.box.y,
                    target.box.width,
                    target.box.height,
                    forceOpaque(BetterUCConfig.INSTANCE.secondChatAccentColor)
            );
        }

        SecondChatTabConfig tab = SecondChatManager.findTab(tabDrag.tabId);
        if (tab == null) {
            return;
        }
        int width = Math.max(48, Math.min(154, client.font.width(tab.name) + 18));
        int x = clamp(mouseX + 10, 2,
                Math.max(2, ClientCompat.scaledWindowWidth(client, 854) - width - 2));
        int y = clamp(mouseY + 10, 2,
                Math.max(2, ClientCompat.scaledWindowHeight(client, 480) - TAB_HEIGHT - 2));
        context.fill(x, y, x + width, y + TAB_HEIGHT, BACKGROUND_HOVER);
        drawOutline(context, x, y, width, TAB_HEIGHT,
                forceOpaque(BetterUCConfig.INSTANCE.secondChatAccentColor));
        String label = fitText(client, tab.name, width - 10);
        context.text(client.font, label, x + 5, y + 5, TEXT);
    }

    private static TabHitbox dropTargetAt(double mouseX, double mouseY) {
        for (int i = tabHitboxes.size() - 1; i >= 0; i--) {
            TabHitbox hitbox = tabHitboxes.get(i);
            if (hitbox.box.contains(mouseX, mouseY)) {
                return hitbox;
            }
        }
        return null;
    }

    private static void renderMenu(
            Minecraft client,
            GuiGraphicsExtractor context,
            String windowId,
            String tabId,
            int x,
            int y,
            int mouseX,
            int mouseY,
            List<MenuItemHitbox> targets
    ) {
        boolean customTab = tabId != null && !SecondChatManager.MAIN_TAB_ID.equals(tabId);
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption("new_tab", "Neuer Tab", "+", false));
        options.add(new MenuOption("new_window", "Neues Fenster", "+", false));
        if (customTab) {
            options.add(new MenuOption("duplicate", "Duplizieren", "\u29C9", false));
            if (!SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId)) {
                options.add(new MenuOption("to_primary", "Zum Hauptchat", "\u2199", false));
            }
            List<SecondChatTabConfig> windowTabs = SecondChatManager.tabsForWindow(windowId);
            int tabIndex = -1;
            for (int i = 0; i < windowTabs.size(); i++) {
                if (windowTabs.get(i).id.equals(tabId)) {
                    tabIndex = i;
                    break;
                }
            }
            if (tabIndex > 0) {
                options.add(new MenuOption("left", "Nach links", "\u2190", false));
            }
            if (tabIndex >= 0 && tabIndex < windowTabs.size() - 1) {
                options.add(new MenuOption("right", "Nach rechts", "\u2192", false));
            }
        }
        options.add(new MenuOption("settings", "Einstellungen", "\u2699", false));
        if (customTab) {
            options.add(new MenuOption("delete", "Löschen", "\u2715", true));
        }
        int menuHeight = options.size() * MENU_ITEM_HEIGHT;
        x = clamp(x, 2, Math.max(2, ClientCompat.scaledWindowWidth(client, 854) - MENU_WIDTH - 2));
        y = clamp(y, 2, Math.max(2, ClientCompat.scaledWindowHeight(client, 480) - menuHeight - 2));
        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            int itemY = y + i * MENU_ITEM_HEIGHT;
            Box box = new Box(x, itemY, MENU_WIDTH, MENU_ITEM_HEIGHT);
            targets.add(new MenuItemHitbox(windowId, tabId, option.action, box));
            boolean hovered = box.contains(mouseX, mouseY);
            context.fill(x, itemY, x + MENU_WIDTH, itemY + MENU_ITEM_HEIGHT,
                    hovered ? BACKGROUND_HOVER : BACKGROUND);
            drawOutline(context, x, itemY, MENU_WIDTH, MENU_ITEM_HEIGHT, BORDER);
            context.text(client.font, option.icon + " " + option.label, x + 8, itemY + 6,
                    option.danger
                            ? (hovered ? 0xFFFF7B86 : 0xFFFB7185)
                            : (hovered ? TEXT : MUTED));
        }
    }

    private static void handleMenuAction(Screen chatScreen, String windowId, String tabId, String action) {
        Minecraft client = Minecraft.getInstance();
        if ("new_tab".equals(action)) {
            SecondChatTabConfig created = SecondChatManager.createTab(windowId);
            if (created != null) {
                ClientCompat.setScreen(client, new SecondChatTabSettingsScreen(chatScreen, created.id));
            }
            return;
        }
        if ("new_window".equals(action)) {
            SecondChatTabConfig created = SecondChatManager.createWindow();
            if (created != null) {
                ClientCompat.setScreen(client, new SecondChatTabSettingsScreen(chatScreen, created.id));
            }
            return;
        }
        if ("duplicate".equals(action)) {
            SecondChatTabConfig created = SecondChatManager.duplicateTab(tabId);
            if (created != null) {
                ClientCompat.setScreen(client, new SecondChatTabSettingsScreen(chatScreen, created.id));
            }
            return;
        }
        if ("to_primary".equals(action)) {
            SecondChatManager.moveTabToWindow(tabId, SecondChatManager.PRIMARY_WINDOW_ID);
            return;
        }
        if ("left".equals(action)) {
            SecondChatManager.moveTabBy(tabId, -1);
            return;
        }
        if ("right".equals(action)) {
            SecondChatManager.moveTabBy(tabId, 1);
            return;
        }
        if ("delete".equals(action)) {
            if (tabId != null && !SecondChatManager.MAIN_TAB_ID.equals(tabId)) {
                SecondChatManager.deleteTab(tabId);
            }
            return;
        }

        if (tabId != null && !SecondChatManager.MAIN_TAB_ID.equals(tabId)) {
            SecondChatTabConfig selected = SecondChatManager.findTab(tabId);
            if (selected != null) {
                ClientCompat.setScreen(client, new SecondChatTabSettingsScreen(chatScreen, selected.id));
            }
            return;
        }
        ClientCompat.setScreen(client, new SecondChatGlobalSettingsScreen(chatScreen, windowId));
    }

    private static String activeId(String windowId) {
        SecondChatWindowConfig window = SecondChatManager.findWindow(windowId);
        return window == null ? "" : window.activeTabId;
    }

    private static String fitText(Minecraft client, String text, int maxWidth) {
        if (client.font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        String candidate = text;
        while (!candidate.isEmpty() && client.font.width(candidate + suffix) > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + suffix;
    }

    private static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void closeMenu() {
        openMenuWindowId = null;
        openMenuTabId = null;
    }

    private static int forceOpaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static void drawOutline(
            GuiGraphicsExtractor context,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void drawTabOutline(
            GuiGraphicsExtractor context,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
    }

    private record TabSpec(String id, String label) {
    }

    private record TabHitbox(String windowId, String tabId, Box box) {
    }

    private record MenuButtonHitbox(String windowId, Box box) {
    }

    private record MenuOption(String action, String label, String icon, boolean danger) {
    }

    private record MenuItemHitbox(String windowId, String tabId, String action, Box box) {
    }

    private static final class TabDragState {
        private final String tabId;
        private final double startX;
        private final double startY;
        private boolean dragging;

        private TabDragState(String tabId, double startX, double startY) {
            this.tabId = tabId;
            this.startX = startX;
            this.startY = startY;
        }
    }

    private record Box(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return SecondChatOverlay.contains(x, y, width, height, mouseX, mouseY);
        }
    }
}
