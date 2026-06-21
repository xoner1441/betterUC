package com.betteruc;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class PlayerNameUtil {

    private PlayerNameUtil() {
    }

    public static String resolveProfileName(Object profile) {
        if (profile == null) return null;

        String name = invokeStringMethod(profile, "name");
        if (name != null && !name.isBlank()) return name;

        name = invokeStringMethod(profile, "getName");
        if (name != null && !name.isBlank()) return name;

        return null;
    }

    public static String resolveProfileUuid(Object profile) {
        if (profile == null) return "";

        UUID id = invokeUuidMethod(profile, "id");
        if (id != null) return id.toString();

        id = invokeUuidMethod(profile, "getId");
        if (id != null) return id.toString();

        return "";
    }

    public static Set<String> getOnlinePlayerNamesLowercase(Minecraft client) {
        Set<String> onlineLower = new LinkedHashSet<>();
        if (client == null || client.getConnection() == null) return onlineLower;

        for (PlayerInfo entry : client.getConnection().getOnlinePlayers()) {
            if (entry == null) continue;
            String name = resolveProfileName(entry.getProfile());
            if (name != null && !name.isBlank()) {
                onlineLower.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return onlineLower;
    }

    private static String invokeStringMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof String s ? s : null;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static UUID invokeUuidMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }
}
