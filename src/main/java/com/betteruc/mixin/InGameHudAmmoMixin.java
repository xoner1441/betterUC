package com.betteruc.mixin;

import com.betteruc.hud.AmmoHud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class InGameHudAmmoMixin {

    @Inject(
            method = "setActionBarText(Lnet/minecraft/network/protocol/game/ClientboundSetActionBarTextPacket;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void captureAmmoOverlay(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (packet == null) return;
        try {
            AmmoHud.updateFromOverlay(packet.text());
        } catch (RuntimeException | LinkageError ignored) {
            AmmoHud.clear();
        }
    }
}
