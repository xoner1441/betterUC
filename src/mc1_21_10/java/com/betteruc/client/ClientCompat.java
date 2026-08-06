package com.betteruc.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Style;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class ClientCompat {
    private ClientCompat() {
    }

    public static Screen currentScreen(MinecraftClient client) {
        return client == null ? null : client.currentScreen;
    }

    public static boolean hasScreen(MinecraftClient client) {
        return currentScreen(client) != null;
    }

    public static boolean hasWindow(MinecraftClient client) {
        return client != null && client.getWindow() != null;
    }

    public static int scaledWindowWidth(MinecraftClient client, int fallback) {
        return hasWindow(client)
                ? Math.max(1, client.getWindow().getScaledWidth())
                : Math.max(1, fallback);
    }

    public static int scaledWindowHeight(MinecraftClient client, int fallback) {
        return hasWindow(client)
                ? Math.max(1, client.getWindow().getScaledHeight())
                : Math.max(1, fallback);
    }

    public static boolean isLeftMouseDown(MinecraftClient client) {
        return hasWindow(client)
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
    }

    public static boolean suppressGameplayHud(MinecraftClient client) {
        return false;
    }

    public static void setScreen(MinecraftClient client, Screen screen) {
        if (client != null) {
            client.setScreen(screen);
        }
    }

    public static void openChatWithText(MinecraftClient client, String text) {
        if (client != null) {
            client.setScreen(new ChatScreen(text == null ? "" : text, false));
        }
    }

    public static boolean handleTextClick(MinecraftClient client, Style style) {
        return client != null
                && client.currentScreen != null
                && style != null
                && style.getClickEvent() != null
                && client.currentScreen.handleTextClick(style);
    }

    public static Camera mainCamera(MinecraftClient client) {
        if (client == null || client.gameRenderer == null) return null;
        return client.gameRenderer.getCamera();
    }

    public static Vec3d cameraPosition(Camera camera) {
        return camera == null ? Vec3d.ZERO : camera.getPos();
    }
}
