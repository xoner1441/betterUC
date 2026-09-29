package com.betteruc.mixin;

import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.emote.render.BakedEmoteGlyph;
import me.dancedown.twitchemotes.exception.EmoteStyleNotRecognizedException;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class ChatEmoteFontMixin {

    @Inject(method = "getGlyph", at = @At("HEAD"), cancellable = true)
    private void betteruc$getEmoteGlyph(int codePoint, Style style, CallbackInfoReturnable<BakedGlyph> cir) {
        try {
            if (codePoint != 0xE000 || style.getInsertion() == null) return;
            String emoteName = style.getInsertion();
            BakedEmoteGlyph glyph = TwitchEmotes.EMOTE_IMAGE_CACHE.getGlyph(emoteName);
            if (glyph == null) {
                glyph = new BakedEmoteGlyph(emoteName);
                TwitchEmotes.EMOTE_IMAGE_CACHE.addGlyph(emoteName, glyph);
            }
            cir.setReturnValue(glyph);
        } catch (EmoteStyleNotRecognizedException ignored) {
        }
    }
}
