package me.dancedown.twitchemotes.text;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.emote.image.EmoteImage;
import me.dancedown.twitchemotes.emote.type.Emote;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Collections;

public class EmoteReplacingStyledStringBuilder {

    private static final char DELIMITER = ' ';
    private static final String HOVER_DELIMITER = "\n";
    private static final char EMOTE_SYMBOL = 0xE000;

    private final StyleBuffer<StringBuilder> finalStyleBuffer = new StyleBuffer<>();
    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder emoteNames = new StringBuilder();
    private final StyleBuffer<StringBuilder> styleBuffer = new StyleBuffer<>();
    private MutableComponent hoverText = Component.empty();
    private EmoteImage baseImage;
    private Style spaceStyleBefore;
    private Style spaceStyleAfter;

    /**
     * Appends a string with a style to the buffer and replaces emotes after every space if occurring.
     * @param style The style of the string
     * @param string The text
     * @return Itself
     */
    public EmoteReplacingStyledStringBuilder append(Style style, String string) {
        for(char c : string.toCharArray()) {
            if(c == DELIMITER) {
                handleSpace(style);
            } else {
                // append the character to the buffers
                buffer.append(c);
                add(styleBuffer, style, c);
            }
        }
        return this;
    }

    /**
     * <p>Checks if this word was an emote name and replaces it.</p>
     * If it's an emote name and the corresponding image is loaded:
     * <ul>
     *     <li>If it's not an overlay or the overlay image isn't composited yet: accepts the previously saved emote info</li>
     *     <li>Appends the information of the current info to the existing emote info</li>
     * </ul>
     * Else accepts the previously saved emote and appends the current text
     * @param styleOfDelimiter The style of the current delimiter (space)
     */
    private void handleSpace(Style styleOfDelimiter) {
        String emoteName = buffer.toString();
        boolean isEmote = TwitchEmotes.EMOTE_REGISTRY.contains(emoteName);
        Emote emote;
        EmoteImage emoteImage = null;
        // check if the word is an emote and the image is loaded
        if(isEmote && (emoteImage = getEmoteImageOrDownload(emote = TwitchEmotes.EMOTE_REGISTRY.get(emoteName))) != null) {
            // if emote is an overlay and image is ready, adds it to the list of emotes; else accepts previous emote first
            if(!this.emoteNames.isEmpty()
                    && emote.overlay()
                    && TwitchEmotes.CONFIG.overlayEmotes
                    && (emoteImage = getOverlayOrComposite(emoteName, this.baseImage, emoteImage)) != null) {
                this.emoteNames.append(DELIMITER);
                hoverText.append(HOVER_DELIMITER);
            } else
                appendEmote();
            spaceStyleAfter = styleOfDelimiter;
            this.emoteNames.append(emoteName);
            styleBuffer.forEachStylePoll((bufferStyle, bufferString) ->
                    hoverText.append(Component.literal(bufferString.toString()).withStyle(bufferStyle))
            );
        } else {
            // accept previous emotes and accept text
            appendEmote();
            if(spaceStyleBefore != null)
                add(finalStyleBuffer, spaceStyleBefore, DELIMITER);
            styleBuffer.forEachStylePoll(this::addStringToFinal);
            spaceStyleBefore = styleOfDelimiter;
        }
        this.baseImage = emoteImage;
        buffer.setLength(0);
    }

    /**
     * Checks if the cache has a final image and returns it. If image has not been composited yet, requests a composition.
     * @param emoteName The name of the overlay emote
     * @param baseImage The emote image of the base emote
     * @param overlayImage The emote image of the overlay emote
     * @return The final composition of the two overlays
     */
    private EmoteImage getOverlayOrComposite(String emoteName, EmoteImage baseImage, EmoteImage overlayImage) {
        String overlayName = this.emoteNames.toString() + DELIMITER + emoteName;
        if(!TwitchEmotes.EMOTE_IMAGE_CACHE.contains(overlayName)) {
            TwitchEmotes.EMOTE_IMAGE_CACHE.add(
                    overlayName,
                    TwitchEmotes.EMOTE_IMAGE_CACHE.overlayEmoteImage(overlayName, baseImage, overlayImage),
                    ImmutableSet.of(emoteName, this.emoteNames.toString())
            );
            return null;
        }
        return TwitchEmotes.EMOTE_IMAGE_CACHE.get(overlayName);
    }

    /**
     * Checks if the cache has a final image and returns it. If image has not been requested yet, requests a download.
     * @param emote The emote to download or get the image for
     * @return The final image if contained, otherwise <code>null</code>
     */
    private EmoteImage getEmoteImageOrDownload(Emote emote) {
        if(TwitchEmotes.EMOTE_IMAGE_CACHE.contains(emote.name())) {
            return TwitchEmotes.EMOTE_IMAGE_CACHE.get(emote.name());
        } else
            TwitchEmotes.EMOTE_IMAGE_CACHE.add(
                    emote.name(),
                    TwitchEmotes.EMOTE_IMAGE_CACHE.downloadEmoteImage(emote),
                    Collections.emptySet()
            );
        return null;
    }

    /**
     * If there is an emote to accept: Adds a space (with style <code>spaceStyleBefore</code>),
     * adds the emote symbol with the <code>emoteStyle</code> and the hover event from <code>hoverText</code>,
     * overrides <code>spaceStyleBefore</code> with <code>spaceStyleAfter</code>
     * and clears everything. Does nothing otherwise.
     */
    private void appendEmote() {
        if(this.emoteNames.isEmpty())
            return;
        Style style = Style.EMPTY
                .withHoverEvent(new HoverEvent.ShowText(hoverText))
                .withInsertion(emoteNames.toString());
        if(spaceStyleBefore != null)
            add(finalStyleBuffer, spaceStyleBefore, DELIMITER);
        finalStyleBuffer.add(style, new StringBuilder().append(EMOTE_SYMBOL));
        spaceStyleBefore = spaceStyleAfter;
        hoverText = Component.empty();
        emoteNames.setLength(0);
    }

    /**
     * Appends the last word/emote and builds the final StyleBuffer.
     * @return The final StyleBuffer containing the emote-replaced text
     */
    public StyleBuffer<StringBuilder> build() {
        handleSpace(null);
        appendEmote();
        return new StyleBuffer<>(finalStyleBuffer);
    }

    /**
     * Adds a character to the end of the given style buffer.
     * If it matches the style of the last element, appends the character to the StringBuilder,
     * otherwise creates a new entry in the StyleBuffer
     * @param buffer The buffer to which the style and character is appended
     * @param style The style of this character
     * @param c The character to append
     */
    private void add(StyleBuffer<StringBuilder> buffer, Style style, char c) {
        StringBuilder sb = getLastStringBuilderIfMatching(buffer, style);
        if(sb == null)
            buffer.add(style, new StringBuilder().append(c));
        else
            sb.append(c);
    }

    /**
     * Adds a StringBuilder to the end of the given final style buffer.
     * If it matches the style of the last element, appends the StringBuilder to the StringBuilder,
     * otherwise adds it as a new entry
     * @param style The style of this string
     * @param text  The sequence to append
     */
    private void addStringToFinal(Style style, StringBuilder text) {
        StringBuilder sb = getLastStringBuilderIfMatching(finalStyleBuffer, style);
        if(sb == null)
            finalStyleBuffer.add(style, text);
        else
            sb.append(text);
    }

    /**
     * @param buffer The queue to get the last StringBuilder from
     * @param style The style that has to match the last entry
     * @return The StringBuilder of the end of the queue if style matches the given style or <code>null</code> otherwise
     */
    private StringBuilder getLastStringBuilderIfMatching(StyleBuffer<StringBuilder> buffer, Style style) {
        Pair<Style, StringBuilder> pair;
        if(buffer.isEmpty() || !(pair = buffer.getLast()).getFirst().equals(style))
            return null;
        else
            return pair.getSecond();
    }
}

