package me.dancedown.twitchemotes.network;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.emote.type.Emote;
import me.dancedown.twitchemotes.emote.type.EmoteFormat;
import me.dancedown.twitchemotes.exception.EmoteParseException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.*;

/**
 * The Twitch provider class for retrieving channel and global emotes and converting the response.
 */
public class TwitchEmoteProvider extends EmoteProvider {

    private static final String BASE_URL = "https://api.twitch.tv/helix/chat/emotes";
    // Scalar API: https://api.ivr.fi/v2/docs
    private static final String SEC_URL = "https://api.ivr.fi/v2/twitch/emotes/";
    // hard coded template when no requests to twitch are possible
    // should be the same as in the file
    // optionally: read file for template
    private static final String FALLBACK_TEMPLATE = "https://static-cdn.jtvnw.net/emoticons/v2/{{id}}/{{format}}/{{theme_mode}}/{{scale}}";
    // global twitch emotes if request to twitch (with oauth) and request to Scalar (without oauth) both failed
    private static final String FALLBACK_GLOBAL_FILE = "assets/twitchemotes/emotes/twitch_global.json";
    private static final HashMap<Float, Integer> SCALE_MAPPING = new HashMap<>();

    static {
        SCALE_MAPPING.put(1.f, 1);
        SCALE_MAPPING.put(2.f, 2);
        SCALE_MAPPING.put(3.f, 4);
    }

    /**
     * Converts an emote JSON object from the Scalar API format to the Twitch API Format.
     * Assumes every emote has scales 1.0, 2.0 and 3.0
     * Scalar fields (id, code, assetType) are mapped to (id, name, format, scale)
     * @param element the JSON object that contains the fields
     * @return A new JSON Object containing the fields id, name, format and scale
     */
    private JsonObject convertIvrFields(JsonElement element) {
        JsonObject obj;
        if(element == null || !element.isJsonObject())
            return null;
        obj = element.getAsJsonObject();
        JsonObject convertedObj = new JsonObject();
        if(obj.has("id"))
            convertedObj.add("id", obj.get("id").getAsJsonPrimitive());
        if(obj.has("code"))
            convertedObj.add("name", obj.get("code"));
        if(obj.has("assetType")) {
            JsonArray formatArr = new JsonArray();
            formatArr.add("static");
            if (obj.get("assetType").getAsString().equalsIgnoreCase("ANIMATED"))
                formatArr.add("animated");
            convertedObj.add("format", formatArr);
        }
        JsonArray scaleArr = new JsonArray(3);
        scaleArr.add("1.0");
        scaleArr.add("2.0");
        scaleArr.add("3.0");
        convertedObj.add("scale", scaleArr);
        return convertedObj;
    }

    /**
     * Converts the JSON object received from the Scalar API when requesting the twitch global emotes
     * to an JSON element following the Twitch API response formatting
     * @param obj the JSON object received from the Scalar API when requesting global emotes
     * @return A new JSON Element containing the converted data
     * @throws EmoteParseException When data couldn't be parsed
     */
    private JsonElement convertIvrFormatGlobal(@NotNull JsonObject obj) throws EmoteParseException {
        JsonObject converted = new JsonObject();
        JsonArray convertedEmotes = new JsonArray();
        converted.addProperty("template", FALLBACK_TEMPLATE);
        JsonElement tmpEl;
        if((tmpEl = obj.get("emoteList")) == null || !tmpEl.isJsonArray())
            throw new EmoteParseException();
        for(JsonElement element : tmpEl.getAsJsonArray()) {
            JsonObject convertedFields = convertIvrFields(element);
            if(convertedFields != null)
                convertedEmotes.add(convertedFields);
        }
        converted.add("data", convertedEmotes);
        return converted;
    }

    /**
     * Converts the JSON object received from the Scalar API when requesting the twitch channel emotes
     * to an JSON element following the Twitch API response formatting
     * @param obj the JSON object received from the Scalar API when requesting channel emotes
     * @return A new JSON Element containing the converted data
     * @throws EmoteParseException When data couldn't be parsed
     */
    private JsonElement convertIvrFormatChannel(@NotNull JsonObject obj) throws EmoteParseException {
        JsonObject converted = new JsonObject();
        JsonArray convertedEmotes = new JsonArray();
        converted.addProperty("template", FALLBACK_TEMPLATE);

        obj.asMap().forEach((key, value) -> {
            if(value == null || !value.isJsonArray())
                return;
            for(JsonElement emoteSet : value.getAsJsonArray()) {
                JsonObject tmpObj;
                if (emoteSet != null && !emoteSet.isJsonNull() && emoteSet.isJsonObject() &&
                        (tmpObj = emoteSet.getAsJsonObject()).has("emotes"))
                    for (JsonElement emote : tmpObj.getAsJsonArray("emotes")) {
                        JsonObject convertedFields = convertIvrFields(emote);
                        if(convertedFields != null)
                            convertedEmotes.add(convertedFields);
                    }
            }
        });

        converted.add("data", convertedEmotes);
        return converted;
    }

    /**
     * Reads and parses the fallback JSON file for global twitch emotes contained in the jar
     * @return The root JsonElement built from the fallback file or <code>null</code> if the file couldn't be read/parsed
     * @throws EmoteParseException When data couldn't be parsed
     */
    private JsonElement readJsonResourceFile() throws EmoteParseException {
        InputStream stream = TwitchEmoteProvider.class.getClassLoader().getResourceAsStream(TwitchEmoteProvider.FALLBACK_GLOBAL_FILE);
        if(stream != null)
            try {
                JsonReader jsonReader = new JsonReader(new InputStreamReader(stream));
                return JsonParser.parseReader(jsonReader);
            } catch (JsonParseException ignored) {}
        throw new EmoteParseException();
    }

    @Override
    public String name() {
        return "Twitch";
    }

    /**
     * Attempts to request the global emotes from Twitch (requires the user to be logged in)
     * and converts them into a list of emotes.
     * Falls back on the Scalar API (no authentification required) if previous request failed.
     * Falls back on the local file contained within the jar file if previous request failed.
     * @param config The configuration needed for authentification
     * @return A list of Emotes or an empty list
     * @throws EmoteParseException When data couldn't be received or parsed
     */
    @Override
    public List<Emote> getGlobalEmotes(@NotNull TwitchEmotesConfig config) throws EmoteParseException {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonElement response = networkHandler.getJsonAuthResponse(BASE_URL + "/global", config);
        JsonObject obj;
        if (response == null || response.isJsonNull() || !response.isJsonObject() ||
                !(obj = response.getAsJsonObject()).has("data") || !obj.has("template")) {
            // Fallback to third party service (Scalar API)
            try {
                JsonArray arr = networkHandler.getArrayResponse(SEC_URL + "sets?set_id=0");
                if(arr == null || arr.isJsonNull() || arr.isEmpty() || !arr.get(0).isJsonObject())
                    throw new IOException();
                response = convertIvrFormatGlobal(arr.get(0).getAsJsonObject());
            } catch (URISyntaxException | IOException e) {
                // Fallback to reading the local file for twitch emotes (might be outdated)
                response = readJsonResourceFile();
            }
            if(response == null || response.isJsonNull() || !response.isJsonObject())
                throw new EmoteParseException();
            obj = response.getAsJsonObject();
        }

        try {
            String template = obj.get("template").getAsString();
            JsonArray arr = obj.get("data").getAsJsonArray();
            return getEmotesFromArray(arr, template, config);
        } catch (NullPointerException | IllegalStateException | UnsupportedOperationException ignored) {
            throw new EmoteParseException();
        }
    }

    /**
     * Attempts to request the channel emotes (sub, bit and local) from Twitch (requires the user to be logged in)
     * and converts them into a list of emotes.
     * Falls back on the Scalar API (no authentification required) if previous request failed.
     * @param config The configuration needed for authentification and for the channel id and name
     * @return A list of Emotes
     * @throws EmoteParseException When data couldn't be received or parsed
     */
    @Override
    public List<Emote> getChannelEmotes(@NotNull TwitchEmotesConfig config) throws EmoteParseException {
        NetworkHandler networkHandler = new NetworkHandler();
        JsonElement response = networkHandler.getJsonAuthResponse(BASE_URL + "?broadcaster_id=" + config.twitchChannelId, config);
        if (response == null || response.isJsonNull() || !response.isJsonObject()) {
            // Fallback to Scalar API
            JsonObject obj;
            try {
                obj = networkHandler.getObjectResponse(SEC_URL + "channel/" + config.twitchChannelName);
                if(obj != null && !obj.isJsonNull() && !obj.isEmpty() && !obj.has("error"))
                    response = convertIvrFormatChannel(obj.getAsJsonObject());
            } catch (URISyntaxException | IOException ignored) {}
        }
        if(response == null || response.isJsonNull() || !response.isJsonObject())
            throw new EmoteParseException();
        try {
            String template = response.getAsJsonObject().get("template").getAsString();
            JsonArray arr = response.getAsJsonObject().get("data").getAsJsonArray();
            return getEmotesFromArray(arr, template, config);
        } catch (NullPointerException | IllegalStateException | UnsupportedOperationException ignored) {
            throw new EmoteParseException();
        }
    }

    /**
     * Converts a Twitch emote JSON object to an Emote object
     * @param jsonObject The JSON object containing information about the emote
     * @param template   The template from which the URL is built
     * @param config     The config containing the preferred quality/scale
     * @return The Emote object or <code>null</code> if required fields are missing
     */
    @Override
    protected Emote getEmoteFromJson(JsonObject jsonObject, String template, TwitchEmotesConfig config) {
        String name;
        String id;
        boolean isAnimated;
        JsonArray scales;
        try {
            name = jsonObject.get("name").getAsString();
            id = jsonObject.get("id").getAsString();
            isAnimated = jsonObject.get("format").getAsJsonArray().contains(new JsonPrimitive("animated"));
            scales = jsonObject.get("scale").getAsJsonArray();
        } catch (NullPointerException | UnsupportedOperationException | IllegalStateException ex) {
            return null;
        }

        float finalScale = 1;
        for (JsonElement element : scales) {
            float scale;
            try {
                scale = Float.parseFloat(element.getAsString());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if(!SCALE_MAPPING.containsKey(scale))
                continue;

            int mappedScale = SCALE_MAPPING.get(scale);
            if(mappedScale <= config.preferredQuality && scale > finalScale)
                finalScale = scale;
        }

        String url = template.replace("{{id}}", id)
            .replace("{{format}}", isAnimated ? "animated" : "static")
            .replace("{{theme_mode}}", "dark")
            .replace("{{scale}}", String.valueOf(finalScale));
        return new Emote(id,
                name,
                url,
                isAnimated ? EmoteFormat.GIF : EmoteFormat.PNG,
                false,
                // this is stupid...
                id.length() > 3 && !id.startsWith("5555555") ? SCALE_MAPPING.get(finalScale) : (int) finalScale);
    }
}

