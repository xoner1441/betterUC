package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ColorPickerScreen extends Screen {

    @FunctionalInterface
    public interface ColorApplyTarget {
        void apply(int color);
    }

    private final Screen parent;
    private final String headingText;
    private final ColorApplyTarget applyTarget;
    private final boolean forceOpaqueAlpha;
    private int r;
    private int g;
    private int b;

    public ColorPickerScreen(Screen parent, String title, String headingText, int color, ColorApplyTarget applyTarget) {
        this(parent, title, headingText, color, applyTarget, true);
    }

    private ColorPickerScreen(
            Screen parent,
            String title,
            String headingText,
            int color,
            ColorApplyTarget applyTarget,
            boolean forceOpaqueAlpha
    ) {
        super(Component.literal(title));
        this.parent = parent;
        this.headingText = headingText;
        this.applyTarget = applyTarget;
        this.forceOpaqueAlpha = forceOpaqueAlpha;
        this.r = (color >> 16) & 0xFF;
        this.g = (color >> 8) & 0xFF;
        this.b = color & 0xFF;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        AbstractSliderButton rSlider = new AbstractSliderButton(cx - 100, cy - 38, 200, 20, Component.literal("Rot: " + r), r / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Rot: " + (int) (value * 255)));
            }

            @Override
            protected void applyValue() {
                r = (int) (value * 255);
            }
        };
        AbstractSliderButton gSlider = new AbstractSliderButton(cx - 100, cy - 13, 200, 20, Component.literal("Gruen: " + g), g / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Gruen: " + (int) (value * 255)));
            }

            @Override
            protected void applyValue() {
                g = (int) (value * 255);
            }
        };
        AbstractSliderButton bSlider = new AbstractSliderButton(cx - 100, cy + 12, 200, 20, Component.literal("Blau: " + b), b / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Blau: " + (int) (value * 255)));
            }

            @Override
            protected void applyValue() {
                b = (int) (value * 255);
            }
        };

        addRenderableWidget(rSlider);
        addRenderableWidget(gSlider);
        addRenderableWidget(bSlider);

        int[] presets = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFFFFFF, 0xFFA500};
        for (int i = 0; i < presets.length; i++) {
            final int color = presets[i];
            int px = cx - 80 + (i * 22);
            addRenderableWidget(Button.builder(Component.literal(" "), btn -> applyPreset(color))
                    .bounds(px, cy + 37, 18, 18).build());
        }

        addRenderableWidget(Button.builder(Component.literal("\u2714 Uebernehmen"), widget -> {
            int color = (r << 16) | (g << 8) | b;
            if (forceOpaqueAlpha) {
                color |= 0xFF000000;
            }
            applyTarget.apply(color);
            BetterUCConfig.save();
            if (minecraft != null) minecraft.gui.setScreen(parent);
        }).bounds(cx - 80, cy + 62, 160, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), widget -> {
            if (minecraft != null) minecraft.gui.setScreen(parent);
        }).bounds(cx - 40, cy + 87, 80, 20).build());
    }

    private void applyPreset(int color) {
        r = (color >> 16) & 0xFF;
        g = (color >> 8) & 0xFF;
        b = color & 0xFF;
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = height / 2;

        context.centeredText(
                font,
                Component.literal(headingText),
                cx,
                cy - 85,
                0xFFFFFF
        );

        int previewColor = (r << 16) | (g << 8) | b;
        context.fill(cx - 31, cy - 69, cx + 31, cy - 53, 0xFF444444);
        context.fill(cx - 30, cy - 68, cx + 30, cy - 54, 0xFF000000 | previewColor);
        context.centeredText(
                font,
                Component.literal(String.format("#%02X%02X%02X", r, g, b)),
                cx,
                cy - 50,
                0xAAAAAA
        );

        int[] presets = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFFFFFF, 0xFFA500};
        for (int i = 0; i < presets.length; i++) {
            int px = cx - 80 + (i * 22);
            context.fill(px + 1, cy + 38, px + 17, cy + 54, 0xFF000000 | presets[i]);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
