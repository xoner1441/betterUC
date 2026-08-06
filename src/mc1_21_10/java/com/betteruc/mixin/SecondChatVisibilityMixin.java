package com.betteruc.mixin;

import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class SecondChatVisibilityMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void betteruc$hideVanillaChatForCustomMainTab(CallbackInfo ci) {
        if (BetterUCConfig.INSTANCE.secondChatEnabled && !SecondChatManager.isMainTabActive()) {
            ci.cancel();
        }
    }
}
