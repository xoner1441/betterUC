package com.betteruc.gui;

import com.betteruc.client.PingRelayClient;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class PingWheelScreen extends Screen {
    private static final int OVERLAY = 0x72000000;
    private static final int HUB = 0xEE0D1117;
    private static final int HUB_INNER = 0xF0141A22;
    private static final int PANEL = 0xF00D1117;
    private static final int PANEL_HOVER = 0xF0162330;
    private static final int BORDER = 0xAA334155;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int RADIUS = 78;
    private static final int OPTION_RADIUS = 33;

    private final KeyMapping pingKey;
    private double lastMouseX;
    private double lastMouseY;
    private boolean sent;

    public PingWheelScreen(KeyMapping pingKey) {
        super(Component.literal("Pingrad"));
        this.pingKey = pingKey;
    }

    @Override
    protected void init() {
        lastMouseX = width / 2.0D;
        lastMouseY = height / 2.0D;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        context.fill(0, 0, width, height, OVERLAY);

        int cx = width / 2;
        int cy = height / 2;
        PingRelayClient.PingType hovered = hoveredType(mouseX, mouseY);

        drawLine(context, cx, cy, optionX(cx, PingRelayClient.PingType.NORMAL), optionY(cy, PingRelayClient.PingType.NORMAL), 1, 0x6638BDF8);
        drawLine(context, cx, cy, optionX(cx, PingRelayClient.PingType.DANGER), optionY(cy, PingRelayClient.PingType.DANGER), 1, 0x66FF5555);
        drawLine(context, cx, cy, optionX(cx, PingRelayClient.PingType.GATHER), optionY(cy, PingRelayClient.PingType.GATHER), 1, 0x6622C55E);

        drawFilledCircle(context, cx, cy, 33, HUB);
        drawFilledCircle(context, cx, cy, 26, HUB_INNER);
        drawCircleBorder(context, cx, cy, 33, hovered == null ? 0xAA38BDF8 : colorFor(hovered));
        context.centeredText(font, Component.literal("PING"), cx, cy - 10, TEXT);
        context.centeredText(font, Component.literal(hovered == null ? "Abbruch" : hovered.label()), cx, cy + 5,
                hovered == null ? MUTED : colorFor(hovered));

        drawOption(context, mouseX, mouseY, PingRelayClient.PingType.NORMAL);
        drawOption(context, mouseX, mouseY, PingRelayClient.PingType.DANGER);
        drawOption(context, mouseX, mouseY, PingRelayClient.PingType.GATHER);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (pingKey.matchesMouse(click) || hoveredType(click.x(), click.y()) != null) {
            trySendSelected(click.x(), click.y());
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (pingKey.matches(input)) {
            trySendSelected(lastMouseX, lastMouseY);
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.input() == GLFW.GLFW_KEY_ESCAPE) {
            sent = true;
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawOption(GuiGraphicsExtractor context, double mouseX, double mouseY, PingRelayClient.PingType type) {
        int x = optionX(width / 2, type);
        int y = optionY(height / 2, type);
        boolean hovered = isOverOption(mouseX, mouseY, type);
        int accent = colorFor(type);
        int radius = hovered ? OPTION_RADIUS + hoverPulse() : OPTION_RADIUS;

        drawFilledCircle(context, x, y, radius + 2, hovered ? accent : BORDER);
        drawFilledCircle(context, x, y, radius, hovered ? PANEL_HOVER : PANEL);
        drawFilledCircle(context, x, y - 10, 13, withAlpha(accent, hovered ? 0xAA : 0x66));

        drawOptionIcon(context, x, y - 10, type, accent);
        context.centeredText(font, Component.literal(type.label()), x, y + 1, TEXT);
        context.centeredText(font, Component.literal(shortDescription(type)), x, y + 13, MUTED);
    }

    private boolean trySendSelected(double mouseX, double mouseY) {
        if (sent) return true;
        PingRelayClient.PingType selected = hoveredType(mouseX, mouseY);
        Minecraft minecraft = Minecraft.getInstance();
        sent = true;

        if (selected != null) {
            PingRelayClient.sendPingAtCrosshair(minecraft, selected);
        }
        if (minecraft != null) {
            minecraft.gui.setScreen(null);
        }
        return selected != null;
    }

    private PingRelayClient.PingType hoveredType(double mouseX, double mouseY) {
        for (PingRelayClient.PingType type : PingRelayClient.PingType.values()) {
            if (isOverOption(mouseX, mouseY, type)) return type;
        }
        return null;
    }

    private boolean isOverOption(double mouseX, double mouseY, PingRelayClient.PingType type) {
        int x = optionX(width / 2, type);
        int y = optionY(height / 2, type);
        double dx = mouseX - x;
        double dy = mouseY - y;
        return dx * dx + dy * dy <= (OPTION_RADIUS + 6) * (OPTION_RADIUS + 6);
    }

    private int optionX(int cx, PingRelayClient.PingType type) {
        double angle = angleFor(type);
        return cx + (int) Math.round(Math.cos(angle) * RADIUS);
    }

    private int optionY(int cy, PingRelayClient.PingType type) {
        double angle = angleFor(type);
        return cy + (int) Math.round(Math.sin(angle) * RADIUS);
    }

    private double angleFor(PingRelayClient.PingType type) {
        return switch (type) {
            case NORMAL -> Math.toRadians(-90.0D);
            case DANGER -> Math.toRadians(150.0D);
            case GATHER -> Math.toRadians(30.0D);
        };
    }

    private String shortDescription(PingRelayClient.PingType type) {
        return switch (type) {
            case DANGER -> "Achtung";
            case GATHER -> "Treffen";
            default -> "Standard";
        };
    }

    private int hoverPulse() {
        double wave = (Math.sin(System.currentTimeMillis() / 120.0D) + 1.0D) * 0.5D;
        return 2 + (int) Math.round(wave * 2.0D);
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

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private void drawFilledCircle(GuiGraphicsExtractor context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int width = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            context.fill(cx - width, cy + dy, cx + width + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleBorder(GuiGraphicsExtractor context, int cx, int cy, int radius, int color) {
        drawFilledCircle(context, cx, cy, radius, color);
        drawFilledCircle(context, cx, cy, Math.max(1, radius - 2), HUB);
        drawFilledCircle(context, cx, cy, Math.max(1, radius - 9), HUB_INNER);
    }

    private void drawOptionIcon(GuiGraphicsExtractor context, int x, int y, PingRelayClient.PingType type, int accent) {
        switch (type) {
            case DANGER -> {
                context.fill(x - 1, y - 8, x + 2, y + 3, accent);
                context.fill(x - 1, y + 6, x + 2, y + 9, accent);
            }
            case GATHER -> {
                drawLine(context, x, y - 9, x, y + 6, 1, accent);
                drawLine(context, x, y + 6, x - 7, y - 1, 1, accent);
                drawLine(context, x, y + 6, x + 7, y - 1, 1, accent);
                context.fill(x - 8, y + 9, x + 9, y + 11, accent);
            }
            default -> {
                context.fill(x - 9, y - 1, x + 10, y + 2, accent);
                context.fill(x - 1, y - 9, x + 2, y + 10, accent);
            }
        }
    }

    private void drawLine(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int thickness, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            context.fill(x - thickness, y - thickness, x + thickness + 1, y + thickness + 1, color);
        }
    }
}
