package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RemoteFeatureFlagsClient {
    public static final String PING_SYSTEM = "ping_system";
    public static final String CHAT_CUSTOMIZATION = "chat_customization";
    public static final String REINF_CUSTOMIZATION = "reinf_customization";
    public static final String CLOUD_SETTINGS = "cloud_settings";
    public static final String AUTO_DROPDRINK = "auto_dropdrink";
    public static final String AUTO_FISHER = "auto_fisher";
    public static final String AUTO_WINZER = "auto_winzer";
    public static final String AUTO_GAERTNER = "auto_gaertner";

    private static final long REFRESH_INTERVAL_MS = 60_000L;
    private static final Set<String> KNOWN_FLAGS = Set.of(
            PING_SYSTEM,
            CHAT_CUSTOMIZATION,
            REINF_CUSTOMIZATION,
            CLOUD_SETTINGS,
            AUTO_DROPDRINK,
            AUTO_FISHER,
            AUTO_WINZER,
            AUTO_GAERTNER
    );
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private static volatile Map<String, Boolean> flags = enabledDefaults();
    private static volatile boolean joined;
    private static volatile boolean inFlight;
    private static volatile long nextRefreshAtMs;
    private static volatile String status = "Nicht verbunden";

    private RemoteFeatureFlagsClient() {
    }

    public static void onJoin(Minecraft client) {
        joined = true;
        inFlight = false;
        flags = enabledDefaults();
        nextRefreshAtMs = 0L;
        status = "Wird geladen";
        tick(client);
    }

    public static void onDisconnect() {
        joined = false;
        inFlight = false;
        flags = enabledDefaults();
        nextRefreshAtMs = 0L;
        status = "Nicht verbunden";
    }

    public static void tick(Minecraft client) {
        if (!joined || inFlight || client == null || client.player == null) return;
        if (System.currentTimeMillis() < nextRefreshAtMs) return;
        requestFlags(client);
    }

    public static boolean isEnabled(String key) {
        return flags.getOrDefault(key, true);
    }

    public static String statusLabel() {
        return status;
    }

    public static void sendDisabledMessage(Minecraft client, String featureName) {
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(Component.literal(
                "\u00A7c[betterUC] " + featureName + " ist vor\u00FCbergehend serverseitig deaktiviert."
        ));
    }

    private static void requestFlags(Minecraft client) {
        URI uri = apiUri("/api/client/features");
        if (uri == null) {
            status = "Serveradresse ung\u00FCltig";
            nextRefreshAtMs = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
            return;
        }

        inFlight = true;
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("x-betteruc-version", modVersion())
                .GET()
                .build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> client.execute(() -> {
                    inFlight = false;
                    nextRefreshAtMs = System.currentTimeMillis() + REFRESH_INTERVAL_MS;
                    if (!joined) return;
                    if (error != null) {
                        status = "Nicht erreichbar";
                        BetterUCMod.LOGGER.debug("betterUC feature flags request failed", error);
                        return;
                    }
                    JsonObject body = parseJson(response.body());
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || !body.has("ok") || !body.get("ok").getAsBoolean()
                            || !body.has("features") || !body.get("features").isJsonArray()) {
                        status = "Antwort ung\u00FCltig";
                        return;
                    }

                    Map<String, Boolean> updated = enabledDefaults();
                    JsonArray entries = body.getAsJsonArray("features");
                    for (JsonElement element : entries) {
                        if (!element.isJsonObject()) continue;
                        JsonObject entry = element.getAsJsonObject();
                        if (!entry.has("key") || !entry.has("enabled")) continue;
                        String key = entry.get("key").getAsString();
                        if (!KNOWN_FLAGS.contains(key)) continue;
                        updated.put(key, entry.get("enabled").getAsBoolean());
                    }
                    flags = Map.copyOf(updated);
                    status = "Aktuell";
                }));
    }

    private static Map<String, Boolean> enabledDefaults() {
        Map<String, Boolean> defaults = new HashMap<>();
        for (String key : KNOWN_FLAGS) defaults.put(key, true);
        return Map.copyOf(defaults);
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
            URI relay = URI.create(raw);
            if (relay.getRawAuthority() == null || relay.getRawAuthority().isBlank()) return null;
            String scheme = relay.getScheme() == null ? "https" : relay.getScheme().toLowerCase(Locale.ROOT);
            return URI.create(scheme + "://" + relay.getRawAuthority() + path);
        } catch (Exception error) {
            BetterUCMod.LOGGER.warn("Invalid betterUC feature API URL", error);
            return null;
        }
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
}
