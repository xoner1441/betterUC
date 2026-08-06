package com.betteruc.hud;

import com.betteruc.client.ClientCompat;
import com.betteruc.client.ChatGeometryCompat;
import com.betteruc.client.SecondChatManager;
import com.betteruc.client.SecondChatTextCompat;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatTabConfig;
import com.betteruc.config.SecondChatWindowConfig;
import com.betteruc.gui.SecondChatEditorScreen;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class SecondChatHud {

    public static final int TITLE_HEIGHT = 17;
    public static final int MIN_WIDTH = 180;
    public static final int MIN_HEIGHT = 60;
    public static final int MAX_WIDTH = 600;
    public static final int MAX_HEIGHT = 320;

    private static final int BORDER = 0x99333C49;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int LINE_HEIGHT = 10;
    private static final long MESSAGE_VISIBLE_MS = 10_000L;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final Map<String, CachedLines> LINE_CACHE = new LinkedHashMap<>();
    private static List<TooltipHitbox> tooltipHitboxes = List.of();
    private static List<ActionHitbox> actionHitboxes = List.of();
    private static List<TextHitbox> textHitboxes = List.of();

    private SecondChatHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("betteruc", "second_chat"),
                (context, tickCounter) -> {
                    Minecraft client = Minecraft.getInstance();
                    tooltipHitboxes = List.of();
                    actionHitboxes = List.of();
                    textHitboxes = List.of();
                    if (!BetterUCConfig.INSTANCE.secondChatEnabled) return;
                    if (!ModernHudRenderer.shouldRenderGameplayHud()) return;
                    if (ClientCompat.currentScreen(client) instanceof SecondChatEditorScreen) return;

                    List<TooltipHitbox> rebuiltHitboxes = new ArrayList<>();
                    List<ActionHitbox> rebuiltActions = new ArrayList<>();
                    List<TextHitbox> rebuiltTextHitboxes = new ArrayList<>();
                    SecondChatTabConfig primary = SecondChatManager.activeTab(SecondChatManager.PRIMARY_WINDOW_ID);
                    if (primary != null) {
                        renderWindow(
                                context,
                                SecondChatManager.PRIMARY_WINDOW_ID,
                                primary,
                                configuredBounds(SecondChatManager.PRIMARY_WINDOW_ID),
                                false,
                                rebuiltHitboxes,
                                rebuiltActions,
                                rebuiltTextHitboxes
                        );
                    }
                    for (SecondChatWindowConfig window : SecondChatManager.windows()) {
                        SecondChatTabConfig tab = SecondChatManager.activeTab(window.id);
                        if (tab != null) {
                            renderWindow(
                                    context,
                                    window.id,
                                    tab,
                                    configuredBounds(window.id),
                                    false,
                                    rebuiltHitboxes,
                                    rebuiltActions,
                                    rebuiltTextHitboxes
                            );
                        }
                    }
                    tooltipHitboxes = List.copyOf(rebuiltHitboxes);
                    actionHitboxes = List.copyOf(rebuiltActions);
                    textHitboxes = List.copyOf(rebuiltTextHitboxes);
                }
        );
    }

    public static void renderEditorPreview(GuiGraphicsExtractor context) {
        renderEditorPreview(context, SecondChatManager.PRIMARY_WINDOW_ID);
    }

    public static void renderEditorPreview(GuiGraphicsExtractor context, String windowId) {
        SecondChatTabConfig tab = SecondChatManager.activeTab(windowId);
        if (tab == null) {
            List<SecondChatTabConfig> tabs = SecondChatManager.tabsForWindow(windowId);
            tab = tabs.isEmpty() ? null : tabs.get(0);
        }
        renderWindow(context, windowId, tab, configuredBounds(windowId), true,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public static Bounds configuredBounds() {
        return configuredBounds(SecondChatManager.PRIMARY_WINDOW_ID);
    }

    public static Bounds configuredBounds(String windowId) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId)) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                ChatGeometryCompat.Geometry chat = ChatGeometryCompat.focusedBounds(client);
                return new Bounds(chat.x(), chat.y(), chat.width(), chat.height());
            }
        }
        if (!SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId)) {
            SecondChatWindowConfig window = SecondChatManager.findWindow(windowId);
            if (window != null) {
                return clampedBounds(window.x, window.y, window.width, window.height);
            }
        }
        return clampedBounds(config.secondChatX, config.secondChatY, config.secondChatWidth, config.secondChatHeight);
    }

    public static void renderTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        for (TooltipHitbox hitbox : tooltipHitboxes) {
            if (!hitbox.box.contains(mouseX, mouseY) || hitbox.text.isBlank()) {
                continue;
            }
            int tooltipWidth = client.font.width(hitbox.text) + 12;
            int screenWidth = ClientCompat.scaledWindowWidth(client, 854);
            int x = clamp(mouseX + 9, 2, Math.max(2, screenWidth - tooltipWidth - 2));
            int y = Math.max(2, mouseY - 18);
            context.fill(x, y, x + tooltipWidth, y + 16, 0xF0121821);
            drawOutline(context, x, y, tooltipWidth, 16, 0xFF475569);
            context.text(client.font, Component.literal(hitbox.text), x + 6, y + 4, TEXT_PRIMARY);
            return;
        }
    }

    public static boolean scrollWindow(String windowId, double verticalAmount) {
        SecondChatTabConfig tab = SecondChatManager.activeTab(windowId);
        if (tab == null || verticalAmount == 0.0D) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        Bounds bounds = configuredBounds(windowId);
        int lineHeight = scaledLineHeight(tab);
        int titleHeight = SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId) || tab.name.isBlank()
                ? 0 : TITLE_HEIGHT;
        int visibleLines = Math.max(1, (bounds.panelHeight - titleHeight - 8) / lineHeight);
        int contentWidth = Math.max(1, (int) Math.floor((bounds.panelWidth - 15) / textScale(tab)));
        int maximum = Math.max(0, renderLines(client, tab, contentWidth, false).size() - visibleLines);
        int amount = verticalAmount > 0.0D ? 3 : -3;
        SecondChatManager.scrollBy(tab.id, amount, maximum);
        return true;
    }

    public static boolean mouseClicked(double mouseX, double mouseY) {
        for (ActionHitbox hitbox : actionHitboxes) {
            if (hitbox.box.contains(mouseX, mouseY) && "newest".equals(hitbox.action)) {
                SecondChatManager.jumpToNewest(hitbox.tabId);
                return true;
            }
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        for (TextHitbox hitbox : textHitboxes) {
            if (!hitbox.box.contains(mouseX, mouseY)) {
                continue;
            }
            int localX = Math.max(0, (int) Math.floor((mouseX - hitbox.textX) / hitbox.scale));
            var style = SecondChatTextCompat.styleAtWidth(client.font, hitbox.text, localX);
            if (ClientCompat.handleTextClick(client, style)) {
                return true;
            }
        }
        return false;
    }

    public static void jumpToFirstUnread(String windowId, String tabId, long firstUnreadAt) {
        if (firstUnreadAt <= 0L) {
            return;
        }
        SecondChatTabConfig tab = SecondChatManager.findTab(tabId);
        Minecraft client = Minecraft.getInstance();
        if (tab == null || client == null) {
            return;
        }
        Bounds bounds = configuredBounds(windowId);
        int titleHeight = SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId) || tab.name.isBlank()
                ? 0 : TITLE_HEIGHT;
        int lineHeight = scaledLineHeight(tab);
        int visibleLines = Math.max(1, (bounds.panelHeight - titleHeight - 8) / lineHeight);
        int contentWidth = Math.max(1, (int) Math.floor((bounds.panelWidth - 15) / textScale(tab)));
        List<RenderLine> lines = renderLines(client, tab, contentWidth, false);
        int firstIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).createdAtMs >= firstUnreadAt) {
                firstIndex = i;
                break;
            }
        }
        if (firstIndex < 0) {
            return;
        }
        int maximum = Math.max(0, lines.size() - visibleLines);
        int offset = Math.max(0, lines.size() - firstIndex - visibleLines);
        SecondChatManager.setScrollOffset(tab.id, offset, maximum);
    }

    private static Bounds clampedBounds(int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = ClientCompat.scaledWindowWidth(client, 854);
        int screenHeight = ClientCompat.scaledWindowHeight(client, 480);
        int width = clamp(sourceWidth, MIN_WIDTH, Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, screenWidth)));
        int height = clamp(sourceHeight, MIN_HEIGHT, Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, screenHeight)));
        int x = clamp(sourceX, 0, Math.max(0, screenWidth - width));
        int y = clamp(sourceY, 0, Math.max(0, screenHeight - height));
        return new Bounds(x, y, width, height);
    }

    private static void renderWindow(
            GuiGraphicsExtractor context,
            String windowId,
            SecondChatTabConfig tab,
            Bounds bounds,
            boolean editorPreview,
            List<TooltipHitbox> hitboxes,
            List<ActionHitbox> actions,
            List<TextHitbox> textActions
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || (tab == null && !editorPreview)) return;

        BetterUCConfig config = BetterUCConfig.INSTANCE;
        int x = bounds.x;
        int y = bounds.y;
        int width = bounds.panelWidth;
        int height = bounds.panelHeight;
        boolean dockedPrimary = SecondChatManager.PRIMARY_WINDOW_ID.equals(windowId) && !editorPreview;
        Object currentScreen = ClientCompat.currentScreen(client);
        boolean chatOpen = currentScreen != null
                && "ChatScreen".equals(currentScreen.getClass().getSimpleName());
        float textScale = textScale(tab);
        int contentWidth = Math.max(1, (int) Math.floor((width - 15) / textScale));
        List<RenderLine> lines = renderLines(client, tab, contentWidth, editorPreview);
        long now = System.currentTimeMillis();
        int fadeMs = tab == null ? 3_000 : tab.fadeSeconds * 1_000;
        int windowAlpha = editorPreview || chatOpen ? 255 : newestVisibleAlpha(lines, now, fadeMs);
        if (windowAlpha <= 0) {
            return;
        }

        boolean showBackground = tab == null ? config.secondChatBackgroundEnabled : tab.background;
        if (showBackground || editorPreview) {
            int configuredAlpha = tab == null
                    ? config.secondChatBackgroundOpacity
                    : tab.backgroundOpacity;
            int alpha = editorPreview ? Math.max(110, configuredAlpha) : configuredAlpha;
            int background = withAlpha(0x000D1117, scaleAlpha(alpha, windowAlpha));
            context.fill(x, y, x + width, y + height, background);
            drawOutline(context, x, y, width, height, scaleColorAlpha(BORDER, windowAlpha));
        }

        if (!dockedPrimary) {
            String tabName = tab == null ? "Chat 2" : tab.name;
            if (!tabName.isBlank()) {
                context.text(client.font, Component.literal(tabName), x + 8, y + 5,
                        scaleColorAlpha(TEXT_PRIMARY, windowAlpha));
                int messageCount = tab == null ? 0 : SecondChatManager.snapshot(tab.id).size();
                if (messageCount > 0) {
                    String count = Integer.toString(messageCount);
                    context.text(client.font, Component.literal(count),
                            x + width - 7 - client.font.width(count), y + 5,
                            scaleColorAlpha(TEXT_MUTED, windowAlpha));
                }
            }
        }

        boolean hasTitle = tab == null || !tab.name.isBlank();
        int top = dockedPrimary || !hasTitle ? y + 3 : y + TITLE_HEIGHT + 2;
        int bottom = y + height - 4;
        int lineHeight = scaledLineHeight(tab);
        int lineY = bottom - lineHeight;
        int scrollOffset = tab == null ? 0 : SecondChatManager.scrollOffset(tab.id);
        long firstUnreadAt = tab == null ? 0L : SecondChatManager.firstUnreadAt(tab.id);
        int startIndex = Math.max(-1, lines.size() - 1 - scrollOffset);
        for (int i = startIndex; i >= 0 && lineY >= top; i--) {
            RenderLine line = lines.get(i);
            int lineAlpha = editorPreview || chatOpen ? 255 : visibilityAlpha(line.createdAtMs, now, fadeMs);
            if (lineAlpha <= 0) {
                continue;
            }
            if (line.highlighted) {
                int baseColor = line.highlightColor == 0
                        ? config.secondChatHighlightColor
                        : line.highlightColor;
                context.fill(x + 5, lineY - 1, x + width - 4, lineY + lineHeight,
                        withAlpha(baseColor, scaleAlpha(0x34, lineAlpha)));
                context.fill(x + 5, lineY - 1, x + 7, lineY + lineHeight,
                        scaleColorAlpha(forceOpaque(baseColor), lineAlpha));
            }
            boolean shadow = tab == null || tab.messageShadow;
            int textColor = withAlpha(0x00FFFFFF, lineAlpha);
            drawScaledText(context, client, line.text, x + 9, lineY, textScale, textColor, shadow);
            if (chatOpen && !editorPreview) {
                int renderedTextWidth = Math.max(
                        1,
                        Math.round(client.font.width(line.text) * textScale)
                );
                textActions.add(new TextHitbox(
                        new Bounds(x + 9, lineY, renderedTextWidth, lineHeight),
                        line.text,
                        textScale,
                        x + 9
                ));
            }
            if ((chatOpen || editorPreview) && !line.tooltip.isBlank()) {
                hitboxes.add(new TooltipHitbox(
                        new Bounds(x + 5, lineY - 1, width - 9, lineHeight + 1),
                        line.tooltip
                ));
            }
            if ((chatOpen || editorPreview) && firstUnreadAt > 0L
                    && line.createdAtMs >= firstUnreadAt
                    && (i == 0 || lines.get(i - 1).createdAtMs < firstUnreadAt)) {
                int separatorY = Math.max(top, lineY - 2);
                int separatorColor = scaleColorAlpha(
                        forceOpaque(config.secondChatAccentColor), lineAlpha);
                context.fill(x + 5, separatorY, x + width - 5, separatorY + 1, separatorColor);
                String label = "Neu";
                int labelWidth = client.font.width(label);
                context.fill(x + width - labelWidth - 13, separatorY - 4,
                        x + width - 5, separatorY + 5, withAlpha(0x000D1117, lineAlpha));
                context.text(client.font, label, x + width - labelWidth - 9,
                        separatorY - 3, separatorColor);
            }
            lineY -= lineHeight;
        }
        if (chatOpen && tab != null && SecondChatManager.hasNewWhileScrolled(tab.id)) {
            String marker = "Neue Nachrichten";
            int markerWidth = client.font.width(marker) + 12;
            int markerX = x + width - markerWidth - 5;
            context.fill(markerX, bottom - 14, x + width - 4, bottom, 0xE01D4ED8);
            context.text(client.font, marker, markerX + 6, bottom - 11, 0xFFFFFFFF);
            actions.add(new ActionHitbox(
                    new Bounds(markerX, bottom - 14, markerWidth + 1, 14),
                    tab.id,
                    "newest"
            ));
        }
    }

    private static List<RenderLine> renderLines(
            Minecraft client,
            SecondChatTabConfig tab,
            int contentWidth,
            boolean editorPreview
    ) {
        String tabId = tab == null ? "preview" : tab.id;
        String cacheKey = tabId + ":" + contentWidth + ":" + (tab != null && tab.timestamps);
        long revision = SecondChatManager.revision();
        CachedLines cached = LINE_CACHE.get(cacheKey);
        if (cached == null || cached.revision != revision) {
            List<RenderLine> rebuilt = new ArrayList<>();
            if (tab != null) {
                for (SecondChatManager.Entry entry : SecondChatManager.snapshot(tab.id)) {
                    Component displayMessage = entry.message();
                    if (tab.timestamps) {
                        displayMessage = Component.empty()
                                .append(Component.literal("[" + TIME_FORMAT.format(
                                                Instant.ofEpochMilli(entry.createdAtMs())) + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
                                .append(SecondChatTextCompat.copyWithResolvedStyles(entry.message()));
                    }
                    for (FormattedCharSequence line : SecondChatTextCompat.wrap(client.font, displayMessage, contentWidth)) {
                        rebuilt.add(new RenderLine(
                                 line,
                                 entry.highlighted(),
                                 entry.highlightColor(),
                                 entry.tooltip(),
                                 entry.createdAtMs()
                         ));
                     }
                     if (entry.repeatCount() > 1) {
                        Component repeat = Component.literal("x" + entry.repeatCount());
                        for (FormattedCharSequence line : SecondChatTextCompat.wrap(client.font, repeat, contentWidth)) {
                            rebuilt.add(new RenderLine(
                                     line,
                                     entry.highlighted(),
                                     entry.highlightColor(),
                                     entry.tooltip(),
                                     entry.createdAtMs()
                             ));
                         }
                     }
                }
            }
            cached = new CachedLines(revision, List.copyOf(rebuilt));
            LINE_CACHE.put(cacheKey, cached);
            if (LINE_CACHE.size() > 32) {
                LINE_CACHE.remove(LINE_CACHE.keySet().iterator().next());
            }
        }
        if (!cached.lines.isEmpty() || !editorPreview) {
            return cached.lines;
        }

        List<RenderLine> preview = new ArrayList<>();
        addPreviewLine(preview, client, Component.literal("[HQ] Gesuchter Spieler | 24 Wanteds"),
                contentWidth, false, 0, "");
        addPreviewLine(preview, client, Component.literal("Reinf | Polizei | 126m"),
                contentWidth, false, 0, "");
        addPreviewLine(preview, client, Component.literal("Dein Name wurde erwähnt"),
                contentWidth, true, BetterUCConfig.INSTANCE.secondChatHighlightColor, "Eigener Name");
        return preview;
    }

    private static void addPreviewLine(
            List<RenderLine> target,
            Minecraft client,
            Component message,
            int width,
            boolean highlighted,
            int highlightColor,
            String tooltip
    ) {
        for (FormattedCharSequence line : SecondChatTextCompat.wrap(client.font, message, width)) {
            target.add(new RenderLine(line, highlighted, highlightColor, tooltip, 0L));
        }
    }

    private static int newestVisibleAlpha(List<RenderLine> lines, long now, int fadeMs) {
        int alpha = 0;
        for (RenderLine line : lines) {
            alpha = Math.max(alpha, visibilityAlpha(line.createdAtMs, now, fadeMs));
            if (alpha >= 255) {
                return 255;
            }
        }
        return alpha;
    }

    private static int visibilityAlpha(long createdAtMs, long now, int fadeMs) {
        if (createdAtMs <= 0L) {
            return 255;
        }
        long age = Math.max(0L, now - createdAtMs);
        if (age <= MESSAGE_VISIBLE_MS) {
            return 255;
        }
        long fadeAge = age - MESSAGE_VISIBLE_MS;
        if (fadeMs <= 0 || fadeAge >= fadeMs) {
            return 0;
        }
        return clamp((int) Math.round(255.0D * (1.0D - (double) fadeAge / fadeMs)), 0, 255);
    }

    private static float textScale(SecondChatTabConfig tab) {
        return tab == null ? 1.0F : clamp(tab.fontScalePercent, 75, 150) / 100.0F;
    }

    private static int scaledLineHeight(SecondChatTabConfig tab) {
        return Math.max(8, Math.round(LINE_HEIGHT * textScale(tab)));
    }

    private static void drawScaledText(
            GuiGraphicsExtractor context,
            Minecraft client,
            FormattedCharSequence text,
            int x,
            int y,
            float scale,
            int color,
            boolean shadow
    ) {
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        if (shadow) {
            context.text(client.font, text, 0, 0, color);
        } else {
            context.text(client.font, text, 0, 0, color, false);
        }
        context.pose().popMatrix();
    }

    private static int scaleAlpha(int alpha, int visibilityAlpha) {
        return clamp((alpha * visibilityAlpha + 127) / 255, 0, 255);
    }

    private static int scaleColorAlpha(int color, int visibilityAlpha) {
        return withAlpha(color, scaleAlpha(color >>> 24, visibilityAlpha));
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int forceOpaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void drawOutline(
            GuiGraphicsExtractor context,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record RenderLine(
            FormattedCharSequence text,
            boolean highlighted,
            int highlightColor,
            String tooltip,
            long createdAtMs
    ) {
    }

    private record CachedLines(long revision, List<RenderLine> lines) {
    }

    private record TooltipHitbox(Bounds box, String text) {
    }

    private record ActionHitbox(Bounds box, String tabId, String action) {
    }

    private record TextHitbox(
            Bounds box,
            FormattedCharSequence text,
            float scale,
            int textX
    ) {
    }

    public record Bounds(int x, int y, int panelWidth, int panelHeight) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + panelWidth
                    && mouseY >= y && mouseY < y + panelHeight;
        }
    }
}
