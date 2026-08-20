package com.betteruc.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthHudTest {

    @Test
    void preservesHalfHeartValuesInWidgetText() {
        assertEquals(45, HealthHud.displayedHalfHeartUnits(45.0F));
        assertEquals("22,5", HealthHud.formatHeartCount(45));
    }

    @Test
    void keepsWholeHeartValuesCompact() {
        assertEquals(44, HealthHud.displayedHalfHeartUnits(44.0F));
        assertEquals("22", HealthHud.formatHeartCount(44));
    }

    @Test
    void roundsFractionalDamageUpToTheVisibleHalfHeart() {
        assertEquals(45, HealthHud.displayedHalfHeartUnits(44.2F));
        assertEquals("22,5", HealthHud.formatHeartCount(45));
    }
}
