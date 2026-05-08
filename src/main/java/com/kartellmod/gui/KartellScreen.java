package com.kartellmod.gui;

import com.kartellmod.config.KartellConfig;
import com.kartellmod.hud.BankBalanceHud;
import com.kartellmod.hud.HackTimerHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class KartellScreen extends Screen {

    private static final int BUTTON_W = 170;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_STEP_SMALL = 25;
    private static final int BUTTON_STEP_NORMAL = 30;
    private static final int SECTION_GAP = 6;
    private static final int SETTINGS_PAGES = 3;
    private static final int PAGE_ONE_COLUMN_GAP = 14;
    private static final int PAGE_ONE_ROW_STEP = 24;
    private static final int PAGE_ONE_MIN_BUTTON_W = 140;
    private static final int PAGE_ONE_SIDE_MARGIN = 16;

    private boolean capturingZoomKey = false;
    private int currentPage = 0;

    public KartellScreen() {
        super(Text.literal("Kartell Einstellungen"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int maxScreenX = Math.max(1, width - 1);
        int maxScreenY = Math.max(1, height - 1);
        int y = 58;

        clampCurrentPage();
        addPageSwitchButtons(centerX);

        if (currentPage == 0) {
            addPageOneTwoColumnLayout(centerX, y);
        } else if (currentPage == 1) {
            y = addPositionSliders(centerX, y, maxScreenX, maxScreenY);
            addTimestampField(centerX, y);
        } else {
            addHudColorButtons(centerX, y);
        }

        addSaveAndCloseButton(centerX);
    }

    private void addPageSwitchButtons(int centerX) {
        int btnY = 32;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (currentPage > 0) {
                currentPage--;
                refreshWidgets();
            }
        }).dimensions(centerX - BUTTON_W / 2, btnY, 20, BUTTON_H).build()).active = currentPage > 0;

        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (currentPage < SETTINGS_PAGES - 1) {
                currentPage++;
                refreshWidgets();
            }
        }).dimensions(centerX + BUTTON_W / 2 - 20, btnY, 20, BUTTON_H).build()).active = currentPage < SETTINGS_PAGES - 1;
    }

    private void addPageOneTwoColumnLayout(int centerX, int startY) {
        int availableWidth = Math.max(PAGE_ONE_MIN_BUTTON_W * 2 + PAGE_ONE_COLUMN_GAP, width - PAGE_ONE_SIDE_MARGIN * 2);
        int pageOneButtonW = Math.max(PAGE_ONE_MIN_BUTTON_W, Math.min(BUTTON_W, (availableWidth - PAGE_ONE_COLUMN_GAP) / 2));
        int leftX = centerX - (PAGE_ONE_COLUMN_GAP / 2) - pageOneButtonW;
        int rightX = centerX + (PAGE_ONE_COLUMN_GAP / 2);
        int y = startY;

        addPageOneButton(leftX, y, pageOneButtonW, "\u00A7aFraktion Farbe waehlen",
                b -> openScreen(new ColorPickerScreen(this, false, KartellConfig.INSTANCE.factionColor)));
        addPageOneButton(rightX, y, pageOneButtonW, "\u00A7cBlacklist Farbe waehlen",
                b -> openScreen(new ColorPickerScreen(this, true, KartellConfig.INSTANCE.blacklistColor)));

        y += PAGE_ONE_ROW_STEP;
        addPageOneButton(leftX, y, pageOneButtonW, "\u00A7bFraktionen waehlen", b -> openScreen(new FactionSelectionScreen(this)));
        addPageOneButton(rightX, y, pageOneButtonW, "\u00A76Blacklist Gruende", b -> openScreen(new BlacklistConfigScreen(this)));

        y += PAGE_ONE_ROW_STEP;
        addPageOneButton(leftX, y, pageOneButtonW, "\u00A7dEigenbedarf", b -> openScreen(new EigenbedarfConfigScreen(this)));
        addPageOneButton(rightX, y, pageOneButtonW, "\u00A7bHotkey Commands", b -> openScreen(new HotkeyCommandsScreen(this)));

        y += PAGE_ONE_ROW_STEP;
        addPageOneButton(leftX, y, pageOneButtonW, zoomKeyLabel(), b -> {
            capturingZoomKey = true;
            refreshWidgets();
        });
        addPageOneToggleButton(rightX, y, pageOneButtonW, KartellConfig.INSTANCE.toggleSprintEnabled,
                "\u00A7aToggleSprint: AN", "\u00A77ToggleSprint: AUS",
                () -> KartellConfig.INSTANCE.toggleSprintEnabled = !KartellConfig.INSTANCE.toggleSprintEnabled, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, KartellConfig.INSTANCE.showHealthHud,
                "\u00A7aHerz-Anzeige: AN", "\u00A77Herz-Anzeige: AUS",
                () -> KartellConfig.INSTANCE.showHealthHud = !KartellConfig.INSTANCE.showHealthHud, false);
        addPageOneToggleButton(rightX, y, pageOneButtonW, KartellConfig.INSTANCE.showFpsHud,
                "\u00A7aFPS-HUD: AN", "\u00A77FPS-HUD: AUS",
                () -> KartellConfig.INSTANCE.showFpsHud = !KartellConfig.INSTANCE.showFpsHud, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, KartellConfig.INSTANCE.showPaydayHud,
                "\u00A7aPayday-HUD: AN", "\u00A77Payday-HUD: AUS",
                () -> KartellConfig.INSTANCE.showPaydayHud = !KartellConfig.INSTANCE.showPaydayHud, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, KartellConfig.INSTANCE.showAmmoHud,
                "\u00A7aAmmo-HUD: AN", "\u00A77Ammo-HUD: AUS",
                () -> KartellConfig.INSTANCE.showAmmoHud = !KartellConfig.INSTANCE.showAmmoHud, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, KartellConfig.INSTANCE.showBankHud,
                "\u00A7aBank-HUD: AN", "\u00A77Bank-HUD: AUS",
                () -> KartellConfig.INSTANCE.showBankHud = !KartellConfig.INSTANCE.showBankHud, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, KartellConfig.INSTANCE.zoomEnabled,
                "\u00A7aZoom: AN", "\u00A77Zoom: AUS",
                () -> KartellConfig.INSTANCE.zoomEnabled = !KartellConfig.INSTANCE.zoomEnabled, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, KartellConfig.INSTANCE.zoomInstant,
                "\u00A7eZoom Anim: Instant", "\u00A7bZoom Anim: Smooth",
                () -> KartellConfig.INSTANCE.zoomInstant = !KartellConfig.INSTANCE.zoomInstant, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, KartellConfig.INSTANCE.fullbrightEnabled,
                "\u00A7aFullbright: AN", "\u00A77Fullbright: AUS",
                () -> KartellConfig.INSTANCE.fullbrightEnabled = !KartellConfig.INSTANCE.fullbrightEnabled, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, KartellConfig.INSTANCE.carAutomationEnabled,
                "\u00A7aCar Auto: AN", "\u00A77Car Auto: AUS",
                () -> KartellConfig.INSTANCE.carAutomationEnabled = !KartellConfig.INSTANCE.carAutomationEnabled, true);
    }

    private void addPageOneButton(int x, int y, int buttonWidth, String label, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, buttonWidth, BUTTON_H)
                .build());
    }

    private void addPageOneToggleButton(
            int x,
            int y,
            int buttonWidth,
            boolean state,
            String onLabel,
            String offLabel,
            Runnable toggleAction,
            boolean saveImmediately
    ) {
        addPageOneButton(x, y, buttonWidth, state ? onLabel : offLabel, b -> {
            toggleAction.run();
            if (saveImmediately) {
                KartellConfig.save();
            }
            refreshWidgets();
        });
    }

    private int addNavigationButtons(int centerX, int y) {
        y = addButton(centerX, y, "\u00A7aFraktion Farbe waehlen", b -> openScreen(new ColorPickerScreen(this, false, KartellConfig.INSTANCE.factionColor)), BUTTON_STEP_SMALL);
        y = addButton(centerX, y, "\u00A7cBlacklist Farbe waehlen", b -> openScreen(new ColorPickerScreen(this, true, KartellConfig.INSTANCE.blacklistColor)), BUTTON_STEP_SMALL);
        y = addButton(centerX, y, "\u00A76Blacklist Gruende", b -> openScreen(new BlacklistConfigScreen(this)), BUTTON_STEP_NORMAL);
        y = addButton(centerX, y, "\u00A7dEigenbedarf", b -> openScreen(new EigenbedarfConfigScreen(this)), BUTTON_STEP_SMALL);
        y = addButton(centerX, y, "\u00A7bHotkey Commands", b -> openScreen(new HotkeyCommandsScreen(this)), BUTTON_STEP_NORMAL);
        return y + SECTION_GAP;
    }

    private int addFeatureToggleButtons(int centerX, int y) {
        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.showHealthHud,
                "\u00A7aHerz-Anzeige: AN",
                "\u00A77Herz-Anzeige: AUS",
                () -> KartellConfig.INSTANCE.showHealthHud = !KartellConfig.INSTANCE.showHealthHud,
                false,
                BUTTON_STEP_NORMAL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.showFpsHud,
                "\u00A7aFPS-HUD: AN",
                "\u00A77FPS-HUD: AUS",
                () -> KartellConfig.INSTANCE.showFpsHud = !KartellConfig.INSTANCE.showFpsHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.showPaydayHud,
                "\u00A7aPayday-HUD: AN",
                "\u00A77Payday-HUD: AUS",
                () -> KartellConfig.INSTANCE.showPaydayHud = !KartellConfig.INSTANCE.showPaydayHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.showAmmoHud,
                "\u00A7aAmmo-HUD: AN",
                "\u00A77Ammo-HUD: AUS",
                () -> KartellConfig.INSTANCE.showAmmoHud = !KartellConfig.INSTANCE.showAmmoHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.showBankHud,
                "\u00A7aBank-HUD: AN",
                "\u00A77Bank-HUD: AUS",
                () -> KartellConfig.INSTANCE.showBankHud = !KartellConfig.INSTANCE.showBankHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.toggleSprintEnabled,
                "\u00A7aToggleSprint: AN",
                "\u00A77ToggleSprint: AUS",
                () -> KartellConfig.INSTANCE.toggleSprintEnabled = !KartellConfig.INSTANCE.toggleSprintEnabled,
                true,
                BUTTON_STEP_NORMAL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.zoomEnabled,
                "\u00A7aZoom: AN",
                "\u00A77Zoom: AUS",
                () -> KartellConfig.INSTANCE.zoomEnabled = !KartellConfig.INSTANCE.zoomEnabled,
                true,
                BUTTON_STEP_SMALL
        );

        y = addButton(centerX, y, zoomKeyLabel(), b -> {
            capturingZoomKey = true;
            refreshWidgets();
        }, BUTTON_STEP_SMALL);

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.zoomInstant,
                "\u00A7eZoom Anim: Instant",
                "\u00A7bZoom Anim: Smooth",
                () -> KartellConfig.INSTANCE.zoomInstant = !KartellConfig.INSTANCE.zoomInstant,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.fullbrightEnabled,
                "\u00A7aFullbright: AN",
                "\u00A77Fullbright: AUS",
                () -> KartellConfig.INSTANCE.fullbrightEnabled = !KartellConfig.INSTANCE.fullbrightEnabled,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                KartellConfig.INSTANCE.carAutomationEnabled,
                "\u00A7aCar Auto: AN",
                "\u00A77Car Auto: AUS",
                () -> KartellConfig.INSTANCE.carAutomationEnabled = !KartellConfig.INSTANCE.carAutomationEnabled,
                true,
                BUTTON_STEP_SMALL
        );

        return y + SECTION_GAP;
    }

    private int addPositionSliders(int centerX, int y, int maxScreenX, int maxScreenY) {
        y = addTimerXSlider(centerX, y, maxScreenX);
        y = addTimerYSlider(centerX, y, maxScreenY);
        y = addSprintHudXSlider(centerX, y, maxScreenX);
        y = addSprintHudYSlider(centerX, y, maxScreenY);
        y = addFpsHudXSlider(centerX, y, maxScreenX);
        y = addFpsHudYSlider(centerX, y, maxScreenY);
        y = addPaydayHudXSlider(centerX, y, maxScreenX);
        y = addPaydayHudYSlider(centerX, y, maxScreenY);
        y = addAmmoHudXSlider(centerX, y, maxScreenX);
        y = addAmmoHudYSlider(centerX, y, maxScreenY);
        y = addBankHudXSlider(centerX, y, maxScreenX);
        y = addBankHudYSlider(centerX, y, maxScreenY);
        return y;
    }

    private int addHudColorButtons(int centerX, int y) {
        y = addButton(centerX, y, "\u00A7bFPS HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "FPS HUD Farbe",
                "\u00A7bFPS HUD Farbe waehlen",
                KartellConfig.INSTANCE.fpsHudColor,
                c -> KartellConfig.INSTANCE.fpsHudColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7ePayday HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "Payday HUD Farbe",
                "\u00A7ePayday HUD Farbe waehlen",
                KartellConfig.INSTANCE.paydayHudColor,
                c -> KartellConfig.INSTANCE.paydayHudColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7bBank HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "Bank HUD Farbe",
                "\u00A7bBank HUD Farbe waehlen",
                KartellConfig.INSTANCE.bankHudColor,
                c -> KartellConfig.INSTANCE.bankHudColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7aToggleSprint HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "ToggleSprint HUD Farbe",
                "\u00A7aToggleSprint HUD Farbe waehlen",
                KartellConfig.INSTANCE.toggleSprintHudColor,
                c -> KartellConfig.INSTANCE.toggleSprintHudColor = c
        )), BUTTON_STEP_SMALL);

        return y + SECTION_GAP;
    }

    private int addTimerXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Timer X: " + KartellConfig.INSTANCE.timerX),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.timerX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Timer X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.timerX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addTimerYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Timer Y: " + KartellConfig.INSTANCE.timerY),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.timerY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Timer Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.timerY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addSprintHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Sprint HUD X: " + KartellConfig.INSTANCE.toggleSprintHudX),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.toggleSprintHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Sprint HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.toggleSprintHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addSprintHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Sprint HUD Y: " + KartellConfig.INSTANCE.toggleSprintHudY),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.toggleSprintHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Sprint HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.toggleSprintHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addFpsHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("FPS HUD X: " + KartellConfig.INSTANCE.fpsHudX),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.fpsHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("FPS HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.fpsHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addFpsHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("FPS HUD Y: " + KartellConfig.INSTANCE.fpsHudY),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.fpsHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("FPS HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.fpsHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addPaydayHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Payday HUD X: " + KartellConfig.INSTANCE.paydayHudX),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.paydayHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Payday HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.paydayHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addPaydayHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Payday HUD Y: " + KartellConfig.INSTANCE.paydayHudY),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.paydayHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Payday HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.paydayHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addAmmoHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Ammo HUD X: " + KartellConfig.INSTANCE.ammoHudX),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.ammoHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Ammo HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.ammoHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addAmmoHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Ammo HUD Y: " + KartellConfig.INSTANCE.ammoHudY),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.ammoHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Ammo HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.ammoHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addBankHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Bank HUD X: " + KartellConfig.INSTANCE.bankHudX),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.bankHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Bank HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.bankHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addBankHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Bank HUD Y: " + KartellConfig.INSTANCE.bankHudY),
                Math.max(0.0, Math.min(1.0, KartellConfig.INSTANCE.bankHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Bank HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                KartellConfig.INSTANCE.bankHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private void addTimestampField(int centerX, int y) {
        TextFieldWidget timestampField = new TextFieldWidget(
                textRenderer,
                centerX - BUTTON_W / 2,
                y,
                BUTTON_W,
                BUTTON_H,
                Text.literal("Timestamp Format")
        );
        timestampField.setMaxLength(32);
        timestampField.setText(KartellConfig.INSTANCE.chatTimestampFormat);
        timestampField.setChangedListener(text -> KartellConfig.INSTANCE.chatTimestampFormat = text);
        addDrawableChild(timestampField);
    }

    private void addSaveAndCloseButton(int centerX) {
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Speichern & Schliessen"), b -> {
            KartellConfig.save();
            close();
        }).dimensions(centerX - BUTTON_W / 2, height - 28, BUTTON_W, BUTTON_H).build());
    }

    private int addButton(int centerX, int y, String label, ButtonWidget.PressAction action, int stepAfter) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H)
                .build());
        return y + stepAfter;
    }

    private int addToggleButton(
            int centerX,
            int y,
            boolean state,
            String onLabel,
            String offLabel,
            Runnable toggleAction,
            boolean saveImmediately,
            int stepAfter
    ) {
        return addButton(centerX, y, state ? onLabel : offLabel, b -> {
            toggleAction.run();
            if (saveImmediately) {
                KartellConfig.save();
            }
            refreshWidgets();
        }, stepAfter);
    }

    private void openScreen(Screen screen) {
        if (client != null) {
            client.setScreen(screen);
        }
    }

    private void refreshWidgets() {
        clearChildren();
        init();
    }

    private void clampCurrentPage() {
        if (currentPage < 0) currentPage = 0;
        if (currentPage >= SETTINGS_PAGES) currentPage = SETTINGS_PAGES - 1;
    }

    private String zoomKeyLabel() {
        if (capturingZoomKey) return "Zoom Taste: ...";
        int code = KartellConfig.INSTANCE.zoomKeyCode;
        if (code <= 0) return "Zoom Taste: none";
        try {
            return "Zoom Taste: " + InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        } catch (Exception ignored) {
            return "Zoom Taste: " + code;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A7l\u2699 Kartell Einstellungen"),
                width / 2,
                15,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Seite " + (currentPage + 1) + "/" + SETTINGS_PAGES),
                width / 2,
                38,
                0xBBBBBB
        );

        renderInfoPanel(context);
        if (currentPage == 1) {
            renderTimerPreview(context);
            renderSprintPreview(context);
            renderFpsPreview(context);
            renderPaydayPreview(context);
            renderAmmoPreview(context);
            renderBankPreview(context);
        } else if (currentPage == 2) {
            renderHudColorPreview(context);
        }
        renderZoomCaptureHint(context);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A78N = Settings | M = Command Menu"),
                width / 2,
                27,
                0x999999
        );
    }

    private void renderInfoPanel(DrawContext context) {
        int infoY = height - 82;
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Fraktionsmitglieder: \u00A7a" + KartellConfig.INSTANCE.remoteFactionPlayers.size()),
                width / 2 - 150,
                infoY + (currentPage == 1 ? -12 : 0),
                0xAAAAAA
        );
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Blacklist-Spieler: \u00A7c" + KartellConfig.INSTANCE.chatBlacklistPlayers.size()),
                width / 2 - 150,
                infoY + 12 + (currentPage == 1 ? -12 : 0),
                0xAAAAAA
        );
    }

    private void renderTimerPreview(DrawContext context) {
        int tx = KartellConfig.INSTANCE.timerX;
        int ty = KartellConfig.INSTANCE.timerY;
        String previewText = HackTimerHud.secondsRemaining > 0
                ? String.format("Hack: %02d:%02d", HackTimerHud.secondsRemaining / 60, HackTimerHud.secondsRemaining % 60)
                : "Hack: 02:39 (Vorschau)";
        int textWidth = textRenderer.getWidth(previewText);
        context.fill(tx - 4, ty - 4, tx + textWidth + 4, ty + 14, 0xAA000000);
        context.drawTextWithShadow(textRenderer, Text.literal(previewText), tx, ty, 0xFFFFFFFF);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Timer-Position: \u00A7f" + tx + ", " + ty),
                width / 2 - 150,
                height - 58,
                0xAAAAAA
        );
    }

    private void renderSprintPreview(DrawContext context) {
        int sx = KartellConfig.INSTANCE.toggleSprintHudX;
        int sy = KartellConfig.INSTANCE.toggleSprintHudY;
        String sprintPreview = "ToggleSprint: ON";
        context.drawTextWithShadow(textRenderer, Text.literal(sprintPreview), sx, sy, KartellConfig.INSTANCE.toggleSprintHudColor);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77SprintHUD-Position: \u00A7f" + sx + ", " + sy),
                width / 2 - 150,
                height - 46,
                0xAAAAAA
        );
    }

    private void renderFpsPreview(DrawContext context) {
        int fx = KartellConfig.INSTANCE.fpsHudX;
        int fy = KartellConfig.INSTANCE.fpsHudY;
        String fpsPreview = "FPS: 144";
        context.drawTextWithShadow(textRenderer, Text.literal(fpsPreview), fx, fy, KartellConfig.INSTANCE.fpsHudColor);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77FPS-HUD-Position: \u00A7f" + fx + ", " + fy),
                width / 2 - 150,
                height - 34,
                0xAAAAAA
        );
    }

    private void renderPaydayPreview(DrawContext context) {
        int px = KartellConfig.INSTANCE.paydayHudX;
        int py = KartellConfig.INSTANCE.paydayHudY;
        String paydayPreview = "Payday: 25/60 Minuten";
        context.drawTextWithShadow(textRenderer, Text.literal(paydayPreview), px, py, KartellConfig.INSTANCE.paydayHudColor);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Payday-HUD-Position: \u00A7f" + px + ", " + py),
                width / 2 - 150,
                height - 22,
                0xAAAAAA
        );
    }

    private void renderAmmoPreview(DrawContext context) {
        int ax = KartellConfig.INSTANCE.ammoHudX;
        int ay = KartellConfig.INSTANCE.ammoHudY;
        context.drawTextWithShadow(textRenderer, Text.literal("20/96"), ax, ay, 0xFFFFAA33);
        context.drawTextWithShadow(textRenderer, Text.literal("TS19"), ax, ay + 10, 0xFF55FF55);
    }

    private void renderBankPreview(DrawContext context) {
        int bx = KartellConfig.INSTANCE.bankHudX;
        int by = KartellConfig.INSTANCE.bankHudY;
        int live = BankBalanceHud.getCurrentBankBalance();
        String preview = live >= 0
                ? "Bank: " + BankBalanceHud.formatMoney(live) + "$"
                : "Bank: " + BankBalanceHud.formatMoney(88375) + "$";
        context.drawTextWithShadow(textRenderer, Text.literal(preview), bx, by, KartellConfig.INSTANCE.bankHudColor);
    }

    private void renderHudColorPreview(DrawContext context) {
        int cx = width / 2;
        int y = height - 94;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00A77HUD Farb-Vorschau"), cx, y, 0xBBBBBB);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("FPS: 144"), cx, y + 14, KartellConfig.INSTANCE.fpsHudColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Bank: 88.375$"), cx, y + 26, KartellConfig.INSTANCE.bankHudColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Payday: 25/60 Minuten"), cx, y + 38, KartellConfig.INSTANCE.paydayHudColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("ToggleSprint: ON"), cx, y + 50, KartellConfig.INSTANCE.toggleSprintHudColor);
    }

    private void renderZoomCaptureHint(DrawContext context) {
        if (!capturingZoomKey) return;
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("\u00A7eDruecke eine Taste fuer Zoom (ESC = Abbrechen)"),
                width / 2,
                height - 44,
                0xFFFFFF
        );
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        if (!capturingZoomKey) {
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                if (currentPage > 0) {
                    currentPage--;
                    refreshWidgets();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                if (currentPage < SETTINGS_PAGES - 1) {
                    currentPage++;
                    refreshWidgets();
                    return true;
                }
            }
            return super.keyPressed(input);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            capturingZoomKey = false;
            refreshWidgets();
            return true;
        }

        KartellConfig.INSTANCE.zoomKeyCode = keyCode;
        KartellConfig.save();
        capturingZoomKey = false;
        refreshWidgets();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
