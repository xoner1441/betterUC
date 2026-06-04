package com.betteruc.client;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommunicationDeviceTracker {
    private static final Pattern BATTERY_PERCENT_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");

    private static boolean hasCommunicationDevice = true;
    private static boolean phonePowered = true;
    private static boolean batteryAvailable = true;
    private static String lastReason = "";

    private CommunicationDeviceTracker() {
    }

    public static void reset() {
        hasCommunicationDevice = true;
        phonePowered = true;
        batteryAvailable = true;
        lastReason = "";
    }

    public static void handleChatLine(MinecraftClient client, String raw) {
        if (raw == null || raw.isBlank()) return;
        String line = normalize(raw);
        String ownName = normalize(playerName(client));

        boolean mentionsSelf = line.contains(" dir ")
                || line.startsWith("dir ")
                || (!ownName.isBlank() && line.contains(ownName));

        if (mentionsSelf
                && (line.contains("kommunikationsgeraete") || line.contains("kommunikationsmittel"))
                && line.contains("abgenommen")) {
            hasCommunicationDevice = false;
            lastReason = "Kommunikationsgeräte wurden abgenommen";
            return;
        }

        if (mentionsSelf
                && (line.contains("kommunikationsgeraete") || line.contains("kommunikationsmittel"))
                && line.contains("wiedergegeben")) {
            hasCommunicationDevice = true;
            lastReason = "";
            return;
        }

        if ((line.contains("du kannst sie zuhause") && line.contains("/mobile"))
                || line.contains("dein handy liegt bei dir zuhause")) {
            hasCommunicationDevice = false;
            lastReason = "Handy liegt zuhause";
            return;
        }

        if (line.contains("du hast dein handy genommen")
                || line.contains("dein handy genommen")) {
            hasCommunicationDevice = true;
            lastReason = "";
            return;
        }

        if (line.contains("handy")
                && (line.contains("ist ausgeschaltet")
                || line.contains("ausgeschaltet")
                || line.contains("ausgeschalten"))) {
            phonePowered = false;
            lastReason = "Handy ist ausgeschaltet";
            return;
        }

        if (line.contains("handy")
                && (line.contains("ist eingeschaltet")
                || line.contains("eingeschaltet")
                || line.contains("angeschaltet")
                || line.contains("angemacht"))) {
            phonePowered = true;
            lastReason = "";
            return;
        }

        if (line.contains("akku") && line.contains("handy") && line.contains("leer")) {
            batteryAvailable = false;
            lastReason = "Handyakku ist leer";
            return;
        }

        if ((line.contains("akkustand") || line.contains("handyakku")) && line.contains("handy")) {
            Matcher matcher = BATTERY_PERCENT_PATTERN.matcher(line);
            if (matcher.find()) {
                int percent = parsePercent(matcher.group(1));
                batteryAvailable = percent > 0;
                if (!batteryAvailable) {
                    lastReason = "Handyakku ist leer";
                } else if ("Handyakku ist leer".equals(lastReason)) {
                    lastReason = "";
                }
            }
        }
    }

    public static boolean canPing() {
        return hasCommunicationDevice && phonePowered && batteryAvailable;
    }

    public static String blockMessage() {
        if (!hasCommunicationDevice) {
            if ("Handy liegt zuhause".equals(lastReason)) {
                return "Ping blockiert: Dein Handy liegt zuhause. Hole es mit /mobile wieder ab.";
            }
            return "Ping blockiert: Deine Kommunikationsgeräte fehlen.";
        }
        if (!phonePowered) return "Ping blockiert: Dein Handy ist ausgeschaltet.";
        if (!batteryAvailable) return "Ping blockiert: Dein Handyakku ist leer.";
        return "";
    }

    public static String statusLabel() {
        if (canPing()) return "Bereit";
        if (!hasCommunicationDevice) {
            return "Geräte fehlen";
        }
        if (!phonePowered) {
            return "Handy aus";
        }
        if (!batteryAvailable) {
            return "Akku leer";
        }
        return "Blockiert";
    }

    private static String normalize(String value) {
        return String.valueOf(value)
                .replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "")
                .toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .trim();
    }

    private static String playerName(MinecraftClient client) {
        if (client == null || client.player == null) return "";
        String name = client.player.getName().getString();
        return name == null ? "" : name;
    }

    private static int parsePercent(String raw) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
