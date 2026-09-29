package me.dancedown.twitchemotes.emote.image;

import com.mojang.blaze3d.platform.NativeImage;
import me.dancedown.twitchemotes.TwitchEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class EmoteImageFactory {

    /**
     * Creates a static emote image from the native image in the minecraft thread.
     * @param image The native image to be converted
     * @param imageId The identifier for the image
     * @param textureName The name of the texture (for debugging)
     * @param width The width of the canvas
     * @param height The height of the canvas
     * @param scale The canvas scale
     * @return A new static emote image or <code>null</code> if it could not be created
     */
    public static StaticEmoteImage createStaticEmoteImage(NativeImage image, Identifier imageId, String textureName, int width, int height, int scale) {
        Supplier<StaticEmoteImage> emoteImageSupplier = () -> new StaticEmoteImage(image, imageId,
                new DynamicTexture(() -> textureName, image),
                GlyphRenderTypes.createForColorTexture(imageId),
                width, height, scale);
        if(!Minecraft.getInstance().isSameThread()) {
            try {
                return CompletableFuture.supplyAsync(emoteImageSupplier, Minecraft.getInstance()).get();
            } catch (InterruptedException | ExecutionException e) {
                TwitchEmotes.LOGGER.warn("Couldn't create static emote image for {}", textureName, e);
                return null;
            }
        }
        return emoteImageSupplier.get();
    }

    /**
     * Creates an animated emote image in the minecraft thread.
     * @param images The list of native image to be converted
     * @param durations The list of durations for the individual frames
     * @param imageId The identifier for the image
     * @param textureName The name of the texture (for debugging)
     * @param width The width of the canvas
     * @param height The height of the canvas
     * @param scale The scale of the canvas
     * @return A new animated emote image or <code>null</code> if it could not be created
     */
    public static AnimatedEmoteImage createAnimatedEmoteImage(List<NativeImage> images, List<Integer> durations, Identifier imageId, String textureName, int width, int height, int scale) {
        Supplier<AnimatedEmoteImage> emoteImageSupplier = () -> new AnimatedEmoteImage(images, durations, imageId,
                        new DynamicTexture(textureName, width, height, true),
                        GlyphRenderTypes.createForColorTexture(imageId),
                        width, height, scale);
        if(!Minecraft.getInstance().isSameThread())
            try {
                return CompletableFuture.supplyAsync(emoteImageSupplier, Minecraft.getInstance()).get();
            } catch (InterruptedException | ExecutionException e) {
                TwitchEmotes.LOGGER.warn("Couldn't create animated emote image for {}", textureName, e);
                return null;
            }
        return emoteImageSupplier.get();
    }

    /**
     * Creates an overlay emote image in the minecraft thread.
     * @param baseImage The EmoteImage that is the base of the overlay
     * @param overlayImage The EmoteImage that overlays the base image
     * @param imageId The identifier for the image
     * @param textureName The name of the texture (for debugging)
     * @return A new overlay image or <code>null</code> if it could not be created
     */
    public static OverlayEmoteImage createOverlayEmoteImage(EmoteImage baseImage, EmoteImage overlayImage, Identifier imageId, String textureName) {
        int scale = Math.max(baseImage.scale(), overlayImage.scale());
        int width = Math.max(
                (int) (baseImage.width() * ((float) scale / baseImage.scale())),
                (int) (overlayImage.width() * ((float) scale / overlayImage.scale()))
        );
        int height = Math.max(
                (int) (baseImage.height() * ((float) scale / baseImage.scale())),
                (int) (overlayImage.height() * ((float) scale / overlayImage.scale()))
        );
        Supplier<OverlayEmoteImage> supplier = () ->
                new OverlayEmoteImage(baseImage, overlayImage, imageId,
                        new DynamicTexture(textureName, width, height, true),
                        GlyphRenderTypes.createForColorTexture(imageId),
                        width, height, scale);
        if(!Minecraft.getInstance().isSameThread())
            try {
                return CompletableFuture.supplyAsync(supplier, Minecraft.getInstance()).get();
            } catch (InterruptedException | ExecutionException e) {
                TwitchEmotes.LOGGER.warn("Couldn't create overlay emote image for {}", textureName, e);
                return null;
            }
        return supplier.get();
    }
}

