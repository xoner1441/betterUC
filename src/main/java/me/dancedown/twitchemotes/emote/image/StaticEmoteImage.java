package me.dancedown.twitchemotes.emote.image;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class StaticEmoteImage extends EmoteImage {

    private final NativeImage image;

    public StaticEmoteImage(NativeImage image, Identifier imageId,
                            DynamicTexture texture, GlyphRenderTypes glyphRenderTypes,
                            int width, int height, int scale) {
        super(imageId, texture, glyphRenderTypes, width, height, scale);
        this.image = image;
    }

    @Override
    public void tick() {}

    @Override
    public NativeImage image() {
        return image;
    }

    @Override
    public int frameSignature() {
        return 0;
    }

}

