package com.betteruc.gui;

import com.betteruc.ServerGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

public class CommandGui extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final String[] NAMES = new String[]{
            "Papierfabrik",
            "SH-Park",
            "Japan",
            "Flughafen Parkplatz",
            "Kirche Camper"
    };
    private static final String[] COMMANDS = new String[]{
            "navi -36/70/-278",
            "navi 65/67/347",
            "navi 592/69/91",
            "navi -312/69/670",
            "navi 304/71/-205"
    };

    public CommandGui() {
        super(Text.literal("Command Menu"));
    }

    @Override
    protected void init() {
        int guiWidth = COLS * SLOT_SIZE + 8;
        int guiHeight = ROWS * SLOT_SIZE + 8 + 20;
        int startX = width / 2 - guiWidth / 2;
        int startY = height / 2 - guiHeight / 2;

        for (int i = 0; i < COMMANDS.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;
            final int index = i;

            addDrawableChild(ButtonWidget.builder(Text.literal(""), button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    if (!ServerGate.isAllowedServer(client)) {
                        client.player.sendMessage(Text.literal(
                                "\u00A7cBetterUCMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel()
                        ), false);
                        return;
                    }
                    client.player.networkHandler.sendChatCommand(COMMANDS[index]);
                    close();
                }
            }).dimensions(slotX, slotY, SLOT_SIZE - 2, SLOT_SIZE - 2).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int guiWidth = COLS * SLOT_SIZE + 8;
        int guiHeight = ROWS * SLOT_SIZE + 8 + 20;
        int startX = width / 2 - guiWidth / 2;
        int startY = height / 2 - guiHeight / 2;

        context.fill(startX, startY, startX + guiWidth, startY + guiHeight, 0xCC1A1A1A);
        context.fill(startX, startY, startX + guiWidth, startY + 14, 0xCC2D2D2D);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00A7l Navigation Menu"), width / 2, startY + 3, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);

        for (int i = 0; i < COMMANDS.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;
            context.drawItem(new ItemStack(Items.FERN), slotX + 1, slotY + 1);
        }

        for (int i = 0; i < COMMANDS.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE - 2
                    && mouseY >= slotY && mouseY <= slotY + SLOT_SIZE - 2) {
                context.drawTooltip(textRenderer, List.of(
                        Text.literal("\u00A7e" + NAMES[i]),
                        Text.literal("\u00A77/" + COMMANDS[i])
                ), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
