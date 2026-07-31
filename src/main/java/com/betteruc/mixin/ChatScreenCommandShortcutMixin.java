package com.betteruc.mixin;

import com.betteruc.client.CommandShortcutClient;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ChatScreen.class, priority = 2000)
public abstract class ChatScreenCommandShortcutMixin {

    @ModifyVariable(method = "handleChatInput", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String betteruc$rewriteAttemptedMurderShortcut(String message) {
        return CommandShortcutClient.rewriteOutgoingCommand(message);
    }
}
