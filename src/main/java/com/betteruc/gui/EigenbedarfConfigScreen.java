package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class EigenbedarfConfigScreen extends Screen {

    private static final int ROWS = 2;
    private static final int ROW_START_Y = 70;
    private static final int ROW_STEP_Y = 32;

    private final Screen parent;
    private final int[] drugIndex = new int[ROWS];
    private final int[] purity = new int[ROWS];
    private final TextFieldWidget[] amountFields = new TextFieldWidget[ROWS];
    private final ButtonWidget[] drugButtons = new ButtonWidget[ROWS];
    private final ButtonWidget[] purityButtons = new ButtonWidget[ROWS];

    public EigenbedarfConfigScreen(Screen parent) {
        super(Text.literal("Eigenbedarf Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        for (int i = 0; i < ROWS; i++) {
            BetterUCConfig.EigenbedarfPreset preset = getPreset(i);
            drugIndex[i] = findDrugIndex(preset.droge);
            purity[i] = BetterUCConfig.clampEigenbedarfPurity(preset.reinheit);
            addRow(i);
        }

        int centerX = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Speichern & Zurueck"), b -> {
            saveAndClose();
        }).dimensions(centerX - 90, height - 28, 180, 20).build());
    }

    private void addRow(int row) {
        int y = ROW_START_Y + row * ROW_STEP_Y;
        int centerX = width / 2;

        drugButtons[row] = addDrawableChild(ButtonWidget.builder(
                Text.literal(drugButtonLabel(row)),
                b -> {
                    cycleDrug(row);
                    refreshRowLabels(row);
                }
        ).dimensions(centerX - 170, y, 150, 20).build());

        TextFieldWidget amountField = new TextFieldWidget(
                textRenderer,
                centerX - 10,
                y,
                60,
                20,
                Text.literal("Menge")
        );
        amountField.setMaxLength(6);
        amountField.setText(String.valueOf(Math.max(0, getPreset(row).menge)));
        addDrawableChild(amountField);
        amountFields[row] = amountField;

        purityButtons[row] = addDrawableChild(ButtonWidget.builder(
                Text.literal(purityButtonLabel(row)),
                b -> {
                    cyclePurity(row);
                    refreshRowLabels(row);
                }
        ).dimensions(centerX + 60, y, 110, 20).build());
    }

    private String drugButtonLabel(int row) {
        return "Droge: " + BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS[drugIndex[row]];
    }

    private String purityButtonLabel(int row) {
        return "Reinheit: " + purity[row];
    }

    private void refreshRowLabels(int row) {
        if (row < 0 || row >= ROWS) return;
        if (drugButtons[row] != null) {
            drugButtons[row].setMessage(Text.literal(drugButtonLabel(row)));
        }
        if (purityButtons[row] != null) {
            purityButtons[row].setMessage(Text.literal(purityButtonLabel(row)));
        }
    }

    private void cycleDrug(int row) {
        drugIndex[row] = (drugIndex[row] + 1) % BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS.length;
    }

    private void cyclePurity(int row) {
        purity[row] = (purity[row] + 1) % 4;
    }

    private int findDrugIndex(String raw) {
        String normalized = BetterUCConfig.normalizeEigenbedarfDrug(raw);
        for (int i = 0; i < BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS.length; i++) {
            if (BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS[i].equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return 0;
    }

    private BetterUCConfig.EigenbedarfPreset getPreset(int row) {
        if (row == 0) {
            if (BetterUCConfig.INSTANCE.eigenbedarfSlot1 == null) {
                BetterUCConfig.INSTANCE.eigenbedarfSlot1 = new BetterUCConfig.EigenbedarfPreset(
                        BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS[0], 0, 0
                );
            }
            return BetterUCConfig.INSTANCE.eigenbedarfSlot1;
        }

        if (BetterUCConfig.INSTANCE.eigenbedarfSlot2 == null) {
            BetterUCConfig.INSTANCE.eigenbedarfSlot2 = new BetterUCConfig.EigenbedarfPreset(
                    BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS[1], 0, 0
            );
        }
        return BetterUCConfig.INSTANCE.eigenbedarfSlot2;
    }

    private void saveAndClose() {
        savePreset(0, BetterUCConfig.INSTANCE.eigenbedarfSlot1);
        savePreset(1, BetterUCConfig.INSTANCE.eigenbedarfSlot2);
        BetterUCConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void savePreset(int row, BetterUCConfig.EigenbedarfPreset target) {
        if (target == null) return;

        int amount = 0;
        try {
            String raw = amountFields[row] == null ? "" : amountFields[row].getText().trim();
            if (!raw.isEmpty()) {
                amount = Integer.parseInt(raw);
            }
        } catch (NumberFormatException ignored) {
        }

        target.droge = BetterUCConfig.normalizeEigenbedarfDrug(BetterUCConfig.EIGENBEDARF_DRUG_OPTIONS[drugIndex[row]]);
        target.menge = BetterUCConfig.clampEigenbedarfAmount(amount);
        target.reinheit = BetterUCConfig.clampEigenbedarfPurity(purity[row]);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00A7lEigenbedarf"), centerX, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A77/eigenbedarf nutzt beide Felder mit Menge > 0"),
                centerX,
                30,
                0xBBBBBB
        );

        context.drawTextWithShadow(textRenderer, Text.literal("\u00A77Feld 1"), centerX - 210, ROW_START_Y + 6, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("\u00A77Feld 2"), centerX - 210, ROW_START_Y + ROW_STEP_Y + 6, 0xAAAAAA);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
