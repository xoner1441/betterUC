package com.betteruc.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class SecondChatSettingsUi {

    static final int BACKGROUND = 0xF0121821;
    static final int HEADER_BACKGROUND = 0xFF0D1117;
    static final int PANEL_BACKGROUND = 0xB018222E;
    static final int PANEL_BORDER = 0x66334155;
    static final int FOOTER_BACKGROUND = 0xE60D1117;
    static final int TEXT_PRIMARY = 0xFFF8FAFC;
    static final int TEXT_MUTED = 0xFF94A3B8;
    static final int ACCENT = 0xFF38BDF8;

    private SecondChatSettingsUi() {
    }

    static void renderPage(
            GuiGraphicsExtractor context,
            Font font,
            int width,
            int height,
            String title,
            String subtitle
    ) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 52, HEADER_BACKGROUND);
        context.fill(0, 51, width, 52, ACCENT);
        context.fill(0, height - 38, width, height, FOOTER_BACKGROUND);
        context.fill(0, height - 39, width, height - 38, PANEL_BORDER);
        context.text(font, Component.literal(title), 18, 14, TEXT_PRIMARY);
        context.text(font, Component.literal(subtitle), 18, 29, TEXT_MUTED);
    }

    static void renderPanel(
            GuiGraphicsExtractor context,
            int x,
            int y,
            int width,
            int height
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        context.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        context.fill(x, y, x + width, y + 1, PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        context.fill(x, y, x + 1, y + height, PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);
    }

    static void renderSection(
            GuiGraphicsExtractor context,
            Font font,
            Section section,
            int scrollOffset,
            int clipTop,
            int clipBottom
    ) {
        int y = section.logicalY() - scrollOffset;
        if (y < clipTop || y + 11 > clipBottom) {
            return;
        }
        context.text(font, Component.literal(section.label()), section.x(), y + 1, section.color());
        int lineStart = section.x() + font.width(section.label()) + 8;
        int lineEnd = section.x() + section.span();
        if (lineStart < lineEnd) {
            context.fill(lineStart, y + 7, lineEnd, y + 8, withAlpha(section.color(), 0x66));
        }
    }

    static void renderScrollbar(
            GuiGraphicsExtractor context,
            int x,
            int top,
            int bottom,
            int contentHeight,
            int scrollOffset,
            int maxScroll
    ) {
        if (maxScroll <= 0 || bottom <= top) {
            return;
        }
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(22,
                trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int thumbY = top + (int) ((trackHeight - thumbHeight)
                * (scrollOffset / (double) maxScroll));
        context.fill(x, top, x + 3, bottom, 0x55334155);
        context.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    record Section(String label, int x, int logicalY, int span, int color) {
    }
}
