package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.CashHud;
import com.betteruc.hud.ModernHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HudLayoutScreen extends Screen {

    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int HANDLE_SIZE = 7;
    private static final int SNAP_DISTANCE = 8;
    private static final int SNAP_GAP = 6;
    private static final Identifier HEART_TEXTURE = Identifier.ofVanilla("hud/heart/full");

    private final Screen parent;
    private HudModule draggingModule;
    private HudModule resizingModule;
    private HudModule selectedModule;
    private ResizeHandle resizeHandle;
    private Bounds resizeStartBounds;
    private float resizeStartScale;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudLayoutScreen(Screen parent) {
        super(Text.literal("HUD Vorschau"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(width - 92, height - 28, 80, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawTextWithShadow(textRenderer, Text.literal("HUD Vorschau"), 12, 12, TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Aktive HUDs"), 12, 24, TEXT_MUTED);

        List<HudModule> modules = activeModules();
        if (modules.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Keine HUDs aktiv"), width / 2, height / 2, TEXT_MUTED);
        }

        for (HudModule module : modules) {
            Bounds bounds = boundsFor(module);
            ResizeHandle hoveredHandle = findResizeHandle(bounds, mouseX, mouseY);
            boolean hovered = bounds.contains(mouseX, mouseY) || hoveredHandle != ResizeHandle.NONE;
            boolean selected = module == selectedModule || module == draggingModule || module == resizingModule;
            drawDragBounds(context, module, bounds, hovered || selected, hoveredHandle);
            renderHudModule(context, module, bounds.x, bounds.y);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        List<HudModule> modules = activeModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);
            Bounds bounds = boundsFor(module);
            ResizeHandle handle = findResizeHandle(bounds, event.x(), event.y());
            if (handle != ResizeHandle.NONE) {
                resizingModule = module;
                resizeHandle = handle;
                resizeStartBounds = bounds;
                resizeStartScale = getScale(module);
                selectedModule = module;
                return true;
            }

            if (!bounds.contains(event.x(), event.y())) continue;

            draggingModule = module;
            selectedModule = module;
            dragOffsetX = (int) Math.round(event.x()) - bounds.x;
            dragOffsetY = (int) Math.round(event.y()) - bounds.y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
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
    public boolean mouseReleased(Click click) {
        if (resizingModule != null) {
            resizingModule = null;
            resizeHandle = null;
            resizeStartBounds = null;
            BetterUCConfig.save();
            return true;
        }

        if (draggingModule != null) {
            draggingModule = null;
            BetterUCConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        BetterUCConfig.save();
        super.removed();
    }

    @Override
    public void close() {
        BetterUCConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void renderBackground(DrawContext context) {
        context.fill(0, 0, width, height, 0x88000000);
        for (int x = 0; x < width; x += 20) {
            context.fill(x, 0, x + 1, height, 0x1FFFFFFF);
        }
        for (int y = 0; y < height; y += 20) {
            context.fill(0, y, width, y + 1, 0x1FFFFFFF);
        }
        context.fill(0, 0, width, 38, 0xAA0D1117);
        context.fill(0, height - 36, width, height, 0xAA0D1117);
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
        };
    }

    private void setPosition(HudModule module, int x, int y) {
        switch (module) {
            case HEALTH -> {
                BetterUCConfig.INSTANCE.healthHudX = x;
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
        }
    }

    private int widthFor(HudModule module) {
        TextRenderer renderer = textRenderer;
        return switch (module) {
            case HEALTH -> {
                String style = BetterUCConfig.INSTANCE.healthHudStyle;
                int textWidth = renderer.getWidth("10");
                yield BetterUCConfig.isModernHudStyle(style) ? Math.max(34, textWidth + 27) : 9 + 4 + textWidth;
            }
            case FPS -> singleLineWidth(hudLabel(module), "144", prefixedText(module, "144"), BetterUCConfig.INSTANCE.fpsHudStyle);
            case PAYDAY -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.paydayHudStyle)
                    ? progressWidth(hudLabel(module), "25/60 min")
                    : renderer.getWidth(prefixedText(module, "25/60 Minuten")) + 4;
            case AMMO -> twoLineWidth(hudLabel(module), prefixedText(module, "20/96"), "TS19", BetterUCConfig.INSTANCE.ammoHudStyle);
            case BANK -> singleLineWidth(hudLabel(module), "88.375$", prefixedText(module, "88.375$"), BetterUCConfig.INSTANCE.bankHudStyle);
            case CASH -> singleLineWidth(hudLabel(module), previewCashValue(), prefixedText(module, previewCashValue()), BetterUCConfig.INSTANCE.cashHudStyle);
            case POTION -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.potionHudStyle)
                    ? 120
                    : Math.max(renderer.getWidth("Stärke II"), renderer.getWidth("Speed")) + 4;
            case SPRINT -> singleLineWidth(hudLabel(module), "ON", prefixedText(module, "ON"), BetterUCConfig.INSTANCE.toggleSprintHudStyle);
            case HACK_TIMER -> singleLineWidth(hudLabel(module), "02:39", prefixedText(module, "02:39"), BetterUCConfig.INSTANCE.hackTimerHudStyle);
            case PLANT_TIMER -> twoLineWidth(hudLabel(module), prefixedText(module, "Plantage Pulver 7/10"), "Reif: 1:30:00 | Wasser: 20:00", BetterUCConfig.INSTANCE.plantTimerHudStyle);
        };
    }

    private int heightFor(HudModule module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.healthHudStyle) ? 17 : 12;
            case PAYDAY -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.paydayHudStyle) ? 24 : 13;
            case AMMO -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.ammoHudStyle) ? 31 : 24;
            case POTION -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.potionHudStyle) ? 65 : 48;
            case PLANT_TIMER -> BetterUCConfig.isModernHudStyle(BetterUCConfig.INSTANCE.plantTimerHudStyle) ? 31 : 24;
            default -> 18;
        };
    }

    private int singleLineWidth(String label, String value, String text, String style) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            int labelGap = label.isBlank() ? 0 : 5;
            return Math.max(58, textRenderer.getWidth(label) + textRenderer.getWidth(value) + labelGap + 23);
        }
        return textRenderer.getWidth(text) + 4;
    }

    private int twoLineWidth(String label, String primary, String secondary, String style) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            String modernPrimary = stripPrefixValue(label, primary);
            int labelGap = label.isBlank() ? 0 : 5;
            return Math.max(58,
                    Math.max(
                            textRenderer.getWidth(label) + textRenderer.getWidth(modernPrimary) + labelGap + 23,
                            textRenderer.getWidth(secondary) + 16
                    ));
        }
        return Math.max(textRenderer.getWidth(primary), textRenderer.getWidth(secondary)) + 4;
    }

    private int progressWidth(String label, String value) {
        int labelGap = label.isBlank() ? 0 : 5;
        return Math.max(86, textRenderer.getWidth(label) + textRenderer.getWidth(value) + labelGap + 23);
    }

    private void renderHudModule(DrawContext context, HudModule module, int x, int y) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        String style = styleFor(module);
        String font = fontFor(module);
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
                Text health = Text.literal("10");
                int textColor = BetterUCConfig.INSTANCE.healthHudTextColor;
                int heartColor = BetterUCConfig.INSTANCE.healthHudHeartColor;
                if (modernStyle) {
                    int moduleWidth = widthFor(module);
                    boolean rightAligned = ModernHudRenderer.isRightAligned(x, moduleWidth);
                    int heartX = rightAligned ? x + moduleWidth - 16 : x + 7;
                    int textX = rightAligned ? heartX - minecraft.textRenderer.getWidth(health) - 4 : x + 19;
                    ModernHudRenderer.drawPanel(context, x, y, moduleWidth, 17, heartColor);
                    context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_TEXTURE, heartX, y + 4, 9, 9, heartColor);
                    ModernHudRenderer.drawHudTextWithShadow(context, minecraft.textRenderer, health, Math.max(x + 6, textX), y + 4, textColor);
                    return;
                }
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_TEXTURE, x, y, 9, 9, heartColor);
                if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft.textRenderer, style, font, health, x + 12, y, textColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, minecraft.textRenderer, health, x + 11, y, textColor);
                }
            }
            case FPS -> renderSingleLine(context, minecraft, style, font, x, y, hudLabel(module), "144", prefixedText(module, "144"), BetterUCConfig.INSTANCE.fpsHudColor);
            case PAYDAY -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, x, y, hudLabel(module), "25/60 min", 25.0F / 60.0F, BetterUCConfig.INSTANCE.paydayHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, prefixedText(module, "25/60 Minuten"), x, y, BetterUCConfig.INSTANCE.paydayHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, prefixedText(module, "25/60 Minuten"), x, y, BetterUCConfig.INSTANCE.paydayHudColor);
                }
            }
            case AMMO -> renderTwoLine(context, minecraft, style, font, x, y, hudLabel(module), prefixedText(module, "20/96"), "TS19", 0xFFFFAA33, 0xFF55FF55);
            case BANK -> renderSingleLine(context, minecraft, style, font, x, y, hudLabel(module), "88.375$", prefixedText(module, "88.375$"), BetterUCConfig.INSTANCE.bankHudColor);
            case CASH -> renderSingleLine(context, minecraft, style, font, x, y, hudLabel(module), previewCashValue(), prefixedText(module, previewCashValue()), BetterUCConfig.INSTANCE.cashHudColor);
            case POTION -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, x, y, "EFFECT", "Stärke II", "1:26", 0xFF9328FF);
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, x, y + 33, "EFFECT", "Speed", "0:49", 0xFF7CAFC6);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Stärke II", x, y, 0xFF9328FF);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "1:26", x, y + 11, ModernHudRenderer.TEXT_DIM);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Speed", x, y + 25, 0xFF7CAFC6);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "0:49", x, y + 36, ModernHudRenderer.TEXT_DIM);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Stärke II", x, y, 0xFF9328FF);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "1:26", x, y + 10, ModernHudRenderer.TEXT_DIM);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Speed", x, y + 24, 0xFF7CAFC6);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "0:49", x, y + 34, ModernHudRenderer.TEXT_DIM);
                }
            }
            case SPRINT -> renderSingleLine(context, minecraft, style, font, x, y, hudLabel(module), "ON", prefixedText(module, "ON"), BetterUCConfig.INSTANCE.toggleSprintHudColor);
            case HACK_TIMER -> renderSingleLine(context, minecraft, style, font, x, y, hudLabel(module), "02:39", prefixedText(module, "02:39"), 0xFF60A5FA);
            case PLANT_TIMER -> renderTwoLine(context, minecraft, style, font, x, y, hudLabel(module), prefixedText(module, "Plantage Pulver 7/10"), "Reif: 1:30:00 | Wasser: 20:00", 0xFF6CF27D, 0xFFFFD866);
            }
        });
    }

    private void renderSingleLine(
            DrawContext context,
            MinecraftClient minecraft,
            String style,
            String font,
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
            ModernHudRenderer.drawStyledText(context, minecraft, style, font, text, x, y, color);
        } else {
            ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, text, x, y, color);
        }
    }

    private void renderTwoLine(
            DrawContext context,
            MinecraftClient minecraft,
            String style,
            String font,
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
            ModernHudRenderer.drawStyledText(context, minecraft, style, font, primary, x, y, primaryColor);
            ModernHudRenderer.drawStyledText(context, minecraft, style, font, secondary, x, y + 11, secondaryColor);
        } else {
            ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, primary, x, y, primaryColor);
            ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, secondary, x, y + 10, secondaryColor);
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

    private void resizeSelectedModule(double mouseX, double mouseY) {
        if (resizingModule == null || resizeHandle == null || resizeStartBounds == null) {
            return;
        }

        int baseWidth = widthFor(resizingModule);
        int baseHeight = heightFor(resizingModule);
        boolean horizontal = resizeHandle.affectsLeft() || resizeHandle.affectsRight();
        boolean vertical = resizeHandle.affectsTop() || resizeHandle.affectsBottom();
        double widthScale = resizeStartScale;
        double heightScale = resizeStartScale;

        if (resizeHandle.affectsLeft()) {
            widthScale = (resizeStartBounds.right() - mouseX) / Math.max(1.0D, baseWidth);
        } else if (resizeHandle.affectsRight()) {
            widthScale = (mouseX - resizeStartBounds.x) / Math.max(1.0D, baseWidth);
        }

        if (resizeHandle.affectsTop()) {
            heightScale = (resizeStartBounds.bottom() - mouseY) / Math.max(1.0D, baseHeight);
        } else if (resizeHandle.affectsBottom()) {
            heightScale = (mouseY - resizeStartBounds.y) / Math.max(1.0D, baseHeight);
        }

        double nextScale;
        if (horizontal && vertical) {
            nextScale = (widthScale + heightScale) / 2.0D;
        } else if (horizontal) {
            nextScale = widthScale;
        } else {
            nextScale = heightScale;
        }

        float safeScale = BetterUCConfig.normalizeHudScale((float) nextScale);
        int nextWidth = ModernHudRenderer.scaledSize(baseWidth, safeScale);
        int nextHeight = ModernHudRenderer.scaledSize(baseHeight, safeScale);
        int nextX = resizeHandle.affectsLeft()
                ? resizeStartBounds.right() - nextWidth
                : resizeStartBounds.x;
        int nextY = resizeHandle.affectsTop()
                ? resizeStartBounds.bottom() - nextHeight
                : resizeStartBounds.y;

        nextX = clamp(nextX, 0, Math.max(0, width - nextWidth));
        nextY = clamp(nextY, 0, Math.max(0, height - nextHeight));
        setScale(resizingModule, safeScale);
        setPosition(resizingModule, nextX, nextY);
    }

    private ResizeHandle findResizeHandle(Bounds bounds, double mouseX, double mouseY) {
        int midX = bounds.x + bounds.width / 2;
        int midY = bounds.y + bounds.height / 2;
        int right = bounds.right();
        int bottom = bounds.bottom();

        if (handleContains(mouseX, mouseY, bounds.x, bounds.y)) return ResizeHandle.TOP_LEFT;
        if (handleContains(mouseX, mouseY, right, bounds.y)) return ResizeHandle.TOP_RIGHT;
        if (handleContains(mouseX, mouseY, bounds.x, bottom)) return ResizeHandle.BOTTOM_LEFT;
        if (handleContains(mouseX, mouseY, right, bottom)) return ResizeHandle.BOTTOM_RIGHT;
        if (handleContains(mouseX, mouseY, midX, bounds.y)) return ResizeHandle.TOP;
        if (handleContains(mouseX, mouseY, midX, bottom)) return ResizeHandle.BOTTOM;
        if (handleContains(mouseX, mouseY, bounds.x, midY)) return ResizeHandle.LEFT;
        if (handleContains(mouseX, mouseY, right, midY)) return ResizeHandle.RIGHT;
        return ResizeHandle.NONE;
    }

    private Bounds snapBounds(HudModule movingModule, Bounds moving) {
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

    private boolean handleContains(double mouseX, double mouseY, int centerX, int centerY) {
        int half = HANDLE_SIZE;
        return mouseX >= centerX - half && mouseX <= centerX + half
                && mouseY >= centerY - half && mouseY <= centerY + half;
    }

    private void drawDragBounds(DrawContext context, HudModule module, Bounds bounds, boolean active, ResizeHandle hoveredHandle) {
        int borderColor = active ? module.accent : 0x668899AA;
        context.fill(bounds.x - 3, bounds.y - 3, bounds.x + bounds.width + 3, bounds.y + bounds.height + 3, active ? 0x22000000 : 0x12000000);
        drawBorder(context, bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6, borderColor);
        String label = String.format(Locale.ROOT, "%s %d%%", module.label, Math.round(getScale(module) * 100.0F));
        context.drawTextWithShadow(textRenderer, Text.literal(label), bounds.x, Math.max(0, bounds.y - 12), borderColor | 0xFF000000);
        if (active) {
            drawResizeHandles(context, bounds, module.accent, hoveredHandle);
        }
    }

    private void drawResizeHandles(DrawContext context, Bounds bounds, int color, ResizeHandle hoveredHandle) {
        int midX = bounds.x + bounds.width / 2;
        int midY = bounds.y + bounds.height / 2;
        int right = bounds.right();
        int bottom = bounds.bottom();

        drawHandle(context, bounds.x, bounds.y, color, hoveredHandle == ResizeHandle.TOP_LEFT);
        drawHandle(context, midX, bounds.y, color, hoveredHandle == ResizeHandle.TOP);
        drawHandle(context, right, bounds.y, color, hoveredHandle == ResizeHandle.TOP_RIGHT);
        drawHandle(context, bounds.x, midY, color, hoveredHandle == ResizeHandle.LEFT);
        drawHandle(context, right, midY, color, hoveredHandle == ResizeHandle.RIGHT);
        drawHandle(context, bounds.x, bottom, color, hoveredHandle == ResizeHandle.BOTTOM_LEFT);
        drawHandle(context, midX, bottom, color, hoveredHandle == ResizeHandle.BOTTOM);
        drawHandle(context, right, bottom, color, hoveredHandle == ResizeHandle.BOTTOM_RIGHT);
    }

    private void drawHandle(DrawContext context, int centerX, int centerY, int color, boolean hovered) {
        int half = HANDLE_SIZE / 2;
        int fill = hovered ? 0xFFFFFFFF : 0xFFE2E8F0;
        context.fill(centerX - half - 1, centerY - half - 1, centerX + half + 2, centerY + half + 2, color);
        context.fill(centerX - half, centerY - half, centerX + half + 1, centerY + half + 1, fill);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private int resolveHealthX() {
        if (BetterUCConfig.INSTANCE.healthHudX >= 0) return BetterUCConfig.INSTANCE.healthHudX;
        return width / 2 - widthFor(HudModule.HEALTH) / 2;
    }

    private int resolveHealthY() {
        if (BetterUCConfig.INSTANCE.healthHudY >= 0) return BetterUCConfig.INSTANCE.healthHudY;
        return height / 2 + 15;
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
        PLANT_TIMER("Plant Timer", 0xFF6CF27D);

        private final String label;
        private final int accent;

        HudModule(String label, int accent) {
            this.label = label;
            this.accent = accent;
        }
    }

    private enum ResizeHandle {
        NONE(false, false, false, false),
        LEFT(true, false, false, false),
        RIGHT(false, true, false, false),
        TOP(false, false, true, false),
        BOTTOM(false, false, false, true),
        TOP_LEFT(true, false, true, false),
        TOP_RIGHT(false, true, true, false),
        BOTTOM_LEFT(true, false, false, true),
        BOTTOM_RIGHT(false, true, false, true);

        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;

        ResizeHandle(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        private boolean affectsLeft() {
            return left;
        }

        private boolean affectsRight() {
            return right;
        }

        private boolean affectsTop() {
            return top;
        }

        private boolean affectsBottom() {
            return bottom;
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
            return mouseX >= x - 4 && mouseX <= x + width + 4
                    && mouseY >= y - 14 && mouseY <= y + height + 4;
        }
    }
}
