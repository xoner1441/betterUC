package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZoomControllerTest {

    @Test
    void keepsOriginalFovWhileZoomIsInactive() {
        assertEquals(70.0F, ZoomController.zoomedFov(70.0F, 4.0D, 0.0D), 0.001F);
    }

    @Test
    void appliesConfiguredFactorAtFullZoom() {
        assertEquals(17.5F, ZoomController.zoomedFov(70.0F, 4.0D, 1.0D), 0.001F);
    }

    @Test
    void smoothStepProducesStableHalfwayTransition() {
        assertEquals(0.5D, ZoomController.smoothStep(0.5D), 0.0001D);
        assertEquals(28.0F, ZoomController.zoomedFov(70.0F, 4.0D, 0.5D), 0.001F);
    }

    @Test
    void clampsUnsafeZoomFactors() {
        assertEquals(4.0D, ZoomController.clampZoomFactor(Double.NaN), 0.0001D);
        assertEquals(ZoomController.MIN_ZOOM_FACTOR, ZoomController.clampZoomFactor(0.5D), 0.0001D);
        assertEquals(ZoomController.MAX_ZOOM_FACTOR, ZoomController.clampZoomFactor(100.0D), 0.0001D);
    }
}
