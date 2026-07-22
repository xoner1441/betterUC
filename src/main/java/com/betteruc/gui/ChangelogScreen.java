package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ChangelogScreen extends Screen {

    private static final int ACCENT = 0xFF38BDF8;
    private static final int PANEL_BG = 0xF20D1117;
    private static final int PANEL_INNER = 0xF2141A22;
    private static final int BORDER = 0xFF334155;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int SOFT = 0xFFCBD5E1;
    private static final int MUTED = 0xFF94A3B8;
    private static final int BUTTON_H = 20;

    private final Screen parent;
    private final boolean welcomeMode;
    private final ChangelogContent.Page[] pages;
    private int pageIndex;
    private int contentScroll;
    private int contentHeight;

    public ChangelogScreen(Screen parent) {
        this(parent, false);
    }

    public ChangelogScreen(Screen parent, boolean welcomeMode) {
        super(Component.literal(welcomeMode ? "betterUC Willkommen" : "betterUC Changelog"));
        this.parent = parent;
        this.welcomeMode = welcomeMode;
        this.pages = welcomeMode ? ChangelogContent.latestPages() : ChangelogContent.allPages();
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelW();
        int panelH = panelH();
        int footerY = panelY + panelH - 31;

        addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(panelX + panelW - 126, footerY, 24, BUTTON_H)
                .build()).active = pageIndex > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(panelX + panelW - 96, footerY, 24, BUTTON_H)
                .build()).active = pageIndex < pages.length - 1;

        String closeLabel = welcomeMode ? "Los geht's" : "Zurück";
        addRenderableWidget(Button.builder(Component.literal(closeLabel), button -> closeToParent())
                .bounds(panelX + panelW - 66, footerY, 56, BUTTON_H)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (welcomeMode && parent != null) {
            parent.extractRenderState(context, mouseX, mouseY, delta);
        }
        context.fill(0, 0, width, height, welcomeMode ? 0xAA000000 : 0xE6000000);

        int panelX = panelX();
        int panelY = panelY();
        int panelW = panelW();
        int panelH = panelH();
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        context.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, PANEL_INNER);
        drawBorder(context, panelX, panelY, panelW, panelH, BORDER);
        context.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 4, ACCENT);

        renderHeader(context, panelX, panelY, panelW);
        renderPageNavigation(context, panelX, panelY, panelW, panelH, mouseX, mouseY);
        renderPage(context, panelX, panelY, panelW, panelH);
        renderFooter(context, panelX, panelY, panelW, panelH);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor context, int x, int y, int width) {
        context.text(font, Component.literal(welcomeMode ? "WILLKOMMEN ZURÜCK" : "CHANGELOG & FEATURES"),
                x + 18, y + 15, ACCENT);
        context.text(font, Component.literal(welcomeMode ? "Was ist neu?" : "betterUC im Überblick"),
                x + 18, y + 31, TEXT);
        context.text(font, Component.literal("1.2.9"), x + width - 18 - font.width("1.2.9"), y + 23, 0xFF86EFAC);

        int progressY = y + 50;
        int available = width - 36;
        int gap = 4;
        int segmentW = Math.max(8, (available - gap * (pages.length - 1)) / pages.length);
        for (int i = 0; i < pages.length; i++) {
            int segmentX = x + 18 + i * (segmentW + gap);
            context.fill(segmentX, progressY, segmentX + segmentW, progressY + 2,
                    i == pageIndex ? ACCENT : 0xFF334155);
        }
    }

    private void renderPageNavigation(
            GuiGraphicsExtractor context,
            int panelX,
            int panelY,
            int panelW,
            int panelH,
            int mouseX,
            int mouseY
    ) {
        if (!hasSidebar()) return;

        int x = panelX + 14;
        int y = panelY + 68;
        int width = sidebarW();
        int itemH = 30;
        for (int i = 0; i < pages.length; i++) {
            boolean selected = i == pageIndex;
            boolean hovered = inBounds(mouseX, mouseY, x, y, width, itemH - 4);
            if (selected || hovered) {
                context.fill(x, y, x + width, y + itemH - 4, selected ? 0xC2253342 : 0x80334152);
            }
            context.fill(x, y + 3, x + 2, y + itemH - 7, selected ? ACCENT : 0xFF475569);
            context.text(font, Component.literal(pages[i].title()), x + 9, y + 5, selected ? TEXT : SOFT);
            context.text(font, Component.literal((i + 1) + " / " + pages.length), x + 9, y + 16, MUTED);
            y += itemH;
            if (y + itemH > panelY + panelH - 38) break;
        }
    }

    private void renderPage(GuiGraphicsExtractor context, int panelX, int panelY, int panelW, int panelH) {
        int contentX = panelX + (hasSidebar() ? sidebarW() + 30 : 18);
        int contentY = panelY + 69;
        int contentW = panelW - (contentX - panelX) - 18;
        int contentBottom = panelY + panelH - 39;
        ChangelogContent.Page page = pages[pageIndex];

        drawVisibleText(context, page.eyebrow(), contentX, contentY - contentScroll, ACCENT, contentY, contentBottom);
        int cursorY = contentY + 17;
        cursorY = drawWrapped(context, page.title(), contentX, cursorY - contentScroll, contentW, TEXT,
                contentY, contentBottom, 12) + contentScroll;
        cursorY += 3;
        cursorY = drawWrapped(context, page.description(), contentX, cursorY - contentScroll, contentW, MUTED,
                contentY, contentBottom, 11) + contentScroll;
        cursorY += 10;

        for (String line : page.lines()) {
            int cardHeight = wrappedHeight(line, contentW - 30, 11) + 14;
            int drawY = cursorY - contentScroll;
            if (drawY + cardHeight >= contentY && drawY <= contentBottom) {
                context.fill(contentX, Math.max(contentY, drawY), contentX + contentW,
                        Math.min(contentBottom, drawY + cardHeight), 0x801B2430);
                if (drawY >= contentY) {
                    context.fill(contentX, drawY, contentX + 3, Math.min(contentBottom, drawY + cardHeight), ACCENT);
                }
                drawWrapped(context, line, contentX + 15, drawY + 7, contentW - 25, SOFT,
                        contentY, contentBottom, 11);
            }
            cursorY += cardHeight + 5;
        }

        contentHeight = Math.max(0, cursorY - contentY);
        contentScroll = clamp(contentScroll, 0, maxScroll(panelY, panelH));
        renderScrollbar(context, panelX + panelW - 9, contentY, contentBottom - contentY);
    }

    private void renderFooter(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int footerY = y + height - 37;
        context.fill(x + 10, footerY, x + width - 10, footerY + 1, 0xFF273341);
        context.text(font, Component.literal("Seite " + (pageIndex + 1) + " von " + pages.length),
                x + 18, footerY + 13, MUTED);
    }

    private int drawWrapped(
            GuiGraphicsExtractor context,
            String text,
            int x,
            int y,
            int maxWidth,
            int color,
            int clipTop,
            int clipBottom,
            int lineHeight
    ) {
        String remaining = text;
        int currentY = y;
        while (!remaining.isEmpty()) {
            String part = takeFittingText(remaining, maxWidth);
            drawVisibleText(context, part, x, currentY, color, clipTop, clipBottom);
            remaining = remaining.substring(part.length()).trim();
            currentY += lineHeight;
        }
        return currentY;
    }

    private int wrappedHeight(String text, int maxWidth, int lineHeight) {
        String remaining = text;
        int lines = 0;
        while (!remaining.isEmpty()) {
            String part = takeFittingText(remaining, maxWidth);
            remaining = remaining.substring(part.length()).trim();
            lines++;
        }
        return Math.max(lineHeight, lines * lineHeight);
    }

    private void drawVisibleText(
            GuiGraphicsExtractor context,
            String text,
            int x,
            int y,
            int color,
            int clipTop,
            int clipBottom
    ) {
        if (y >= clipTop && y + 9 <= clipBottom) {
            context.text(font, Component.literal(text), x, y, color);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor context, int x, int y, int height) {
        int max = maxScroll(panelY(), panelH());
        if (max <= 0) return;
        int thumbH = Math.max(24, (int) (height * (height / (double) (height + max))));
        int travel = Math.max(1, height - thumbH);
        int thumbY = y + (int) Math.round(travel * (contentScroll / (double) max));
        context.fill(x, y, x + 3, y + height, 0xFF273341);
        context.fill(x, thumbY, x + 3, thumbY + thumbH, ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inBounds(mouseX, mouseY, contentX(), panelY() + 64,
                panelX() + panelW() - contentX(), panelH() - 102)) {
            contentScroll = clamp(contentScroll - (int) Math.round(verticalAmount * 24.0D),
                    0, maxScroll(panelY(), panelH()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0 || !hasSidebar()) return false;

        int x = panelX() + 14;
        int y = panelY() + 68;
        for (int i = 0; i < pages.length; i++) {
            if (inBounds(event.x(), event.y(), x, y, sidebarW(), 26)) {
                pageIndex = i;
                contentScroll = 0;
                refreshWidgets();
                return true;
            }
            y += 30;
            if (y + 30 > panelY() + panelH() - 38) break;
        }
        return false;
    }

    private void changePage(int direction) {
        pageIndex = clamp(pageIndex + direction, 0, pages.length - 1);
        contentScroll = 0;
        refreshWidgets();
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    private void closeToParent() {
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, parent);
        }
    }

    private int panelW() {
        return Math.min(720, Math.max(300, width - 32));
    }

    private int panelH() {
        return Math.min(390, Math.max(220, height - 32));
    }

    private int panelX() {
        return width / 2 - panelW() / 2;
    }

    private int panelY() {
        return height / 2 - panelH() / 2;
    }

    private boolean hasSidebar() {
        return panelW() >= 560 && panelH() >= 285;
    }

    private int sidebarW() {
        return Math.min(174, panelW() / 3);
    }

    private int contentX() {
        return panelX() + (hasSidebar() ? sidebarW() + 30 : 18);
    }

    private int maxScroll(int panelY, int panelH) {
        int visibleHeight = panelH - 108;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private String takeFittingText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int lastSpace = -1;
        for (int i = 1; i <= text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i - 1))) lastSpace = i - 1;
            if (font.width(text.substring(0, i).trim()) > maxWidth) {
                return text.substring(0, lastSpace > 0 ? lastSpace : Math.max(1, i - 1)).trim();
            }
        }
        return text;
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
