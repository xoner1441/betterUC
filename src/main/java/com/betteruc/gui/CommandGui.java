package com.betteruc.gui;

import com.betteruc.ServerGate;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CommandGui extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final String[] NAMES = new String[]{
            "Ruine",
            "Wohnwagen Kirche",
            "Flughafen",
            "U-Bahn Mexican",
            "Stadtpark",
            "Deathmatch",
            "Chinatown Berg",
            "Verlassenes Gebäude",
            "Leuchtturm",
            "Mühle"
    };
    private static final String[] COMMANDS = new String[]{
            "navi 743/69/316",
            "navi 304/71/-205 ",
            "navi -311/69/669",
            "navi -92/52/-34",
            "navi 65/67/348",
            "navi -467/69/426",
            "navi 819/79/-33",
            "navi 986/105/433",
            "navi -772/64/149",
            "navi 461/75/593"
    };

    public CommandGui() {
        super(Component.literal("Command Menu"));
    }

    @Override
    protected void init() {
        int guiWidth = guiWidth();
        int guiHeight = guiHeight();
        int startX = width / 2 - guiWidth / 2;
        int startY = height / 2 - guiHeight / 2;

        for (int i = 0; i < COMMANDS.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;
            final int index = i;

            addRenderableWidget(Button.builder(Component.literal(""), button -> {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    if (!ServerGate.isAllowedServer(client)) {
                        client.player.sendSystemMessage(Component.literal(
                                "\u00A7cBetterUCMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel()
                        ));
                        return;
                    }
                    client.player.connection.sendCommand(COMMANDS[index]);
                    onClose();
                }
            }).bounds(slotX, slotY, SLOT_SIZE - 2, SLOT_SIZE - 2).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int guiWidth = guiWidth();
        int guiHeight = guiHeight();
        int startX = width / 2 - guiWidth / 2;
        int startY = height / 2 - guiHeight / 2;

        context.fill(startX, startY, startX + guiWidth, startY + guiHeight, 0xCC1A1A1A);
        context.fill(startX, startY, startX + guiWidth, startY + 14, 0xCC2D2D2D);
        context.centeredText(font, Component.literal("\u00A7l Navigation Menu"), width / 2, startY + 3, 0xFFFFFF);

        super.extractRenderState(context, mouseX, mouseY, delta);

        for (int i = 0; i < COMMANDS.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;
            context.item(new ItemStack(Items.FERN), slotX + 1, slotY + 1);
        }

        for (int i = 0; i < COMMANDS.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int slotX = startX + 4 + col * SLOT_SIZE;
            int slotY = startY + 18 + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX <= slotX + SLOT_SIZE - 2
                    && mouseY >= slotY && mouseY <= slotY + SLOT_SIZE - 2) {
                context.setComponentTooltipForNextFrame(font, List.of(
                        Component.literal("\u00A7e" + nameFor(i)),
                        Component.literal("\u00A77/" + COMMANDS[i])
                ), mouseX, mouseY);
            }
        }
    }

    private static int guiWidth() {
        return COLS * SLOT_SIZE + 8;
    }

    private static int guiHeight() {
        return rows() * SLOT_SIZE + 8 + 20;
    }

    private static int rows() {
        return Math.max(1, (COMMANDS.length + COLS - 1) / COLS);
    }

    private static String nameFor(int index) {
        if (index >= 0 && index < NAMES.length && !NAMES[index].isBlank()) {
            return NAMES[index];
        }
        return "Navi " + (index + 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
