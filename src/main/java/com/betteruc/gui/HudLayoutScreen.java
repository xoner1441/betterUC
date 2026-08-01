package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.CashHud;
import com.betteruc.hud.HealthHud;
import com.betteruc.hud.ModernHudRenderer;
import com.betteruc.hud.PotionEffectsHud;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudLayoutScreen extends Screen {

    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int HANDLE_SIZE = 7;
    private static final int HANDLE_HIT_RADIUS = 5;
    private static final int SNAP_DISTANCE = 8;
    private static final int SNAP_GAP = 2;
    private static final int TOOLBAR_HEIGHT = 48;
    private static final float SCALE_STEP = 0.05F;
    private static final long RESET_CONFIRMATION_MS = 5_000L;
    private final Screen parent;
    private HudModule draggingModule;
    private HudModule resizingModule;
    private HudModule selectedModule;
    private Bounds resizeStartBounds;
    private float resizeStartScale;
    private double resizeStartMouseX;
    private double resizeStartMouseY;
    private Bounds dragStartBounds;
    private int dragOffsetX;
    private int dragOffsetY;
    private Integer snapGuideX;
    private Integer snapGuideY;
    private long resetAllConfirmationUntil;
    private Button scaleDownButton;
    private Button scaleResetButton;
    private Button scaleUpButton;
    private Button resetSelectedButton;
    private Button resetAllButton;

    public HudLayoutScreen(Screen parent) {
        super(Component.literal("HUD Vorschau"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int controlsY = height - 27;
        scaleDownButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustSelectedScale(-SCALE_STEP))
                .bounds(12, controlsY, 24, 20)
                .build());
        scaleResetButton = addRenderableWidget(Button.builder(Component.literal("100%"), b -> setSelectedScale(BetterUCConfig.DEFAULT_HUD_SCALE))
                .bounds(40, controlsY, 52, 20)
                .build());
        scaleUpButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustSelectedScale(SCALE_STEP))
                .bounds(96, controlsY, 24, 20)
                .build());
        resetSelectedButton = addRenderableWidget(Button.builder(Component.literal("HUD zurücksetzen"), b -> resetSelectedLayout())
                .bounds(126, controlsY, 112, 20)
                .build());
        resetAllButton = addRenderableWidget(Button.builder(Component.literal("Alle zurücksetzen"), b -> requestResetAllLayouts())
                .bounds(244, controlsY, 112, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(width - 92, height - 28, 80, 20)
                .build());
        refreshToolbarButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.text(font, Component.literal("HUD Vorschau"), 12, 12, TEXT_PRIMARY);
        context.text(font, Component.literal("Aktive HUDs"), 12, 24, TEXT_MUTED);

        List<HudModule> modules = activeModules();
        if (modules.isEmpty()) {
            context.centeredText(font, Component.literal("Keine HUDs aktiv"), width / 2, height / 2, TEXT_MUTED);
        }

        for (HudModule module : modules) {
            Bounds bounds = boundsFor(module);
            boolean handleHovered = module == selectedModule && resizeHandleContains(bounds, mouseX, mouseY);
            boolean hovered = bounds.contains(mouseX, mouseY) || handleHovered;
            boolean selected = module == selectedModule || module == draggingModule || module == resizingModule;
            drawDragBounds(context, module, bounds, hovered || selected, handleHovered);
            renderHudModule(context, module, bounds.x, bounds.y);
        }

        drawSnapGuides(context);
        drawToolbarStatus(context);
        refreshToolbarButtons();
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        if (selectedModule != null) {
            Bounds selectedBounds = boundsFor(selectedModule);
            if (resizeHandleContains(selectedBounds, event.x(), event.y())) {
                resizingModule = selectedModule;
                resizeStartBounds = selectedBounds;
                resizeStartScale = getScale(selectedModule);
                resizeStartMouseX = event.x();
                resizeStartMouseY = event.y();
                return true;
            }
        }

        List<HudModule> modules = activeModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);
            Bounds bounds = boundsFor(module);
            if (!bounds.contains(event.x(), event.y())) continue;

            draggingModule = module;
            selectedModule = module;
            dragStartBounds = bounds;
            dragOffsetX = (int) Math.round(event.x()) - bounds.x;
            dragOffsetY = (int) Math.round(event.y()) - bounds.y;
            refreshToolbarButtons();
            return true;
        }
        selectedModule = null;
        refreshToolbarButtons();
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (resizingModule != null && click.button() == 0) {
            resizeSelectedModule(click.x(), click.y());
            return true;
        }

        if (draggingModule == null || click.button() != 0) {
            return super.mouseDragged(click, offsetX, offsetY);
        }

        Bounds bounds = boundsFor(draggingModule);
        int maxX = Math.max(0, width - bounds.width);
        int maxY = Math.max(0, height - bounds.height);
        int newX = clamp((int) Math.round(click.x()) - dragOffsetX, 0, maxX);
        int newY = clamp((int) Math.round(click.y()) - dragOffsetY, 0, maxY);
        Bounds snapped = snapBounds(draggingModule, new Bounds(newX, newY, bounds.width, bounds.height));
        setPosition(draggingModule, snapped.x, snapped.y);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (resizingModule != null) {
            resizingModule = null;
            resizeStartBounds = null;
            BetterUCConfig.save();
            refreshToolbarButtons();
            return true;
        }

        if (draggingModule != null) {
            draggingModule = null;
            dragStartBounds = null;
            clearSnapGuides();
            BetterUCConfig.save();
            refreshToolbarButtons();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int keyCode = input.input();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && cancelCurrentOperation()) {
            return true;
        }

        if (selectedModule != null && isArrowKey(keyCode)) {
            int step = isShiftDown() ? 10 : 1;
            Bounds bounds = boundsFor(selectedModule);
            int nextX = bounds.x;
            int nextY = bounds.y;
            if (keyCode == GLFW.GLFW_KEY_LEFT) nextX -= step;
            if (keyCode == GLFW.GLFW_KEY_RIGHT) nextX += step;
            if (keyCode == GLFW.GLFW_KEY_UP) nextY -= step;
            if (keyCode == GLFW.GLFW_KEY_DOWN) nextY += step;
            nextX = clamp(nextX, 0, Math.max(0, width - bounds.width));
            nextY = clamp(nextY, 0, Math.max(0, height - bounds.height));
            setPosition(selectedModule, nextX, nextY);
            BetterUCConfig.save();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        BetterUCConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        BetterUCConfig.save();
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, width, height, 0x88000000);
        for (int x = 0; x < width; x += 20) {
            context.fill(x, 0, x + 1, height, 0x1FFFFFFF);
        }
        for (int y = 0; y < height; y += 20) {
            context.fill(0, y, width, y + 1, 0x1FFFFFFF);
        }
        context.fill(0, 0, width, 38, 0xAA0D1117);
        context.fill(0, height - TOOLBAR_HEIGHT, width, height, 0xD00D1117);
    }

    private List<HudModule> activeModules() {
        List<HudModule> modules = new ArrayList<>();
        for (HudModule module : HudModule.values()) {
            if (isActive(module)) {
                modules.add(module);
            }
        }
        return modules;
    }

    private boolean isActive(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.showHealthHud;
            case FPS -> BetterUCConfig.INSTANCE.showFpsHud;
            case PAYDAY -> BetterUCConfig.INSTANCE.showPaydayHud;
            case AMMO -> BetterUCConfig.INSTANCE.showAmmoHud;
            case BANK -> BetterUCConfig.INSTANCE.showBankHud;
            case CASH -> BetterUCConfig.INSTANCE.showCashHud;
            case POTION -> BetterUCConfig.INSTANCE.showPotionEffectsHud;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.showPlantTimerHud;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.showDealerTimerHud;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.showMaskTimerHud;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.showProductionTimerHud;
            case HACK_TIMER -> true;
        };
    }

    private Bounds boundsFor(HudModule module) {
        int x = getX(module);
        int y = getY(module);
        int w = ModernHudRenderer.scaledSize(widthFor(module), getScale(module));
        int h = ModernHudRenderer.scaledSize(heightFor(module), getScale(module));
        return new Bounds(x, y, w, h);
    }

    private int getX(HudModule module) {
        return switch (module) {
            case HEALTH -> resolveHealthX();
            case FPS -> BetterUCConfig.INSTANCE.fpsHudX;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudX;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudX;
            case BANK -> BetterUCConfig.INSTANCE.bankHudX;
            case CASH -> BetterUCConfig.INSTANCE.cashHudX;
            case POTION -> BetterUCConfig.INSTANCE.potionHudX;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudX;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerX;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerX;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerX;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerX;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerX;
        };
    }

    private int getY(HudModule module) {
        return switch (module) {
            case HEALTH -> resolveHealthY();
            case FPS -> BetterUCConfig.INSTANCE.fpsHudY;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudY;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudY;
            case BANK -> BetterUCConfig.INSTANCE.bankHudY;
            case CASH -> BetterUCConfig.INSTANCE.cashHudY;
            case POTION -> BetterUCConfig.INSTANCE.potionHudY;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudY;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerY;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerY;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerY;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerY;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerY;
        };
    }

    private void setPosition(HudModule module, int x, int y) {
        switch (module) {
            case HEALTH -> {
                BetterUCConfig.INSTANCE.healthHudX = x + healthPreviewCenterOffset();
                BetterUCConfig.INSTANCE.healthHudY = y;
            }
            case FPS -> {
                BetterUCConfig.INSTANCE.fpsHudX = x;
                BetterUCConfig.INSTANCE.fpsHudY = y;
            }
            case PAYDAY -> {
                BetterUCConfig.INSTANCE.paydayHudX = x;
                BetterUCConfig.INSTANCE.paydayHudY = y;
            }
            case AMMO -> {
                BetterUCConfig.INSTANCE.ammoHudX = x;
                BetterUCConfig.INSTANCE.ammoHudY = y;
            }
            case BANK -> {
                BetterUCConfig.INSTANCE.bankHudX = x;
                BetterUCConfig.INSTANCE.bankHudY = y;
            }
            case CASH -> {
                BetterUCConfig.INSTANCE.cashHudX = x;
                BetterUCConfig.INSTANCE.cashHudY = y;
            }
            case POTION -> {
                BetterUCConfig.INSTANCE.potionHudX = x;
                BetterUCConfig.INSTANCE.potionHudY = y;
            }
            case SPRINT -> {
                BetterUCConfig.INSTANCE.toggleSprintHudX = x;
                BetterUCConfig.INSTANCE.toggleSprintHudY = y;
            }
            case HACK_TIMER -> {
                BetterUCConfig.INSTANCE.hackTimerX = x;
                BetterUCConfig.INSTANCE.hackTimerY = y;
            }
            case PLANT_TIMER -> {
                BetterUCConfig.INSTANCE.plantTimerX = x;
                BetterUCConfig.INSTANCE.plantTimerY = y;
            }
            case DEALER_TIMER -> {
                BetterUCConfig.INSTANCE.dealerTimerX = x;
                BetterUCConfig.INSTANCE.dealerTimerY = y;
            }
            case MASK_TIMER -> {
                BetterUCConfig.INSTANCE.maskTimerX = x;
                BetterUCConfig.INSTANCE.maskTimerY = y;
            }
            case PRODUCTION_TIMER -> {
                BetterUCConfig.INSTANCE.productionTimerX = x;
                BetterUCConfig.INSTANCE.productionTimerY = y;
            }
        }
    }

    private float getScale(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudScale;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudScale;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudScale;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudScale;
            case BANK -> BetterUCConfig.INSTANCE.bankHudScale;
            case CASH -> BetterUCConfig.INSTANCE.cashHudScale;
            case POTION -> BetterUCConfig.INSTANCE.potionHudScale;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudScale;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudScale;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudScale;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudScale;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudScale;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudScale;
        };
    }

    private void setScale(HudModule module, float scale) {
        float safeScale = BetterUCConfig.normalizeHudScale(scale);
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudScale = safeScale;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudScale = safeScale;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudScale = safeScale;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudScale = safeScale;
            case BANK -> BetterUCConfig.INSTANCE.bankHudScale = safeScale;
            case CASH -> BetterUCConfig.INSTANCE.cashHudScale = safeScale;
            case POTION -> BetterUCConfig.INSTANCE.potionHudScale = safeScale;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudScale = safeScale;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudScale = safeScale;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudScale = safeScale;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudScale = safeScale;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudScale = safeScale;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudScale = safeScale;
        }
    }

    private int widthFor(HudModule module) {
        Font renderer = font;
        return switch (module) {
            case HEALTH -> {
                yield HealthHud.getPreviewWidth(renderer, BetterUCConfig.INSTANCE.healthHudStyle);
            }
            case FPS -> singleLineWidth(hudLabel(module), "144", prefixedText(module, "144"), BetterUCConfig.INSTANCE.fpsHudStyle);
            case PAYDAY -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.paydayHudStyle)
                    ? progressWidth(hudLabel(module), "25/60 min")
                    : renderer.width(prefixedText(module, "25/60 Minuten")) + 4;
            case AMMO -> twoLineWidth(hudLabel(module), prefixedText(module, "20/96"), "TS19", BetterUCConfig.INSTANCE.ammoHudStyle);
            case BANK -> singleLineWidth(hudLabel(module), "88.375$", prefixedText(module, "88.375$"), BetterUCConfig.INSTANCE.bankHudStyle);
            case CASH -> singleLineWidth(hudLabel(module), previewCashValue(), prefixedText(module, previewCashValue()), BetterUCConfig.INSTANCE.cashHudStyle);
            case POTION -> {
                if (BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.potionHudStyle)) {
                    yield 120;
                }
                if (BetterUCConfig.HUD_STYLE_TRANSPARENT.equals(BetterUCConfig.INSTANCE.potionHudStyle)) {
                    yield PotionEffectsHud.transparentPreviewWidth(renderer);
                }
                yield Math.max(renderer.width("Stärke II"), renderer.width("Speed")) + 4;
            }
            case SPRINT -> singleLineWidth(hudLabel(module), "ON", prefixedText(module, "ON"), BetterUCConfig.INSTANCE.toggleSprintHudStyle);
            case HACK_TIMER -> singleLineWidth(hudLabel(module), "02:39", prefixedText(module, "02:39"), BetterUCConfig.INSTANCE.hackTimerHudStyle);
            case DEALER_TIMER -> singleLineWidth(hudLabel(module), "00:15", prefixedText(module, "00:15"), BetterUCConfig.INSTANCE.dealerTimerHudStyle);
            case MASK_TIMER -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.maskTimerHudStyle)
                    ? progressWidth(hudLabel(module), "18:42")
                    : singleLineWidth(hudLabel(module), "18:42", prefixedText(module, "18:42"),
                    BetterUCConfig.INSTANCE.maskTimerHudStyle);
            case PRODUCTION_TIMER -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.productionTimerHudStyle)
                    ? progressWidth(hudLabel(module), "19:59")
                    : singleLineWidth(hudLabel(module), "19:59", prefixedText(module, "19:59"), BetterUCConfig.INSTANCE.productionTimerHudStyle);
            case PLANT_TIMER -> twoLineWidth(hudLabel(module), prefixedText(module, "Plantage Pulver 7/10"), "Reif: 1:30:00 | Wasser: 20:00", BetterUCConfig.INSTANCE.plantTimerHudStyle);
        };
    }

    private int heightFor(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.healthHudStyle) ? 17 : 12;
            case PAYDAY -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.paydayHudStyle) ? 24 : 13;
            case AMMO -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.ammoHudStyle)
                    ? (BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled ? 35 : 31)
                    : (BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled ? 27 : 24);
            case POTION -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.potionHudStyle) ? 65 : 48;
            case PLANT_TIMER -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.plantTimerHudStyle) ? 31 : 24;
            case FPS -> singleLineHeight(BetterUCConfig.INSTANCE.fpsHudStyle);
            case BANK -> singleLineHeight(BetterUCConfig.INSTANCE.bankHudStyle);
            case CASH -> singleLineHeight(BetterUCConfig.INSTANCE.cashHudStyle);
            case SPRINT -> singleLineHeight(BetterUCConfig.INSTANCE.toggleSprintHudStyle);
            case HACK_TIMER -> singleLineHeight(BetterUCConfig.INSTANCE.hackTimerHudStyle);
            case DEALER_TIMER -> singleLineHeight(BetterUCConfig.INSTANCE.dealerTimerHudStyle);
            case MASK_TIMER -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.maskTimerHudStyle)
                    ? 24 : singleLineHeight(BetterUCConfig.INSTANCE.maskTimerHudStyle);
            case PRODUCTION_TIMER -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.productionTimerHudStyle) ? 24 : singleLineHeight(BetterUCConfig.INSTANCE.productionTimerHudStyle);
        };
    }

    private int singleLineHeight(String style) {
        return BetterUCConfig.isModernHudStyle(style) ? 18 : 12;
    }

    private int singleLineWidth(String label, String value, String text, String style) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            int labelGap = label.isBlank() ? 0 : 5;
            return Math.max(58, font.width(label) + font.width(value) + labelGap + 23);
        }
        return font.width(text) + 4;
    }

    private int twoLineWidth(String label, String primary, String secondary, String style) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            String modernPrimary = stripPrefixValue(label, primary);
            int labelGap = label.isBlank() ? 0 : 5;
            return Math.max(58,
                    Math.max(
                            font.width(label) + font.width(modernPrimary) + labelGap + 23,
                            font.width(secondary) + 16
                    ));
        }
        return Math.max(font.width(primary), font.width(secondary)) + 4;
    }

    private int progressWidth(String label, String value) {
        int labelGap = label.isBlank() ? 0 : 5;
        return Math.max(86, font.width(label) + font.width(value) + labelGap + 23);
    }

    private void renderHudModule(GuiGraphicsExtractor context, HudModule module, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        String style = styleFor(module);
        String fontId = fontFor(module);
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);

        ModernHudRenderer.drawScaledAroundWithGradient(
                context,
                x,
                y,
                getScale(module),
                getGradientEnabled(module),
                getGradientColor(module),
                () -> {
            switch (module) {
            case HEALTH -> {
                HealthHud.drawPreview(
                        context,
                        minecraft,
                        x,
                        y,
                        style,
                        fontId,
                        BetterUCConfig.INSTANCE.healthHudHeartColor,
                        BetterUCConfig.INSTANCE.healthHudTextColor
                );
            }
            case FPS -> renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "144", prefixedText(module, "144"), BetterUCConfig.INSTANCE.fpsHudColor);
            case PAYDAY -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, x, y, hudLabel(module), "25/60 min", 25.0F / 60.0F, BetterUCConfig.INSTANCE.paydayHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, prefixedText(module, "25/60 Minuten"), x, y, BetterUCConfig.INSTANCE.paydayHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, minecraft.font, prefixedText(module, "25/60 Minuten"), x, y, BetterUCConfig.INSTANCE.paydayHudColor);
                }
            }
            case AMMO -> {
                if (modernStyle && BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled) {
                    ModernHudRenderer.drawTwoLineProgressModule(context, minecraft, x, y, hudLabel(module),
                            "20/96", "TS19", 20.0F / 21.0F, 0xFFFFAA33, 0xFF55FF55);
                } else {
                    renderTwoLine(context, minecraft, style, fontId, x, y, hudLabel(module),
                            prefixedText(module, "20/96"), "TS19", 0xFFFFAA33, 0xFF55FF55);
                }
            }
            case BANK -> renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "88.375$", prefixedText(module, "88.375$"), BetterUCConfig.INSTANCE.bankHudColor);
            case CASH -> renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), previewCashValue(), prefixedText(module, previewCashValue()), BetterUCConfig.INSTANCE.cashHudColor);
            case POTION -> {
                int potionColor = BetterUCConfig.INSTANCE.potionHudColor;
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, x, y, "EFFECT", "Stärke II", "1:26", potionColor);
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, x, y + 33, "EFFECT", "Speed", "0:49", potionColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "Stärke II", x, y, potionColor);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "1:26", x, y + 11, ModernHudRenderer.TEXT_DIM);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "Speed", x, y + 25, potionColor);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "0:49", x, y + 36, ModernHudRenderer.TEXT_DIM);
                } else {
                    PotionEffectsHud.drawTransparentPreview(context, minecraft, x, y, potionColor);
                }
            }
            case SPRINT -> renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "ON", prefixedText(module, "ON"), BetterUCConfig.INSTANCE.toggleSprintHudColor);
            case HACK_TIMER -> renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "02:39", prefixedText(module, "02:39"), 0xFF60A5FA);
            case DEALER_TIMER -> renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "00:15", prefixedText(module, "00:15"), BetterUCConfig.INSTANCE.dealerTimerHudColor);
            case MASK_TIMER -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, x, y, hudLabel(module), "18:42",
                            0.07F, BetterUCConfig.INSTANCE.maskTimerHudColor);
                } else {
                    renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "18:42",
                            prefixedText(module, "18:42"), BetterUCConfig.INSTANCE.maskTimerHudColor);
                }
            }
            case PRODUCTION_TIMER -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, x, y, hudLabel(module), "19:59", 0.35F, BetterUCConfig.INSTANCE.productionTimerHudColor);
                } else {
                    renderSingleLine(context, minecraft, style, fontId, x, y, hudLabel(module), "19:59", prefixedText(module, "19:59"), BetterUCConfig.INSTANCE.productionTimerHudColor);
                }
            }
            case PLANT_TIMER -> renderTwoLine(context, minecraft, style, fontId, x, y, hudLabel(module), prefixedText(module, "Plantage Pulver 7/10"), "Reif: 1:30:00 | Wasser: 20:00", 0xFF6CF27D, 0xFFFFD866);
            }
        });
    }

    private void renderSingleLine(
            GuiGraphicsExtractor context,
            Minecraft minecraft,
            String style,
            String fontId,
            int x,
            int y,
            String label,
            String value,
            String text,
            int color
    ) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            ModernHudRenderer.drawModule(context, minecraft, x, y, label, value, color);
        } else if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, text, x, y, color);
        } else {
            ModernHudRenderer.drawHudTextWithShadow(context, minecraft.font, text, x, y, color);
        }
    }

    private void renderTwoLine(
            GuiGraphicsExtractor context,
            Minecraft minecraft,
            String style,
            String fontId,
            int x,
            int y,
            String label,
            String primary,
            String secondary,
            int primaryColor,
            int secondaryColor
    ) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            ModernHudRenderer.drawTwoLineModule(context, minecraft, x, y, label, stripPrefixValue(label, primary), secondary, primaryColor, secondaryColor);
        } else if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, primary, x, y, primaryColor);
            ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, secondary, x, y + 11, secondaryColor);
        } else {
            ModernHudRenderer.drawHudTextWithShadow(context, minecraft.font, primary, x, y, primaryColor);
            ModernHudRenderer.drawHudTextWithShadow(context, minecraft.font, secondary, x, y + 10, secondaryColor);
        }
    }

    private String styleFor(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle;
            case CASH -> BetterUCConfig.INSTANCE.cashHudStyle;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudStyle;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudStyle;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudStyle;
        };
    }

    private String fontFor(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont;
            case CASH -> BetterUCConfig.INSTANCE.cashHudCustomFont;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudCustomFont;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudCustomFont;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudCustomFont;
        };
    }

    private boolean getGradientEnabled(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientEnabled;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientEnabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientEnabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientEnabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientEnabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientEnabled;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientEnabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientEnabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientEnabled;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudGradientEnabled;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudGradientEnabled;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudGradientEnabled;
        };
    }

    private int getGradientColor(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientColor;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientColor;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientColor;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientColor;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientColor;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientColor;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientColor;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientColor;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientColor;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientColor;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudGradientColor;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudGradientColor;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudGradientColor;
        };
    }

    private String previewCashValue() {
        int live = CashHud.getCurrentCash();
        return live >= 0 ? CashHud.formatMoney(live) + "$" : CashHud.formatMoney(1278) + "$";
    }

    private String hudLabel(HudModule module) {
        return switch (module) {
            case FPS -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.fpsHudPrefixEnabled, BetterUCConfig.INSTANCE.fpsHudPrefix);
            case PAYDAY -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.paydayHudPrefixEnabled, BetterUCConfig.INSTANCE.paydayHudPrefix);
            case AMMO -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.ammoHudPrefixEnabled, BetterUCConfig.INSTANCE.ammoHudPrefix);
            case BANK -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.bankHudPrefixEnabled, BetterUCConfig.INSTANCE.bankHudPrefix);
            case CASH -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.cashHudPrefixEnabled, BetterUCConfig.INSTANCE.cashHudPrefix);
            case SPRINT -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.toggleSprintHudPrefixEnabled, BetterUCConfig.INSTANCE.toggleSprintHudPrefix);
            case HACK_TIMER -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.hackTimerHudPrefix);
            case PLANT_TIMER -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.plantTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.plantTimerHudPrefix);
            case DEALER_TIMER -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.dealerTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.dealerTimerHudPrefix);
            case MASK_TIMER -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.maskTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.maskTimerHudPrefix);
            case PRODUCTION_TIMER -> BetterUCConfig.hudModuleLabel(BetterUCConfig.INSTANCE.productionTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.productionTimerHudPrefix);
            default -> "";
        };
    }

    private String prefixedText(HudModule module, String value) {
        return switch (module) {
            case FPS -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.fpsHudPrefixEnabled, BetterUCConfig.INSTANCE.fpsHudPrefix, value);
            case PAYDAY -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.paydayHudPrefixEnabled, BetterUCConfig.INSTANCE.paydayHudPrefix, value);
            case AMMO -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.ammoHudPrefixEnabled, BetterUCConfig.INSTANCE.ammoHudPrefix, value);
            case BANK -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.bankHudPrefixEnabled, BetterUCConfig.INSTANCE.bankHudPrefix, value);
            case CASH -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.cashHudPrefixEnabled, BetterUCConfig.INSTANCE.cashHudPrefix, value);
            case SPRINT -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.toggleSprintHudPrefixEnabled, BetterUCConfig.INSTANCE.toggleSprintHudPrefix, value);
            case HACK_TIMER -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.hackTimerHudPrefix, value);
            case PLANT_TIMER -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.plantTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.plantTimerHudPrefix, value);
            case DEALER_TIMER -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.dealerTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.dealerTimerHudPrefix, value);
            case MASK_TIMER -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.maskTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.maskTimerHudPrefix, value);
            case PRODUCTION_TIMER -> BetterUCConfig.prefixedHudText(BetterUCConfig.INSTANCE.productionTimerHudPrefixEnabled, BetterUCConfig.INSTANCE.productionTimerHudPrefix, value);
            default -> value;
        };
    }

    private String stripPrefixValue(String label, String text) {
        if (label.isBlank()) {
            return text;
        }
        int separator = text.indexOf(':');
        if (separator < 0 || separator + 1 >= text.length()) {
            return text;
        }
        return text.substring(separator + 1).trim();
    }

    private void adjustSelectedScale(float delta) {
        if (selectedModule == null) return;
        setSelectedScale(getScale(selectedModule) + delta);
    }

    private void setSelectedScale(float scale) {
        if (selectedModule == null) return;
        scaleAroundCenter(selectedModule, scale);
        BetterUCConfig.save();
        refreshToolbarButtons();
    }

    private void scaleAroundCenter(HudModule module, float scale) {
        Bounds before = boundsFor(module);
        int centerX = before.x + before.width / 2;
        int centerY = before.y + before.height / 2;
        float safeScale = BetterUCConfig.normalizeHudScale(scale);
        setScale(module, safeScale);

        Bounds after = boundsFor(module);
        int nextX = clamp(centerX - after.width / 2, 0, Math.max(0, width - after.width));
        int nextY = clamp(centerY - after.height / 2, 0, Math.max(0, height - after.height));
        setPosition(module, nextX, nextY);
    }

    private void resetSelectedLayout() {
        if (selectedModule == null) return;
        applyDefaultLayout(selectedModule, new BetterUCConfig());
        BetterUCConfig.save();
        refreshToolbarButtons();
    }

    private void requestResetAllLayouts() {
        long now = System.currentTimeMillis();
        if (resetAllConfirmationUntil > now) {
            BetterUCConfig defaults = new BetterUCConfig();
            for (HudModule module : HudModule.values()) {
                applyDefaultLayout(module, defaults);
            }
            resetAllConfirmationUntil = 0L;
            BetterUCConfig.save();
        } else {
            resetAllConfirmationUntil = now + RESET_CONFIRMATION_MS;
        }
        refreshToolbarButtons();
    }

    private void applyDefaultLayout(HudModule module, BetterUCConfig defaults) {
        switch (module) {
            case HEALTH -> {
                BetterUCConfig.INSTANCE.healthHudX = defaults.healthHudX;
                BetterUCConfig.INSTANCE.healthHudY = defaults.healthHudY;
                BetterUCConfig.INSTANCE.healthHudScale = defaults.healthHudScale;
            }
            case FPS -> {
                BetterUCConfig.INSTANCE.fpsHudX = defaults.fpsHudX;
                BetterUCConfig.INSTANCE.fpsHudY = defaults.fpsHudY;
                BetterUCConfig.INSTANCE.fpsHudScale = defaults.fpsHudScale;
            }
            case PAYDAY -> {
                BetterUCConfig.INSTANCE.paydayHudX = defaults.paydayHudX;
                BetterUCConfig.INSTANCE.paydayHudY = defaults.paydayHudY;
                BetterUCConfig.INSTANCE.paydayHudScale = defaults.paydayHudScale;
            }
            case AMMO -> {
                BetterUCConfig.INSTANCE.ammoHudX = defaults.ammoHudX;
                BetterUCConfig.INSTANCE.ammoHudY = defaults.ammoHudY;
                BetterUCConfig.INSTANCE.ammoHudScale = defaults.ammoHudScale;
            }
            case BANK -> {
                BetterUCConfig.INSTANCE.bankHudX = defaults.bankHudX;
                BetterUCConfig.INSTANCE.bankHudY = defaults.bankHudY;
                BetterUCConfig.INSTANCE.bankHudScale = defaults.bankHudScale;
            }
            case CASH -> {
                BetterUCConfig.INSTANCE.cashHudX = defaults.cashHudX;
                BetterUCConfig.INSTANCE.cashHudY = defaults.cashHudY;
                BetterUCConfig.INSTANCE.cashHudScale = defaults.cashHudScale;
            }
            case POTION -> {
                BetterUCConfig.INSTANCE.potionHudX = defaults.potionHudX;
                BetterUCConfig.INSTANCE.potionHudY = defaults.potionHudY;
                BetterUCConfig.INSTANCE.potionHudScale = defaults.potionHudScale;
            }
            case SPRINT -> {
                BetterUCConfig.INSTANCE.toggleSprintHudX = defaults.toggleSprintHudX;
                BetterUCConfig.INSTANCE.toggleSprintHudY = defaults.toggleSprintHudY;
                BetterUCConfig.INSTANCE.toggleSprintHudScale = defaults.toggleSprintHudScale;
            }
            case HACK_TIMER -> {
                BetterUCConfig.INSTANCE.hackTimerX = defaults.hackTimerX;
                BetterUCConfig.INSTANCE.hackTimerY = defaults.hackTimerY;
                BetterUCConfig.INSTANCE.hackTimerHudScale = defaults.hackTimerHudScale;
            }
            case PLANT_TIMER -> {
                BetterUCConfig.INSTANCE.plantTimerX = defaults.plantTimerX;
                BetterUCConfig.INSTANCE.plantTimerY = defaults.plantTimerY;
                BetterUCConfig.INSTANCE.plantTimerHudScale = defaults.plantTimerHudScale;
            }
            case DEALER_TIMER -> {
                BetterUCConfig.INSTANCE.dealerTimerX = defaults.dealerTimerX;
                BetterUCConfig.INSTANCE.dealerTimerY = defaults.dealerTimerY;
                BetterUCConfig.INSTANCE.dealerTimerHudScale = defaults.dealerTimerHudScale;
            }
            case MASK_TIMER -> {
                BetterUCConfig.INSTANCE.maskTimerX = defaults.maskTimerX;
                BetterUCConfig.INSTANCE.maskTimerY = defaults.maskTimerY;
                BetterUCConfig.INSTANCE.maskTimerHudScale = defaults.maskTimerHudScale;
            }
            case PRODUCTION_TIMER -> {
                BetterUCConfig.INSTANCE.productionTimerX = defaults.productionTimerX;
                BetterUCConfig.INSTANCE.productionTimerY = defaults.productionTimerY;
                BetterUCConfig.INSTANCE.productionTimerHudScale = defaults.productionTimerHudScale;
            }
        }
    }

    private void resizeSelectedModule(double mouseX, double mouseY) {
        if (resizingModule == null || resizeStartBounds == null) {
            return;
        }

        int baseWidth = widthFor(resizingModule);
        int baseHeight = heightFor(resizingModule);
        double widthScale = (resizeStartBounds.width + mouseX - resizeStartMouseX) / Math.max(1.0D, baseWidth);
        double heightScale = (resizeStartBounds.height + mouseY - resizeStartMouseY) / Math.max(1.0D, baseHeight);
        double widthDelta = Math.abs(widthScale - resizeStartScale);
        double heightDelta = Math.abs(heightScale - resizeStartScale);
        double nextScale = widthDelta >= heightDelta ? widthScale : heightScale;
        float safeScale = BetterUCConfig.normalizeHudScale((float) nextScale);
        int nextWidth = ModernHudRenderer.scaledSize(baseWidth, safeScale);
        int nextHeight = ModernHudRenderer.scaledSize(baseHeight, safeScale);
        int nextX = clamp(resizeStartBounds.x, 0, Math.max(0, width - nextWidth));
        int nextY = clamp(resizeStartBounds.y, 0, Math.max(0, height - nextHeight));
        setScale(resizingModule, safeScale);
        setPosition(resizingModule, nextX, nextY);
        refreshToolbarButtons();
    }

    private boolean resizeHandleContains(Bounds bounds, double mouseX, double mouseY) {
        int centerX = resizeHandleX(bounds);
        int centerY = resizeHandleY(bounds);
        return mouseX >= centerX - HANDLE_HIT_RADIUS && mouseX <= centerX + HANDLE_HIT_RADIUS
                && mouseY >= centerY - HANDLE_HIT_RADIUS && mouseY <= centerY + HANDLE_HIT_RADIUS;
    }

    private int resizeHandleX(Bounds bounds) {
        int outside = bounds.right() + 5;
        return outside <= width - 5 ? outside : bounds.right() - 4;
    }

    private int resizeHandleY(Bounds bounds) {
        int outside = bounds.bottom() + 5;
        return outside <= height - TOOLBAR_HEIGHT - 5 ? outside : bounds.bottom() - 4;
    }

    private Bounds snapBounds(HudModule movingModule, Bounds moving) {
        clearSnapGuides();
        int snapX = moving.x;
        int snapY = moving.y;
        int bestXDistance = SNAP_DISTANCE + 1;
        int bestYDistance = SNAP_DISTANCE + 1;

        for (HudModule otherModule : activeModules()) {
            if (otherModule == movingModule) continue;

            Bounds other = boundsFor(otherModule);
            if (rangesOverlapOrClose(moving.y, moving.height, other.y, other.height)) {
                int[] xCandidates = {
                        other.x,
                        other.right() - moving.width,
                        other.x + other.width / 2 - moving.width / 2,
                        other.right() + SNAP_GAP,
                        other.x - moving.width - SNAP_GAP
                };
                for (int candidate : xCandidates) {
                    int distance = Math.abs(moving.x - candidate);
                    if (distance <= SNAP_DISTANCE && distance < bestXDistance) {
                        snapX = candidate;
                        bestXDistance = distance;
                        snapGuideX = candidate;
                    }
                }
            }

            if (rangesOverlapOrClose(moving.x, moving.width, other.x, other.width)) {
                int[] yCandidates = {
                        other.y,
                        other.bottom() - moving.height,
                        other.y + other.height / 2 - moving.height / 2,
                        other.bottom() + SNAP_GAP,
                        other.y - moving.height - SNAP_GAP
                };
                for (int candidate : yCandidates) {
                    int distance = Math.abs(moving.y - candidate);
                    if (distance <= SNAP_DISTANCE && distance < bestYDistance) {
                        snapY = candidate;
                        bestYDistance = distance;
                        snapGuideY = candidate;
                    }
                }
            }
        }

        int maxX = Math.max(0, width - moving.width);
        int maxY = Math.max(0, height - moving.height);
        return new Bounds(clamp(snapX, 0, maxX), clamp(snapY, 0, maxY), moving.width, moving.height);
    }

    private boolean rangesOverlapOrClose(int startA, int sizeA, int startB, int sizeB) {
        int endA = startA + sizeA;
        int endB = startB + sizeB;
        return startA <= endB + SNAP_DISTANCE && startB <= endA + SNAP_DISTANCE;
    }

    private void clearSnapGuides() {
        snapGuideX = null;
        snapGuideY = null;
    }

    private void drawSnapGuides(GuiGraphicsExtractor context) {
        int color = 0xAA38BDF8;
        if (snapGuideX != null) {
            context.fill(snapGuideX, 38, snapGuideX + 1, height - TOOLBAR_HEIGHT, color);
        }
        if (snapGuideY != null) {
            context.fill(0, snapGuideY, width, snapGuideY + 1, color);
        }
    }

    private void drawDragBounds(GuiGraphicsExtractor context, HudModule module, Bounds bounds, boolean active, boolean handleHovered) {
        int borderColor = active ? module.accent : 0x668899AA;
        context.fill(bounds.x - 3, bounds.y - 3, bounds.x + bounds.width + 3, bounds.y + bounds.height + 3, active ? 0x22000000 : 0x12000000);
        drawBorder(context, bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6, borderColor);
        if (active) {
            String label = String.format(Locale.ROOT, "%s %d%%", module.label, Math.round(getScale(module) * 100.0F));
            context.text(font, Component.literal(label), bounds.x, Math.max(0, bounds.y - 12), borderColor | 0xFF000000);
        }
        if (module == selectedModule) {
            drawResizeHandle(context, bounds, module.accent, handleHovered);
        }
    }

    private void drawResizeHandle(GuiGraphicsExtractor context, Bounds bounds, int color, boolean hovered) {
        drawHandle(context, resizeHandleX(bounds), resizeHandleY(bounds), color, hovered);
    }

    private void drawHandle(GuiGraphicsExtractor context, int centerX, int centerY, int color, boolean hovered) {
        int half = HANDLE_SIZE / 2;
        int fill = hovered ? 0xFFFFFFFF : 0xFFE2E8F0;
        context.fill(centerX - half - 1, centerY - half - 1, centerX + half + 2, centerY + half + 2, color);
        context.fill(centerX - half, centerY - half, centerX + half + 1, centerY + half + 1, fill);
    }

    private void drawToolbarStatus(GuiGraphicsExtractor context) {
        String selected = selectedModule == null
                ? "Kein HUD ausgewählt"
                : "Ausgewählt: " + selectedModule.label;
        int color = selectedModule == null ? TEXT_MUTED : selectedModule.accent;
        context.text(font, Component.literal(selected), 12, height - 42, color);
    }

    private void refreshToolbarButtons() {
        boolean hasSelection = selectedModule != null;
        if (scaleDownButton != null) scaleDownButton.active = hasSelection;
        if (scaleResetButton != null) {
            scaleResetButton.active = hasSelection;
            String scale = hasSelection
                    ? Math.round(getScale(selectedModule) * 100.0F) + "%"
                    : "100%";
            scaleResetButton.setMessage(Component.literal(scale));
        }
        if (scaleUpButton != null) scaleUpButton.active = hasSelection;
        if (resetSelectedButton != null) resetSelectedButton.active = hasSelection;
        if (resetAllButton != null) {
            if (resetAllConfirmationUntil > 0L && resetAllConfirmationUntil <= System.currentTimeMillis()) {
                resetAllConfirmationUntil = 0L;
            }
            resetAllButton.setMessage(Component.literal(
                    resetAllConfirmationUntil > System.currentTimeMillis()
                            ? "Wirklich alle?"
                            : "Alle zurücksetzen"
            ));
        }
    }

    private boolean cancelCurrentOperation() {
        if (resizingModule != null && resizeStartBounds != null) {
            setScale(resizingModule, resizeStartScale);
            setPosition(resizingModule, resizeStartBounds.x, resizeStartBounds.y);
            resizingModule = null;
            resizeStartBounds = null;
            clearSnapGuides();
            refreshToolbarButtons();
            return true;
        }
        if (draggingModule != null && dragStartBounds != null) {
            setPosition(draggingModule, dragStartBounds.x, dragStartBounds.y);
            draggingModule = null;
            dragStartBounds = null;
            clearSnapGuides();
            refreshToolbarButtons();
            return true;
        }
        return false;
    }

    private boolean isArrowKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN;
    }

    private boolean isShiftDown() {
        if (minecraft == null) return false;
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private int resolveHealthX() {
        if (BetterUCConfig.INSTANCE.healthHudX >= 0) {
            return BetterUCConfig.INSTANCE.healthHudX - healthPreviewCenterOffset();
        }
        int previewWidth = ModernHudRenderer.scaledSize(
                widthFor(HudModule.HEALTH),
                BetterUCConfig.INSTANCE.healthHudScale
        );
        return width / 2 - previewWidth / 2;
    }

    private int resolveHealthY() {
        if (BetterUCConfig.INSTANCE.healthHudY >= 0) return BetterUCConfig.INSTANCE.healthHudY;
        return height / 2 + 15;
    }

    private int healthPreviewCenterOffset() {
        return HealthHud.getPreviewCenterOffset(
                font,
                BetterUCConfig.INSTANCE.healthHudStyle,
                BetterUCConfig.INSTANCE.healthHudScale
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum HudModule {
        HEALTH("Health", 0xFFFF5555),
        FPS("FPS", BetterUCConfig.DEFAULT_FPS_HUD_COLOR),
        PAYDAY("Payday", BetterUCConfig.DEFAULT_PAYDAY_HUD_COLOR),
        AMMO("Ammo", 0xFFFFAA33),
        BANK("Bank", BetterUCConfig.DEFAULT_BANK_HUD_COLOR),
        CASH("Bargeld", BetterUCConfig.DEFAULT_CASH_HUD_COLOR),
        POTION("Potion", 0xFF9328FF),
        SPRINT("Sprint", BetterUCConfig.DEFAULT_TOGGLE_SPRINT_HUD_COLOR),
        HACK_TIMER("Hack Timer", 0xFF60A5FA),
        DEALER_TIMER("Dealer Timer", 0xFFD946EF),
        MASK_TIMER("Masken Timer", 0xFF22D3EE),
        PRODUCTION_TIMER("Produktion", 0xFFFBBF24),
        PLANT_TIMER("Plant Timer", 0xFF6CF27D);

        private final String label;
        private final int accent;

        HudModule(String label, int accent) {
            this.label = label;
            this.accent = accent;
        }
    }

    private record Bounds(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x - 3 && mouseX <= x + width + 3
                    && mouseY >= y - 3 && mouseY <= y + height + 3;
        }
    }
}
