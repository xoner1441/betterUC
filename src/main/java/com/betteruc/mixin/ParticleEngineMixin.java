package com.betteruc.mixin;

import com.betteruc.client.BloodEffectClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void betteruc$replaceBloodParticle(ParticleOptions options, double x, double y, double z,
                                               double dx, double dy, double dz,
                                               CallbackInfoReturnable<Particle> cir) {
        if (BloodEffectClient.replace(options, x, y, z)) {
            cir.setReturnValue(null);
        }
    }
}
