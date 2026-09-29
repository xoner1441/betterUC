package com.betteruc.mixin;

import java.util.ArrayList;
import java.util.Collection;
import me.dancedown.twitchemotes.TwitchEmotes;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientSuggestionProvider.class)
public class ChatEmoteSuggestionsMixin {

    @Inject(method = "getCustomTabSuggestions()Ljava/util/Collection;", at = @At("RETURN"), cancellable = true)
    private void betteruc$addEmoteSuggestions(CallbackInfoReturnable<Collection<String>> cir) {
        if (!TwitchEmotes.CONFIG.enabled) return;
        Collection<String> suggestions = new ArrayList<>(cir.getReturnValue());
        suggestions.addAll(TwitchEmotes.EMOTE_REGISTRY.getKeys());
        cir.setReturnValue(suggestions);
    }
}
