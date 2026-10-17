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
    ),
    FORTNITE_ELITE_AGENT(
            "fortnite_elite_agent",
            "Fortnite Elite Agent",
            "/assets/betteruc/models/entity/fortnite_elite_agent.bumesh",
            "textures/entity/fortnite_elite_agent.png",
            180.0F
    ),
    VERITY(
            "verity",
            "Verity",
            "/assets/betteruc/models/entity/verity.bumesh",
            "textures/entity/verity.png",
            0.0F
    ),
    KASSADIN(
            "kassadin",
            "Kassadin",
            "/assets/betteruc/models/entity/kassadin.bumesh",
            "textures/entity/kassadin.png",
            180.0F
    ),
    PVZ_SUNFLOWER(
            "pvz_sunflower",
            "PvZ: Sonnenblume",
            "/assets/betteruc/models/entity/pvz_sunflower.bumesh",
            "textures/entity/pvz_sunflower.png",
            180.0F
    ),
    PVZ_FOOT_SOLDIER(
            "pvz_foot_soldier",
            "PvZ: Foot Soldier",
            "/assets/betteruc/models/entity/pvz_foot_soldier.bumesh",
            "textures/entity/pvz_foot_soldier.png",
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
