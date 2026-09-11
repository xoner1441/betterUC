package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BloodEffectBurstLimiterTest {
    @Test void collapsesNearbyFragmentsOnlyWithinTheSameServerTick() {
        var limiter = new BloodEffectBurstLimiter();
        assertTrue(limiter.accept(100, 10, 65, 20));
        assertFalse(limiter.accept(100, 10.2, 65.1, 20.2));
        assertTrue(limiter.accept(100, 13, 65, 20));
        assertTrue(limiter.accept(101, 10.2, 65.1, 20.2));
    }

    @Test void acceptsFirstFragmentAgainAfterWorldReset() {
        var limiter = new BloodEffectBurstLimiter();
        assertTrue(limiter.accept(100, 10, 65, 20));
        assertFalse(limiter.accept(100, 10.1, 65, 20));

        limiter.reset();

        assertTrue(limiter.accept(100, 10.1, 65, 20));
    }
}
