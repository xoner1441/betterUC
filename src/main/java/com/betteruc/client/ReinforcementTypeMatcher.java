package com.betteruc.client;

import java.text.Normalizer;
import java.util.Locale;

final class ReinforcementTypeMatcher {

    enum Type {
        NORMAL("Normal"),
        URGENT("Dringend"),
        MEDIC("Medic"),
        HOSTAGE("Geiselnahme"),
        CONTRACT("Contract"),
        TRAINING("Training"),
        DRUGS("Drogenabnahme"),
        BODY_GUARD("Leichenbewachung"),
        BOMB("Bombe");

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

        if (normalized.contains("dringend")) return Type.URGENT;
        if (normalized.contains("medic")) return Type.MEDIC;
        if (normalized.contains("geiselnahme")) return Type.HOSTAGE;
        if (normalized.contains("contract")) return Type.CONTRACT;
        if (normalized.contains("training")) return Type.TRAINING;
        if (normalized.contains("drogenabnahme")) return Type.DRUGS;
        if (normalized.contains("leichenbewachung")) return Type.BODY_GUARD;
        if (normalized.contains("bombe")) return Type.BOMB;
        return Type.NORMAL;
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
