package com.betteruc.gui;

import com.betteruc.client.PingRelayClient;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class PingWheelScreen extends Screen {
    private static final int PANEL = 0xEE0D1117;
    private static final int PANEL_HOVER = 0xF0162330;
    private static final int BORDER = 0xAA334155;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int OPTION_W = 92;
    private static final int OPTION_H = 36;

    private final KeyBinding pingKey;
    private double lastMouseX;
    private double lastMouseY;
    private boolean sent;

    public PingWheelScreen(KeyBinding pingKey) {
        super(Text.literal("Pingrad"));
        this.pingKey = pingKey;
    }

    @Override
    protected void init() {
        lastMouseX = width / 2.0D;
        lastMouseY = height / 2.0D;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        context.fill(0, 0, width, height, 0x66000000);

        int cx = width / 2;
        int cy = height / 2;
        context.fill(cx - 42, cy - 14, cx + 42, cy + 14, 0xCC020617);
        drawBorder(context, cx - 42, cy - 14, 84, 28, 0x8038BDF8);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Pingtyp"), cx, cy - 5, TEXT);

        drawOption(context, mouseX, mouseY, PingRelayClient.PingType.NORMAL, cx - OPTION_W / 2, cy - 74);
        drawOption(context, mouseX, mouseY, PingRelayClient.PingType.DANGER, cx - OPTION_W - 24, cy + 34);
        drawOption(context, mouseX, mouseY, PingRelayClient.PingType.GATHER, cx + 24, cy + 34);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (pingKey.matchesMouse(click) || hoveredType(click.x(), click.y()) != null) {
            trySendSelected(click.x(), click.y());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (pingKey.matchesKey(input)) {
            trySendSelected(lastMouseX, lastMouseY);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            sent = true;
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawOption(
            DrawContext context,
            double mouseX,
            double mouseY,
            PingRelayClient.PingType type,
            int x,
            int y
    ) {
        boolean hovered = inBounds(mouseX, mouseY, x, y, OPTION_W, OPTION_H);
        int accent = colorFor(type);
        context.fill(x, y, x + OPTION_W, y + OPTION_H, hovered ? PANEL_HOVER : PANEL);
        drawBorder(context, x, y, OPTION_W, OPTION_H, hovered ? accent : BORDER);

        String icon = switch (type) {
            case DANGER -> "!";
            case GATHER -> "v";
            default -> "+";
        };
        context.drawTextWithShadow(textRenderer, Text.literal(icon), x + 10, y + 7, accent);
        context.drawTextWithShadow(textRenderer, Text.literal(type.label()), x + 24, y + 7, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(shortDescription(type)), x + 24, y + 19, MUTED);
    }

    private boolean trySendSelected(double mouseX, double mouseY) {
        if (sent) return true;
        PingRelayClient.PingType selected = hoveredType(mouseX, mouseY);
        if (selected == null) {
            sent = true;
            MinecraftClient minecraft = MinecraftClient.getInstance();
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return false;
        }

        sent = true;
        MinecraftClient minecraft = MinecraftClient.getInstance();
        PingRelayClient.sendPingAtCrosshair(minecraft, selected);
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
        return true;
    }

    private PingRelayClient.PingType hoveredType(double mouseX, double mouseY) {
        int cx = width / 2;
        int cy = height / 2;
        if (inBounds(mouseX, mouseY, cx - OPTION_W / 2, cy - 74, OPTION_W, OPTION_H)) {
            return PingRelayClient.PingType.NORMAL;
        }
        if (inBounds(mouseX, mouseY, cx - OPTION_W - 24, cy + 34, OPTION_W, OPTION_H)) {
            return PingRelayClient.PingType.DANGER;
        }
        if (inBounds(mouseX, mouseY, cx + 24, cy + 34, OPTION_W, OPTION_H)) {
            return PingRelayClient.PingType.GATHER;
        }
        return null;
    }

    private String shortDescription(PingRelayClient.PingType type) {
        return switch (type) {
            case DANGER -> "Achtung";
            case GATHER -> "Treffen";
            default -> "Standard";
        };
    }

    private int colorFor(PingRelayClient.PingType type) {
        String raw = switch (type) {
            case DANGER -> BetterUCConfig.INSTANCE.pingDangerColor;
            case GATHER -> BetterUCConfig.INSTANCE.pingGatherColor;
            default -> BetterUCConfig.INSTANCE.pingNormalColor;
        };
        if (raw == null) return 0xFF38BDF8;
        String clean = raw.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        try {
            return 0xFF000000 | (Integer.parseInt(clean, 16) & 0x00FFFFFF);
        } catch (NumberFormatException ignored) {
            return 0xFF38BDF8;
        }
    }

    private boolean inBounds(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
