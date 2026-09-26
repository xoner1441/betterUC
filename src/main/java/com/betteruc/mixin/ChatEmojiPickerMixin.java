package com.betteruc.mixin;

import com.betteruc.client.ChatEmojiFormatter;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatEmojiPickerMixin {

    @Unique private static final int BUTTON_GAP = 2;
    @Unique private static final int BUTTON_WIDTH = 16;
    @Unique private static final int PANEL_PADDING = 4;
    @Unique private static final int PANEL_HEADER_HEIGHT = 15;
    @Unique private static final int CELL_SIZE = 18;
    @Unique private static final int COLUMNS = 5;
    @Unique private static final int PANEL_BACKGROUND = 0xEE111827;
    @Unique private static final int PANEL_BORDER = 0xFF64748B;
    @Unique private static final int CELL_HOVER = 0xCC334155;
    @Unique private static final int ACCENT = 0xFFF472B6;

    @Shadow protected EditBox input;

    @Unique private boolean betteruc$emojiPickerOpen;
    @Unique private EditBox betteruc$adjustedInput;

    @Inject(method = "init", at = @At("TAIL"))
    private void betteruc$makeRoomForEmojiButton(CallbackInfo ci) {
        if (!BetterUCConfig.INSTANCE.chatEmojisEnabled || input == null || input == betteruc$adjustedInput) {
            return;
        }
        input.setWidth(Math.max(40, input.getWidth() - BUTTON_WIDTH - BUTTON_GAP));
        betteruc$adjustedInput = input;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void betteruc$renderEmojiPicker(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        if (!BetterUCConfig.INSTANCE.chatEmojisEnabled || input == null) return;

        Font font = ((Screen) (Object) this).getFont();
        int buttonX = betteruc$buttonX();
        int buttonY = input.getY();
        int buttonHeight = input.getHeight();
        boolean buttonHovered = betteruc$contains(
                mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, buttonHeight
        );
        context.fill(
                buttonX,
                buttonY,
                buttonX + BUTTON_WIDTH,
                buttonY + buttonHeight,
                buttonHovered || betteruc$emojiPickerOpen ? CELL_HOVER : PANEL_BACKGROUND
        );
        context.outline(buttonX, buttonY, BUTTON_WIDTH, buttonHeight, PANEL_BORDER);
        context.centeredText(font, "☺", buttonX + BUTTON_WIDTH / 2, buttonY + 2, ACCENT);

        if (buttonHovered) {
            context.setTooltipForNextFrame(font, Component.literal("Symbole auswählen"), mouseX, mouseY);
        }
        if (!betteruc$emojiPickerOpen) return;

        List<ChatEmojiFormatter.PickerEntry> entries = ChatEmojiFormatter.pickerEntries();
        int panelX = betteruc$panelX();
        int panelY = betteruc$panelY(entries.size());
        int panelWidth = betteruc$panelWidth();
        int panelHeight = betteruc$panelHeight(entries.size());
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
        context.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
        context.text(font, "Symbole", panelX + PANEL_PADDING, panelY + 4, ACCENT);

        for (int index = 0; index < entries.size(); index++) {
            int cellX = betteruc$cellX(panelX, index);
            int cellY = betteruc$cellY(panelY, index);
            boolean hovered = betteruc$contains(mouseX, mouseY, cellX, cellY, CELL_SIZE, CELL_SIZE);
            if (hovered) {
                context.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, CELL_HOVER);
            }
            ChatEmojiFormatter.PickerEntry entry = entries.get(index);
            context.centeredText(
                    font,
                    entry.symbol(),
                    cellX + CELL_SIZE / 2,
                    cellY + 5,
                    0xFF000000 | TextColor.fromLegacyFormat(entry.color()).getValue()
            );
            if (hovered) {
                context.setTooltipForNextFrame(font, Component.literal(entry.label()), mouseX, mouseY);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betteruc$handleEmojiPickerClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!BetterUCConfig.INSTANCE.chatEmojisEnabled || input == null || event.button() != 0) return;

        int buttonX = betteruc$buttonX();
        int buttonY = input.getY();
        if (betteruc$contains(event.x(), event.y(), buttonX, buttonY, BUTTON_WIDTH, input.getHeight())) {
            betteruc$emojiPickerOpen = !betteruc$emojiPickerOpen;
            cir.setReturnValue(true);
            return;
        }
        if (!betteruc$emojiPickerOpen) return;

        List<ChatEmojiFormatter.PickerEntry> entries = ChatEmojiFormatter.pickerEntries();
        int panelX = betteruc$panelX();
        int panelY = betteruc$panelY(entries.size());
        int index = betteruc$entryAt(entries.size(), panelX, panelY, event.x(), event.y());
        if (index >= 0) {
            input.insertText(entries.get(index).symbol());
            input.setFocused(true);
            betteruc$emojiPickerOpen = false;
            cir.setReturnValue(true);
            return;
        }

        if (betteruc$contains(
                event.x(), event.y(), panelX, panelY,
                betteruc$panelWidth(), betteruc$panelHeight(entries.size())
        )) {
            cir.setReturnValue(true);
        } else {
            betteruc$emojiPickerOpen = false;
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void betteruc$closeEmojiPickerWithEscape(
            KeyEvent event,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (betteruc$emojiPickerOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            betteruc$emojiPickerOpen = false;
            cir.setReturnValue(true);
        }
    }

    @Unique
    private int betteruc$buttonX() {
        return input.getRight() + BUTTON_GAP;
    }

    @Unique
    private int betteruc$panelX() {
        return betteruc$buttonX() + BUTTON_WIDTH - betteruc$panelWidth();
    }

    @Unique
    private int betteruc$panelY(int entryCount) {
        return input.getY() - betteruc$panelHeight(entryCount) - 4;
    }

    @Unique
    private int betteruc$panelWidth() {
        return PANEL_PADDING * 2 + COLUMNS * CELL_SIZE;
    }

    @Unique
    private int betteruc$panelHeight(int entryCount) {
        int rows = (entryCount + COLUMNS - 1) / COLUMNS;
        return PANEL_PADDING * 2 + PANEL_HEADER_HEIGHT + rows * CELL_SIZE;
    }

    @Unique
    private int betteruc$cellX(int panelX, int index) {
        return panelX + PANEL_PADDING + (index % COLUMNS) * CELL_SIZE;
    }

    @Unique
    private int betteruc$cellY(int panelY, int index) {
        return panelY + PANEL_PADDING + PANEL_HEADER_HEIGHT + (index / COLUMNS) * CELL_SIZE;
    }

    @Unique
    private int betteruc$entryAt(
            int entryCount,
            int panelX,
            int panelY,
            double mouseX,
            double mouseY
    ) {
        int gridX = (int) Math.floor(mouseX) - panelX - PANEL_PADDING;
        int gridY = (int) Math.floor(mouseY) - panelY - PANEL_PADDING - PANEL_HEADER_HEIGHT;
        if (gridX < 0 || gridY < 0) return -1;
        int column = gridX / CELL_SIZE;
        int row = gridY / CELL_SIZE;
        if (column >= COLUMNS) return -1;
        int index = row * COLUMNS + column;
        return index < entryCount ? index : -1;
    }

    @Unique
    private static boolean betteruc$contains(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
