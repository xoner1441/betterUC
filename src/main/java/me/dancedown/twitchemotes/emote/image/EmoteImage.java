package me.dancedown.twitchemotes.emote.image;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public abstract class EmoteImage implements AutoCloseable {

    private final Identifier imageId;
    protected final DynamicTexture texture;
    private final GlyphRenderTypes glyphRenderTypes;
    private final GpuTextureView gpuTextureView;
    private final int width;
    private final int height;
    private final int scale;

    protected EmoteImage(Identifier imageId, DynamicTexture texture, GlyphRenderTypes types, int width, int height, int scale) {
        this.imageId = imageId;
        this.texture = texture;
        this.glyphRenderTypes = types;
        this.gpuTextureView = texture.getTextureView();
        this.width = width;
        this.height = height;
        this.scale = scale;

        Minecraft.getInstance().getTextureManager().register(this.imageId, this.texture);
    }

    /**
     * Called every client tick
     */
    public abstract void tick();

    /**
     * @return The current image
     */
    public abstract NativeImage image();

    /**
     * @return A signature of the current frame e.g. the index of an image in a sequence
     */
    public abstract int frameSignature();

    /**
     * @return The GPU texture view of the uploaded texture
     */
    public GpuTextureView gpuTextureView() {
        return gpuTextureView;
    }

    /**
     * @return The glyph render types
     */
    public GlyphRenderTypes glyphRenderTypes() {
        return glyphRenderTypes;
    }

    /**
     * @return The width of the image
     */
    public int width() {
        return width;
    }

    /**
     * @return The height of the image
     */
    public int height() {
        return height;
    }

    /**
     * @return The identifier of the registered image
     */
    public Identifier imageId() {
        return imageId;
    }

    /**
     * Releases the dynamic texture from the memory
     */
    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(this.imageId);
    }

    /**
     * @return The scale of the image (e.g. 1x, 2x, 3x or 4x)
     */
    public int scale() {
        return scale;
    }
}

