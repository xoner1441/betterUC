package com.betteruc.client.clips;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import java.util.Optional;

final class ClipCapturePipeline {
    private ClipCapturePipeline() {}

    // This is the finished scene, not a transparent overlay. Its alpha may contain
    // renderer/shader metadata even though its RGB is already correct for display.
    // Never multiply RGB by that alpha again: it blackens sky, fog and particles.
    // Keep the destination alpha at the opaque clear value for video conversion.
    static final RenderPipeline COLOR_COPY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("betteruc", "pipeline/clip_color_copy"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
            .withDepthStencilState(Optional.empty())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
}
