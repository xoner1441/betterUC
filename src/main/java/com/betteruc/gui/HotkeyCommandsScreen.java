package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class HotkeyCommandsScreen extends Screen {

    private static final int ROWS_PER_PAGE = 6;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_Y = 24;
    private static final int ROW_START_Y = 52;
    private static final int COMMAND_X_OFFSET = -150;
    private static final int COMMAND_WIDTH = 180;
    private static final int KEY_X_OFFSET = 35;
    private static final int KEY_WIDTH = 95;
    private static final int DELETE_X_OFFSET = 135;
    private static final int DELETE_WIDTH = 35;
    private static final int ENTRY_HINT_Y_OFFSET = 42;
    private static final int CAPTURE_HINT_Y_OFFSET = 54;

    private final Screen parent;
    private int page = 0;
    private int capturingIndex = -1;
    private Button prevButton;
    private Button nextButton;

    public HotkeyCommandsScreen(Screen parent) {
        super(Component.literal("Hotkey Commands"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private List<BetterUCConfig.HotkeyCommand> entries() {
        return BetterUCConfig.INSTANCE.hotkeyCommands;
    }

    private void rebuild() {
        clearWidgets();
        clampCurrentPage();

        int centerX = width / 2;
        addHeaderButtons(centerX);
        addVisibleRows(centerX);
        addSaveBackButton(centerX);
    }

    private void clampCurrentPage() {
        int maxPage = getMaxPage();
        if (page > maxPage) {
            page = maxPage;
        }
        if (page < 0) {
            page = 0;
        }
    }

    private int getMaxPage() {
        int total = entries().size();
        return Math.max(0, (total - 1) / ROWS_PER_PAGE);
    }

    private void addHeaderButtons(int centerX) {
        addRenderableWidget(Button.builder(Component.literal("+ Add"), b -> {
            entries().add(new BetterUCConfig.HotkeyCommand(-1, ""));
            page = getMaxPage();
            rebuild();
        }).bounds(centerX - 150, HEADER_Y, 70, 20).build());

        prevButton = addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).bounds(centerX - 35, HEADER_Y, 60, 20).build());

        nextButton = addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            int maxPage = getMaxPage();
            if (page < maxPage) {
                page++;
                rebuild();
            }
        }).bounds(centerX + 30, HEADER_Y, 60, 20).build());

        int maxPage = getMaxPage();
        prevButton.active = page > 0;
        nextButton.active = page < maxPage;
    }

    private void addVisibleRows(int centerX) {
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(entries().size(), start + ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            addRow(centerX, i, ROW_START_Y + (i - start) * ROW_HEIGHT);
        }
    }

    private void addRow(int centerX, int index, int rowY) {
        BetterUCConfig.HotkeyCommand entry = entries().get(index);

        EditBox commandField = new EditBox(
                font,
                centerX + COMMAND_X_OFFSET,
                rowY,
                COMMAND_WIDTH,
                20,
                Component.literal("Command")
        );
        commandField.setMaxLength(256);
        commandField.setValue(entry.command == null ? "" : entry.command);
        commandField.setResponder(text -> {
            if (index < entries().size()) {
                entries().get(index).command = text;
            }
        });
        addRenderableWidget(commandField);

        addRenderableWidget(Button.builder(Component.literal(getKeyLabel(index)), b -> {
            capturingIndex = index;
            rebuild();
        }).bounds(centerX + KEY_X_OFFSET, rowY, KEY_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Del"), b -> {
            deleteEntry(index);
            rebuild();
        }).bounds(centerX + DELETE_X_OFFSET, rowY, DELETE_WIDTH, 20).build());
    }

    private void deleteEntry(int index) {
        if (index < 0 || index >= entries().size()) return;
        entries().remove(index);
        if (capturingIndex == index) {
            capturingIndex = -1;
        } else if (capturingIndex > index) {
            capturingIndex--;
        }
        clampCurrentPage();
    }

    private void addSaveBackButton(int centerX) {
        addRenderableWidget(Button.builder(Component.literal("Save & Back"), b -> {
            BetterUCConfig.save();
            if (minecraft != null) minecraft.gui.setScreen(parent);
        }).bounds(centerX - 80, height - 28, 160, 20).build());
    }

    private String getKeyLabel(int index) {
        if (index == capturingIndex) return "Press key...";
        if (index < 0 || index >= entries().size()) return "Key: none";

        int code = entries().get(index).keyCode;
        if (code < 0) return "Key: none";
        try {
            return "Key: " + InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
        } catch (Exception ignored) {
            return "Key: " + code;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (capturingIndex < 0) {
            return super.keyPressed(input);
        }

        int keyCode = input.input();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            capturingIndex = -1;
            rebuild();
            return true;
        }

        if (capturingIndex < entries().size()) {
            entries().get(capturingIndex).keyCode = keyCode;
        }
        capturingIndex = -1;
        rebuild();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return capturingIndex < 0;
    }

    @Override
    public void onClose() {
        BetterUCConfig.save();
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int totalPages = Math.max(1, (entries().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        context.centeredText(font, Component.literal("Hotkey Commands"), centerX, 8, 0xFFFFFF);
        context.centeredText(
                font,
                Component.literal("Page " + (page + 1) + "/" + totalPages + " | Entries: " + entries().size()),
                centerX,
                33,
                0xAAAAAA
        );
        context.text(font, Component.literal("Command"), centerX + COMMAND_X_OFFSET, 44, 0xAAAAAA);
        context.text(font, Component.literal("Key"), centerX + KEY_X_OFFSET, 44, 0xAAAAAA);
        context.text(font, Component.literal("Use /command or command"), centerX + COMMAND_X_OFFSET, height - ENTRY_HINT_Y_OFFSET, 0x777777);

        if (entries().isEmpty()) {
            context.centeredText(
                    font,
                    Component.literal("No hotkeys yet - click + Add"),
                    centerX,
                    ROW_START_Y + 8,
                    0xAAAAAA
            );
        }

        if (capturingIndex >= 0) {
            context.centeredText(
                    font,
                    Component.literal("\u00A7ePress a key for this row (ESC = cancel)"),
                    centerX,
                    height - CAPTURE_HINT_Y_OFFSET,
                    0xFFFFFF
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
