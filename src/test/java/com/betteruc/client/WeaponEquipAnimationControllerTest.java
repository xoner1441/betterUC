package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponEquipAnimationControllerTest {

    @Test
    void keepsVanillaSpeedForDisabledFeatureOrOtherItems() {
        assertEquals(0.4F, WeaponEquipAnimationController.equipStep(false, "instant", true));
        assertEquals(0.4F, WeaponEquipAnimationController.equipStep(true, "instant", false));
    }

    @Test
    void appliesConfiguredSpeedOnlyToSupportedWeapons() {
        assertEquals(0.8F, WeaponEquipAnimationController.equipStep(true, "fast", true));
        assertEquals(1.0F, WeaponEquipAnimationController.equipStep(true, "instant", true));
    }

    @Test
    void removesOnlyTheVisualToolSwapDelayForSupportedWeapons() {
        assertEquals(0.25F, WeaponEquipAnimationController.itemSwapScale(false, true, 0.25F));
        assertEquals(0.25F, WeaponEquipAnimationController.itemSwapScale(true, false, 0.25F));
        assertEquals(1.0F, WeaponEquipAnimationController.itemSwapScale(true, true, 0.25F));
    }

    @Test
    void fallsBackToFastForUnknownModes() {
        assertEquals("fast", WeaponEquipAnimationController.normalizeMode(null));
        assertEquals("fast", WeaponEquipAnimationController.normalizeMode("unknown"));
        assertEquals("instant", WeaponEquipAnimationController.normalizeMode(" INSTANT "));
    }
}
