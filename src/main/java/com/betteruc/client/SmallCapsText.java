package com.betteruc.client;

import java.util.Locale;

final class SmallCapsText {
    private static final String ASCII = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘqʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCapsText() {
    }

    static String convert(String value) {
        if (value == null || value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder converted = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char current = lower.charAt(i);
            int index = ASCII.indexOf(current);
            converted.append(index >= 0 ? SMALL_CAPS.charAt(index) : current);
        }
        return converted.toString();
    }

    static String fold(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder folded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            int index = SMALL_CAPS.indexOf(current);
            folded.append(index >= 0 ? ASCII.charAt(index) : current);
        }
        return folded.toString();
    }
}
