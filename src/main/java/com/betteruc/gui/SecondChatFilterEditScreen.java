package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatFilterConfig;
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

public final class SecondChatFilterEditScreen extends Screen {

    private static final int VIEW_TOP = 70;
    private static final int ROW_HEIGHT = 24;
    private static final String[] MATCHERS = {
            "custom", "hq", "payday", "reinf", "private", "server", "betteruc", "ownname"
    };
    private final Screen parent;
    private final String tabId;
    private final String filterId;
    private final boolean newFilter;
    private final SecondChatFilterConfig draft;
    private final List<WidgetRow> widgetRows = new ArrayList<>();
    private final List<TextRow> textRows = new ArrayList<>();
    private final List<SecondChatSettingsUi.Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int contentHeight;
    private int contentX;
    private int contentWidth;
    private boolean saved;
    private EditBox testMessage;
    private Button testButton;

    public SecondChatFilterEditScreen(Screen parent, String tabId, String filterId, boolean newFilter) {
        super(Component.literal("Chatfilter bearbeiten"));
        this.parent = parent;
        this.tabId = tabId;
        this.filterId = filterId;
        this.newFilter = newFilter;
        SecondChatFilterConfig source = filter();
        this.draft = source == null ? new SecondChatFilterConfig() : source.copy();
    }

    @Override
    protected void init() {
        widgetRows.clear();
        textRows.clear();
        sections.clear();
        if (filter() == null) {
            onClose();
            return;
        }

        int rowWidth = Math.min(470, width - 32);
        int x = (width - rowWidth) / 2;
        int y = VIEW_TOP;
        contentX = x;
        contentWidth = rowWidth;

        y = addSection(x, y, rowWidth, "Grundlage", 0xFF38BDF8);
        y = addTextField(x, y, rowWidth, "Name", "Filtername", draft.name, 28,
                value -> draft.name = value);
        y = addButtonRow(x, y, rowWidth, "Quelle",
                () -> "Quelle: " + SecondChatFilterListScreen.matcherLabel(draft.matcher),
                () -> draft.matcher = nextMatcher(draft.matcher));
        y += 6;

        y = addSection(x, y, rowWidth, "Erkennung", 0xFF4ADE80);
        y = addTextField(x, y, rowWidth, "Enthält",
                "Mehrere Begriffe mit ; trennen (ODER)", draft.includeText, 160,
                value -> draft.includeText = value);
        y = addTextField(x, y, rowWidth, "Enthält nicht",
                "Ausgeschlossene Begriffe", draft.excludeText, 160,
                value -> draft.excludeText = value);
        y = addButtonRow(x, y, rowWidth, "Vergleich",
                () -> "Vergleich: " + matchTypeLabel(draft.matchType),
                () -> draft.matchType = "starts".equals(draft.matchType)
                        ? "contains" : "starts");
        y = addToggleRow(x, y, rowWidth, "Groß-/Kleinschreibung beachten",
                () -> draft.caseSensitive, value -> draft.caseSensitive = value);
        y += 6;

        y = addSection(x, y, rowWidth, "Aktion", 0xFFFACC15);
        y = addButtonRow(x, y, rowWidth, "Aktion",
                () -> "Aktion: " + SecondChatManager.modeLabel(draft.mode),
                () -> draft.mode = SecondChatManager.nextMode(draft.mode));
        y = addToggleRow(x, y, rowWidth, "Filter aktiv",
                () -> draft.enabled, value -> draft.enabled = value);
        y = addToggleRow(x, y, rowWidth, "Aus normalem Chat entfernen",
                () -> draft.hideMessage, value -> draft.hideMessage = value);
        y = addToggleRow(x, y, rowWidth, "Ton abspielen",
                () -> draft.playSound, value -> draft.playSound = value);
        y += 6;

        y = addSection(x, y, rowWidth, "Darstellung", 0xFFA78BFA);
        y = addToggleRow(x, y, rowWidth, "Eigener Hintergrund",
                () -> draft.customBackground, value -> draft.customBackground = value);

        Button color = Button.builder(colorLabel(), button -> openColorPicker())
                .bounds(x, y, rowWidth, 20)
                .build();
        addWidgetRow(color, y);
        y += ROW_HEIGHT;

        y = addToggleRow(x, y, rowWidth, "Filter-Tooltip",
                () -> draft.filterTooltip, value -> draft.filterTooltip = value);
        y += 6;

        y = addSection(x, y, rowWidth, "Filter testen", 0xFF38BDF8);
        testMessage = new EditBox(font, x, y, rowWidth, 20, Component.literal("Beispielnachricht"));
        testMessage.setMaxLength(240);
        testMessage.setResponder(value -> updateTestLabel());
        addWidgetRow(testMessage, y);
        y += ROW_HEIGHT;
        testButton = Button.builder(Component.literal("Test: Beispiel eingeben"),
                        button -> updateTestLabel())
                .bounds(x, y, rowWidth, 20)
                .build();
        addWidgetRow(testButton, y);
        y += ROW_HEIGHT;
        contentHeight = y - VIEW_TOP;

        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), button -> onClose())
                .bounds(16, height - 29, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Speichern"), button -> saveAndClose())
                .bounds(width - 116, height - 29, 100, 20)
                .build());
        updateRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        SecondChatSettingsUi.renderPage(
                context,
                font,
                width,
                height,
                newFilter ? "Neuer Chatfilter" : "Chatfilter bearbeiten",
                "Erkennung, Ziel und Darstellung der Nachricht");
        SecondChatSettingsUi.renderPanel(
                context,
                contentX - 10,
                VIEW_TOP - 7,
                contentWidth + 20,
                Math.max(1, height - VIEW_TOP - 36));

        int bottom = height - 38;
        for (SecondChatSettingsUi.Section section : sections) {
            SecondChatSettingsUi.renderSection(
                    context, font, section, scrollOffset, VIEW_TOP - 2, bottom);
        }
        for (TextRow row : textRows) {
            int y = row.logicalY - scrollOffset;
            if (y >= VIEW_TOP - 12 && y < bottom) {
                context.text(
                        font,
                        Component.literal(row.text),
                        row.x,
                        y,
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
            scrollOffset = clamp(scrollOffset - (int) Math.round(verticalAmount * 24.0D), 0, maxScroll());
            updateRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (!saved && newFilter) {
            SecondChatTabConfig tab = tab();
            if (tab != null) {
                tab.filters.removeIf(candidate -> filterId.equals(candidate.id));
                BetterUCConfig.sanitizeSecondChat();
                BetterUCConfig.save();
            }
        }
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int addTextField(
            int x,
            int y,
            int width,
            String label,
            String hint,
            String value,
            int maxLength,
            Consumer<String> responder
    ) {
        textRows.add(new TextRow(x, y, label));
        EditBox field = new EditBox(font, x, y + 12, width, 20, Component.literal(hint));
        field.setMaxLength(maxLength);
        field.setValue(value == null ? "" : value);
        field.setResponder(responder);
        addWidgetRow(field, y + 12);
        return y + ROW_HEIGHT + 18;
    }

    private int addButtonRow(
            int x,
            int y,
            int width,
            String ignoredLabel,
            Supplier<String> label,
            Runnable action
    ) {
        Button button = Button.builder(Component.literal(label.get()), clicked -> {
                    action.run();
                    clicked.setMessage(Component.literal(label.get()));
                })
                .bounds(x, y, width, 20)
                .build();
        addWidgetRow(button, y);
        return y + ROW_HEIGHT;
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
        addWidgetRow(button, y);
        return y + ROW_HEIGHT;
    }

    private int addSection(int x, int y, int width, String label, int color) {
        sections.add(new SecondChatSettingsUi.Section(label, x, y, width, color));
        return y + 18;
    }

    private void openColorPicker() {
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new ColorPickerScreen(
                    this,
                    "Filterfarbe",
                    "Hintergrundfarbe",
                    draft.backgroundColor,
                    color -> draft.backgroundColor = color
            ));
        }
    }

    private void saveAndClose() {
        SecondChatFilterConfig target = filter();
        if (target != null) {
            target.name = draft.name;
            target.matcher = draft.matcher;
            target.includeText = draft.includeText;
            target.excludeText = draft.excludeText;
            target.matchType = draft.matchType;
            target.mode = draft.mode;
            target.enabled = draft.enabled;
            target.customBackground = draft.customBackground;
            target.backgroundColor = draft.backgroundColor;
            target.playSound = draft.playSound;
            target.hideMessage = draft.hideMessage;
            target.filterTooltip = draft.filterTooltip;
            target.caseSensitive = draft.caseSensitive;
            SecondChatTabConfig tab = tab();
            int index = tab == null ? 0 : Math.max(0, tab.filters.indexOf(target));
            target.sanitize(index);
            BetterUCConfig.sanitizeSecondChat();
            BetterUCConfig.save();
        }
        saved = true;
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    private void addWidgetRow(AbstractWidget widget, int logicalY) {
        addRenderableWidget(widget);
        widgetRows.add(new WidgetRow(widget, logicalY));
    }

    private void updateRows() {
        int bottom = height - 38;
        for (WidgetRow row : widgetRows) {
            int y = row.logicalY - scrollOffset;
            row.widget.setY(y);
            row.widget.visible = y + row.widget.getHeight() > VIEW_TOP - 3 && y < bottom;
            row.widget.active = row.widget.visible;
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - Math.max(1, height - VIEW_TOP - 42));
    }

    private SecondChatTabConfig tab() {
        return SecondChatManager.findTab(tabId);
    }

    private SecondChatFilterConfig filter() {
        SecondChatTabConfig tab = tab();
        if (tab == null) {
            return null;
        }
        for (SecondChatFilterConfig candidate : tab.filters) {
            if (filterId.equals(candidate.id)) {
                return candidate;
            }
        }
        return null;
    }

    private Component colorLabel() {
        return Component.literal(String.format("Hintergrundfarbe: #%06X",
                draft.backgroundColor & 0x00FFFFFF));
    }

    private void updateTestLabel() {
        if (testButton == null || testMessage == null) {
            return;
        }
        String sample = testMessage.getValue();
        if (sample.isBlank()) {
            testButton.setMessage(Component.literal("Test: Beispiel eingeben"));
            return;
        }
        testButton.setMessage(Component.literal(SecondChatManager.testFilter(draft, sample)
                ? "Test: Nachricht passt"
                : "Test: Nachricht passt nicht"));
    }

    private static String matchTypeLabel(String matchType) {
        return "starts".equals(matchType) ? "Beginnt mit" : "Enthält";
    }

    private static String nextMatcher(String current) {
        for (int i = 0; i < MATCHERS.length; i++) {
            if (MATCHERS[i].equals(current)) {
                return MATCHERS[(i + 1) % MATCHERS.length];
            }
        }
        return MATCHERS[0];
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "AN" : "AUS"));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record WidgetRow(AbstractWidget widget, int logicalY) {
    }

    private record TextRow(int x, int logicalY, String text) {
    }
}
