package com.betteruc.mixin;

import com.betteruc.client.ZoomController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MouseHandler.class)
public class ZoomMouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void betteruc$useScrollForZoom(
            long windowHandle,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfo ci
    ) {
        if (ZoomController.handleMouseScroll(windowHandle, verticalAmount)) {
            ci.cancel();
        }
    }

    @ModifyArgs(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void betteruc$scaleZoomSensitivity(Args args) {
        double multiplier = ZoomController.mouseSensitivityMultiplier();
        args.set(0, (double) args.get(0) * multiplier);
        args.set(1, (double) args.get(1) * multiplier);
    }
}
