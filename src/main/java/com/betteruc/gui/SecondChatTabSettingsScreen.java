package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatTabConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SecondChatTabSettingsScreen extends Screen {

    private static final int VIEW_TOP = 70;
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private final String tabId;
    private final List<Row> rows = new ArrayList<>();
    private final List<SecondChatSettingsUi.Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int contentHeight;
    private int contentX;
    private int contentWidth;

    public SecondChatTabSettingsScreen(Screen parent, String tabId) {
        super(Component.literal("Chat-Tab Einstellungen"));
        this.parent = parent;
        this.tabId = tabId;
    }

    @Override
    protected void init() {
        rows.clear();
        sections.clear();
        SecondChatTabConfig tab = tab();
        if (tab == null) {
            onClose();
            return;
        }

        int rowWidth = Math.min(430, width - 32);
        int x = (width - rowWidth) / 2;
        int y = VIEW_TOP;
        contentX = x;
        contentWidth = rowWidth;

        y = addSection(x, y, rowWidth, "Tab", 0xFF38BDF8);
        EditBox name = new EditBox(font, x, y, rowWidth, 20, Component.literal("Tab-Name"));
        name.setMaxLength(20);
        name.setValue(tab.name);
        name.setResponder(value -> tab.name = value);
        addRow(name, y);
        y += ROW_HEIGHT + 6;

        y = addSection(x, y, rowWidth, "Darstellung", 0xFF4ADE80);
        y = addToggleRow(x, y, rowWidth, "Gleiche Nachrichten zusammenfassen",
                () -> tab.combineEqualMessages, value -> tab.combineEqualMessages = value);
        y = addToggleRow(x, y, rowWidth, "Chat-Leerungen ignorieren",
                () -> tab.antiChatClear, value -> tab.antiChatClear = value);
        y = addToggleRow(x, y, rowWidth, "Textschatten",
                () -> tab.messageShadow, value -> tab.messageShadow = value);
        y = addToggleRow(x, y, rowWidth, "Hintergrund",
                () -> tab.background, value -> tab.background = value);
        y = addCycleRow(x, y, rowWidth,
                () -> "Hintergrund-Deckkraft: " + tab.backgroundOpacity,
                () -> tab.backgroundOpacity = nextValue(
                        tab.backgroundOpacity, new int[]{0, 50, 100, 150, 200, 230}));
        y = addCycleRow(x, y, rowWidth,
                () -> "Schriftgröße: " + tab.fontScalePercent + "%",
                () -> tab.fontScalePercent = nextValue(
                        tab.fontScalePercent, new int[]{75, 90, 100, 110, 125, 150}));
        y = addCycleRow(x, y, rowWidth,
                () -> "Ausblendzeit: " + tab.fadeSeconds + " Sekunden",
                () -> tab.fadeSeconds = nextValue(
                        tab.fadeSeconds, new int[]{0, 1, 3, 5, 10, 15}));
        y = addToggleRow(x, y, rowWidth, "Zeitstempel",
                () -> tab.timestamps, value -> tab.timestamps = value);
        if (tab.timestamps) {
            Button timestampMode = Button.builder(timestampModeLabel(tab), button -> {
                        tab.timestampUseGlobalFormat = !tab.timestampUseGlobalFormat;
                        save();
                        reopen();
                    })
                    .bounds(x, y, rowWidth, 20)
                    .build();
            addRow(timestampMode, y);
            y += ROW_HEIGHT;

            if (!tab.timestampUseGlobalFormat) {
                EditBox timestampFormat = new EditBox(
                        font, x, y, rowWidth, 20, Component.literal("Zeitformat, z. B. [HH:mm:ss]"));
                timestampFormat.setMaxLength(32);
                timestampFormat.setValue(tab.timestampFormat);
                timestampFormat.setResponder(value -> tab.timestampFormat = value);
                addRow(timestampFormat, y);
                y += ROW_HEIGHT;
            }
        }

        Button limit = Button.builder(limitLabel(tab), button -> {
                    tab.messageLimit += 25;
                    if (tab.messageLimit > 500) tab.messageLimit = 25;
                    button.setMessage(limitLabel(tab));
                })
                .bounds(x, y, rowWidth, 20)
                .build();
        addRow(limit, y);
        y += ROW_HEIGHT + 6;

        y = addSection(x, y, rowWidth, "Erwähnungen", 0xFFFACC15);
        EditBox mentions = new EditBox(
                font, x, y, rowWidth, 20, Component.literal("Erwähnungsbegriffe"));
        mentions.setMaxLength(160);
        mentions.setValue(tab.mentionTerms);
        mentions.setResponder(value -> tab.mentionTerms = value);
        addRow(mentions, y);
        y += ROW_HEIGHT;

        y = addToggleRow(x, y, rowWidth, "Ton bei Erwähnung",
                () -> tab.mentionSound, value -> tab.mentionSound = value);
        Button mentionColor = Button.builder(mentionColorLabel(tab),
                        button -> openMentionColorPicker(tab))
                .bounds(x, y, rowWidth, 20)
                .build();
        addRow(mentionColor, y);
        y += ROW_HEIGHT + 6;

        y = addSection(x, y, rowWidth, "Filter & Fenster", 0xFF38BDF8);
        Button filters = Button.builder(
                        Component.literal("Filter verwalten: " + tab.filters.size()),
                        button -> openFilters(tab))
                .bounds(x, y, rowWidth, 20)
                .build();
        addRow(filters, y);
        y += ROW_HEIGHT;

        Button position = Button.builder(
                        Component.literal("Fenster positionieren & skalieren"),
                        button -> openEditor(tab))
                .bounds(x, y, rowWidth, 20)
                .build();
        addRow(position, y);
        y += ROW_HEIGHT;
        contentHeight = y - VIEW_TOP;

        addRenderableWidget(Button.builder(Component.literal("Verlauf leeren"),
                        button -> SecondChatManager.clear(tab.id))
                .bounds(16, height - 29, 110, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Tab löschen"), button -> {
                    SecondChatManager.deleteTab(tab.id);
                    onClose();
                })
                .bounds(132, height - 29, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Speichern"), button -> onClose())
                .bounds(width - 116, height - 29, 100, 20)
                .build());
        updateRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        SecondChatTabConfig tab = tab();
        String subtitle = tab == null ? "Tab nicht gefunden"
                : tab.name.isBlank()
                        ? "Filter, Verlauf und Darstellung"
                        : tab.name + " | Filter, Verlauf und Darstellung";
        SecondChatSettingsUi.renderPage(
                context, font, width, height, "Chat-Tab konfigurieren", subtitle);
        SecondChatSettingsUi.renderPanel(
                context,
                contentX - 10,
                VIEW_TOP - 7,
                contentWidth + 20,
                Math.max(1, height - VIEW_TOP - 36));
        for (SecondChatSettingsUi.Section section : sections) {
            SecondChatSettingsUi.renderSection(
                    context, font, section, scrollOffset, VIEW_TOP - 2, height - 39);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
        SecondChatSettingsUi.renderScrollbar(
                context, width - 7, VIEW_TOP, height - 40,
                contentHeight, scrollOffset, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY >= VIEW_TOP && mouseY < height - 38 && maxScroll() > 0) {
            scrollOffset = clamp(scrollOffset - (int) Math.round(verticalAmount * 24.0D), 0, maxScroll());
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        save();
        super.removed();
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

    private void openFilters(SecondChatTabConfig tab) {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new SecondChatFilterListScreen(this, tab.id));
        }
    }

    private void openEditor(SecondChatTabConfig tab) {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new SecondChatEditorScreen(this, tab.windowId));
        }
    }

    private void reopen() {
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new SecondChatTabSettingsScreen(parent, tabId));
        }
    }

    private int addToggleRow(
            int x,
            int y,
            int width,
            String label,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter
    ) {
        Button button = Button.builder(toggleLabel(label, getter.get()), clicked -> {
                    setter.accept(!getter.get());
                    clicked.setMessage(toggleLabel(label, getter.get()));
                })
                .bounds(x, y, width, 20)
                .build();
        addRow(button, y);
        return y + ROW_HEIGHT;
    }

    private int addCycleRow(
            int x,
            int y,
            int width,
            Supplier<String> label,
            Runnable action
    ) {
        Button button = Button.builder(Component.literal(label.get()), clicked -> {
                    action.run();
                    clicked.setMessage(Component.literal(label.get()));
                })
                .bounds(x, y, width, 20)
                .build();
        addRow(button, y);
        return y + ROW_HEIGHT;
    }

    private int addSection(int x, int y, int width, String label, int color) {
        sections.add(new SecondChatSettingsUi.Section(label, x, y, width, color));
        return y + 18;
    }

    private void openMentionColorPicker(SecondChatTabConfig tab) {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new ColorPickerScreen(
                    this,
                    "Erwähnungsfarbe",
                    "Farbe hervorgehobener Erwähnungen",
                    tab.mentionColor,
                    color -> tab.mentionColor = color
            ));
        }
    }

    private void addRow(AbstractWidget widget, int logicalY) {
        addRenderableWidget(widget);
        rows.add(new Row(widget, logicalY));
    }

    private void updateRows() {
        int bottom = height - 38;
        for (Row row : rows) {
            int y = row.logicalY - scrollOffset;
            row.widget.setY(y);
            row.widget.visible = y + row.widget.getHeight() > VIEW_TOP - 4 && y < bottom;
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
        SecondChatTabConfig tab = tab();
        if (tab != null) {
            int index = BetterUCConfig.INSTANCE.secondChatTabs.indexOf(tab);
            tab.sanitize(Math.max(0, index));
        }
        BetterUCConfig.sanitizeSecondChat();
        BetterUCConfig.save();
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "AN" : "AUS"));
    }

    private static Component limitLabel(SecondChatTabConfig tab) {
        return Component.literal("Nachrichtenlimit: " + tab.messageLimit);
    }

    private static Component timestampModeLabel(SecondChatTabConfig tab) {
        String value = tab.timestampUseGlobalFormat
                ? "Global (" + BetterUCConfig.INSTANCE.chatTimestampFormat + ")"
                : "Eigenes Format";
        return Component.literal("Zeitformat: " + value);
    }

    private static Component mentionColorLabel(SecondChatTabConfig tab) {
        return Component.literal(String.format(
                "Erwähnungsfarbe: #%06X", tab.mentionColor & 0x00FFFFFF));
    }

    private static int nextValue(int current, int[] values) {
        for (int value : values) {
            if (value > current) {
                return value;
            }
        }
        return values[0];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Row(AbstractWidget widget, int logicalY) {
    }
}
