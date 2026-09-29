package com.betteruc.mixin;

import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.text.EmoteReplacingText;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ComponentRenderUtils.class)
public class ChatEmoteWrapMixin {

    @ModifyVariable(method = "wrapComponents", at = @At("HEAD"), argsOnly = true)
    private static FormattedText betteruc$replaceEmoteNames(FormattedText text) {
        return TwitchEmotes.CONFIG.enabled ? new EmoteReplacingText(text) : text;
    }
}
