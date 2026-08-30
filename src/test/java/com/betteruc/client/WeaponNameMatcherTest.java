package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponNameMatcherTest {

    @Test
    void acceptsExactlyTheConfiguredWeaponNames() {
        assertEquals("P69", WeaponNameMatcher.canonicalName("P69"));
        assertEquals("Scatter3", WeaponNameMatcher.canonicalName("scatter3"));
        assertEquals("KR47", WeaponNameMatcher.canonicalName("KR47"));
        assertEquals("TS19", WeaponNameMatcher.canonicalName("TS19"));
        assertEquals("AX12", WeaponNameMatcher.canonicalName("AX12"));
        assertEquals("Extenso18", WeaponNameMatcher.canonicalName("Extenso18"));
        assertEquals("Viper9", WeaponNameMatcher.canonicalName("Viper9"));
    }

    @Test
    void handlesFormattingAndSmallCapsButRejectsPartialNames() {
        assertEquals("P69", WeaponNameMatcher.canonicalName("§6ᴘ69"));
        assertEquals("", WeaponNameMatcher.canonicalName("P69 Quest"));
        assertEquals("", WeaponNameMatcher.canonicalName("AUG"));
        assertEquals("", WeaponNameMatcher.canonicalName("Goldaxt"));
    }
}
