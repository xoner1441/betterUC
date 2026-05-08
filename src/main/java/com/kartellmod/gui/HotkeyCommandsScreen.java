package com.kartellmod.gui;

import com.kartellmod.config.KartellConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

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
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;

    public HotkeyCommandsScreen(Screen parent) {
        super(Text.literal("Hotkey Commands"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private List<KartellConfig.HotkeyCommand> entries() {
        return KartellConfig.INSTANCE.hotkeyCommands;
    }

    private void rebuild() {
        clearChildren();
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
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add"), b -> {
            entries().add(new KartellConfig.HotkeyCommand(-1, ""));
            page = getMaxPage();
            rebuild();
        }).dimensions(centerX - 150, HEADER_Y, 70, 20).build());

        prevButton = addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
            if (page > 0) {
                page--;
                rebuild();
            }
        }).dimensions(centerX - 35, HEADER_Y, 60, 20).build());

        nextButton = addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
            int maxPage = getMaxPage();
            if (page < maxPage) {
                page++;
                rebuild();
            }
        }).dimensions(centerX + 30, HEADER_Y, 60, 20).build());

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
        KartellConfig.HotkeyCommand entry = entries().get(index);

        TextFieldWidget commandField = new TextFieldWidget(
                textRenderer,
                centerX + COMMAND_X_OFFSET,
                rowY,
                COMMAND_WIDTH,
                20,
                Text.literal("Command")
        );
        commandField.setMaxLength(256);
        commandField.setText(entry.command == null ? "" : entry.command);
        commandField.setChangedListener(text -> {
            if (index < entries().size()) {
                entries().get(index).command = text;
            }
        });
        addDrawableChild(commandField);

        addDrawableChild(ButtonWidget.builder(Text.literal(getKeyLabel(index)), b -> {
            capturingIndex = index;
            rebuild();
        }).dimensions(centerX + KEY_X_OFFSET, rowY, KEY_WIDTH, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Del"), b -> {
            deleteEntry(index);
            rebuild();
        }).dimensions(centerX + DELETE_X_OFFSET, rowY, DELETE_WIDTH, 20).build());
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
        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Back"), b -> {
            KartellConfig.save();
            if (client != null) client.setScreen(parent);
        }).dimensions(centerX - 80, height - 28, 160, 20).build());
    }

    private String getKeyLabel(int index) {
        if (index == capturingIndex) return "Press key...";
        if (index < 0 || index >= entries().size()) return "Key: none";

        int code = entries().get(index).keyCode;
        if (code < 0) return "Key: none";
        try {
            return "Key: " + InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        } catch (Exception ignored) {
            return "Key: " + code;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (capturingIndex < 0) {
            return super.keyPressed(input);
        }

        int keyCode = input.getKeycode();
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
    public void close() {
        KartellConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int totalPages = Math.max(1, (entries().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Hotkey Commands"), centerX, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Page " + (page + 1) + "/" + totalPages + " | Entries: " + entries().size()),
                centerX,
                33,
                0xAAAAAA
        );
        context.drawTextWithShadow(textRenderer, Text.literal("Command"), centerX + COMMAND_X_OFFSET, 44, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Key"), centerX + KEY_X_OFFSET, 44, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Use /command or command"), centerX + COMMAND_X_OFFSET, height - ENTRY_HINT_Y_OFFSET, 0x777777);

        if (entries().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("No hotkeys yet - click + Add"),
                    centerX,
                    ROW_START_Y + 8,
                    0xAAAAAA
            );
        }

        if (capturingIndex >= 0) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("\u00A7ePress a key for this row (ESC = cancel)"),
                    centerX,
                    height - CAPTURE_HINT_Y_OFFSET,
                    0xFFFFFF
            );
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
