package me.dancedown.twitchemotes.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.emote.type.EmoteFormat;
import me.dancedown.twitchemotes.exception.EmoteParseException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * The 7tv provider class for retrieving channel and global emotes and converting the response.
 */
public class STVEmoteProvider extends EmoteProvider {

    private static final String BASE_URL = "https://7tv.io/v3";

    @Override
    public String name() {
        return "7tv";
    }

    /**
     * Requests the global 7tv Emotes and converts the response into a list of Emotes
     * @param config The configuration needed for quality preference and "unlisted" preference
     * @return A list of Emotes or an empty list
     * @throws EmoteParseException If emotes couldn't be loaded/parsed
     */
    @Override
    public List<Emote> getGlobalEmotes(TwitchEmotesConfig config) throws EmoteParseException {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonObject response;
        try {
            response = networkHandler.getObjectResponse(BASE_URL + "/emote-sets/global");
        } catch (URISyntaxException | IOException e) {
            throw new EmoteParseException();
        }
        if (response.has("emotes"))
            return getEmotesFromArray(response.getAsJsonArray("emotes"), null, config);
        throw new EmoteParseException();
    }

    /**
     * Requests 7tv channel emotes of a channel
     * @param config The configuration needed for the channel id and preferences
     * @return A list of Emotes or an empty list
     * @throws EmoteParseException If emotes couldn't be loaded/parsed
     */
    @Override
    public List<Emote> getChannelEmotes(TwitchEmotesConfig config) throws EmoteParseException {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonObject response;
        try {
            response = networkHandler.getObjectResponse(BASE_URL + "/users/twitch/" + config.twitchChannelId);
        } catch (URISyntaxException | IOException e) {
            throw new EmoteParseException();
        }
        if (response.has("emote_set") && !response.get("emote_set").isJsonNull())
            return getEmotesFromArray(response.get("emote_set").getAsJsonObject().get("emotes").getAsJsonArray(),
                    null, config);
        throw new EmoteParseException();
    }

    /**
     * Converts a 7tv emote JSON object to an Emote object
     *
     * @param jsonObject The JSON object containing information about the emote
     * @param template   Unused
     * @param config     The config containing the preferred quality/scale and unlisted preference
     * @return The Emote object or <code>null</code> if required fields are missing
     */
    @Override
    protected Emote getEmoteFromJson(JsonObject jsonObject, String template, TwitchEmotesConfig config) {
        String name;
        String id;
        String url;
        boolean overlay;
        JsonObject emoteData;
        JsonObject host;
        JsonArray files;
        boolean listed;
        int finalScale;
        try {
            name = jsonObject.get("name").getAsString();
            id = jsonObject.get("id").getAsString();
            overlay = (jsonObject.get("flags").getAsLong() & 1) == 1;
            emoteData = jsonObject.get("data").getAsJsonObject();
            listed = emoteData.get("listed").getAsBoolean();
            host = emoteData.get("host").getAsJsonObject();
            url = "https:" + host.get("url").getAsString();
            files = host.get("files").getAsJsonArray();

            if(!listed && !config.includeUnlisted)
                return null;
            finalScale = getScale(config, files);
            url += "/" + finalScale + "x.gif";
        } catch (NullPointerException | IllegalStateException | UnsupportedOperationException | ClassCastException ex) {
            return null;
        }
        return new Emote(id, name, url, EmoteFormat.GIF, false, finalScale);
    }

    /**
     * Gets the final scale given the preference and available 7tv scale links
     * @param config The config containing the quality/scale preference
     * @param files The available links for the individual sizes
     * @return The final scale nearest to the preference, but below or equal
     */
    private static int getScale(TwitchEmotesConfig config, JsonArray files) {
        int finalScale = 1;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.get("format").getAsString().equals("GIF")) {
                try {
                    int scale = Integer.parseInt(file.get("name").getAsString().replace("x.gif", ""));
                    if (scale <= config.preferredQuality && scale > finalScale)
                        finalScale = scale;
                } catch(NumberFormatException ignored) {}
            }
        }
        return finalScale;
    }
}

