package com.kartellmod.mixin;

import com.kartellmod.KartellModClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererZoomMixin {

    @Inject(
            method = "getFov(Lnet/minecraft/client/render/Camera;FZ)F",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void applyZoomFloat(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        float base = cir.getReturnValue();
        cir.setReturnValue((float) KartellModClient.applyZoomFov(base));
    }
}
