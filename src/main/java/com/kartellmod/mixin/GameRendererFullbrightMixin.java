package com.kartellmod.mixin;

import com.kartellmod.config.KartellConfig;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererFullbrightMixin {

    @Inject(
            method = "getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void forceNightVisionStrength(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (!KartellConfig.INSTANCE.fullbrightEnabled) return;
        cir.setReturnValue(1.0F);
    }
}
