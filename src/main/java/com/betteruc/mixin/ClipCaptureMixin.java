package com.betteruc.mixin;

import com.betteruc.client.clips.ClipCaptureClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ClipCaptureMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void betteruc$captureClipFrame(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        ClipCaptureClient.onRenderedFrame(((GameRenderer) (Object) this).mainRenderTarget());
    }
}
