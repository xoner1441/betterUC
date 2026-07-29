package com.betteruc.mixin;

import com.betteruc.client.TabBadgeRenderState;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatBadgeBoundaryMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
            at = @At("HEAD")
    )
    private void betteruc$clearTabBadgeContextBeforeChat(CallbackInfo ci) {
        TabBadgeRenderState.clearPlayerListRender();
    }
}
