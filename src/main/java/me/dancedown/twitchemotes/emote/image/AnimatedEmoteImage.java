package me.dancedown.twitchemotes.emote.image;

import com.mojang.blaze3d.platform.NativeImage;
import me.dancedown.twitchemotes.TwitchEmotes;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Arrays;
import java.util.List;

public class AnimatedEmoteImage extends EmoteImage {
    private NativeImage image;
    private final NativeImage[] images;
    private final int[] durationIndices;
    private int total = 0;
    private int lastIndex;

    public AnimatedEmoteImage(List<NativeImage> images, List<Integer> durations,
                              Identifier imageId, DynamicTexture image, GlyphRenderTypes glyphRenderTypes,
                              int width, int height, int scale) {
        super(imageId, image, glyphRenderTypes, width, height, scale);
        this.images = images.toArray(NativeImage[]::new);
        this.durationIndices = new int[durations.size()];
        for(int i = 0; i < durations.size(); i++) {
            total += durations.get(i);
            durationIndices[i] = total;
        }
        // Loading an image (necessary if the user has animations turned off before loading the emotes)
        tick();
    }

    /**
     * Calculates the new image based on the current system time and updates the texture
     */
    @Override
    public void tick() {
        int time = (int) Util.getMillis() % total;
        int index = Arrays.binarySearch(durationIndices, time);
        if(index < 0)
            index = -index - 1;
        if(lastIndex != index) {
            lastIndex = index;
            image = new NativeImage(width(), height(), false);
            image.copyFrom(images[index]);
            texture.setPixels(image);
            texture.upload();
        }
    }

    @Override
    public int frameSignature() {
        return lastIndex;
    }

    @Override
    public NativeImage image() {
        return image;
    }

    @Override
    public void close() {
        super.close();
        for(NativeImage image : images) {
            image.close();
        }
    }
}

