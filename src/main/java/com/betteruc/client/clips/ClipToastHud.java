package com.betteruc.client.clips;

import com.betteruc.hud.ModernHudRenderer;
import com.betteruc.hud.RichTaxAlertHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Same slide-in card style as the tax warning; never a permanent recording overlay. */
final class ClipToastHud {
    static final int WIDTH = 240;
    static final int HEIGHT = 50;
    static final int GAP = 6;
    private static final ClipToastState STATE = new ClipToastState();

    private ClipToastHud() {}

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "clip_toasts"),
                (context, delta) -> render(context));
    }

    static void show(ClipNotice notice) {
        Minecraft.getInstance().execute(() -> STATE.show(notice));
    }

    static void clearCapture() { STATE.clearCapture(); }

    static void tick() {
        STATE.advance(System.nanoTime() / 1_000_000L, canRender());
    }

    private static boolean canRender() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.isWindowActive() && !client.gui.hud.isHidden()
                && ModernHudRenderer.shouldRenderGameplayHud();
    }

    static int stackY(int occupiedHeight, int index) {
        return 10 + occupiedHeight + index * (HEIGHT + GAP);
    }

    private static void render(GuiGraphicsExtractor context) {
        tick();
        if (!canRender()) return;
        Minecraft client = Minecraft.getInstance();
        int width = Math.min(WIDTH, Math.max(1, context.guiWidth() - 20));
        int occupiedHeight = RichTaxAlertHud.toastStackOffset();
        var notices = STATE.visible();
        for (int i = 0; i < notices.size(); i++) {
            var visible = notices.get(i);
            var notice = visible.notice();
            int x = context.guiWidth() - 10 - width + (int) Math.round((1 - visible.visibility()) * (width + 14));
            int y = stackY(occupiedHeight, i);
            context.fill(x, y, x + width, y + HEIGHT, 0xEC0D1117);
            context.fill(x, y, x + 4, y + HEIGHT, notice.accent());
            context.fill(x, y, x + width, y + 1, 0xAA475569);
            context.fill(x, y + HEIGHT - 1, x + width, y + HEIGHT, 0xAA475569);
            context.fill(x, y, x + 1, y + HEIGHT, 0xAA475569);
            context.fill(x + width - 1, y, x + width, y + HEIGHT, 0xAA475569);
            drawLine(context, notice.title(), x + 11, y + 6, width - 22, notice.accent());
            drawLine(context, notice.detail(), x + 11, y + 19, width - 22, 0xFFF8FAFC);
            drawLine(context, notice.footer(), x + 11, y + 31, width - 22, 0xFFCBD5E1);
            int barX = x + 11;
            int barY = y + HEIGHT - 5;
            int barWidth = Math.max(0, width - 22);
            context.fill(barX, barY, barX + barWidth, barY + 2, 0xFF293241);
            if (notice.kind() == ClipNotice.Kind.SAVING) {
                // Indeterminate activity indicator, not a fabricated export percentage.
                int segment = Math.max(1, barWidth / 4);
                double phase = (1 - Math.cos(visible.elapsedMs() * Math.PI / 900)) / 2;
                int start = (int) Math.round((barWidth - segment) * phase);
                context.fill(barX + start, barY, barX + start + segment, barY + 2, notice.accent());
            } else {
                int filled = (int) Math.round(barWidth * visible.remaining());
                context.fill(barX, barY, barX + filled, barY + 2, notice.accent());
            }
        }
    }

    private static void drawLine(GuiGraphicsExtractor context, String text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        if (width <= 0) return;
        String fitted = font.width(text) <= width ? text
                : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
        context.text(font, Component.literal(fitted), x, y, color);
    }
}
