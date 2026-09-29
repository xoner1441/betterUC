package me.dancedown.twitchemotes.emote.render;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.emote.image.EmoteImage;
import me.dancedown.twitchemotes.exception.EmoteStyleNotRecognizedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;
import org.jspecify.annotations.NonNull;

public class BakedEmoteGlyph implements BakedGlyph {

    private final int maxHeight;
    private final float emoteHeight;
    private final float emoteWidth;
    private final EmoteImage emoteImage;
    private final EmoteGlyphInfo emoteGlyphInfo;

    public BakedEmoteGlyph(@NonNull String emoteName) throws EmoteStyleNotRecognizedException {
        // Retrieving the EmoteImage from the insertion of the style
        EmoteImage image = TwitchEmotes.EMOTE_IMAGE_CACHE.get(emoteName);
        if(image == null)
            throw new EmoteStyleNotRecognizedException();

        this.emoteImage = image;
        this.maxHeight = Minecraft.getInstance().font.lineHeight;
        // Calculating the ratio of the image to the quality size (canvas size)
        this.emoteHeight = Math.min(1.f, image.height() / (32.f * image.scale())) * this.maxHeight;
        this.emoteWidth = this.emoteHeight * image.width() / image.height();
        this.emoteGlyphInfo = new EmoteGlyphInfo((int) Math.ceil(emoteWidth));
    }

    /**
     * @return The GlyphInfo containing the advance
     */
    @Override
    public @NonNull GlyphInfo info() {
        return emoteGlyphInfo;
    }

    /**
     * Returns a new renderable instance of the emote
     * @param x The x position
     * @param y The y position
     * @param color The color (only alpha is taken into consideration)
     * @param shadowColor The shadow color (ignored)
     * @param style The style of the emote (ignored)
     * @param boldOffset The bold offset (ignored)
     * @param shadowOffset The shadow offset (ignored)
     * @return A new BakedEmoteGlyph.GlyphInstance
     */
    @Override
    public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, @NonNull Style style, float boldOffset, float shadowOffset) {
        return new GlyphInstance(x, y, color, style, this);
    }

    static class EmoteGlyphInfo implements GlyphInfo {

        private final int advance;
        public EmoteGlyphInfo(int advance) {
            this.advance = advance;
        }
        /**
         * Returns the advance of the widest EmoteImages object
         * @return The advance of the widest emote
         */
        @Override
        public float getAdvance() {
            return advance;
        }

        /**
         * Returns the advance of the widest EmoteImages object
         * @param bl If bold style is applied (ignored)
         * @return The advance of the widest emote
         */
        @Override
        public float getAdvance(boolean bl) {
            return advance;
        }

        /**
         * Emotes can't be bold
         * @return Always <code>0</code>
         */
        @Override
        public float getBoldOffset() {
            return 0;
        }

        /**
         * Emotes don't have a shadow
         * @return Always <code>0</code>
         */
        @Override
        public float getShadowOffset() {
            return 0;
        }
    }

    record GlyphInstance(float x, float y, int color, Style style, BakedEmoteGlyph glyph)
            implements TextRenderable.Styled {

        private static final float offsetY = 1;

        @Override
        public void render(@NonNull Matrix4fc matrix4f, @NonNull VertexConsumer vertexConsumer, int light, boolean bold) {

            float left = x;
            float right = left + glyph.emoteWidth;
            float bottom = y + glyph.maxHeight - offsetY;
            float top = y + glyph.maxHeight - glyph.emoteHeight - offsetY;

            vertexConsumer.addVertex(matrix4f, left, top, 0f).setColor(color).setUv(0, 0).setLight(light);
            vertexConsumer.addVertex(matrix4f, left, bottom, 0f).setColor(color).setUv(0, 1).setLight(light);
            vertexConsumer.addVertex(matrix4f, right, bottom, 0f).setColor(color).setUv(1, 1).setLight(light);
            vertexConsumer.addVertex(matrix4f, right, top, 0f).setColor(color).setUv(1, 0).setLight(light);

        }

        @Override
        public @NonNull RenderType renderType(Font.@NonNull DisplayMode displayMode) {
            return glyph.emoteImage.glyphRenderTypes().select(displayMode);
        }

        @Override
        public @NonNull GpuTextureView textureView() {
            return glyph.emoteImage.gpuTextureView();
        }

        @Override
        public @NonNull RenderPipeline guiPipeline() {
            return glyph.emoteImage.glyphRenderTypes().guiPipeline();
        }

        @Override
        public float left() {
            return x;
        }

        @Override
        public float top() {
            return y + glyph.maxHeight - glyph.emoteHeight - offsetY;
        }

        @Override
        public float right() {
            return x + glyph.emoteWidth;
        }

        @Override
        public float bottom() {
            return y + glyph.maxHeight - offsetY;
        }

        @Override
        public @NonNull Style style() {
            return style;
        }
    }

}

