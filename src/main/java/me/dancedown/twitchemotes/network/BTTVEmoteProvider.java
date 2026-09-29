package me.dancedown.twitchemotes.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.emote.type.EmoteFormat;
import me.dancedown.twitchemotes.exception.EmoteParseException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

/**
 * The BTTV provider class for retrieving channel and global emotes and converting the response.
 */
public class BTTVEmoteProvider extends EmoteProvider {

    private static final String BASE_URL = "https://api.betterttv.net/3/cached";
    private static final String IMAGE_URL = "https://cdn.betterttv.net/emote/%s/%sx.%s";

    @Override
    public String name() {
        return "BetterTTV";
    }

    /**
     * Requests the global BTTV Emotes and converts them into a list of Emotes
     * @param config The configuration needed for quality preference
     * @return A list of Emotes or an empty list
     */
    @Override
    public List<Emote> getGlobalEmotes(TwitchEmotesConfig config) throws EmoteParseException {
        NetworkHandler networkHandler = new NetworkHandler();
        try {
            return getEmotesFromArray(networkHandler.getArrayResponse(BASE_URL + "/emotes/global"),
                    IMAGE_URL, config);
        } catch(URISyntaxException | IOException ignored) {
            throw new EmoteParseException();
        }
    }

    /**
     * Requests BTTV channel and shared emotes of a channel
     * @param config The configuration needed for the channel id and name
     * @return A list of Emotes or an empty list
     */
    @Override
    public List<Emote> getChannelEmotes(TwitchEmotesConfig config) throws EmoteParseException {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonArray result = new JsonArray();
        JsonObject response;
        try {
            response = networkHandler.getObjectResponse(BASE_URL + "/users/twitch/" + config.twitchChannelId);
        } catch (URISyntaxException | IOException e) {
            throw new EmoteParseException();
        }
        if (response.has("channelEmotes")) {
            result.addAll(response.get("channelEmotes").getAsJsonArray());
        }
        if (response.has("sharedEmotes")) {
            result.addAll(response.get("sharedEmotes").getAsJsonArray());
        }
        return getEmotesFromArray(result, IMAGE_URL, config);
    }

    /**
     * Converts a BTTV emote JSON object to an Emote object
     *
     * @param jsonObject The JSON object containing information about the emote
     * @param template   The template from which the URL is built
     * @param config     The config containing the preferred quality/scale
     * @return The Emote object or <code>null</code> if required fields are missing
     */
    @Override
    protected Emote getEmoteFromJson(JsonObject jsonObject, String template, TwitchEmotesConfig config) {
        String id;
        String name;
        String imageFormat;
        EmoteFormat format;
        try {
            id = jsonObject.get("id").getAsString();
            name = jsonObject.get("code").getAsString();
            boolean animated = jsonObject.has("animated")
                    ? jsonObject.get("animated").getAsBoolean()
                    : jsonObject.has("imageType")
                    && !"png".equalsIgnoreCase(jsonObject.get("imageType").getAsString());
            imageFormat = animated ? "gif" : "png";
            format = animated ? EmoteFormat.GIF : EmoteFormat.PNG;
        } catch (NullPointerException | IllegalStateException | UnsupportedOperationException ex) {
            return null;
        }
        HashMap<Integer, Integer> qualityMap = new HashMap<>();
        qualityMap.put(1, 1);
        qualityMap.put(2, 2);
        qualityMap.put(3, 2);
        qualityMap.put(4, 3);
        int quality = qualityMap.getOrDefault(config.preferredQuality, 1);
        String url = String.format(template, id, quality, imageFormat);
        return new Emote(id, name, url, format, false, quality);
    }
}

