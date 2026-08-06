package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SecondChatGlobalSettingsScreen extends Screen {

    private static final int VIEW_TOP = 70;
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private final String windowId;
    private final List<Row> rows = new ArrayList<>();
    private final List<SecondChatSettingsUi.Section> sections = new ArrayList<>();
    private int scrollOffset;
    private int contentHeight;
    private int contentX;
    private int contentWidth;

    public SecondChatGlobalSettingsScreen(Screen parent, String windowId) {
        super(Component.literal("Chatfenster-Einstellungen"));
        this.parent = parent;
        this.windowId = windowId == null ? SecondChatManager.PRIMARY_WINDOW_ID : windowId;
    }

    @Override
    protected void init() {
        rows.clear();
        sections.clear();
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        int rowWidth = Math.min(430, width - 32);
        int x = (width - rowWidth) / 2;
        int y = VIEW_TOP;
        contentX = x;
        contentWidth = rowWidth;

        y = addSection(x, y, rowWidth, "Allgemein", 0xFF38BDF8);
        y = addToggleRow(x, y, rowWidth, "Chatfenster",
                () -> config.secondChatEnabled, value -> config.secondChatEnabled = value);
        y = addToggleRow(x, y, rowWidth, "Hintergrund",
                () -> config.secondChatBackgroundEnabled, value -> config.secondChatBackgroundEnabled = value);

        AbstractSliderButton opacity = new AbstractSliderButton(
                x,
                y,
                rowWidth,
                20,
                opacityLabel(config.secondChatBackgroundOpacity),
                config.secondChatBackgroundOpacity / 255.0D
        ) {
            @Override
            protected void updateMessage() {
                setMessage(opacityLabel((int) Math.round(value * 255.0D)));
            }

            @Override
            protected void applyValue() {
                config.secondChatBackgroundOpacity = (int) Math.round(value * 255.0D);
            }
        };
        addRow(opacity, y);
        y += ROW_HEIGHT;

        y += 6;
        y = addSection(x, y, rowWidth, "Farben & Hinweise", 0xFF4ADE80);
        y = addColorRow(x, y, rowWidth, "Akzentfarbe", config.secondChatAccentColor,
                color -> config.secondChatAccentColor = color);
        y = addColorRow(x, y, rowWidth, "Highlightfarbe", config.secondChatHighlightColor,
                color -> config.secondChatHighlightColor = color);
        y = addToggleRow(x, y, rowWidth, "Erwähnungston",
                () -> config.secondChatMentionSoundEnabled, value -> config.secondChatMentionSoundEnabled = value);

        y += 6;
        y = addSection(x, y, rowWidth, "Fenster & Verlauf", 0xFFFACC15);
        Button editor = Button.builder(Component.literal("Fenster positionieren & skalieren"), button -> openEditor())
                .bounds(x, y, rowWidth, 20)
                .build();
        addRow(editor, y);
        y += ROW_HEIGHT;

        Button clear = Button.builder(Component.literal("Chatverläufe leeren"), button -> SecondChatManager.clear())
                .bounds(x, y, rowWidth, 20)
                .build();
        addRow(clear, y);
        y += ROW_HEIGHT;

        contentHeight = y - VIEW_TOP;
        addRenderableWidget(Button.builder(Component.literal("Speichern"), button -> onClose())
                .bounds(width - 116, height - 29, 100, 20)
                .build());
        updateRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        SecondChatSettingsUi.renderPage(
                context, font, width, height,
                "Chatfenster-Einstellungen", "Darstellung und Verhalten");
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

    private int addColorRow(
            int x,
            int y,
            int width,
            String label,
            int color,
            Consumer<Integer> setter
    ) {
        Button button = Button.builder(
                        Component.literal(label + " " + colorHex(color)),
                        clicked -> openColorPicker(label, color, setter))
                .bounds(x, y, width, 20)
                .build();
        addRow(button, y);
        return y + ROW_HEIGHT;
    }

    private int addSection(int x, int y, int width, String label, int color) {
        sections.add(new SecondChatSettingsUi.Section(label, x, y, width, color));
        return y + 18;
    }

    private void openColorPicker(String label, int color, Consumer<Integer> setter) {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new ColorPickerScreen(
                    this,
                    label,
                    label + " wählen",
                    color,
                    setter::accept
            ));
        }
    }

    private void openEditor() {
        save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, new SecondChatEditorScreen(this, windowId));
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

    private void save() {
        BetterUCConfig.sanitizeSecondChat();
        BetterUCConfig.save();
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "AN" : "AUS"));
    }

    private static Component opacityLabel(int opacity) {
        return Component.literal("Hintergrund-Deckkraft: " + clamp(opacity, 0, 255));
    }

    private static String colorHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Row(AbstractWidget widget, int logicalY) {
    }
}
