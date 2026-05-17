package com.betteruc.gui;

import com.betteruc.client.SyncRefreshActions;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.HackTimerHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BetterUCScreen extends Screen {

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

    public BetterUCScreen() {
        super(Text.literal("betterUC Einstellungen"));
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

        addPageOneButton(leftX, y, pageOneButtonW, "\u00A76Blacklist Gruende", b -> openScreen(new BlacklistConfigScreen(this)));
        addPageOneButton(rightX, y, pageOneButtonW, "\u00A7dEigenbedarf", b -> openScreen(new EigenbedarfConfigScreen(this)));

        y += PAGE_ONE_ROW_STEP;
        addPageOneButton(leftX, y, pageOneButtonW, "\u00A7bHotkey Commands", b -> openScreen(new HotkeyCommandsScreen(this)));
        addPageOneButton(rightX, y, pageOneButtonW, zoomKeyLabel(), b -> {
            capturingZoomKey = true;
            refreshWidgets();
        });

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, BetterUCConfig.INSTANCE.toggleSprintEnabled,
                "\u00A7aToggleSprint: AN", "\u00A77ToggleSprint: AUS",
                () -> BetterUCConfig.INSTANCE.toggleSprintEnabled = !BetterUCConfig.INSTANCE.toggleSprintEnabled, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, BetterUCConfig.INSTANCE.showHealthHud,
                "\u00A7aHerz-Anzeige: AN", "\u00A77Herz-Anzeige: AUS",
                () -> BetterUCConfig.INSTANCE.showHealthHud = !BetterUCConfig.INSTANCE.showHealthHud, false);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, BetterUCConfig.INSTANCE.showFpsHud,
                "\u00A7aFPS-HUD: AN", "\u00A77FPS-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showFpsHud = !BetterUCConfig.INSTANCE.showFpsHud, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, BetterUCConfig.INSTANCE.showPaydayHud,
                "\u00A7aPayday-HUD: AN", "\u00A77Payday-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showPaydayHud = !BetterUCConfig.INSTANCE.showPaydayHud, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, BetterUCConfig.INSTANCE.showAmmoHud,
                "\u00A7aAmmo-HUD: AN", "\u00A77Ammo-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showAmmoHud = !BetterUCConfig.INSTANCE.showAmmoHud, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, BetterUCConfig.INSTANCE.showBankHud,
                "\u00A7aBank-HUD: AN", "\u00A77Bank-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showBankHud = !BetterUCConfig.INSTANCE.showBankHud, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, BetterUCConfig.INSTANCE.showPotionEffectsHud,
                "\u00A7aPotion-HUD: AN", "\u00A77Potion-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showPotionEffectsHud = !BetterUCConfig.INSTANCE.showPotionEffectsHud, true);
        addPageOneToggleButton(rightX, y, pageOneButtonW, BetterUCConfig.INSTANCE.zoomEnabled,
                "\u00A7aZoom: AN", "\u00A77Zoom: AUS",
                () -> BetterUCConfig.INSTANCE.zoomEnabled = !BetterUCConfig.INSTANCE.zoomEnabled, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, BetterUCConfig.INSTANCE.carAutomationEnabled,
                "\u00A7aCar Auto: AN", "\u00A77Car Auto: AUS",
                () -> BetterUCConfig.INSTANCE.carAutomationEnabled = !BetterUCConfig.INSTANCE.carAutomationEnabled, true);

        y += PAGE_ONE_ROW_STEP;
        addPageOneToggleButton(leftX, y, pageOneButtonW, BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled,
                "\u00A7aAuto-Stats Join: AN", "\u00A77Auto-Stats Join: AUS",
                () -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled = !BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled, true);
        addPageOneButton(rightX, y, pageOneButtonW, "\u00A7eStats neu laden",
                b -> SyncRefreshActions.requestStatsRefresh(client, true));

        y += PAGE_ONE_ROW_STEP;
        addPageOneButton(leftX, y, pageOneButtonW, "\u00A76Changelog & Features",
                b -> openScreen(new ChangelogScreen(this)));
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
                BetterUCConfig.save();
            }
            refreshWidgets();
        });
    }

    private int addFeatureToggleButtons(int centerX, int y) {
        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.showHealthHud,
                "\u00A7aHerz-Anzeige: AN",
                "\u00A77Herz-Anzeige: AUS",
                () -> BetterUCConfig.INSTANCE.showHealthHud = !BetterUCConfig.INSTANCE.showHealthHud,
                false,
                BUTTON_STEP_NORMAL
        );

        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.showFpsHud,
                "\u00A7aFPS-HUD: AN",
                "\u00A77FPS-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showFpsHud = !BetterUCConfig.INSTANCE.showFpsHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.showPaydayHud,
                "\u00A7aPayday-HUD: AN",
                "\u00A77Payday-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showPaydayHud = !BetterUCConfig.INSTANCE.showPaydayHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.showAmmoHud,
                "\u00A7aAmmo-HUD: AN",
                "\u00A77Ammo-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showAmmoHud = !BetterUCConfig.INSTANCE.showAmmoHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.showBankHud,
                "\u00A7aBank-HUD: AN",
                "\u00A77Bank-HUD: AUS",
                () -> BetterUCConfig.INSTANCE.showBankHud = !BetterUCConfig.INSTANCE.showBankHud,
                true,
                BUTTON_STEP_SMALL
        );

        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.toggleSprintEnabled,
                "\u00A7aToggleSprint: AN",
                "\u00A77ToggleSprint: AUS",
                () -> BetterUCConfig.INSTANCE.toggleSprintEnabled = !BetterUCConfig.INSTANCE.toggleSprintEnabled,
                true,
                BUTTON_STEP_NORMAL
        );

        y = addToggleButton(
                centerX,
                y,
                BetterUCConfig.INSTANCE.zoomEnabled,
                "\u00A7aZoom: AN",
                "\u00A77Zoom: AUS",
                () -> BetterUCConfig.INSTANCE.zoomEnabled = !BetterUCConfig.INSTANCE.zoomEnabled,
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
                BetterUCConfig.INSTANCE.carAutomationEnabled,
                "\u00A7aCar Auto: AN",
                "\u00A77Car Auto: AUS",
                () -> BetterUCConfig.INSTANCE.carAutomationEnabled = !BetterUCConfig.INSTANCE.carAutomationEnabled,
                true,
                BUTTON_STEP_SMALL
        );

        return y + SECTION_GAP;
    }

    private int addPositionSliders(int centerX, int y, int maxScreenX, int maxScreenY) {
        int leftCenterX = centerX - BUTTON_W / 2 - PAGE_ONE_COLUMN_GAP / 2;
        int rightCenterX = centerX + BUTTON_W / 2 + PAGE_ONE_COLUMN_GAP / 2;

        int timerY = y;
        timerY = addPlantTimerXSlider(leftCenterX, timerY, maxScreenX);
        timerY = addPlantTimerYSlider(leftCenterX, timerY, maxScreenY);
        timerY = addHackTimerXSlider(leftCenterX, timerY, maxScreenX);
        timerY = addHackTimerYSlider(leftCenterX, timerY, maxScreenY);

        int hudY = y;
        hudY = addHealthHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addHealthHudYSlider(rightCenterX, hudY, maxScreenY);
        hudY = addSprintHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addSprintHudYSlider(rightCenterX, hudY, maxScreenY);
        hudY = addFpsHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addFpsHudYSlider(rightCenterX, hudY, maxScreenY);
        hudY = addPaydayHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addPaydayHudYSlider(rightCenterX, hudY, maxScreenY);
        hudY = addAmmoHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addAmmoHudYSlider(rightCenterX, hudY, maxScreenY);
        hudY = addBankHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addBankHudYSlider(rightCenterX, hudY, maxScreenY);
        hudY = addPotionHudXSlider(rightCenterX, hudY, maxScreenX);
        hudY = addPotionHudYSlider(rightCenterX, hudY, maxScreenY);

        return Math.max(timerY, hudY);
    }

    private int addHudColorButtons(int centerX, int y) {
        y = addButton(centerX, y, "\u00A7bFPS HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "FPS HUD Farbe",
                "\u00A7bFPS HUD Farbe waehlen",
                BetterUCConfig.INSTANCE.fpsHudColor,
                c -> BetterUCConfig.INSTANCE.fpsHudColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7ePayday HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "Payday HUD Farbe",
                "\u00A7ePayday HUD Farbe waehlen",
                BetterUCConfig.INSTANCE.paydayHudColor,
                c -> BetterUCConfig.INSTANCE.paydayHudColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7bBank HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "Bank HUD Farbe",
                "\u00A7bBank HUD Farbe waehlen",
                BetterUCConfig.INSTANCE.bankHudColor,
                c -> BetterUCConfig.INSTANCE.bankHudColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7cHealth Herz Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "Health Herz Farbe",
                "\u00A7cHealth Herz Farbe waehlen",
                BetterUCConfig.INSTANCE.healthHudHeartColor,
                c -> BetterUCConfig.INSTANCE.healthHudHeartColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7cHealth Zahl Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "Health Zahl Farbe",
                "\u00A7cHealth Zahl Farbe waehlen",
                BetterUCConfig.INSTANCE.healthHudTextColor,
                c -> BetterUCConfig.INSTANCE.healthHudTextColor = c
        )), BUTTON_STEP_SMALL);

        y = addButton(centerX, y, "\u00A7aToggleSprint HUD Farbe", b -> openScreen(new ColorPickerScreen(
                this,
                "ToggleSprint HUD Farbe",
                "\u00A7aToggleSprint HUD Farbe waehlen",
                BetterUCConfig.INSTANCE.toggleSprintHudColor,
                c -> BetterUCConfig.INSTANCE.toggleSprintHudColor = c
        )), BUTTON_STEP_SMALL);

        return y + SECTION_GAP;
    }

    private int addHealthHudXSlider(int centerX, int y, int maxScreenX) {
        int currentX = resolveHealthHudPreviewX();
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Health HUD X: " + currentX),
                Math.max(0.0, Math.min(1.0, currentX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Health HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.healthHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addHealthHudYSlider(int centerX, int y, int maxScreenY) {
        int currentY = resolveHealthHudPreviewY();
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Health HUD Y: " + currentY),
                Math.max(0.0, Math.min(1.0, currentY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Health HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.healthHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addHackTimerXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Hack-Timer X: " + BetterUCConfig.INSTANCE.hackTimerX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.hackTimerX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Hack-Timer X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.hackTimerX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addHackTimerYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Hack-Timer Y: " + BetterUCConfig.INSTANCE.hackTimerY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.hackTimerY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Hack-Timer Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.hackTimerY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addPlantTimerXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Plant-Timer X: " + BetterUCConfig.INSTANCE.plantTimerX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.plantTimerX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Plant-Timer X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.plantTimerX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addPlantTimerYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Plant-Timer Y: " + BetterUCConfig.INSTANCE.plantTimerY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.plantTimerY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Plant-Timer Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.plantTimerY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addSprintHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Sprint HUD X: " + BetterUCConfig.INSTANCE.toggleSprintHudX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.toggleSprintHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Sprint HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.toggleSprintHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addSprintHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Sprint HUD Y: " + BetterUCConfig.INSTANCE.toggleSprintHudY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.toggleSprintHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Sprint HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.toggleSprintHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addFpsHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("FPS HUD X: " + BetterUCConfig.INSTANCE.fpsHudX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.fpsHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("FPS HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.fpsHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addFpsHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("FPS HUD Y: " + BetterUCConfig.INSTANCE.fpsHudY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.fpsHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("FPS HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.fpsHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addPaydayHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Payday HUD X: " + BetterUCConfig.INSTANCE.paydayHudX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.paydayHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Payday HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.paydayHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addPaydayHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Payday HUD Y: " + BetterUCConfig.INSTANCE.paydayHudY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.paydayHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Payday HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.paydayHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addAmmoHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Ammo HUD X: " + BetterUCConfig.INSTANCE.ammoHudX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.ammoHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Ammo HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.ammoHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addAmmoHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Ammo HUD Y: " + BetterUCConfig.INSTANCE.ammoHudY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.ammoHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Ammo HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.ammoHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addBankHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Bank HUD X: " + BetterUCConfig.INSTANCE.bankHudX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.bankHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Bank HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.bankHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addBankHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Bank HUD Y: " + BetterUCConfig.INSTANCE.bankHudY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.bankHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Bank HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.bankHudY = (int) (value * maxScreenY);
            }
        });
        return y + BUTTON_STEP_NORMAL;
    }

    private int addPotionHudXSlider(int centerX, int y, int maxScreenX) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Potion HUD X: " + BetterUCConfig.INSTANCE.potionHudX),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.potionHudX / (double) maxScreenX))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Potion HUD X: " + (int) (value * maxScreenX)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.potionHudX = (int) (value * maxScreenX);
            }
        });
        return y + BUTTON_STEP_SMALL;
    }

    private int addPotionHudYSlider(int centerX, int y, int maxScreenY) {
        addDrawableChild(new SliderWidget(
                centerX - BUTTON_W / 2, y, BUTTON_W, BUTTON_H,
                Text.literal("Potion HUD Y: " + BetterUCConfig.INSTANCE.potionHudY),
                Math.max(0.0, Math.min(1.0, BetterUCConfig.INSTANCE.potionHudY / (double) maxScreenY))
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Potion HUD Y: " + (int) (value * maxScreenY)));
            }

            @Override
            protected void applyValue() {
                BetterUCConfig.INSTANCE.potionHudY = (int) (value * maxScreenY);
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
        timestampField.setText(BetterUCConfig.INSTANCE.chatTimestampFormat);
        timestampField.setChangedListener(text -> BetterUCConfig.INSTANCE.chatTimestampFormat = text);
        addDrawableChild(timestampField);
    }

    private void addSaveAndCloseButton(int centerX) {
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Speichern & Schliessen"), b -> {
            BetterUCConfig.save();
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
                BetterUCConfig.save();
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
        int code = BetterUCConfig.INSTANCE.zoomKeyCode;
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
                Text.literal("\u00A7l\u2699 betterUC Einstellungen"),
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
            renderPositionColumnLabels(context);
            renderTimerPreview(context);
            renderHealthPreview(context);
            renderSprintPreview(context);
            renderFpsPreview(context);
            renderPaydayPreview(context);
            renderAmmoPreview(context);
            renderBankPreview(context);
            renderPotionPreview(context);
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
                Text.literal("\u00A77Blacklist: \u00A7c" + BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
                        + "\u00A77 | \u00A7f" + syncAge(BetterUCConfig.INSTANCE.lastBlacklistSyncMs)),
                width / 2 - 150,
                infoY + (currentPage == 1 ? -12 : 0),
                0xAAAAAA
        );
    }

    private String syncAge(long timestampMs) {
        if (timestampMs <= 0L) return "noch nicht geladen";
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
        if (ageSeconds < 60L) return "vor " + ageSeconds + "s";
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) return "vor " + ageMinutes + "m";
        return "vor " + (ageMinutes / 60L) + "h";
    }

    private void renderPositionColumnLabels(DrawContext context) {
        int leftCenterX = width / 2 - BUTTON_W / 2 - PAGE_ONE_COLUMN_GAP / 2;
        int rightCenterX = width / 2 + BUTTON_W / 2 + PAGE_ONE_COLUMN_GAP / 2;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00A7eTimer"), leftCenterX, 49, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00A7bHUDs"), rightCenterX, 49, 0xFFFFFF);
    }

    private void renderTimerPreview(DrawContext context) {
        int hx = BetterUCConfig.INSTANCE.hackTimerX;
        int hy = BetterUCConfig.INSTANCE.hackTimerY;
        String hackPreview = HackTimerHud.secondsRemaining > 0
                ? String.format("Hack: %02d:%02d", HackTimerHud.secondsRemaining / 60, HackTimerHud.secondsRemaining % 60)
                : "Hack: 02:39 (Vorschau)";
        renderTimerPreviewLine(context, hackPreview, hx, hy, 0xFFFFFFFF);

        int px = BetterUCConfig.INSTANCE.plantTimerX;
        int py = BetterUCConfig.INSTANCE.plantTimerY;
        renderTimerPreviewLine(context, "Plant: 1:30:00 | Wasser: 20:00 | Duenger: 25:00", px, py, 0xFFFFD866);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Timer: \u00A7fHack " + hx + "," + hy
                        + " | Plant " + px + "," + py),
                width / 2 - 150,
                height - 58,
                0xAAAAAA
        );
    }

    private void renderTimerPreviewLine(DrawContext context, String text, int x, int y, int color) {
        int textWidth = textRenderer.getWidth(text);
        context.fill(x - 4, y - 4, x + textWidth + 4, y + 14, 0xAA000000);
        context.drawTextWithShadow(textRenderer, Text.literal(text), x, y, color);
    }

    private void renderSprintPreview(DrawContext context) {
        int sx = BetterUCConfig.INSTANCE.toggleSprintHudX;
        int sy = BetterUCConfig.INSTANCE.toggleSprintHudY;
        String sprintPreview = "ToggleSprint: ON";
        context.drawTextWithShadow(textRenderer, Text.literal(sprintPreview), sx, sy, BetterUCConfig.INSTANCE.toggleSprintHudColor);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77SprintHUD-Position: \u00A7f" + sx + ", " + sy),
                width / 2 - 150,
                height - 46,
                0xAAAAAA
        );
    }

    private void renderFpsPreview(DrawContext context) {
        int fx = BetterUCConfig.INSTANCE.fpsHudX;
        int fy = BetterUCConfig.INSTANCE.fpsHudY;
        String fpsPreview = "FPS: 144";
        context.drawTextWithShadow(textRenderer, Text.literal(fpsPreview), fx, fy, BetterUCConfig.INSTANCE.fpsHudColor);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77FPS-HUD-Position: \u00A7f" + fx + ", " + fy),
                width / 2 - 150,
                height - 34,
                0xAAAAAA
        );
    }

    private void renderPaydayPreview(DrawContext context) {
        int px = BetterUCConfig.INSTANCE.paydayHudX;
        int py = BetterUCConfig.INSTANCE.paydayHudY;
        String paydayPreview = "Payday: 25/60 Minuten";
        context.drawTextWithShadow(textRenderer, Text.literal(paydayPreview), px, py, BetterUCConfig.INSTANCE.paydayHudColor);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("\u00A77Payday-HUD-Position: \u00A7f" + px + ", " + py),
                width / 2 - 150,
                height - 22,
                0xAAAAAA
        );
    }

    private void renderAmmoPreview(DrawContext context) {
        int ax = BetterUCConfig.INSTANCE.ammoHudX;
        int ay = BetterUCConfig.INSTANCE.ammoHudY;
        context.drawTextWithShadow(textRenderer, Text.literal("20/96"), ax, ay, 0xFFFFAA33);
        context.drawTextWithShadow(textRenderer, Text.literal("TS19"), ax, ay + 10, 0xFF55FF55);
    }

    private void renderBankPreview(DrawContext context) {
        int bx = BetterUCConfig.INSTANCE.bankHudX;
        int by = BetterUCConfig.INSTANCE.bankHudY;
        int live = BankBalanceHud.getCurrentBankBalance();
        String preview = live >= 0
                ? "Bank: " + BankBalanceHud.formatMoney(live) + "$"
                : "Bank: " + BankBalanceHud.formatMoney(88375) + "$";
        context.drawTextWithShadow(textRenderer, Text.literal(preview), bx, by, BetterUCConfig.INSTANCE.bankHudColor);
    }

    private void renderPotionPreview(DrawContext context) {
        int px = BetterUCConfig.INSTANCE.potionHudX;
        int py = BetterUCConfig.INSTANCE.potionHudY;
        context.drawTextWithShadow(textRenderer, Text.literal("Staerke II 1:26"), px, py, 0xFF9328FF);
        context.drawTextWithShadow(textRenderer, Text.literal("Schnelligkeit 0:49"), px, py + textRenderer.fontHeight + 1, 0xFF7CAFC6);
    }

    private void renderHealthPreview(DrawContext context) {
        int x = resolveHealthHudPreviewX();
        int y = resolveHealthHudPreviewY();
        String healthText = "10";
        int heartColor = BetterUCConfig.INSTANCE.healthHudHeartColor;
        int textColor = BetterUCConfig.INSTANCE.healthHudTextColor;
        context.drawGuiTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                x,
                y,
                9,
                9,
                heartColor
        );
        context.drawText(textRenderer, Text.literal(healthText), x + 11, y, textColor, true);
    }

    private int resolveHealthHudPreviewX() {
        if (BetterUCConfig.INSTANCE.healthHudX >= 0) {
            return BetterUCConfig.INSTANCE.healthHudX;
        }
        String healthText = "10";
        int totalWidth = 9 + 2 + textRenderer.getWidth(healthText);
        return width / 2 - totalWidth / 2;
    }

    private int resolveHealthHudPreviewY() {
        if (BetterUCConfig.INSTANCE.healthHudY >= 0) {
            return BetterUCConfig.INSTANCE.healthHudY;
        }
        return height / 2 + 15;
    }

    private void renderHudColorPreview(DrawContext context) {
        int cx = width / 2;
        int y = height - 118;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00A77HUD Farb-Vorschau"), cx, y, 0xBBBBBB);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("FPS: 144"), cx, y + 14, BetterUCConfig.INSTANCE.fpsHudColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Bank: 88.375$"), cx, y + 26, BetterUCConfig.INSTANCE.bankHudColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Payday: 25/60 Minuten"), cx, y + 38, BetterUCConfig.INSTANCE.paydayHudColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Health Herz"), cx, y + 50, BetterUCConfig.INSTANCE.healthHudHeartColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Health Zahl: 10"), cx, y + 62, BetterUCConfig.INSTANCE.healthHudTextColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("ToggleSprint: ON"), cx, y + 74, BetterUCConfig.INSTANCE.toggleSprintHudColor);
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

        BetterUCConfig.INSTANCE.zoomKeyCode = keyCode;
        BetterUCConfig.save();
        capturingZoomKey = false;
        refreshWidgets();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
