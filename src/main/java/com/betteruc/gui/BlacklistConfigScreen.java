package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlacklistConfigScreen extends Screen {

    private static final int FIELD_W = 90;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 26;
    private static final int FIRST_Y = 65;

    private final Screen parent;
    private final List<EditBox> killsFields = new ArrayList<>();
    private final List<EditBox> priceFields = new ArrayList<>();
    private final List<String> reasonKeys = new ArrayList<>();

    public BlacklistConfigScreen(Screen parent) {
        super(Component.literal("Blacklist Gründe"));
        this.parent = parent;
    }

    private int killsX() {
        return width / 2 + 10;
    }

    private int priceX() {
        return width / 2 + 110;
    }

    private int labelX() {
        return width / 2 - 160;
    }

    @Override
    protected void init() {
        killsFields.clear();
        priceFields.clear();
        reasonKeys.clear();

        int rowIndex = 0;
        for (Map.Entry<String, BetterUCConfig.BlacklistReason> entry : BetterUCConfig.INSTANCE.blReasons.entrySet()) {
            reasonKeys.add(entry.getKey());
            int fieldY = FIRST_Y + rowIndex * ROW_H;

            Button label = Button.builder(Component.literal(entry.getKey()), btn -> {
            }).bounds(labelX(), fieldY, 140, FIELD_H).build();
            label.active = false;
            addRenderableWidget(label);

            EditBox kills = new EditBox(
                    font, killsX(), fieldY, FIELD_W, FIELD_H, Component.literal("Kills"));
            kills.setMaxLength(6);
            kills.setValue(String.valueOf(entry.getValue().kills));
            addRenderableWidget(kills);
            killsFields.add(kills);

            EditBox price = new EditBox(
                    font, priceX(), fieldY, FIELD_W, FIELD_H, Component.literal("Preis"));
            price.setMaxLength(8);
            price.setValue(String.valueOf(entry.getValue().price));
            addRenderableWidget(price);
            priceFields.add(price);

            rowIndex++;
        }

        int headerY = FIRST_Y - ROW_H;
        Button hGrund = Button.builder(Component.literal("\u00A77Grund"), b -> {
        }).bounds(labelX(), headerY, 140, FIELD_H).build();
        hGrund.active = false;
        addRenderableWidget(hGrund);

        Button hKills = Button.builder(Component.literal("\u00A7eKills"), b -> {
        }).bounds(killsX(), headerY, FIELD_W, FIELD_H).build();
        hKills.active = false;
        addRenderableWidget(hKills);

        Button hPreis = Button.builder(Component.literal("\u00A7aPreis $"), b -> {
        }).bounds(priceX(), headerY, FIELD_W, FIELD_H).build();
        hPreis.active = false;
        addRenderableWidget(hPreis);

        addRenderableWidget(Button.builder(Component.literal("\u2714 Speichern & Zurück"), b -> {
            saveValues();
            BetterUCConfig.save();
            if (minecraft != null) minecraft.gui.setScreen(parent);
        }).bounds(width / 2 - 80, height - 28, 160, 20).build());
    }

    private void saveValues() {
        for (int i = 0; i < reasonKeys.size(); i++) {
            BetterUCConfig.BlacklistReason reason = BetterUCConfig.INSTANCE.blReasons.get(reasonKeys.get(i));
            if (reason == null) continue;
            try {
                reason.kills = Integer.parseInt(killsFields.get(i).getValue().trim());
            } catch (NumberFormatException ignored) {
            }
            try {
                reason.price = Integer.parseInt(priceFields.get(i).getValue().trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(
                font,
                Component.literal("\u00A7l\u2694 Blacklist Gründe"),
                width / 2,
                10,
                0xFFFFFF
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
