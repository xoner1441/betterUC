package com.betteruc.gui;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

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

    public ColorPickerScreen(Screen parent, boolean isBlacklist, int color) {
        this(
                parent,
                isBlacklist ? "Blacklist Farbe" : "Fraktion Farbe",
                isBlacklist ? "\u00A7cBlacklist Farbe waehlen" : "\u00A7aFraktion Farbe waehlen",
                color,
                c -> {
                    if (isBlacklist) BetterUCConfig.INSTANCE.blacklistColor = c;
                    else BetterUCConfig.INSTANCE.factionColor = c;
                },
                false
        );
    }

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
        super(Text.literal(title));
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

        SliderWidget rSlider = new SliderWidget(cx - 100, cy - 38, 200, 20, Text.literal("Rot: " + r), r / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Rot: " + (int) (value * 255)));
            }

            @Override
            protected void applyValue() {
                r = (int) (value * 255);
            }
        };
        SliderWidget gSlider = new SliderWidget(cx - 100, cy - 13, 200, 20, Text.literal("Gruen: " + g), g / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Gruen: " + (int) (value * 255)));
            }

            @Override
            protected void applyValue() {
                g = (int) (value * 255);
            }
        };
        SliderWidget bSlider = new SliderWidget(cx - 100, cy + 12, 200, 20, Text.literal("Blau: " + b), b / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal("Blau: " + (int) (value * 255)));
            }

            @Override
            protected void applyValue() {
                b = (int) (value * 255);
            }
        };

        addDrawableChild(rSlider);
        addDrawableChild(gSlider);
        addDrawableChild(bSlider);

        int[] presets = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFFFFFF, 0xFFA500};
        for (int i = 0; i < presets.length; i++) {
            final int color = presets[i];
            int px = cx - 80 + (i * 22);
            addDrawableChild(ButtonWidget.builder(Text.literal(" "), btn -> applyPreset(color))
                    .dimensions(px, cy + 37, 18, 18).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("\u2714 Uebernehmen"), widget -> {
            int color = (r << 16) | (g << 8) | b;
            if (forceOpaqueAlpha) {
                color |= 0xFF000000;
            }
            applyTarget.apply(color);
            BetterUCConfig.save();
            if (client != null) client.setScreen(parent);
        }).dimensions(cx - 80, cy + 62, 160, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"), widget -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(cx - 40, cy + 87, 80, 20).build());
    }

    private void applyPreset(int color) {
        r = (color >> 16) & 0xFF;
        g = (color >> 8) & 0xFF;
        b = color & 0xFF;
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = height / 2;

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(headingText),
                cx,
                cy - 85,
                0xFFFFFF
        );

        int previewColor = (r << 16) | (g << 8) | b;
        context.fill(cx - 31, cy - 69, cx + 31, cy - 53, 0xFF444444);
        context.fill(cx - 30, cy - 68, cx + 30, cy - 54, 0xFF000000 | previewColor);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(String.format("#%02X%02X%02X", r, g, b)),
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
    public boolean shouldPause() {
        return false;
    }
}
