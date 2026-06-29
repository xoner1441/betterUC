package com.betteruc.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientCompat {
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

    public static void setScreen(Minecraft client, Screen screen) {
        if (client == null) return;

        Method guiMethod = resolveGuiSetScreenMethod(client);
        if (invokeOneArg(guiMethod, client.gui, screen)) return;

        Method minecraftMethod = resolveMinecraftSetScreenMethod(client);
        invokeOneArg(minecraftMethod, client, screen);
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
}
