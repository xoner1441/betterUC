package com.betteruc.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.Normalizer;
import java.util.Map;
import java.util.Locale;

public final class WeaponNameMatcher {

    private static final Map<String, String> SUPPORTED_NAMES = Map.ofEntries(
            Map.entry("p69", "P69"),
            Map.entry("scatter3", "Scatter3"),
            Map.entry("kr47", "KR47"),
            Map.entry("ts19", "TS19"),
            Map.entry("ax12", "AX12"),
            Map.entry("extenso18", "Extenso18"),
            Map.entry("viper9", "Viper9")
    );

    private WeaponNameMatcher() {
    }

    public static boolean isSupportedWeapon(ItemStack stack) {
        return !canonicalName(stack).isBlank();
    }

    public static String canonicalName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Component hoverName = stack.getHoverName();
        return canonicalName(hoverName == null ? "" : hoverName.getString());
    }

    static String canonicalName(String rawName) {
        return SUPPORTED_NAMES.getOrDefault(normalize(rawName), "");
    }

    public static String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";

        String cleaned = rawName.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}+", "");
        StringBuilder ascii = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            ascii.append(toAsciiWeaponCharacter(normalized.charAt(i)));
        }
        return ascii.toString().replaceAll("[^a-z0-9]+", "");
    }

    private static char toAsciiWeaponCharacter(char value) {
        return switch (value) {
            case 'ᴀ' -> 'a';
            case 'ʙ' -> 'b';
            case 'ᴄ' -> 'c';
            case 'ᴅ' -> 'd';
            case 'ᴇ' -> 'e';
            case 'ꜰ' -> 'f';
            case 'ɢ' -> 'g';
            case 'ʜ' -> 'h';
            case 'ɪ' -> 'i';
            case 'ᴊ' -> 'j';
            case 'ᴋ' -> 'k';
            case 'ʟ' -> 'l';
            case 'ᴍ' -> 'm';
            case 'ɴ' -> 'n';
            case 'ᴏ' -> 'o';
            case 'ᴘ' -> 'p';
            case 'ʀ' -> 'r';
            case 'ꜱ' -> 's';
            case 'ᴛ' -> 't';
            case 'ᴜ' -> 'u';
            case 'ᴠ' -> 'v';
            case 'ᴡ' -> 'w';
            case 'ʏ' -> 'y';
            case 'ᴢ' -> 'z';
            default -> value;
        };
    }
}
