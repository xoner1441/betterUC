package com.betteruc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;
import java.util.Set;

public final class ServerGate {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "unicacity.eu",
            "server.unicacity.eu",
            "mc.unicacity.eu"
    );

    private ServerGate() {
    }

    public static boolean isAllowedServer(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) return false;
        ServerInfo info = client.getCurrentServerEntry();
        if (info == null || info.address == null || info.address.isBlank()) return false;
        return isAllowedAddress(info.address);
    }

    public static boolean isAllowedAddress(String rawAddress) {
        String host = extractHost(rawAddress);
        return !host.isEmpty() && ALLOWED_HOSTS.contains(host);
    }

    public static String allowedServersLabel() {
        return "unicacity.eu, server.unicacity.eu, mc.unicacity.eu";
    }

    private static String extractHost(String rawAddress) {
        if (rawAddress == null) return "";
        String address = rawAddress.trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty()) return "";

        int schemeIdx = address.indexOf("://");
        if (schemeIdx >= 0) {
            address = address.substring(schemeIdx + 3);
        }

        int slashIdx = address.indexOf('/');
        if (slashIdx >= 0) {
            address = address.substring(0, slashIdx);
        }

        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end > 1) {
                address = address.substring(1, end);
            }
        } else {
            int colon = address.indexOf(':');
            if (colon >= 0) {
                address = address.substring(0, colon);
            }
        }

        while (address.endsWith(".")) {
            address = address.substring(0, address.length() - 1);
        }
        return address;
    }
}
