package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.ServerGate;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CarFindTracker {

    private static final Pattern NAVI_COORDS_PATTERN = Pattern.compile(
            "(?i)(?:^|\\s)/?navi\\s+(-?\\d+)\\s*/\\s*(-?\\d+)\\s*/\\s*(-?\\d+)(?:\\b|$)"
    );
    private static final Pattern AXIS_TOKEN_PATTERN = Pattern.compile(
            "(?i)\\b([XYZ])\\s*[:=]\\s*([+\\-]?\\d+)"
    );
    private static final long CAR_FIND_RESULT_WINDOW_MS = 20_000L;
    private static final long AUTO_NAVI_COOLDOWN_MS = 2_000L;
    private static final long AUTO_NAVI_DUPLICATE_WINDOW_MS = 45_000L;

    private static long lastCarFindCommandMs = 0L;
    private static long lastAutoNaviMs = 0L;
    private static String lastAutoNaviKey = "";
    private static Integer pendingFindX = null;
    private static Integer pendingFindY = null;
    private static Integer pendingFindZ = null;

    private CarFindTracker() {
    }

    public static void handleOutgoingCommand(String command) {
        if (!isCarFindCommand(command)) return;

        lastCarFindCommandMs = System.currentTimeMillis();
        lastAutoNaviMs = 0L;
        lastAutoNaviKey = "";
        pendingFindX = null;
        pendingFindY = null;
        pendingFindZ = null;
        BetterUCMod.LOGGER.info("Car-find: /car find erkannt, warte auf Koordinaten-Nachricht");
    }

    public static void handleIncomingChat(MinecraftClient client, String raw) {
        if (client == null || client.player == null) return;
        if (raw == null || raw.isBlank()) return;
        if (!ServerGate.isAllowedServer(client)) return;

        long now = System.currentTimeMillis();
        if (now - lastCarFindCommandMs > CAR_FIND_RESULT_WINDOW_MS) return;
        if (now - lastAutoNaviMs < AUTO_NAVI_COOLDOWN_MS) return;

        Matcher matcher = NAVI_COORDS_PATTERN.matcher(raw);
        if (matcher.find()) {
            Integer x = parseCoordinateValue(matcher.group(1));
            Integer y = parseCoordinateValue(matcher.group(2));
            Integer z = parseCoordinateValue(matcher.group(3));
            if (x != null && y != null && z != null) {
                maybeSendNavi(client, x, y, z, now);
            }
            return;
        }

        Matcher axisMatcher = AXIS_TOKEN_PATTERN.matcher(raw);
        boolean sawAxisToken = false;
        while (axisMatcher.find()) {
            sawAxisToken = true;
            String axis = axisMatcher.group(1);
            Integer value = parseCoordinateValue(axisMatcher.group(2));
            if (value == null) continue;

            if ("X".equalsIgnoreCase(axis)) {
                pendingFindX = value;
            } else if ("Y".equalsIgnoreCase(axis)) {
                pendingFindY = value;
            } else if ("Z".equalsIgnoreCase(axis)) {
                pendingFindZ = value;
            }
        }
        if (!sawAxisToken) return;

        if (pendingFindX == null || pendingFindY == null || pendingFindZ == null) return;
        maybeSendNavi(client, pendingFindX, pendingFindY, pendingFindZ, now);
    }

    private static boolean isCarFindCommand(String command) {
        String normalized = normalizeCommand(command);
        if (normalized.isEmpty()) return false;

        String[] parts = normalized.split("\\s+");
        return parts.length >= 2
                && "car".equals(parts[0])
                && "find".equals(parts[1]);
    }

    private static String normalizeCommand(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static Integer parseCoordinateValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void maybeSendNavi(MinecraftClient client, int x, int y, int z, long now) {
        String key = x + "/" + y + "/" + z;

        if (key.equals(lastAutoNaviKey) && now - lastAutoNaviMs < AUTO_NAVI_DUPLICATE_WINDOW_MS) {
            return;
        }

        String command = "navi " + key;
        if (!ServerCommandUtil.sendAutomatic(client, command)) return;

        lastAutoNaviMs = now;
        lastAutoNaviKey = key;
        lastCarFindCommandMs = 0L;
        pendingFindX = null;
        pendingFindY = null;
        pendingFindZ = null;
        BetterUCMod.LOGGER.info("Car-find: Koordinaten erkannt -> /{}", command);
    }
}
