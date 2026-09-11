package com.betteruc.mixin;

import com.betteruc.client.BloodEffectClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientDamageEventMixin {
    @Inject(
            method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V",
            at = @At("TAIL")
    )
    private void betteruc$showPlayerDamageEffect(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        BloodEffectClient.onPlayerDamage(packet);
    }
}
