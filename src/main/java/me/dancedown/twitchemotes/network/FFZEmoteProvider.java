package me.dancedown.twitchemotes.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.emote.type.EmoteFormat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The FrankerFaceZ provider class for retrieving channel and global emotes and converting the response.
 */
public class FFZEmoteProvider extends EmoteProvider {

    private static final String BASE_URL = "https://api.frankerfacez.com/v1";

    @Override
    public String name() {
        return "FrankerFaceZ";
    }

    /**
     * Requests the global FFZ Emotes and converts them into a list of Emotes
     * @param config The configuration needed for quality and unlisted preference
     * @return A list of Emotes or an empty list
     */
    @Override
    public List<Emote> getGlobalEmotes(TwitchEmotesConfig config) {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonArray emotesData = new JsonArray();
        JsonObject data;
        try {
            data = networkHandler.getObjectResponse(BASE_URL + "/set/global");
        } catch (URISyntaxException | IOException e) {
            return Collections.emptyList();
        }
        if(data == null || data.isJsonNull() || !data.has("default_sets") || !data.has("sets"))
            return Collections.emptyList();
        JsonObject obj = data.get("sets").getAsJsonObject();
        if(obj != null && !obj.isJsonNull())
            for (JsonElement element : data.get("default_sets").getAsJsonArray()) {
                String id = String.valueOf(element.getAsLong());
                JsonObject setInfo;
                if(!obj.has(id) || !obj.get(id).isJsonObject() ||
                        !(setInfo = obj.getAsJsonObject(id)).has("emoticons") ||
                        !setInfo.get("emoticons").isJsonArray())
                    continue;
                emotesData.addAll(setInfo.getAsJsonArray("emoticons"));
            }
        return getEmotesFromArray(emotesData, null, config);
    }

    /**
     * Requests FFZ room emotes of a channel
     * @param config The configuration needed for the channel id
     * @return A list of Emotes or an empty list
     */
    @Override
    public List<Emote> getChannelEmotes(TwitchEmotesConfig config) {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonObject data;
        try {
            data = networkHandler.getObjectResponse(BASE_URL + "/room/id/" + config.twitchChannelId);
        } catch (URISyntaxException | IOException e) {
            return Collections.emptyList();
        }
        JsonObject obj;
        if(data == null || data.isJsonNull() || !data.has("room") ||
                !data.get("room").isJsonObject() ||
                !(obj = data.getAsJsonObject("room")).has("set") ||
                !data.has("sets"))
            return Collections.emptyList();
        String setId = String.valueOf(obj.get("set").getAsLong());
        JsonObject setsObj = data.get("sets").getAsJsonObject();
        if(!setsObj.has(setId))
            return Collections.emptyList();
        JsonObject setObj = setsObj.get(setId).getAsJsonObject();
        if(setObj == null || setObj.isJsonNull() || !setObj.has("emoticons"))
            return Collections.emptyList();
        return getEmotesFromArray(setObj.getAsJsonArray("emoticons"), null, config);
    }

    /**
     * Converts a FFZ emote JSON object to an Emote object
     *
     * @param jsonObject The JSON object containing information about the emote
     * @param template   Unused
     * @param config     The config containing the quality/scale and unlisted preference
     * @return The Emote object or <code>null</code> if required fields are missing
     */
    @Override
    protected Emote getEmoteFromJson(JsonObject jsonObject, String template, TwitchEmotesConfig config) {
        String id;
        String name;
        boolean hidden;
        int finalScale = 0;
        String url = "";
        try {
            id =  String.valueOf(jsonObject.get("id").getAsLong());
            name =   String.valueOf(jsonObject.get("name").getAsString());
            hidden = jsonObject.get("hidden").getAsBoolean();
            if(hidden && !config.includeUnlisted)
                return null;
            JsonObject images = jsonObject.get("urls").getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : images.entrySet()) {
                String key = entry.getKey();
                int scale = Integer.parseInt(key);
                if(scale <= config.preferredQuality && scale > finalScale) {
                    finalScale = scale;
                    url = entry.getValue().getAsString();
                }
            }
        } catch (NullPointerException | IllegalStateException | UnsupportedOperationException ex) {
            return null;
        }
        if (url.isBlank()) return null;
        if (url.startsWith("//")) url = "https:" + url;
        return new Emote(id, name, url, EmoteFormat.PNG, false, Math.max(1, finalScale));
    }
}

