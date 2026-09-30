package com.betteruc.gui;

import com.betteruc.client.ClientCompat;
import java.util.List;
import java.util.Locale;
import me.dancedown.twitchemotes.TwitchEmotes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Searchable in-game overview of all currently loaded image emotes. */
public final class EmoteListScreen extends Screen {
    private static final int ACCENT = 0xFFF472B6;
    private static final int PANEL = 0xEE111827;
    private static final int TILE = 0xDD1E293B;
    private static final int TILE_HOVER = 0xFF334155;
    private static final int BORDER = 0xFF475569;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int MUTED = 0xFF94A3B8;
    private static final int COLUMNS = 5;
    private static final int ROWS = 4;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int TILE_HEIGHT = 32;
    private static final int GAP = 4;

    private final Screen parent;
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;

    public EmoteListScreen(Screen parent) {
        super(Component.literal("betterUC Emotes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = panelWidth();
        int panelX = (width - panelWidth) / 2;
        search = new EditBox(font, panelX, 38, panelWidth, 20, Component.literal("Emotes durchsuchen"));
        search.setHint(Component.literal("Emote-Namen suchen …"));
        search.setMaxLength(64);
        search.setResponder(value -> {
            page = 0;
            updateNavigation();
        });
        addRenderableWidget(search);

        int footerY = Math.min(height - 28, gridY() + ROWS * (TILE_HEIGHT + GAP) + 8);
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> onClose())
                .bounds(panelX, footerY, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Neu laden"), button -> TwitchEmotes.reload())
                .bounds(panelX + 78, footerY, 82, 20).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(panelX + panelWidth - 76, footerY, 34, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(panelX + panelWidth - 38, footerY, 34, 20).build());
        setInitialFocus(search);
        updateNavigation();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xD90B1018);
        int panelX = (width - panelWidth()) / 2;
        int top = 12;
        int bottom = Math.min(height - 34, gridY() + ROWS * (TILE_HEIGHT + GAP) + 4);
        context.fill(panelX - 10, top, panelX + panelWidth() + 10, bottom, PANEL);
        context.outline(panelX - 10, top, panelWidth() + 20, bottom - top, BORDER);
        context.centeredText(font, Component.literal("BILD-EMOTES"), width / 2, 18, ACCENT);

        List<String> names = filteredNames();
        String status = names.isEmpty()
                ? "Emotes werden geladen oder es gibt keinen Treffer"
                : names.size() + " Emotes | Seite " + (page + 1) + "/" + pageCount(names);
        context.centeredText(font, Component.literal(status), width / 2, 62, MUTED);

        int start = page * PAGE_SIZE;
        int end = Math.min(names.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int x = tileX(local);
            int y = tileY(local);
            boolean hovered = contains(mouseX, mouseY, x, y, tileWidth(), TILE_HEIGHT);
            context.fill(x, y, x + tileWidth(), y + TILE_HEIGHT, hovered ? TILE_HOVER : TILE);
            context.outline(x, y, tileWidth(), TILE_HEIGHT, hovered ? ACCENT : BORDER);
            String name = names.get(index);
            context.centeredText(font, TwitchEmotes.emotePreview(name), x + 14, y + 6, TEXT);
            String clipped = font.plainSubstrByWidth(name, tileWidth() - 8);
            context.centeredText(font, Component.literal(clipped), x + tileWidth() / 2, y + 19, TEXT);
            if (hovered) {
                context.setTooltipForNextFrame(font,
                        Component.literal(name + " – anklicken zum Einfügen"), mouseX, mouseY);
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            List<String> names = filteredNames();
            int local = entryAt(event.x(), event.y());
            int index = page * PAGE_SIZE + local;
            if (local >= 0 && index < names.size()) {
                ClientCompat.openChatWithText(minecraft, names.get(index) + " ");
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        ClientCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(page + direction, pageCount(filteredNames()) - 1));
        updateNavigation();
    }

    private void updateNavigation() {
        int pages = pageCount(filteredNames());
        page = Math.min(page, pages - 1);
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = page + 1 < pages;
    }

    private List<String> filteredNames() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return TwitchEmotes.EMOTE_REGISTRY.getKeys().stream()
                .filter(name -> query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private int pageCount(List<String> names) {
        return Math.max(1, (names.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int panelWidth() {
        return Math.min(680, Math.max(300, width - 40));
    }

    private int tileWidth() {
        return (panelWidth() - GAP * (COLUMNS - 1)) / COLUMNS;
    }

    private int gridY() {
        return 78;
    }

    private int tileX(int localIndex) {
        return (width - panelWidth()) / 2 + (localIndex % COLUMNS) * (tileWidth() + GAP);
    }

    private int tileY(int localIndex) {
        return gridY() + (localIndex / COLUMNS) * (TILE_HEIGHT + GAP);
    }

    private int entryAt(double mouseX, double mouseY) {
        for (int local = 0; local < PAGE_SIZE; local++) {
            if (contains(mouseX, mouseY, tileX(local), tileY(local), tileWidth(), TILE_HEIGHT)) return local;
        }
        return -1;
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
