package com.betteruc.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmorHudTest {

    @Test
    void calculatesRemainingDurabilityAsPercentage() {
        assertEquals(100, ArmorHud.durabilityPercent(400, 0));
        assertEquals(75, ArmorHud.durabilityPercent(400, 100));
        assertEquals(0, ArmorHud.durabilityPercent(400, 400));
    }

    @Test
    void clampsInvalidDurabilityValues() {
        assertEquals(100, ArmorHud.durabilityPercent(0, 50));
        assertEquals(100, ArmorHud.durabilityPercent(100, -10));
        assertEquals(0, ArmorHud.durabilityPercent(100, 140));
    }

    @Test
    void assignsTrafficLightColorsToDurability() {
        assertEquals(0xFF55FF55, ArmorHud.durabilityColor(51));
        assertEquals(0xFFFFAA00, ArmorHud.durabilityColor(50));
        assertEquals(0xFFFF5555, ArmorHud.durabilityColor(20));
    }

    @Test
    void sizesTheHudOnlyForOccupiedArmorSlots() {
        assertEquals(26, ArmorHud.widthForSlotCount(false, 1));
        assertEquals(38, ArmorHud.widthForSlotCount(true, 1));
        assertEquals(116, ArmorHud.widthForSlotCount(true, 4));
    }
}
