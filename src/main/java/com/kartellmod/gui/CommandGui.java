package com.kartellmod.gui;

import com.kartellmod.ServerGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class CommandGui extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final int ROWS = 3;

    private record CommandSlot(String name, ItemStack icon, String command) {
    }

    private final List<CommandSlot> slots = new ArrayList<>();

    public CommandGui() {
        super(Text.literal("Command Menu"));
        slots.add(new CommandSlot("Papierfabrik", new ItemStack(Items.FERN), "navi -36/70/-278"));
        slots.add(new CommandSlot("SH-Park", new ItemStack(Items.FERN), "navi 65/67/347"));
        slots.add(new CommandSlot("Japan", new ItemStack(Items.FERN), "navi 592/69/91"));
        slots.add(new CommandSlot("Flughafen Parkplatz", new ItemStack(Items.FERN), "navi -312/69/670"));
        slots.add(new CommandSlot("Kirche Camper", new ItemStack(Items.FERN), "navi 304/71/-205"));
    }

    @Override
    protected void init() {
        int guiWidth = COLS * SLOT_SIZE + 8;
        int guiHeight = ROWS * SLOT_SIZE + 8 + 20;
        int startX = width / 2 - guiWidth / 2;
        int startY = height / 2 - guiHeight / 2;

        for (int i = 0; i < slots.size(); i++) {
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
                                "\u00A7cKartellMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel()
                        ), false);
                        return;
                    }
                    client.player.networkHandler.sendChatCommand(slots.get(index).command());
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

        for (int i = 0; i < slots.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;
            context.drawItem(slots.get(i).icon(), slotX + 1, slotY + 1);
        }

        for (int i = 0; i < slots.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE - 2
                    && mouseY >= slotY && mouseY <= slotY + SLOT_SIZE - 2) {
                context.drawTooltip(textRenderer, List.of(
                        Text.literal("\u00A7e" + slots.get(i).name()),
                        Text.literal("\u00A77/" + slots.get(i).command())
                ), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
