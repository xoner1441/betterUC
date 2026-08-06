package com.betteruc.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SecondChatTabConfig {

    public String id = UUID.randomUUID().toString();
    public String name = "Chat 2";
    public int messageLimit = 100;
    public boolean combineEqualMessages = false;
    public boolean antiChatClear = true;
    public boolean messageShadow = true;
    public boolean background = true;
    public int backgroundOpacity = 150;
    public int fontScalePercent = 100;
    public int fadeSeconds = 3;
    public boolean timestamps = false;
    public String mentionTerms = "";
    public boolean mentionSound = true;
    public int mentionColor = 0xFFFFD54A;
    public String hqMode = "copy";
    public String reinfMode = "copy";
    public String privateMode = "copy";
    public String serverInfoMode = "off";
    public String betterUcMode = "copy";
    public String ownNameMode = "highlight";
    public String custom1Mode = "off";
    public String custom2Mode = "off";
    public String custom3Mode = "off";
    public String custom1Text = "";
    public String custom2Text = "";
    public String custom3Text = "";
    public String windowId = "primary";
    public boolean filtersMigrated = false;
    public List<SecondChatFilterConfig> filters = new ArrayList<>();

    public SecondChatTabConfig() {
    }

    public SecondChatTabConfig(String name) {
        this.name = name;
    }

    public SecondChatTabConfig copyAs(String newName) {
        SecondChatTabConfig copy = new SecondChatTabConfig(newName);
        copy.messageLimit = messageLimit;
        copy.combineEqualMessages = combineEqualMessages;
        copy.antiChatClear = antiChatClear;
        copy.messageShadow = messageShadow;
        copy.background = background;
        copy.backgroundOpacity = backgroundOpacity;
        copy.fontScalePercent = fontScalePercent;
        copy.fadeSeconds = fadeSeconds;
        copy.timestamps = timestamps;
        copy.mentionTerms = mentionTerms;
        copy.mentionSound = mentionSound;
        copy.mentionColor = mentionColor;
        copy.hqMode = hqMode;
        copy.reinfMode = reinfMode;
        copy.privateMode = privateMode;
        copy.serverInfoMode = serverInfoMode;
        copy.betterUcMode = betterUcMode;
        copy.ownNameMode = ownNameMode;
        copy.custom1Mode = custom1Mode;
        copy.custom2Mode = custom2Mode;
        copy.custom3Mode = custom3Mode;
        copy.custom1Text = custom1Text;
        copy.custom2Text = custom2Text;
        copy.custom3Text = custom3Text;
        copy.windowId = windowId;
        copy.filtersMigrated = true;
        copy.filters = new ArrayList<>();
        for (SecondChatFilterConfig filter : filters) {
            if (filter != null) {
                copy.filters.add(filter.copy());
            }
        }
        return copy;
    }

    public void sanitize(int index) {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        name = sanitizeText(name, "", 20);
        messageLimit = Math.max(25, Math.min(500, messageLimit));
        backgroundOpacity = Math.max(0, Math.min(230, backgroundOpacity));
        fontScalePercent = Math.max(75, Math.min(150, fontScalePercent));
        fadeSeconds = Math.max(0, Math.min(15, fadeSeconds));
        mentionTerms = sanitizeText(mentionTerms, "", 160);
        mentionColor = 0xFF000000 | (mentionColor & 0x00FFFFFF);
        hqMode = sanitizeMode(hqMode);
        reinfMode = sanitizeMode(reinfMode);
        privateMode = sanitizeMode(privateMode);
        serverInfoMode = sanitizeMode(serverInfoMode);
        betterUcMode = sanitizeMode(betterUcMode);
        ownNameMode = sanitizeMode(ownNameMode);
        custom1Mode = sanitizeMode(custom1Mode);
        custom2Mode = sanitizeMode(custom2Mode);
        custom3Mode = sanitizeMode(custom3Mode);
        custom1Text = sanitizeText(custom1Text, "", 96);
        custom2Text = sanitizeText(custom2Text, "", 96);
        custom3Text = sanitizeText(custom3Text, "", 96);
        if (windowId == null || windowId.isBlank()) {
            windowId = "primary";
        }
        ensureFilters();
        filters.removeIf(filter -> filter == null);
        while (filters.size() > 24) {
            filters.remove(filters.size() - 1);
        }
        for (int i = 0; i < filters.size(); i++) {
            filters.get(i).sanitize(i);
        }
    }

    private void ensureFilters() {
        if (filters == null) {
            filters = new ArrayList<>();
        }
        if (filtersMigrated) {
            return;
        }
        addPreset("WPS / HQ", "hq", hqMode);
        addPreset("Reinforcements", "reinf", reinfMode);
        addPreset("Privatnachrichten", "private", privateMode);
        addPreset("Server-Infos", "server", serverInfoMode);
        addPreset("betterUC", "betteruc", betterUcMode);
        addPreset("Eigener Name", "ownname", ownNameMode);
        addCustom("Eigener Filter 1", custom1Text, custom1Mode);
        addCustom("Eigener Filter 2", custom2Text, custom2Mode);
        addCustom("Eigener Filter 3", custom3Text, custom3Mode);
        filtersMigrated = true;
    }

    private void addPreset(String filterName, String matcher, String filterMode) {
        if (!"off".equals(sanitizeMode(filterMode))) {
            filters.add(new SecondChatFilterConfig(filterName, matcher, filterMode));
        }
    }

    private void addCustom(String filterName, String text, String filterMode) {
        if (text == null || text.isBlank() || "off".equals(sanitizeMode(filterMode))) {
            return;
        }
        SecondChatFilterConfig filter = new SecondChatFilterConfig(filterName, "custom", filterMode);
        filter.includeText = text;
        filters.add(filter);
    }

    private static String sanitizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "copy", "move", "highlight", "ignore" -> normalized;
            default -> "off";
        };
    }

    private static String sanitizeText(String value, String fallback, int maxLength) {
        String clean = value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
        if (clean.isBlank()) {
            clean = fallback;
        }
        return clean.substring(0, Math.min(clean.length(), maxLength));
    }
}
