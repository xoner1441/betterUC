package com.betteruc.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class ClientCompat {
    private static final long HUD_SCREEN_CACHE_NS = 16_000_000L;
    private static Method guiScreenMethod;
    private static boolean guiScreenMethodResolved;
    private static Field minecraftScreenField;
    private static boolean minecraftScreenFieldResolved;
    private static Method guiSetScreenMethod;
    private static boolean guiSetScreenMethodResolved;
    private static Method minecraftSetScreenMethod;
    private static boolean minecraftSetScreenMethodResolved;
    private static Method mainCameraMethod;
    private static boolean mainCameraMethodResolved;
    private static Method getMainCameraMethod;
    private static boolean getMainCameraMethodResolved;
    private static long lastHudScreenCheckNs;
    private static boolean suppressGameplayHud;

    private ClientCompat() {
    }

    public static Screen currentScreen(Minecraft client) {
        if (client == null) return null;

        Object fromGui = invokeNoArg(resolveGuiScreenMethod(client), client.gui);
        if (fromGui instanceof Screen screen) return screen;

        try {
            Field field = resolveMinecraftScreenField(client);
            Object value = field == null ? null : field.get(client);
            return value instanceof Screen screen ? screen : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static boolean hasScreen(Minecraft client) {
        return currentScreen(client) != null;
    }

    public static boolean hasWindow(Minecraft client) {
        return client != null && client.getWindow() != null;
    }

    public static int scaledWindowWidth(Minecraft client, int fallback) {
        return hasWindow(client)
                ? Math.max(1, client.getWindow().getGuiScaledWidth())
                : Math.max(1, fallback);
    }

    public static int scaledWindowHeight(Minecraft client, int fallback) {
        return hasWindow(client)
                ? Math.max(1, client.getWindow().getGuiScaledHeight())
                : Math.max(1, fallback);
    }

    public static boolean isLeftMouseDown(Minecraft client) {
        return hasWindow(client)
                && GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
    }

    public static boolean suppressGameplayHud(Minecraft client) {
        long now = System.nanoTime();
        if (now - lastHudScreenCheckNs < HUD_SCREEN_CACHE_NS) {
            return suppressGameplayHud;
        }

        Screen screen = currentScreen(client);
        suppressGameplayHud = screen != null && !(screen instanceof ChatScreen);
        lastHudScreenCheckNs = now;
        return suppressGameplayHud;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        if (client == null) return;

        Method guiMethod = resolveGuiSetScreenMethod(client);
        if (invokeOneArg(guiMethod, client.gui, screen)) return;

        Method minecraftMethod = resolveMinecraftSetScreenMethod(client);
        invokeOneArg(minecraftMethod, client, screen);
    }

    public static void openChatWithText(Minecraft client, String text) {
        if (client != null) {
            setScreen(client, new ChatScreen(text == null ? "" : text, false));
        }
    }

    public static boolean handleTextClick(Minecraft client, Style style) {
        if (client == null || style == null || style.getClickEvent() == null) {
            return false;
        }
        Screen screen = currentScreen(client);
        if (screen == null) {
            return false;
        }
        ClickEvent event = style.getClickEvent();
        return invokeDefaultClick("defaultHandleGameClickEvent", event, client, screen)
                || invokeDefaultClick("defaultHandleClickEvent", event, client, screen);
    }

    public static Camera mainCamera(Minecraft client) {
        if (client == null || client.gameRenderer == null) return null;

        Object fromNewApi = invokeNoArg(resolveMainCameraMethod(client), client.gameRenderer);
        if (fromNewApi instanceof Camera camera) return camera;

        Object fromOldApi = invokeNoArg(resolveGetMainCameraMethod(client), client.gameRenderer);
        return fromOldApi instanceof Camera camera ? camera : null;
    }

    private static Method resolveGuiScreenMethod(Minecraft client) {
        if (guiScreenMethodResolved) return guiScreenMethod;
        guiScreenMethodResolved = true;
        guiScreenMethod = findMethod(client.gui, "screen");
        return guiScreenMethod;
    }

    private static Field resolveMinecraftScreenField(Minecraft client) {
        if (minecraftScreenFieldResolved) return minecraftScreenField;
        minecraftScreenFieldResolved = true;
        try {
            minecraftScreenField = client.getClass().getField("screen");
        } catch (NoSuchFieldException ignored) {
            minecraftScreenField = null;
        }
        return minecraftScreenField;
    }

    private static Method resolveGuiSetScreenMethod(Minecraft client) {
        if (guiSetScreenMethodResolved) return guiSetScreenMethod;
        guiSetScreenMethodResolved = true;
        guiSetScreenMethod = findMethod(client.gui, "setScreen", Screen.class);
        return guiSetScreenMethod;
    }

    private static Method resolveMinecraftSetScreenMethod(Minecraft client) {
        if (minecraftSetScreenMethodResolved) return minecraftSetScreenMethod;
        minecraftSetScreenMethodResolved = true;
        minecraftSetScreenMethod = findMethod(client, "setScreen", Screen.class);
        return minecraftSetScreenMethod;
    }

    private static Method resolveMainCameraMethod(Minecraft client) {
        if (mainCameraMethodResolved) return mainCameraMethod;
        mainCameraMethodResolved = true;
        mainCameraMethod = findMethod(client.gameRenderer, "mainCamera");
        return mainCameraMethod;
    }

    private static Method resolveGetMainCameraMethod(Minecraft client) {
        if (getMainCameraMethodResolved) return getMainCameraMethod;
        getMainCameraMethodResolved = true;
        getMainCameraMethod = findMethod(client.gameRenderer, "getMainCamera");
        return getMainCameraMethod;
    }

    private static Method findMethod(Object target, String name, Class<?>... parameterTypes) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    public static Vec3 cameraPosition(Camera camera) {
        return camera == null ? Vec3.ZERO : camera.position();
    }

    private static Object invokeNoArg(Method method, Object target) {
        if (method == null || target == null) return null;
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeOneArg(Method method, Object target, Object argument) {
        if (method == null || target == null) return false;
        try {
            method.invoke(target, argument);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean invokeDefaultClick(
            String methodName,
            ClickEvent event,
            Minecraft client,
            Screen screen
    ) {
        try {
            Method method = Screen.class.getDeclaredMethod(
                    methodName,
                    ClickEvent.class,
                    Minecraft.class,
                    Screen.class
            );
            method.setAccessible(true);
            Object result = method.invoke(null, event, client, screen);
            return !(result instanceof Boolean handled) || handled;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
