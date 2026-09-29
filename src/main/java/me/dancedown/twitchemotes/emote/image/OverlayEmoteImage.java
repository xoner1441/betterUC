package me.dancedown.twitchemotes.emote.image;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class OverlayEmoteImage extends EmoteImage {

    private NativeImage image;
    private final EmoteImage baseImage;
    private final EmoteImage overlayImage;
    private int lastSignatureBase;
    private int lastSignatureOverlay;

    protected OverlayEmoteImage(EmoteImage baseImage, EmoteImage overlayImage, Identifier imageId, DynamicTexture texture, GlyphRenderTypes types, int width, int height, int scale) {
        super(imageId, texture, types, width, height, scale);
        this.baseImage = baseImage;
        this.overlayImage = overlayImage;
        tickUnchecked();
    }

    /**
     * Checks if any of the children changed and if yes, composites a new image
     */
    @Override
    public void tick() {
        // Check if any of the images changed, do nothing
        if (lastSignatureBase == baseImage.frameSignature() && lastSignatureOverlay == overlayImage.frameSignature())
            return;
        tickUnchecked();
    }

    /**
     * Sets the last signatures of base and overlay image and then
     * composites a new image from the frames
     */
    private void tickUnchecked() {
        lastSignatureBase = baseImage.frameSignature();
        lastSignatureOverlay = overlayImage.frameSignature();

        // compositing
        image = new NativeImage(width(), height(), true);
        blend(image, baseImage.image(), baseImage.width(), baseImage.height(), baseImage.scale());
        blend(image, overlayImage.image(), overlayImage.width(), overlayImage.height(), overlayImage.scale());
        texture.setPixels(image);
        texture.upload();
    }

    @Override
    public NativeImage image() {
        return image;
    }

    /**
     * @return Hashes the indices of the images. Collisions are possible, but rare.
     */
    @Override
    public int frameSignature() {
        return 31 * lastSignatureBase + lastSignatureOverlay;
    }

    /**
     * Blends 2 images together
     * @param base The image that is beneath and is overwritten
     * @param overlay The image that is on top
     * @param overlayWidth The width of the overlay image
     * @param overlayHeight The height of the overlay image
     * @param overlayScale The scale of the overlay image
     */
    private void blend(NativeImage base, NativeImage overlay, int overlayWidth, int overlayHeight, int overlayScale) {
        int baseWidth = base.getWidth();
        int baseHeight = base.getHeight();
        int overlayFrameWidth = overlay.getWidth();
        int overlayFrameHeight = overlay.getHeight();
        int[] basePixels = base.getPixelsABGR();
        int[] overlayPixels = overlay.getPixelsABGR();

        float overlayScaleRatio = (float) this.scale() / overlayScale;
        int scaledOverlayWidth = (int) (overlayFrameWidth * overlayScaleRatio);
        int scaledOverlayHeight = (int) (overlayFrameHeight * overlayScaleRatio);

        for(int y = 0; y < scaledOverlayHeight; y++) {
            for(int x = 0; x < scaledOverlayWidth; x++) {
                int srcX = (int) (x / overlayScaleRatio);
                int srcY = (int) (y / overlayScaleRatio);
                int overlayPixel = overlayPixels[srcX + srcY * overlayFrameWidth];
                int overlayAlpha = (overlayPixel >>> 24);

                if(overlayAlpha == 0)
                    continue;

                int positionedX = x + (baseWidth - (int) (overlayWidth * overlayScaleRatio)) / 2; // centering
                int positionedY = y + (baseHeight - (int) (overlayHeight * overlayScaleRatio)); // flooring
                int basePixel = basePixels[positionedX + positionedY * baseWidth];
                int baseAlpha = (basePixel >>> 24);
                // skip if transparent, draw directly if overlay is opaque or background is transparent
                if(overlayAlpha == 255 || baseAlpha == 0)
                    base.setPixelABGR(positionedX, positionedY, overlayPixel);
                else
                    base.setPixelABGR(positionedX, positionedY, blendPixel(basePixel, baseAlpha, overlayPixel, overlayAlpha));
            }
        }
    }

    /**
     * Calculates the resulting pixel from blending 2 semi transparent pixels
     * @param basePixel The pixel of the base image (ABGR)
     * @param baseAlpha The alpha of the base pixel (ABGR)
     * @param overlayPixel The pixel that is on top (ABGR)
     * @param overlayAlpha The alpha of the overlay pixel
     * @return The resulting pixel of the blend (ABGR)
     */
    private static int blendPixel(int basePixel, int baseAlpha, int overlayPixel, int overlayAlpha) {
        int overlayR = overlayPixel & 0xFF;
        int overlayG = (overlayPixel >> 8) & 0xFF;
        int overlayB = (overlayPixel >> 16) & 0xFF;
        int baseR = basePixel & 0xFF;
        int baseG = (basePixel >> 8) & 0xFF;
        int baseB = (basePixel >> 16) & 0xFF;
        int overlayAlphaInv = 255 - overlayAlpha;
        int outR = (overlayR * overlayAlpha + baseR * overlayAlphaInv) / 255;
        int outG = (overlayG * overlayAlpha + baseG * overlayAlphaInv) / 255;
        int outB = (overlayB * overlayAlpha + baseB * overlayAlphaInv) / 255;
        int outA = overlayAlpha + (baseAlpha * overlayAlphaInv) / 255;
        return (outA << 24) |
                (outB << 16) |
                (outG << 8) |
                outR;
    }
}

