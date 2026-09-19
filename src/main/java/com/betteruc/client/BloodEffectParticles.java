package com.betteruc.client;

import com.betteruc.BetterUCMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Custom client-visible particles; the server never needs to know these ids. */
public final class BloodEffectParticles {
    public static final SimpleParticleType BURST = FabricParticleTypes.simple();
    public static final SimpleParticleType DROP = FabricParticleTypes.simple();
    public static final SimpleParticleType STREAK = FabricParticleTypes.simple();
    public static final SimpleParticleType NEON_BUTTERFLY = FabricParticleTypes.simple();
    public static final SimpleParticleType STAR = FabricParticleTypes.simple();
    public static final SimpleParticleType HEART = FabricParticleTypes.simple();

    private BloodEffectParticles() {}

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("blood_burst"), BURST);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("blood_drop"), DROP);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("blood_streak"), STREAK);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("neon_butterfly"), NEON_BUTTERFLY);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("hit_star"), STAR);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("hit_heart"), HEART);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(BetterUCMod.MOD_ID, path);
    }
}
