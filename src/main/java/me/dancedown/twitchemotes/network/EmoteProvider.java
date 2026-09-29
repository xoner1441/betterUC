package me.dancedown.twitchemotes.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.exception.EmoteParseException;

import java.util.ArrayList;
import java.util.List;

public abstract class EmoteProvider {

    public abstract String name();
    /**
     * Collects all the global emotes from the provider
     * @param config The config containing information about what to include
     * @return A list of Emote objects
     */
    public abstract List<Emote> getGlobalEmotes(TwitchEmotesConfig config) throws EmoteParseException;

    /**
     * Collects all the channel emotes from the provider
     * @param config The config containing information about channel and what to include
     * @return A list of Emote objects
     */
    public abstract List<Emote> getChannelEmotes(TwitchEmotesConfig config) throws EmoteParseException;

    /**
     * Builds an Emote object from a JSON object containing the data necessary
     * @param jsonObject The JSON object containing the necessary emote data
     * @param template The template from which the URL is built
     * @param config The config containing information about what to include
     * @return An Emote object or <code>null</code> if the data was invalid
     */
    protected abstract Emote getEmoteFromJson(JsonObject jsonObject, String template, TwitchEmotesConfig config);

    /**
     * Calls the <code>getEmotesFromJson(...)</code> for every array element and collects the results in a list
     * @param array The JSON array containing the emote data
     * @param template The template from which the emote URL is built
     * @param config The config containing information about what to include
     * @return A list of Emotes
     */
    protected final List<Emote> getEmotesFromArray(JsonArray array, String template, TwitchEmotesConfig config) throws EmoteParseException {
        int exceptionCount = 0;
        List<Emote> emotes = new ArrayList<>();
        for(JsonElement element : array) {
            Emote emote = getEmoteFromJson(element.getAsJsonObject(), template, config);
            if(emote != null)
                emotes.add(emote);
            else
                exceptionCount++;
        }
        if(exceptionCount > 0 && !emotes.isEmpty())
            TwitchEmotes.LOGGER.warn("Some {} emotes couldn't be parsed: {}", name(), exceptionCount);
        else if(exceptionCount > 0)
            throw new EmoteParseException();
        return emotes;
    }

}

