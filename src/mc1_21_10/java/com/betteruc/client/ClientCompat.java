package com.betteruc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;

public final class ClientCompat {
    private ClientCompat() {
    }

    public static Screen currentScreen(MinecraftClient client) {
        return client == null ? null : client.currentScreen;
    }

    public static boolean hasScreen(MinecraftClient client) {
        return currentScreen(client) != null;
    }

    public static void setScreen(MinecraftClient client, Screen screen) {
        if (client != null) {
            client.setScreen(screen);
        }
    }

    public static Camera mainCamera(MinecraftClient client) {
        if (client == null || client.gameRenderer == null) return null;
        return client.gameRenderer.getCamera();
    }
}
