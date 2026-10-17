package com.betteruc.client.model;

import java.util.Locale;

public enum CustomSelfModelOption {
    TUNG_TUNG_SAHUR(
            "tung_tung_sahur",
            "Tung Tung Tung Sahur",
            "/assets/betteruc/models/entity/tung_tung_sahur.bumesh",
            "textures/entity/tung_tung_sahur.png",
            180.0F
    ),
    SIXTY_SEVEN(
            "sixty_seven",
            "67 (Steal a Brainrot)",
            "/assets/betteruc/models/entity/sixty_seven.bumesh",
            "textures/entity/white.png",
            180.0F
    );

    private final String id;
    private final String label;
    private final String meshResource;
    private final String texturePath;
    private final float yawOffset;

    CustomSelfModelOption(String id, String label, String meshResource, String texturePath, float yawOffset) {
        this.id = id;
        this.label = label;
        this.meshResource = meshResource;
        this.texturePath = texturePath;
        this.yawOffset = yawOffset;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String meshResource() {
        return meshResource;
    }

    public String texturePath() {
        return texturePath;
    }

    public float yawOffset() {
        return yawOffset;
    }

    public static CustomSelfModelOption fromId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (CustomSelfModelOption option : values()) {
            if (option.id.equals(normalized)) return option;
        }
        return TUNG_TUNG_SAHUR;
    }
}
