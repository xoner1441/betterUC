package com.betteruc.client;

import com.betteruc.mixin.ChatComponentAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

public final class ChatCopyClient {

    private ChatCopyClient() {
    }

    public static boolean copyHoveredMainChatLine(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null || client.gui.hud == null || client.options == null) {
            return false;
        }

        ChatComponent chat = client.gui.hud.getChat();
        ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
        List<GuiMessage.Line> lines = accessor.betteruc$getTrimmedMessages();
        if (lines == null || lines.isEmpty()) {
            return false;
        }

        double scale = Math.max(0.01D, client.options.chatScale().get());
        int lineHeight = Math.max(1, accessor.betteruc$invokeGetLineHeight());
        int lineIndex = hoveredLineIndex(
                mouseY,
                ClientCompat.scaledWindowHeight(client, 480),
                scale,
                lineHeight,
                accessor.betteruc$getChatScrollbarPos(),
                lines.size(),
                accessor.betteruc$invokeGetLinesPerPage()
        );
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return false;
        }

        GuiMessage.Line line = lines.get(lineIndex);
        double localX = mouseX / scale - 4.0D;
        if (localX < 0.0D || localX > client.font.width(line.content())) {
            return false;
        }
        return copyText(client, line.parent().content().getString());
    }

    public static boolean copyText(Minecraft client, String text) {
        if (client == null || client.keyboardHandler == null || text == null || text.isBlank()) {
            return false;
        }
        client.keyboardHandler.setClipboard(text);
        if (client.gui != null && client.gui.hud != null) {
            client.gui.hud.setOverlayMessage(Component.literal("Chattext kopiert"), false);
        }
        return true;
    }

    static int hoveredLineIndex(
            double mouseY,
            int screenHeight,
            double scale,
            int lineHeight,
            int scrollPosition,
            int messageCount,
            int linesPerPage
    ) {
        if (scale <= 0.0D || lineHeight <= 0 || messageCount <= 0 || linesPerPage <= 0) {
            return -1;
        }
        double localY = mouseY / scale;
        int chatBottom = (int) Math.floor((screenHeight - 40.0D) / scale);
        double distanceFromBottom = chatBottom - localY;
        if (distanceFromBottom < 0.0D) {
            return -1;
        }
        int visibleIndex = (int) Math.floor(distanceFromBottom / lineHeight);
        int visibleCount = Math.min(Math.max(0, messageCount - scrollPosition), linesPerPage);
        if (visibleIndex < 0 || visibleIndex >= visibleCount) {
            return -1;
        }
        int index = visibleIndex + Math.max(0, scrollPosition);
        return index < messageCount ? index : -1;
    }
}
