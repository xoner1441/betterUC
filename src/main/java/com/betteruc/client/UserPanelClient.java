package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public final class UserPanelClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private UserPanelClient() {
    }

    public static void registerPassword(MinecraftClient client, String password) {
        if (client == null || client.player == null) return;
        String token = accessToken();
        if (token.isBlank()) {
            sendLocalMessage(client, "\u00A7cBitte zuerst deinen Access Code im ClickGUI eintragen.");
            return;
        }
        if (password == null || password.length() < 6 || password.length() > 72 || password.trim().length() < 6) {
            sendLocalMessage(client, "\u00A7cPasswort muss 6 bis 72 Zeichen lang sein.");
            return;
        }

        URI uri = apiUri("/api/user/register");
        if (uri == null) {
            sendLocalMessage(client, "\u00A7cWebserver-Adresse ist ungültig.");
            return;
        }

        JsonObject body = baseIdentityPayload(client);
        body.addProperty("password", password);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json")
                .header("x-betteruc-token", token)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        sendLocalMessage(client, "\u00A77Web-Login wird eingerichtet...");
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> client.execute(() -> {
                    if (client.player == null) return;
                    if (error != null) {
                        BetterUCMod.LOGGER.debug("betterUC web registration failed", error);
                        sendLocalMessage(client, "\u00A7cWeb-Login konnte nicht eingerichtet werden.");
                        return;
                    }

                    JsonObject json = parseJson(response.body());
                    if (response.statusCode() >= 200 && response.statusCode() < 300 && boolValue(json, "ok")) {
                        sendLocalMessage(client, "\u00A7aWeb-Login eingerichtet. Du kannst dich jetzt im Userpanel anmelden.");
                    } else {
                        sendLocalMessage(client, "\u00A7c" + stringValue(json, "error", "Web-Login fehlgeschlagen."));
                    }
                }));
    }

    static void uploadStats(MinecraftClient client, JsonObject stats) {
        if (client == null || client.player == null || stats == null || stats.size() == 0) return;
        String token = accessToken();
        if (token.isBlank()) return;

        URI uri = apiUri("/api/user/stats");
        if (uri == null) return;

        JsonObject body = baseIdentityPayload(client);
        body.add("stats", stats.deepCopy());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("content-type", "application/json")
                .header("x-betteruc-token", token)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        BetterUCMod.LOGGER.debug("betterUC stats upload failed", error);
                    } else if (response.statusCode() >= 400) {
                        BetterUCMod.LOGGER.debug("betterUC stats upload rejected with status {}", response.statusCode());
                    }
                });
    }

    private static JsonObject baseIdentityPayload(MinecraftClient client) {
        JsonObject body = new JsonObject();
        body.addProperty("minecraftName", playerName(client));
        body.addProperty("minecraftUuid", playerUuid(client));
        body.addProperty("server", PingRelayClient.currentServerId(client));
        body.addProperty("version", modVersion());
        String faction = BetterUCConfig.INSTANCE.currentPlayerFactionLabel == null
                || BetterUCConfig.INSTANCE.currentPlayerFactionLabel.isBlank()
                ? BetterUCConfig.factionLabelForQuery(BetterUCConfig.INSTANCE.currentPlayerFaction)
                : BetterUCConfig.INSTANCE.currentPlayerFactionLabel;
        if (faction != null && !faction.isBlank() && !"Unbekannt".equals(faction)) {
            body.addProperty("faction", faction);
        }
        return body;
    }

    private static URI apiUri(String path) {
        try {
            String raw = BetterUCConfig.INSTANCE.pingRelayUrl == null
                    || BetterUCConfig.INSTANCE.pingRelayUrl.isBlank()
                    ? BetterUCConfig.DEFAULT_PING_RELAY_URL
                    : BetterUCConfig.INSTANCE.pingRelayUrl.trim();
            if (!raw.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) {
                boolean directIp = raw.matches("^[0-9.]+(?::[0-9]+)?(?:/.*)?$") || raw.contains(":3000");
                raw = (directIp ? "http://" : "https://") + raw;
            }
            if (raw.startsWith("ws://")) raw = "http://" + raw.substring("ws://".length());
            if (raw.startsWith("wss://")) raw = "https://" + raw.substring("wss://".length());

            URI uri = URI.create(raw);
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isBlank()) return null;
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            return URI.create(scheme + "://" + authority + path);
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Invalid betterUC web API URL", e);
            return null;
        }
    }

    private static String accessToken() {
        return BetterUCConfig.INSTANCE.pingRelayToken == null ? "" : BetterUCConfig.INSTANCE.pingRelayToken.trim();
    }

    private static String playerName(MinecraftClient client) {
        if (client == null || client.player == null) return "unknown";
        String name = client.player.getName().getString();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private static String playerUuid(MinecraftClient client) {
        if (client == null || client.player == null || client.player.getUuid() == null) return "";
        return client.player.getUuid().toString();
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(BetterUCMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static JsonObject parseJson(String raw) {
        try {
            return JsonParser.parseString(raw == null ? "{}" : raw).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static boolean boolValue(JsonObject json, String key) {
        try {
            return json != null && json.has(key) && json.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        try {
            return json != null && json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void sendLocalMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("\u00A7b[betterUC]\u00A7r " + message), false);
        }
    }
}
