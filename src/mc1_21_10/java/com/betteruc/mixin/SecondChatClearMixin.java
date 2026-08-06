package com.betteruc.mixin;

import com.betteruc.client.SecondChatManager;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class SecondChatClearMixin {

    @Inject(method = "clear", at = @At("HEAD"))
    private void betteruc$handleSecondChatClear(boolean clearHistory, CallbackInfo ci) {
        SecondChatManager.handleMainChatCleared();
    }
}
