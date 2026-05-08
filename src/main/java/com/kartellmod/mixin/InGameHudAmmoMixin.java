package com.kartellmod.mixin;

import com.kartellmod.hud.AmmoHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudAmmoMixin {

    @Inject(
            method = "setOverlayMessage(Lnet/minecraft/text/Text;Z)V",
            at = @At("HEAD"),
            require = 0
    )
    private void captureAmmoOverlay(Text message, boolean tinted, CallbackInfo ci) {
        AmmoHud.updateFromOverlay(message);
    }
}
