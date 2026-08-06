package com.betteruc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;

public final class ChatGeometryCompat {

    private ChatGeometryCompat() {
    }

    public static Geometry focusedBounds(MinecraftClient client) {
        int screenHeight = client.getWindow().getScaledHeight();
        double scale = Math.max(0.01D, client.options.getChatScale().getValue());
        int width = ChatHud.getWidth(client.options.getChatWidth().getValue())
                + (int) Math.ceil(8.0D * scale);
        int height = ChatHud.getHeight(client.options.getChatHeightFocused().getValue());
        int y = Math.max(0, screenHeight - 40 - height);
        return new Geometry(0, y, width, height);
    }

    public record Geometry(int x, int y, int width, int height) {
        public int getWidth() {
            return width;
        }
    }
}
