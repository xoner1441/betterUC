package com.betteruc.mixin;

import com.betteruc.client.SecondChatManager;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class SecondChatClearMixin {

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void betteruc$handleSecondChatClear(boolean clearHistory, CallbackInfo ci) {
        SecondChatManager.handleMainChatCleared();
    }
}
