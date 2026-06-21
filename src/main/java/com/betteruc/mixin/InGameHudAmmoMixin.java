package com.betteruc.mixin;

import com.betteruc.hud.AmmoHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudAmmoMixin {

    @Inject(
            method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            require = 0
    )
    private void captureAmmoOverlay(Component message, boolean tinted, CallbackInfo ci) {
        AmmoHud.updateFromOverlay(message);
    }
}
