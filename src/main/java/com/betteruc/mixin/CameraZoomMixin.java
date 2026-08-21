package com.betteruc.mixin;

import com.betteruc.client.ZoomController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraZoomMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void betteruc$applyZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ZoomController.applyFov(cir.getReturnValueF()));
    }
}
