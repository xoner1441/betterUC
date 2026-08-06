package com.betteruc.mixin;

import com.betteruc.client.SecondChatManager;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class SecondChatVisibilityMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void betteruc$hideVanillaChatForCustomMainTab(CallbackInfo ci) {
        if (BetterUCConfig.INSTANCE.secondChatEnabled && !SecondChatManager.isMainTabActive()) {
            ci.cancel();
        }
    }
}
