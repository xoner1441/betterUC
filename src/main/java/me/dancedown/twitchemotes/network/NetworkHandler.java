package me.dancedown.twitchemotes.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.dancedown.twitchemotes.TwitchEmotes;
import me.dancedown.twitchemotes.config.TwitchEmotesConfig;
import me.dancedown.twitchemotes.exception.InvalidTwitchChannelException;
import me.dancedown.twitchemotes.exception.TwitchNotLoggedInException;
import me.dancedown.twitchemotes.exception.UnknownTwitchEmotesException;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * The handler for network requests.
 */
public class NetworkHandler {

    /**
     * Sends a GET request with 5s timeout to the specified endpoint
     * @param endpoint The endpoint to connect to
     * @return The response as JSON element
     * @throws URISyntaxException If endpoint isn't formatted correctly
     * @throws IOException If connection couldn't be established
     */
    private JsonElement getJsonResponse(String endpoint) throws URISyntaxException, IOException {
        URL url = new URI(endpoint).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "betterUC/1.5.1 chat-emotes");
        InputStream inputStream = connection.getInputStream();
        String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return JsonParser.parseString(result);
    }

    /**
     * Sends a GET request with 5s timeout to the specified endpoint
     * @param endpoint The endpoint to connect to
     * @return The response as JSON Array or an empty JSON array if the response was invalid
     * @throws URISyntaxException If endpoint isn't formatted correctly
     * @throws IOException If connection couldn't be established
     */
    public JsonArray getArrayResponse(String endpoint) throws URISyntaxException, IOException {
        JsonElement response = getJsonResponse(endpoint);
        if (response == null || response.isJsonNull() || !response.isJsonArray()) {
            return new JsonArray();
        }
        return response.getAsJsonArray();
    }

    /**
     * Sends a GET request with 5s timeout to the specified endpoint
     * @param endpoint The endpoint to connect to
     * @return The response as JSON Object or an empty JSON object if the response was invalid
     * @throws URISyntaxException If endpoint isn't formatted correctly
     * @throws IOException If connection couldn't be established
     */
    public JsonObject getObjectResponse(String endpoint) throws URISyntaxException, IOException {
        JsonElement response = getJsonResponse(endpoint);
        if (response == null || response.isJsonNull() || !response.isJsonObject()) {
            return new JsonObject();
        }
        return response.getAsJsonObject();
    }

    /**
     * Sends a GET request with twitch authorization header
     * @param endpoint The endpoint to connect to
     * @param config The configuration containing the OAuth Token and the Client ID
     * @return The response as a JSON Element
     */
    public JsonElement getJsonAuthResponse(String endpoint, TwitchEmotesConfig config) {
        String auth = config.twitchOAuthToken;
        String clientId = config.twitchClientId;
        if (auth != null && !auth.isBlank())
            try {
                URL url = new URI(endpoint).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "betterUC/1.5.1 chat-emotes");
                connection.addRequestProperty("Authorization", "Bearer " + auth);
                if(clientId != null)
                    connection.addRequestProperty("Client-Id", clientId);
                connection.setUseCaches(false);
                InputStream inputStream = connection.getInputStream();
                String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                return JsonParser.parseString(result);
            } catch (IOException | URISyntaxException ignored) {}
        return null;
    }

    /**
     * Attempts to send a request to twitch for a conversion from a Twitch username to the Twitch id.
     * If not authorized or response invalid, attempts a request to Scalar API for conversion.
     * @param value The Twitch name
     * @return The id corresponding to that username
     * @throws InvalidTwitchChannelException If channel doesn't exist
     * @throws UnknownTwitchEmotesException If another error occurs
     * @throws TwitchNotLoggedInException If the user is not authorized
     */
    public String getTwitchIdFromName(String value) throws InvalidTwitchChannelException, UnknownTwitchEmotesException, TwitchNotLoggedInException {
        if(value == null || value.isBlank())
            throw new InvalidTwitchChannelException();
        JsonElement element = getJsonAuthResponse("https://api.twitch.tv/helix/users?login=" + value, TwitchEmotes.CONFIG);
        JsonArray array;
        if(element == null || element.isJsonNull() || !element.isJsonObject()) {
            // Fallback to third-party-service api
            try {
                array = getArrayResponse("https://api.ivr.fi/v2/twitch/user?login=" + value);
            } catch(URISyntaxException | IOException ignored) {
                throw new TwitchNotLoggedInException();
            }
            if(array.isEmpty())
                throw new InvalidTwitchChannelException();
        } else {
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("data")) {
                throw new UnknownTwitchEmotesException();
            } else if (!(element = obj.get("data")).isJsonArray())
                throw new UnknownTwitchEmotesException();
            array = element.getAsJsonArray();
            if(array.isEmpty())
                throw new UnknownTwitchEmotesException();
        }

        element = array.get(0);
        if(element == null || element.isJsonNull() || !element.isJsonObject())
            throw new UnknownTwitchEmotesException();

        JsonObject obj = element.getAsJsonObject();
        if(!obj.has("id") || (element = obj.get("id")).isJsonNull())
            throw new UnknownTwitchEmotesException();
        return element.getAsString();
    }

    /**
     * Sends a validate request with the auth key to twitch and compares the client id received with the given client id
     * @param oauthToken The OAuth Token to check
     * @param clientId The client id to check
     * @return <code>true</code> if oauth token and client id are valid, else <code>false</code>
     */
    public boolean validateTwitchOauthToken(String oauthToken, String clientId) {
        TwitchEmotesConfig config = new TwitchEmotesConfig();
        config.twitchOAuthToken = oauthToken;
        JsonElement response = getJsonAuthResponse("https://id.twitch.tv/oauth2/validate", config);
        JsonObject obj;
        return response != null && !response.isJsonNull() && response.isJsonObject() &&
                !(obj = response.getAsJsonObject()).has("status") && obj.has("client_id") &&
                (response = obj.get("client_id")).isJsonPrimitive() && response.getAsString().equals(clientId);
    }

    /**
     * Gets the username and user_id from a given oauth token and client id
     * and compares if with given username and user_id.
     * @param config Config containing username, user id, client id and oauth token
     * @return The display name or <code>null</code> if anything is invalid
     */
    public String validateTwitchUser(TwitchEmotesConfig config) {
        JsonElement response = getJsonAuthResponse("https://api.twitch.tv/helix/users", config);
        if (response == null || !response.isJsonObject())
            return null;
        JsonObject root = response.getAsJsonObject();
        if (!root.has("data") || !root.get("data").isJsonArray())
            return null;
        JsonArray arr = root.getAsJsonArray("data");
        if(arr.isEmpty())
            return null;
        if (!arr.get(0).isJsonObject())
            return null;
        JsonObject obj = arr.get(0).getAsJsonObject();
        String userId = getString(obj, "id");
        String username = getString(obj, "login");
        String displayName = getString(obj, "display_name");
        if (userId == null || username == null || displayName == null)
            return null;
        if (!username.equals(config.twitchUserName) || !userId.equals(config.twitchUserId))
            return null;
        return displayName;
    }

    /**
     * Gets a string value from a JSON Object key
     * @param obj The JSON object
     * @param key A key in the JSON object
     * @return The String value or <code>null</code> if the key doesn't exist or the value is not of type String
     */
    @Nullable
    private static String getString(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement e = obj.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /**
     * Downloads the bytes from the given url.
     * @param url The URL from where the data is downloaded
     */
    public static byte[] download(@NotNull String url) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URI(url).toURL().openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "TwitchEmotes");
            InputStream in = con.getInputStream();
            return in.readAllBytes();
        } catch (URISyntaxException | IOException | IllegalArgumentException ex) {
            return null;
        }


    }
}

