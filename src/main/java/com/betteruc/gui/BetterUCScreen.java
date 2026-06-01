package com.betteruc.gui;

import com.betteruc.client.BetterUCFontManager;
import com.betteruc.client.SyncRefreshActions;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.ModernHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

public class BetterUCScreen extends Screen {

    private static final int PANEL_BG = 0xDD0D1117;
    private static final int PANEL_INNER = 0xCC141A22;
    private static final int PANEL_ALT = 0xB81B222D;
    private static final int PANEL_BORDER = 0x80333C49;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int TEXT_SOFT = 0xFFCBD5E1;
    private static final int BUTTON_H = 20;
    private static final int MODULE_H = 16;
    private static final int MODULE_GAP = 3;

    private Category selectedCategory = Category.HUD;
    private ModuleOption selectedModule = ModuleOption.FPS;
    private boolean capturingZoomKey = false;

    public BetterUCScreen() {
        super(Text.literal("betterUC ClickGUI"));
    }

    @Override
    protected void init() {
        if (selectedModule.category != selectedCategory) {
            selectedModule = firstModuleFor(selectedCategory);
        }

        addDetailControls();
        addDrawableChild(ButtonWidget.builder(Text.literal("Speichern & Schliessen"), b -> {
            BetterUCConfig.save();
            close();
        }).dimensions(mainX() + mainW() - 150, mainY() + mainH() - 28, 138, BUTTON_H).build());
    }

    private void addDetailControls() {
        int x = detailX() + 14;
        int y = detailY() + 58;
        int controlW = Math.max(120, Math.min(194, detailW() - 28));
        int maxScreenX = Math.max(1, width - 1);
        int maxScreenY = Math.max(1, height - 1);

        if (selectedModule.hasHudStyle()) {
            y = addHudStyleButton(x, y, controlW, selectedModule);
            if (BetterUCConfig.isCustomHudStyle(getHudStyle(selectedModule))) {
                y = addCustomFontControls(x, y, controlW, selectedModule);
            }
        }

        switch (selectedModule) {
            case HEALTH -> {
                y = addToggle(x, y, controlW, "Health HUD", BetterUCConfig.INSTANCE.showHealthHud,
                        () -> BetterUCConfig.INSTANCE.showHealthHud = !BetterUCConfig.INSTANCE.showHealthHud);
                y = addIntSlider(x, y, controlW, "Health X", resolveHealthHudPreviewX(), maxScreenX,
                        value -> BetterUCConfig.INSTANCE.healthHudX = value);
                y = addIntSlider(x, y, controlW, "Health Y", resolveHealthHudPreviewY(), maxScreenY,
                        value -> BetterUCConfig.INSTANCE.healthHudY = value);
                y = addColorButton(x, y, controlW, "Herz Farbe", BetterUCConfig.INSTANCE.healthHudHeartColor,
                        color -> BetterUCConfig.INSTANCE.healthHudHeartColor = color);
                addColorButton(x, y, controlW, "Zahl Farbe", BetterUCConfig.INSTANCE.healthHudTextColor,
                        color -> BetterUCConfig.INSTANCE.healthHudTextColor = color);
            }
            case FPS -> {
                y = addToggle(x, y, controlW, "FPS HUD", BetterUCConfig.INSTANCE.showFpsHud,
                        () -> BetterUCConfig.INSTANCE.showFpsHud = !BetterUCConfig.INSTANCE.showFpsHud);
                y = addIntSlider(x, y, controlW, "FPS X", BetterUCConfig.INSTANCE.fpsHudX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.fpsHudX = value);
                y = addIntSlider(x, y, controlW, "FPS Y", BetterUCConfig.INSTANCE.fpsHudY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.fpsHudY = value);
                addColorButton(x, y, controlW, "FPS Farbe", BetterUCConfig.INSTANCE.fpsHudColor,
                        color -> BetterUCConfig.INSTANCE.fpsHudColor = color);
            }
            case PAYDAY -> {
                y = addToggle(x, y, controlW, "Payday HUD", BetterUCConfig.INSTANCE.showPaydayHud,
                        () -> BetterUCConfig.INSTANCE.showPaydayHud = !BetterUCConfig.INSTANCE.showPaydayHud);
                y = addIntSlider(x, y, controlW, "Payday X", BetterUCConfig.INSTANCE.paydayHudX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.paydayHudX = value);
                y = addIntSlider(x, y, controlW, "Payday Y", BetterUCConfig.INSTANCE.paydayHudY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.paydayHudY = value);
                addColorButton(x, y, controlW, "Payday Farbe", BetterUCConfig.INSTANCE.paydayHudColor,
                        color -> BetterUCConfig.INSTANCE.paydayHudColor = color);
            }
            case AMMO -> {
                y = addToggle(x, y, controlW, "Ammo HUD", BetterUCConfig.INSTANCE.showAmmoHud,
                        () -> BetterUCConfig.INSTANCE.showAmmoHud = !BetterUCConfig.INSTANCE.showAmmoHud);
                y = addIntSlider(x, y, controlW, "Ammo X", BetterUCConfig.INSTANCE.ammoHudX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.ammoHudX = value);
                addIntSlider(x, y, controlW, "Ammo Y", BetterUCConfig.INSTANCE.ammoHudY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.ammoHudY = value);
            }
            case BANK -> {
                y = addToggle(x, y, controlW, "Bank HUD", BetterUCConfig.INSTANCE.showBankHud,
                        () -> BetterUCConfig.INSTANCE.showBankHud = !BetterUCConfig.INSTANCE.showBankHud);
                y = addIntSlider(x, y, controlW, "Bank X", BetterUCConfig.INSTANCE.bankHudX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.bankHudX = value);
                y = addIntSlider(x, y, controlW, "Bank Y", BetterUCConfig.INSTANCE.bankHudY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.bankHudY = value);
                addColorButton(x, y, controlW, "Bank Farbe", BetterUCConfig.INSTANCE.bankHudColor,
                        color -> BetterUCConfig.INSTANCE.bankHudColor = color);
            }
            case POTION -> {
                y = addToggle(x, y, controlW, "Potion HUD", BetterUCConfig.INSTANCE.showPotionEffectsHud,
                        () -> BetterUCConfig.INSTANCE.showPotionEffectsHud = !BetterUCConfig.INSTANCE.showPotionEffectsHud);
                y = addIntSlider(x, y, controlW, "Potion X", BetterUCConfig.INSTANCE.potionHudX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.potionHudX = value);
                addIntSlider(x, y, controlW, "Potion Y", BetterUCConfig.INSTANCE.potionHudY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.potionHudY = value);
            }
            case SPRINT -> {
                y = addToggle(x, y, controlW, "ToggleSprint", BetterUCConfig.INSTANCE.toggleSprintEnabled,
                        () -> BetterUCConfig.INSTANCE.toggleSprintEnabled = !BetterUCConfig.INSTANCE.toggleSprintEnabled);
                y = addIntSlider(x, y, controlW, "Sprint X", BetterUCConfig.INSTANCE.toggleSprintHudX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.toggleSprintHudX = value);
                y = addIntSlider(x, y, controlW, "Sprint Y", BetterUCConfig.INSTANCE.toggleSprintHudY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.toggleSprintHudY = value);
                addColorButton(x, y, controlW, "Sprint Farbe", BetterUCConfig.INSTANCE.toggleSprintHudColor,
                        color -> BetterUCConfig.INSTANCE.toggleSprintHudColor = color);
            }
            case HACK_TIMER -> {
                y = addIntSlider(x, y, controlW, "Hack X", BetterUCConfig.INSTANCE.hackTimerX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.hackTimerX = value);
                addIntSlider(x, y, controlW, "Hack Y", BetterUCConfig.INSTANCE.hackTimerY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.hackTimerY = value);
            }
            case PLANT_TIMER -> {
                y = addIntSlider(x, y, controlW, "Plant X", BetterUCConfig.INSTANCE.plantTimerX, maxScreenX,
                        value -> BetterUCConfig.INSTANCE.plantTimerX = value);
                addIntSlider(x, y, controlW, "Plant Y", BetterUCConfig.INSTANCE.plantTimerY, maxScreenY,
                        value -> BetterUCConfig.INSTANCE.plantTimerY = value);
            }
            case ZOOM -> {
                y = addToggle(x, y, controlW, "Zoom", BetterUCConfig.INSTANCE.zoomEnabled,
                        () -> BetterUCConfig.INSTANCE.zoomEnabled = !BetterUCConfig.INSTANCE.zoomEnabled);
                y = addButton(x, y, controlW, zoomKeyLabel(), b -> {
                    capturingZoomKey = true;
                    refreshWidgets();
                });
                addDoubleSlider(x, y, controlW, "Zoom FOV", BetterUCConfig.INSTANCE.zoomFovMultiplier, 0.05, 0.80,
                        value -> BetterUCConfig.INSTANCE.zoomFovMultiplier = (float) value);
            }
            case AUTO_STATS -> {
                y = addToggle(x, y, controlW, "Auto-Stats Join", BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled,
                        () -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled = !BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled);
                addButton(x, y, controlW, "Stats neu laden", b -> SyncRefreshActions.requestStatsRefresh(client, true));
            }
            case CHAT -> addTimestampField(x, y, controlW);
            case BLACKLIST -> {
                y = addButton(x, y, controlW, "Blacklist Gruende", b -> openScreen(new BlacklistConfigScreen(this)));
                addButton(x, y, controlW, "Stats neu laden", b -> SyncRefreshActions.requestStatsRefresh(client, true));
            }
            case HOTKEYS -> addButton(x, y, controlW, "Hotkey Commands", b -> openScreen(new HotkeyCommandsScreen(this)));
            case COMMANDS -> {
                y = addButton(x, y, controlW, "Command Menu", b -> openScreen(new CommandGui()));
                addButton(x, y, controlW, "Changelog", b -> openScreen(new ChangelogScreen(this)));
            }
        }
    }

    private int addToggle(int x, int y, int width, String label, boolean active, Runnable toggleAction) {
        return addButton(x, y, width, label + ": " + (active ? "AN" : "AUS"), b -> {
            toggleAction.run();
            BetterUCConfig.save();
            refreshWidgets();
        });
    }

    private int addHudStyleButton(int x, int y, int width, ModuleOption module) {
        return addButton(x, y, width, "Stil: " + BetterUCConfig.hudStyleLabel(getHudStyle(module)), b -> {
            setHudStyle(module, BetterUCConfig.toggleHudStyle(getHudStyle(module)));
            BetterUCConfig.save();
            refreshWidgets();
        });
    }

    private int addCustomFontControls(int x, int y, int width, ModuleOption module) {
        y = addButton(x, y, width, "Font: " + BetterUCFontManager.selectedFontLabel(getHudFont(module)), b -> {
            setHudFont(module, BetterUCFontManager.nextCustomFontId(getHudFont(module)));
            BetterUCConfig.save();
            BetterUCFontManager.rebuildAndReload(client);
            refreshWidgets();
        });
        y = addButton(x, y, width, "Fonts neu laden", b -> {
            BetterUCFontManager.rebuildAndReload(client);
            refreshWidgets();
        });
        return addButton(x, y, width, "Font Ordner", b -> BetterUCFontManager.openFontsFolder(client));
    }

    private int addButton(int x, int y, int width, String label, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, width, BUTTON_H)
                .build());
        return y + 24;
    }

    private int addColorButton(
            int x,
            int y,
            int width,
            String label,
            int color,
            ColorPickerScreen.ColorApplyTarget target
    ) {
        return addButton(x, y, width, label + " #" + hex(color), b -> openScreen(new ColorPickerScreen(
                this,
                label,
                label + " waehlen",
                color,
                target
        )));
    }

    private int addIntSlider(int x, int y, int width, String label, int current, int max, IntConsumer setter) {
        int safeMax = Math.max(1, max);
        int safeCurrent = clamp(current, 0, safeMax);
        addDrawableChild(new SliderWidget(
                x,
                y,
                width,
                BUTTON_H,
                Text.literal(label + ": " + safeCurrent),
                safeCurrent / (double) safeMax
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(label + ": " + sliderIntValue(value, safeMax)));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderIntValue(value, safeMax));
            }
        });
        return y + 24;
    }

    private int addDoubleSlider(
            int x,
            int y,
            int width,
            String label,
            double current,
            double min,
            double max,
            DoubleConsumer setter
    ) {
        double normalized = clamp01((current - min) / Math.max(0.0001, max - min));
        addDrawableChild(new SliderWidget(
                x,
                y,
                width,
                BUTTON_H,
                Text.literal(label + ": " + percent(current)),
                normalized
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(label + ": " + percent(sliderDoubleValue(value, min, max))));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderDoubleValue(value, min, max));
            }
        });
        return y + 24;
    }

    private void addTimestampField(int x, int y, int width) {
        TextFieldWidget timestampField = new TextFieldWidget(
                textRenderer,
                x,
                y,
                width,
                BUTTON_H,
                Text.literal("Timestamp Format")
        );
        timestampField.setMaxLength(32);
        timestampField.setText(BetterUCConfig.INSTANCE.chatTimestampFormat);
        timestampField.setChangedListener(text -> BetterUCConfig.INSTANCE.chatTimestampFormat = text);
        addDrawableChild(timestampField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);
        renderFrame(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        renderZoomCaptureHint(context);
    }

    private void renderFrame(DrawContext context, int mouseX, int mouseY) {
        int x = mainX();
        int y = mainY();
        int w = mainW();
        int h = mainH();

        drawSoftRect(context, x, y, w, h, PANEL_BG);
        drawSoftRect(context, x + 1, y + 1, w - 2, h - 2, PANEL_INNER);
        drawBorder(context, x, y, w, h, PANEL_BORDER);
        context.fill(x + 2, y + 2, x + w - 2, y + 3, 0x24FFFFFF);

        context.drawTextWithShadow(textRenderer, Text.literal("betterUC ClickGUI"), x + 14, y + 11, TEXT_PRIMARY);

        renderCategoryTabs(context, mouseX, mouseY);
        renderModuleList(context, mouseX, mouseY);
        renderDetailPanel(context, mouseX, mouseY);
        renderFooter(context);
    }

    private void renderCategoryTabs(DrawContext context, int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = mainY() + 42;
        int tabW = Math.max(38, (sidebarW() - 20) / Category.values().length);

        for (Category category : Category.values()) {
            boolean selected = category == selectedCategory;
            boolean hovered = inBounds(mouseX, mouseY, x, y, tabW, 18);
            int color = selected ? withAlpha(category.accent, 0xCC) : hovered ? 0x80404A59 : 0x50333C49;
            drawSoftRect(context, x, y, tabW - 3, 18, color);
            drawBorder(context, x, y, tabW - 3, 18, selected ? withAlpha(category.accent, 0xFF) : 0x50333C49);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(category.label), x + (tabW - 3) / 2, y + 5,
                    selected ? TEXT_PRIMARY : TEXT_SOFT);
            x += tabW;
        }
    }

    private void renderModuleList(DrawContext context, int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = moduleListY();
        int w = sidebarW() - 20;

        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) continue;
            boolean selected = module == selectedModule;
            boolean hovered = inBounds(mouseX, mouseY, x, y, w, MODULE_H);
            int bg = selected ? 0xB82A3442 : hovered ? 0x80313A47 : 0x4D1F2631;
            drawSoftRect(context, x, y, w, MODULE_H, bg);
            context.fill(x + 2, y + 2, x + 4, y + MODULE_H - 2,
                    selected ? withAlpha(module.accent, 0xFF) : withAlpha(module.accent, 0x99));
            context.drawTextWithShadow(textRenderer, Text.literal(module.label), x + 8, y + 4,
                    selected ? TEXT_PRIMARY : TEXT_SOFT);

            String state = module.hasToggle() ? (isEnabled(module) ? "ON" : "OFF") : "SET";
            int stateColor = module.hasToggle() && !isEnabled(module) ? TEXT_MUTED : withAlpha(module.accent, 0xFF);
            context.drawTextWithShadow(textRenderer, Text.literal(state), x + w - textRenderer.getWidth(state) - 6, y + 4, stateColor);
            y += MODULE_H + MODULE_GAP;
        }
    }

    private void renderDetailPanel(DrawContext context, int mouseX, int mouseY) {
        int x = detailX();
        int y = detailY();
        int w = detailW();
        int h = detailH();
        drawSoftRect(context, x, y, w, h, PANEL_ALT);
        drawBorder(context, x, y, w, h, 0x70333C49);
        context.fill(x + 2, y + 2, x + 4, y + h - 2, withAlpha(selectedModule.accent, 0xEE));

        context.drawTextWithShadow(textRenderer, Text.literal(selectedModule.label), x + 14, y + 12, TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal(selectedModule.description), x + 14, y + 25, TEXT_MUTED);
        renderSelectedStatus(context, x + 14, y + 39);
        renderPreview(context, x + Math.max(224, w - 172), y + 58);
    }

    private void renderSelectedStatus(DrawContext context, int x, int y) {
        if (selectedModule == ModuleOption.BLACKLIST) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Eintraege: " + BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
                            + " | Sync " + syncAge(BetterUCConfig.INSTANCE.lastBlacklistSyncMs)),
                    x,
                    y,
                    TEXT_SOFT
            );
            return;
        }

        if (selectedModule.hasToggle()) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(isEnabled(selectedModule) ? "Status: aktiv" : "Status: aus"),
                    x,
                    y,
                    isEnabled(selectedModule) ? 0xFF86EFAC : TEXT_MUTED
            );
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Modul-Einstellungen"), x, y, TEXT_SOFT);
        }
    }

    private void renderPreview(DrawContext context, int x, int y) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        int previewX = Math.min(x, detailX() + detailW() - 150);
        int previewY = y;

        context.drawTextWithShadow(textRenderer, Text.literal("Preview"), previewX, previewY - 14, TEXT_MUTED);
        String style = getHudStyle(selectedModule);
        String font = getHudFont(selectedModule);
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);
        switch (selectedModule) {
            case HEALTH -> {
                if (modernStyle) {
                    ModernHudRenderer.drawPanel(context, previewX, previewY, 38, 17, BetterUCConfig.INSTANCE.healthHudHeartColor);
                    context.drawGuiTexture(
                            net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                            net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                            previewX + 7,
                            previewY + 4,
                            9,
                            9,
                            BetterUCConfig.INSTANCE.healthHudHeartColor
                    );
                    context.drawText(textRenderer, Text.literal("10"), previewX + 19, previewY + 4,
                            BetterUCConfig.INSTANCE.healthHudTextColor, true);
                    return;
                }
                context.drawGuiTexture(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                        previewX,
                        previewY,
                        9,
                        9,
                        BetterUCConfig.INSTANCE.healthHudHeartColor
                );
                if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, textRenderer, style, font, Text.literal("10"), previewX + 12, previewY,
                            BetterUCConfig.INSTANCE.healthHudTextColor);
                    return;
                }
                context.drawText(textRenderer, Text.literal("10"), previewX + 11, previewY,
                        BetterUCConfig.INSTANCE.healthHudTextColor, true);
            }
            case FPS -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "FPS", "144",
                            BetterUCConfig.INSTANCE.fpsHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "FPS: 144", previewX, previewY,
                            BetterUCConfig.INSTANCE.fpsHudColor);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("FPS: 144"), previewX, previewY,
                            BetterUCConfig.INSTANCE.fpsHudColor);
                }
            }
            case PAYDAY -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, previewX, previewY, "PAYDAY",
                            "25/60 min", 25.0F / 60.0F, BetterUCConfig.INSTANCE.paydayHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Payday: 25/60 Minuten", previewX, previewY,
                            BetterUCConfig.INSTANCE.paydayHudColor);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("Payday: 25/60 Minuten"), previewX, previewY,
                            BetterUCConfig.INSTANCE.paydayHudColor);
                }
            }
            case AMMO -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "AMMO", "20/96",
                            "TS19", 0xFFFFAA33, 0xFF7CFF8A);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "20/96", previewX, previewY, 0xFFFFAA33);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "TS19", previewX, previewY + 11, 0xFF55FF55);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("20/96"), previewX, previewY, 0xFFFFAA33);
                    context.drawTextWithShadow(textRenderer, Text.literal("TS19"), previewX, previewY + 10, 0xFF55FF55);
                }
            }
            case BANK -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "BANK",
                            previewBankValue(), BetterUCConfig.INSTANCE.bankHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Bank: " + previewBankValue(), previewX, previewY,
                            BetterUCConfig.INSTANCE.bankHudColor);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("Bank: " + previewBankValue()), previewX, previewY,
                            BetterUCConfig.INSTANCE.bankHudColor);
                }
            }
            case POTION -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "EFFECT", "Staerke II",
                            "1:26", 0xFF9328FF);
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY + 33, "EFFECT", "Speed",
                            "0:49", 0xFF7CAFC6);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Staerke II", previewX, previewY, 0xFF9328FF);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "1:26", previewX, previewY + 11, TEXT_MUTED);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Speed", previewX, previewY + 25, 0xFF7CAFC6);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "0:49", previewX, previewY + 36, TEXT_MUTED);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("Staerke II"), previewX, previewY, 0xFF9328FF);
                    context.drawTextWithShadow(textRenderer, Text.literal("1:26"), previewX, previewY + 10, TEXT_MUTED);
                    context.drawTextWithShadow(textRenderer, Text.literal("Speed"), previewX, previewY + 24, 0xFF7CAFC6);
                    context.drawTextWithShadow(textRenderer, Text.literal("0:49"), previewX, previewY + 34, TEXT_MUTED);
                }
            }
            case SPRINT -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "SPRINT", "ON",
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "ToggleSprint: ON", previewX, previewY,
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("ToggleSprint: ON"), previewX, previewY,
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                }
            }
            case HACK_TIMER -> {
                String time = HackTimerHud.secondsRemaining > 0
                        ? String.format(Locale.ROOT, "%02d:%02d", HackTimerHud.secondsRemaining / 60, HackTimerHud.secondsRemaining % 60)
                        : "02:39";
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "HACK", time, 0xFF60A5FA);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Hack: " + time, previewX, previewY, 0xFF60A5FA);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("Hack: " + time), previewX, previewY, 0xFF60A5FA);
                }
            }
            case PLANT_TIMER -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "PLANT",
                            "Plantage Pulver 7/10", "Reif: 1:30:00 | Wasser: 20:00", 0xFF6CF27D, 0xFFFFD866);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Plantage Pulver 7/10", previewX, previewY, 0xFF6CF27D);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Reif: 1:30:00 | Wasser: 20:00", previewX, previewY + 11, 0xFFFFD866);
                } else {
                    context.drawTextWithShadow(textRenderer, Text.literal("Plantage Pulver 7/10"), previewX, previewY, 0xFF6CF27D);
                    context.drawTextWithShadow(textRenderer, Text.literal("Reif: 1:30:00 | Wasser: 20:00"), previewX, previewY + 10, 0xFFFFD866);
                }
            }
            case ZOOM -> drawMiniInfo(context, previewX, previewY, "Zoom", zoomKeyLabel(), BetterUCConfig.INSTANCE.zoomEnabled);
            case AUTO_STATS -> drawMiniInfo(context, previewX, previewY, "Auto-Stats", "Join /stats", BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled);
            case CHAT -> drawMiniInfo(context, previewX, previewY, "Chat", BetterUCConfig.INSTANCE.chatTimestampFormat, true);
            case BLACKLIST -> drawMiniInfo(context, previewX, previewY, "Blacklist",
                    BetterUCConfig.INSTANCE.chatBlacklistPlayers.size() + " Spieler", true);
            case HOTKEYS -> drawMiniInfo(context, previewX, previewY, "Hotkeys",
                    BetterUCConfig.INSTANCE.hotkeyCommands.size() + " Commands", true);
            case COMMANDS -> drawMiniInfo(context, previewX, previewY, "Tools", "Command Menu", true);
        }
    }

    private void drawMiniInfo(DrawContext context, int x, int y, String label, String value, boolean active) {
        int w = Math.max(110, Math.max(textRenderer.getWidth(label), textRenderer.getWidth(value)) + 22);
        ModernHudRenderer.drawPanel(context, x, y, w, 38, active ? selectedModule.accent : 0xFF64748B);
        context.drawTextWithShadow(textRenderer, Text.literal(label), x + 10, y + 8, withAlpha(selectedModule.accent, 0xFF));
        context.drawTextWithShadow(textRenderer, Text.literal(value), x + 10, y + 20, TEXT_PRIMARY);
    }

    private void renderFooter(DrawContext context) {
        int x = mainX() + 12;
        int y = mainY() + mainH() - 23;
        context.drawTextWithShadow(textRenderer, Text.literal("Links Modul waehlen, rechts direkt konfigurieren."),
                x, y, TEXT_MUTED);
    }

    private void renderZoomCaptureHint(DrawContext context) {
        if (!capturingZoomKey) return;
        int w = Math.min(320, width - 28);
        int h = 58;
        int x = width / 2 - w / 2;
        int y = height / 2 - h / 2;
        drawSoftRect(context, x, y, w, h, 0xF0101318);
        drawBorder(context, x, y, w, h, withAlpha(ModuleOption.ZOOM.accent, 0xFF));
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Druecke eine Taste fuer Zoom"), width / 2, y + 17, TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("ESC = Abbrechen"), width / 2, y + 33, TEXT_MUTED);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        double mouseX = event.x();
        double mouseY = event.y();

        Category category = categoryAt(mouseX, mouseY);
        if (category != null) {
            selectedCategory = category;
            selectedModule = firstModuleFor(category);
            refreshWidgets();
            return true;
        }

        ModuleOption module = moduleAt(mouseX, mouseY);
        if (module != null) {
            selectedCategory = module.category;
            selectedModule = module;
            refreshWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        if (!capturingZoomKey) {
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
    public void removed() {
        BetterUCConfig.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Category categoryAt(double mouseX, double mouseY) {
        int x = mainX() + 10;
        int y = mainY() + 42;
        int tabW = Math.max(38, (sidebarW() - 20) / Category.values().length);
        for (Category category : Category.values()) {
            if (inBounds(mouseX, mouseY, x, y, tabW - 3, 18)) return category;
            x += tabW;
        }
        return null;
    }

    private ModuleOption moduleAt(double mouseX, double mouseY) {
        int x = mainX() + 10;
        int y = moduleListY();
        int w = sidebarW() - 20;
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) continue;
            if (inBounds(mouseX, mouseY, x, y, w, MODULE_H)) return module;
            y += MODULE_H + MODULE_GAP;
        }
        return null;
    }

    private ModuleOption firstModuleFor(Category category) {
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category == category) return module;
        }
        return ModuleOption.FPS;
    }

    private boolean isEnabled(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.showHealthHud;
            case FPS -> BetterUCConfig.INSTANCE.showFpsHud;
            case PAYDAY -> BetterUCConfig.INSTANCE.showPaydayHud;
            case AMMO -> BetterUCConfig.INSTANCE.showAmmoHud;
            case BANK -> BetterUCConfig.INSTANCE.showBankHud;
            case POTION -> BetterUCConfig.INSTANCE.showPotionEffectsHud;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintEnabled;
            case ZOOM -> BetterUCConfig.INSTANCE.zoomEnabled;
            case AUTO_STATS -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled;
            default -> true;
        };
    }

    private String getHudStyle(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle;
            default -> BetterUCConfig.HUD_STYLE_MODERN;
        };
    }

    private void setHudStyle(ModuleOption module, String style) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle = style;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle = style;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle = style;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle = style;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle = style;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle = style;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle = style;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle = style;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle = style;
            default -> {
            }
        }
    }

    private String getHudFont(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont;
            default -> BetterUCConfig.INSTANCE.customHudFont;
        };
    }

    private void setHudFont(ModuleOption module, String fontId) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont = fontId;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont = fontId;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont = fontId;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont = fontId;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont = fontId;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont = fontId;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont = fontId;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont = fontId;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont = fontId;
            default -> BetterUCConfig.INSTANCE.customHudFont = fontId;
        }
    }

    private String previewBankValue() {
        int live = BankBalanceHud.getCurrentBankBalance();
        return live >= 0 ? BankBalanceHud.formatMoney(live) + "$" : BankBalanceHud.formatMoney(88375) + "$";
    }

    private int resolveHealthHudPreviewX() {
        if (BetterUCConfig.INSTANCE.healthHudX >= 0) return BetterUCConfig.INSTANCE.healthHudX;
        String healthText = "10";
        int totalWidth = 9 + 2 + textRenderer.getWidth(healthText);
        return width / 2 - totalWidth / 2;
    }

    private int resolveHealthHudPreviewY() {
        if (BetterUCConfig.INSTANCE.healthHudY >= 0) return BetterUCConfig.INSTANCE.healthHudY;
        return height / 2 + 15;
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

    private String syncAge(long timestampMs) {
        if (timestampMs <= 0L) return "nie";
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
        if (ageSeconds < 60L) return "vor " + ageSeconds + "s";
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) return "vor " + ageMinutes + "m";
        return "vor " + (ageMinutes / 60L) + "h";
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

    private int mainW() {
        return Math.max(360, Math.min(width - 20, 760));
    }

    private int mainH() {
        return Math.max(220, Math.min(height - 20, 430));
    }

    private int mainX() {
        return width / 2 - mainW() / 2;
    }

    private int mainY() {
        return height / 2 - mainH() / 2;
    }

    private int sidebarW() {
        return Math.max(128, Math.min(150, mainW() / 4));
    }

    private int detailX() {
        return mainX() + sidebarW() + 10;
    }

    private int detailY() {
        return mainY() + 42;
    }

    private int detailW() {
        return mainW() - sidebarW() - 22;
    }

    private int detailH() {
        return mainH() - 78;
    }

    private int moduleListY() {
        return mainY() + 66;
    }

    private void drawSoftRect(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private boolean inBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private int sliderIntValue(double value, int max) {
        return clamp((int) (value * max), 0, max);
    }

    private double sliderDoubleValue(double value, double min, double max) {
        return min + clamp01(value) * (max - min);
    }

    private String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0x00FFFFFF);
    }

    private enum Category {
        HUD("HUD", 0xFF38BDF8),
        GAMEPLAY("Client", 0xFFA78BFA),
        TOOLS("Tools", 0xFFFBBF24);

        private final String label;
        private final int accent;

        Category(String label, int accent) {
            this.label = label;
            this.accent = accent;
        }
    }

    private enum ModuleOption {
        HEALTH(Category.HUD, "Health", "Transparentes Herz-HUD", 0xFFFF5555, true),
        FPS(Category.HUD, "FPS", "Performance-Modul", BetterUCConfig.DEFAULT_FPS_HUD_COLOR, true),
        PAYDAY(Category.HUD, "Payday", "Payday-Fortschritt", BetterUCConfig.DEFAULT_PAYDAY_HUD_COLOR, true),
        AMMO(Category.HUD, "Ammo", "Munition und Waffe", 0xFFFFAA33, true),
        BANK(Category.HUD, "Bank", "Kontostand im HUD", BetterUCConfig.DEFAULT_BANK_HUD_COLOR, true),
        POTION(Category.HUD, "Potion", "Aktive Effekte", 0xFF9328FF, true),
        SPRINT(Category.HUD, "Sprint", "ToggleSprint Anzeige", BetterUCConfig.DEFAULT_TOGGLE_SPRINT_HUD_COLOR, true),
        HACK_TIMER(Category.HUD, "Hack Timer", "Timer-Position", 0xFF60A5FA, false),
        PLANT_TIMER(Category.HUD, "Plant Timer", "Plantage-Timer", 0xFF6CF27D, false),

        ZOOM(Category.GAMEPLAY, "Zoom", "Taste und FOV", 0xFFA78BFA, true),
        AUTO_STATS(Category.GAMEPLAY, "Auto Stats", "Automatisches /stats", 0xFF34D399, true),
        CHAT(Category.GAMEPLAY, "Chat", "Zeitstempel", 0xFF38BDF8, false),

        BLACKLIST(Category.TOOLS, "Blacklist", "Gruende und Sync", 0xFFF97316, false),
        HOTKEYS(Category.TOOLS, "Hotkeys", "Commands auf Tasten", 0xFFFBBF24, false),
        COMMANDS(Category.TOOLS, "Commands", "Menues und Changelog", 0xFF22C55E, false);

        private final Category category;
        private final String label;
        private final String description;
        private final int accent;
        private final boolean toggle;

        ModuleOption(Category category, String label, String description, int accent, boolean toggle) {
            this.category = category;
            this.label = label;
            this.description = description;
            this.accent = accent;
            this.toggle = toggle;
        }

        private boolean hasToggle() {
            return toggle;
        }

        private boolean hasHudStyle() {
            return category == Category.HUD;
        }
    }
}
