package com.betteruc.config;

import java.util.Locale;
import java.util.UUID;

public final class SecondChatFilterConfig {

    public String id = UUID.randomUUID().toString();
    public String name = "Neuer Filter";
    public String matcher = "custom";
    public String includeText = "";
    public String excludeText = "";
    public String matchType = "contains";
    public String mode = "copy";
    public boolean enabled = true;
    public boolean customBackground = false;
    public int backgroundColor = 0xFFFFD54A;
    public boolean playSound = false;
    public boolean hideMessage = false;
    public boolean filterTooltip = true;
    public boolean caseSensitive = false;

    public SecondChatFilterConfig() {
    }

    public SecondChatFilterConfig(String name, String matcher, String mode) {
        this.name = name;
        this.matcher = matcher;
        this.mode = mode;
    }

    public SecondChatFilterConfig copy() {
        SecondChatFilterConfig copy = new SecondChatFilterConfig(name, matcher, mode);
        copy.includeText = includeText;
        copy.excludeText = excludeText;
        copy.matchType = matchType;
        copy.enabled = enabled;
        copy.customBackground = customBackground;
        copy.backgroundColor = backgroundColor;
        copy.playSound = playSound;
        copy.hideMessage = hideMessage;
        copy.filterTooltip = filterTooltip;
        copy.caseSensitive = caseSensitive;
        return copy;
    }

    public void sanitize(int index) {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        name = sanitizeText(name, "Filter " + (index + 1), 28);
        if ("custom".equalsIgnoreCase(matcher)
                && "PayDay".equalsIgnoreCase(name)
                && "payday;gehalt;neuerbetrag".equalsIgnoreCase(
                includeText == null ? "" : includeText.replace(" ", ""))) {
            matcher = "payday";
            includeText = "";
        }
        matcher = sanitizeMatcher(matcher);
        includeText = sanitizeText(includeText, "", 160);
        excludeText = sanitizeText(excludeText, "", 160);
        matchType = sanitizeMatchType(matchType);
        mode = sanitizeMode(mode);
        backgroundColor = 0xFF000000 | (backgroundColor & 0x00FFFFFF);
    }

    private static String sanitizeMatcher(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "hq", "payday", "reinf", "private", "server", "betteruc", "ownname" -> normalized;
            default -> "custom";
        };
    }

    private static String sanitizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "copy", "move", "highlight", "ignore" -> normalized;
            default -> "off";
        };
    }

    private static String sanitizeMatchType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "starts".equals(normalized) ? "starts" : "contains";
    }

    private static String sanitizeText(String value, String fallback, int maxLength) {
        String clean = value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
        if (clean.isBlank()) {
            clean = fallback;
        }
        return clean.substring(0, Math.min(clean.length(), maxLength));
    }
}
