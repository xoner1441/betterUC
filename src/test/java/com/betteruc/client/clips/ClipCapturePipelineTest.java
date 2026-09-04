package com.betteruc.client.clips;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipCapturePipelineTest {
    @Test void completedSceneIsCopiedWithoutAlphaBlendingOrDepthTest() {
        var pipeline = ClipCapturePipeline.COLOR_COPY;
        var target = pipeline.getColorTargetState();
        assertTrue(target.blendFunction().isEmpty(), "Framebuffer RGB must not be multiplied by scene alpha");
        assertEquals(ColorTargetState.WRITE_COLOR, target.writeMask(), "Keep opaque clear alpha, copy RGB only");
        assertEquals(GpuFormat.RGBA8_UNORM, target.format());
        assertFalse(pipeline.wantsDepthTexture());
        assertEquals("minecraft:core/screenquad", pipeline.getVertexShader().toString());
        assertEquals("minecraft:core/blit_screen", pipeline.getFragmentShader().toString());
    }
}
