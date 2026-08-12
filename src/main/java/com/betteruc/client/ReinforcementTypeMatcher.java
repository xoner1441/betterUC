package com.betteruc.client;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReinforcementTypeMatcher {

    private static final Pattern REQUESTING_PLAYER_PATTERN = Pattern.compile(
            "(?<![a-z0-9_])([a-z0-9_]{2,16})\\s+benotigt\\s+unterstutzung\\b"
    );

    enum Type {
        NORMAL("Normal"),
        URGENT("Dringend"),
        MEDIC("Medic"),
        HOSTAGE("Geiselnahme"),
        CONTRACT("Contract"),
        TRAINING("Training"),
        DRUGS("Drogenabnahme"),
        BODY_GUARD("Leichenbewachung"),
        BOMB("Bombe"),
        PLANTAGE("Plantage");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private ReinforcementTypeMatcher() {
    }

    static Type classify(String raw) {
        String normalized = normalize(raw);
        if (!normalized.contains("benotigt unterstutzung")) return null;
        if (!normalized.contains("meter entfernt") && !normalized.contains("in der nahe")) return null;

        int headlineEnd = normalized.indexOf('!');
        String headline = headlineEnd >= 0 ? normalized.substring(0, headlineEnd) : normalized;
        if (headline.contains("dringend")) return Type.URGENT;
        if (headline.contains("medic")) return Type.MEDIC;
        if (headline.contains("geiselnahme")) return Type.HOSTAGE;
        if (headline.contains("contract")) return Type.CONTRACT;
        if (headline.contains("training")) return Type.TRAINING;
        if (headline.contains("drogenabnahme")) return Type.DRUGS;
        if (headline.contains("leichenbewachung")) return Type.BODY_GUARD;
        if (headline.contains("bombe")) return Type.BOMB;
        if (headline.contains("plantage")) return Type.PLANTAGE;
        return Type.NORMAL;
    }

    static boolean isRequestedBy(String raw, String playerName) {
        String normalizedPlayerName = normalize(playerName);
        if (normalizedPlayerName.isBlank()) return false;

        Matcher matcher = REQUESTING_PLAYER_PATTERN.matcher(normalize(raw));
        return matcher.find() && matcher.group(1).equals(normalizedPlayerName);
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
