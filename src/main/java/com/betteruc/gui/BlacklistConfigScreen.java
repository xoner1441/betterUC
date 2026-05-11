package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlacklistConfigScreen extends Screen {

    private static final int FIELD_W = 90;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 26;
    private static final int FIRST_Y = 65;

    private final Screen parent;
    private final List<TextFieldWidget> killsFields = new ArrayList<>();
    private final List<TextFieldWidget> priceFields = new ArrayList<>();
    private final List<String> reasonKeys = new ArrayList<>();

    public BlacklistConfigScreen(Screen parent) {
        super(Text.literal("Blacklist Gruende"));
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

            ButtonWidget label = ButtonWidget.builder(Text.literal(entry.getKey()), btn -> {
            }).dimensions(labelX(), fieldY, 140, FIELD_H).build();
            label.active = false;
            addDrawableChild(label);

            TextFieldWidget kills = new TextFieldWidget(
                    textRenderer, killsX(), fieldY, FIELD_W, FIELD_H, Text.literal("Kills"));
            kills.setMaxLength(6);
            kills.setText(String.valueOf(entry.getValue().kills));
            addDrawableChild(kills);
            killsFields.add(kills);

            TextFieldWidget price = new TextFieldWidget(
                    textRenderer, priceX(), fieldY, FIELD_W, FIELD_H, Text.literal("Preis"));
            price.setMaxLength(8);
            price.setText(String.valueOf(entry.getValue().price));
            addDrawableChild(price);
            priceFields.add(price);

            rowIndex++;
        }

        int headerY = FIRST_Y - ROW_H;
        ButtonWidget hGrund = ButtonWidget.builder(Text.literal("\u00A77Grund"), b -> {
        }).dimensions(labelX(), headerY, 140, FIELD_H).build();
        hGrund.active = false;
        addDrawableChild(hGrund);

        ButtonWidget hKills = ButtonWidget.builder(Text.literal("\u00A7eKills"), b -> {
        }).dimensions(killsX(), headerY, FIELD_W, FIELD_H).build();
        hKills.active = false;
        addDrawableChild(hKills);

        ButtonWidget hPreis = ButtonWidget.builder(Text.literal("\u00A7aPreis $"), b -> {
        }).dimensions(priceX(), headerY, FIELD_W, FIELD_H).build();
        hPreis.active = false;
        addDrawableChild(hPreis);

        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Speichern & Zurueck"), b -> {
            saveValues();
            BetterUCConfig.save();
            if (client != null) client.setScreen(parent);
        }).dimensions(width / 2 - 80, height - 28, 160, 20).build());
    }

    private void saveValues() {
        for (int i = 0; i < reasonKeys.size(); i++) {
            BetterUCConfig.BlacklistReason reason = BetterUCConfig.INSTANCE.blReasons.get(reasonKeys.get(i));
            if (reason == null) continue;
            try {
                reason.kills = Integer.parseInt(killsFields.get(i).getText().trim());
            } catch (NumberFormatException ignored) {
            }
            try {
                reason.price = Integer.parseInt(priceFields.get(i).getText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A7l\u2694 Blacklist Gruende"),
                width / 2,
                10,
                0xFFFFFF
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
