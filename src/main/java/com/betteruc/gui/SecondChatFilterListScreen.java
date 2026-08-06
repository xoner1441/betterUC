package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatFilterConfig;
import com.betteruc.config.SecondChatTabConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SecondChatFilterListScreen extends Screen {

    private static final int VIEW_TOP = 92;
    private static final int ROW_HEIGHT = 42;
    private final Screen parent;
    private final String tabId;
    private final List<WidgetRow> widgetRows = new ArrayList<>();
    private int scrollOffset;
    private int contentHeight;
    private int contentX;
    private int contentWidth;

    public SecondChatFilterListScreen(Screen parent, String tabId) {
        super(Component.literal("Chatfilter"));
        this.parent = parent;
        this.tabId = tabId;
    }

    @Override
    protected void init() {
        widgetRows.clear();
        SecondChatTabConfig tab = tab();
        if (tab == null) {
            onClose();
            return;
        }

        int rowWidth = Math.min(620, width - 32);
        int x = (width - rowWidth) / 2;
        int y = VIEW_TOP;
        contentX = x;
        contentWidth = rowWidth;
        for (SecondChatFilterConfig filter : tab.filters) {
            int buttonY = y + 10;
            Button enabled = Button.builder(enabledLabel(filter), button -> {
                        filter.enabled = !filter.enabled;
                        button.setMessage(enabledLabel(filter));
                        save();
                    })
                    .bounds(x + rowWidth - 188, buttonY, 72, 20)
                    .build();
            addRow(enabled, y);

            Button edit = Button.builder(Component.literal("Bearbeiten"),
                            button -> openEditor(filter, false))
                    .bounds(x + rowWidth - 110, buttonY, 80, 20)
                    .build();
            addRow(edit, y);

            Button delete = Button.builder(Component.literal("X"), button -> deleteFilter(filter.id))
                    .bounds(x + rowWidth - 24, buttonY, 24, 20)
                    .build();
            addRow(delete, y);
            y += ROW_HEIGHT + 4;
        }
        contentHeight = Math.max(0, y - VIEW_TOP);

        Button add = Button.builder(Component.literal("+ Filter"), button -> addFilter())
                .bounds(width - 96, 16, 80, 20)
                .build();
        add.active = tab.filters.size() < 24;
        addRenderableWidget(add);
        Button presets = Button.builder(Component.literal("Vorlagen"), button -> openPresets())
                .bounds(width - 186, 16, 84, 20)
                .build();
        presets.active = tab.filters.size() < 24;
        addRenderableWidget(presets);
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> onClose())
                .bounds(16, height - 29, 90, 20)
                .build());
        updateRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        SecondChatTabConfig tab = tab();
        String subtitle = tab == null
                ? "Tab nicht gefunden"
                : tab.name.isBlank()
                        ? "Nachrichten erkennen, hervorheben oder umleiten"
                        : tab.name + " | Nachrichten erkennen, hervorheben oder umleiten";
        SecondChatSettingsUi.renderPage(context, font, width, height, "Chatfilter", subtitle);
        SecondChatSettingsUi.renderPanel(
                context,
                contentX - 10,
                VIEW_TOP - 8,
                contentWidth + 20,
                Math.max(1, height - VIEW_TOP - 35));

        if (tab != null) {
            int activeCount = 0;
            for (SecondChatFilterConfig filter : tab.filters) {
                if (filter.enabled) {
                    activeCount++;
                }
            }
            SecondChatSettingsUi.renderSection(
                    context,
                    font,
                    new SecondChatSettingsUi.Section(
                            "Filterregeln", contentX, 64, contentWidth, 0xFF38BDF8),
                    0,
                    52,
                    VIEW_TOP);
            String status = activeCount + " aktiv | " + tab.filters.size() + " gesamt";
            context.text(
                    font,
                    Component.literal(status),
                    contentX + contentWidth - font.width(status),
                    66,
                    SecondChatSettingsUi.TEXT_MUTED);
        }

        if (tab != null && tab.filters.isEmpty()) {
            context.centeredText(font, Component.literal("Noch keine Filter angelegt."),
                    width / 2, VIEW_TOP + 26, SecondChatSettingsUi.TEXT_MUTED);
            context.centeredText(font, Component.literal("Mit + Filter erstellst du die erste Regel."),
                    width / 2, VIEW_TOP + 42, SecondChatSettingsUi.TEXT_MUTED);
        } else if (tab != null) {
            int bottom = height - 38;
            for (int i = 0; i < tab.filters.size(); i++) {
                int y = VIEW_TOP + i * (ROW_HEIGHT + 4) - scrollOffset;
                if (y + ROW_HEIGHT < VIEW_TOP || y >= bottom) {
                    continue;
                }
                SecondChatFilterConfig filter = tab.filters.get(i);
                context.fill(
                        contentX,
                        y,
                        contentX + contentWidth,
                        Math.min(bottom, y + ROW_HEIGHT),
                        SecondChatSettingsUi.PANEL_BACKGROUND);
                context.fill(
                        contentX,
                        y,
                        contentX + 3,
                        Math.min(bottom, y + ROW_HEIGHT),
                        filter.enabled ? SecondChatSettingsUi.ACCENT : 0xFF475569);
                context.text(
                        font,
                        Component.literal(filter.name),
                        contentX + 12,
                        y + 8,
                        SecondChatSettingsUi.TEXT_PRIMARY);
                context.text(
                        font,
                        Component.literal(summary(filter)),
                        contentX + 12,
                        y + 23,
                        SecondChatSettingsUi.TEXT_MUTED);
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
        SecondChatSettingsUi.renderScrollbar(
                context,
                width - 7,
                VIEW_TOP,
                height - 40,
                contentHeight,
                scrollOffset,
                maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= VIEW_TOP && mouseY < height - 38 && maxScroll() > 0) {
            scrollOffset = clamp(scrollOffset - (int) Math.round(verticalAmount * 28.0D), 0, maxScroll());
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addFilter() {
        SecondChatTabConfig tab = tab();
        if (tab == null || tab.filters.size() >= 24) {
            return;
        }
        SecondChatFilterConfig filter = new SecondChatFilterConfig();
        filter.sanitize(tab.filters.size());
        tab.filters.add(filter);
        save();
        openEditor(filter, true);
    }

    private void deleteFilter(String filterId) {
        SecondChatTabConfig tab = tab();
        if (tab == null) {
            return;
        }
        tab.filters.removeIf(filter -> filterId.equals(filter.id));
        save();
        scrollOffset = Math.min(scrollOffset, maxScroll());
        clearWidgets();
        init();
    }

    private void openEditor(SecondChatFilterConfig filter, boolean newFilter) {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft,
                    new SecondChatFilterEditScreen(this, tabId, filter.id, newFilter));
        }
    }

    private void openPresets() {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(
                    minecraft, new SecondChatPresetScreen(this, tabId));
        }
    }

    private void addRow(AbstractWidget widget, int logicalY) {
        addRenderableWidget(widget);
        widgetRows.add(new WidgetRow(widget, logicalY));
    }

    private void updateRows() {
        int bottom = height - 38;
        for (WidgetRow row : widgetRows) {
            int y = row.logicalY - scrollOffset + 10;
            row.widget.setY(y);
            row.widget.visible = y + row.widget.getHeight() > VIEW_TOP && y < bottom;
            row.widget.active = row.widget.visible;
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - Math.max(1, height - VIEW_TOP - 42));
    }

    private SecondChatTabConfig tab() {
        return SecondChatManager.findTab(tabId);
    }

    private void save() {
        BetterUCConfig.sanitizeSecondChat();
        BetterUCConfig.save();
    }

    private static Component enabledLabel(SecondChatFilterConfig filter) {
        return Component.literal(filter.enabled ? "AN" : "AUS");
    }

    private static String summary(SecondChatFilterConfig filter) {
        String source = matcherLabel(filter.matcher);
        if ("custom".equals(filter.matcher) && !filter.includeText.isBlank()) {
            source = "\"" + shorten(filter.includeText, 26) + "\"";
        }
        String comparison = "starts".equals(filter.matchType) ? "beginnt mit" : "enthält";
        return source + " | " + comparison + " | " + SecondChatManager.modeLabel(filter.mode);
    }

    static String matcherLabel(String matcher) {
        return switch (matcher) {
            case "hq" -> "WPS / HQ";
            case "payday" -> "PayDay-Block";
            case "reinf" -> "Reinforcements";
            case "private" -> "Privatnachrichten";
            case "server" -> "Server-Infos";
            case "betteruc" -> "betterUC";
            case "ownname" -> "Eigener Name";
            default -> "Eigener Text";
        };
    }

    private static String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record WidgetRow(AbstractWidget widget, int logicalY) {
    }
}
