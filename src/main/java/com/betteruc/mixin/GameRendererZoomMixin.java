package com.betteruc.mixin;

import com.betteruc.client.MovementController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class GameRendererZoomMixin {

    @Inject(
            method = "getFov()F",
            at = @At("RETURN"),
            cancellable = true,
            require = 1
    )
    private void applyZoomFloat(CallbackInfoReturnable<Float> cir) {
        float base = cir.getReturnValue();
        cir.setReturnValue((float) MovementController.applyZoomFov(base));
    }
}
