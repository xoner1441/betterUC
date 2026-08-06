package com.betteruc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;

public final class ChatGeometryCompat {

    private ChatGeometryCompat() {
    }

    public static Geometry focusedBounds(Minecraft client) {
        if (client == null || client.options == null) {
            return new Geometry(0, 240, 320, 200);
        }
        int screenHeight = ClientCompat.scaledWindowHeight(client, 480);
        double scale = Math.max(0.01D, client.options.chatScale().get());
        int width = ChatComponent.getWidth(client.options.chatWidth().get())
                + (int) Math.ceil(8.0D * scale);
        int height = ChatComponent.getHeight(client.options.chatHeightFocused().get());
        int y = Math.max(0, screenHeight - 40 - height);
        return new Geometry(0, y, width, height);
    }

    public record Geometry(int x, int y, int width, int height) {
    }
}
