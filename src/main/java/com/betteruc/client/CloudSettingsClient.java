package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.Gson;
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
import java.util.Locale;

public final class CloudSettingsClient {
    private static final int SCHEMA_VERSION = 1;
    private static final long UPLOAD_DEBOUNCE_MS = 1800L;
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private static volatile boolean joined;
    private static volatile boolean initialized;
    private static volatile boolean inFlight;
    private static volatile boolean dirty;
    private static volatile boolean applyingRemote;
    private static volatile long uploadAtMs;
    private static volatile long revision;
    private static volatile String activeToken = "";
    private static volatile String lastSyncedSnapshot = "";
    private static volatile String status = "Nicht verbunden";
    private static volatile String lastSync = "nie";

    private CloudSettingsClient() {
    }

    public static void initialize() {
        BetterUCConfig.setSaveListener(CloudSettingsClient::onConfigSaved);
    }

    public static void onJoin(Minecraft client) {
        joined = true;
        initialized = false;
        inFlight = false;
        dirty = false;
        activeToken = "";
        lastSyncedSnapshot = "";
        revision = 0L;
        status = BetterUCConfig.INSTANCE.cloudSettingsEnabled ? "Wird verbunden" : "Pausiert";
        tick(client);
    }

    public static void onDisconnect() {
        joined = false;
        initialized = false;
        inFlight = false;
        dirty = false;
        activeToken = "";
        status = "Nicht verbunden";
    }

    public static void tick(Minecraft client) {
        if (!joined || client == null || client.player == null) return;
        String token = accessToken();
        if (token.isBlank()) {
            activeToken = "";
            initialized = false;
            dirty = false;
            if (!inFlight) status = "Access Code fehlt";
            return;
        }
        if (!token.equals(activeToken)) {
            activeToken = token;
            initialized = false;
            dirty = false;
            revision = 0L;
            lastSyncedSnapshot = "";
            if (token.isBlank()) {
                status = "Access Code fehlt";
                return;
            }
            if (BetterUCConfig.INSTANCE.cloudSettingsEnabled && !inFlight) {
                requestDownload(client, false);
            }
            return;
        }

        if (!BetterUCConfig.INSTANCE.cloudSettingsEnabled) {
            if (!inFlight) status = "Pausiert";
            return;
        }
        if (!initialized && !inFlight && !token.isBlank()) {
            requestDownload(client, false);
            return;
        }
        if (initialized && dirty && !inFlight && System.currentTimeMillis() >= uploadAtMs) {
            uploadSnapshot(client, false, revision, false);
        }
    }

    public static void downloadNow(Minecraft client) {
        if (!prepareManual(client)) return;
        requestDownload(client, true);
    }

    public static void uploadNow(Minecraft client) {
        if (!prepareManual(client)) return;
        requestRevisionThenUpload(client);
    }

    public static String statusLabel() {
        return status;
    }

    public static String lastSyncLabel() {
        return lastSync + (revision > 0 ? " | Rev. " + revision : "");
    }

    public static boolean isReady() {
        return initialized && !inFlight;
    }

    private static boolean prepareManual(Minecraft client) {
        if (client == null || client.player == null) return false;
        if (inFlight) {
            sendLocalMessage(client, "\u00A77Cloud-Synchronisierung l\u00E4uft bereits.");
            return false;
        }
        String token = accessToken();
        if (token.isBlank()) {
            status = "Access Code fehlt";
            sendLocalMessage(client, "\u00A7cBitte zuerst deinen Access Code eintragen.");
            return false;
        }
        activeToken = token;
        return true;
    }

    private static void requestDownload(Minecraft client, boolean manual) {
        URI uri = apiUri("/api/user/settings");
        if (uri == null) {
            status = "Serveradresse ung\u00FCltig";
            return;
        }
        String token = accessToken();
        if (token.isBlank()) {
            status = "Access Code fehlt";
            return;
        }

        inFlight = true;
        status = "Cloud wird geladen";
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("x-betteruc-token", token)
                .GET()
                .build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> client.execute(() -> {
                    if (!joined || !token.equals(accessToken())) {
                        inFlight = false;
                        return;
                    }
                    if (error != null) {
                        fail(client, manual, "Cloud nicht erreichbar", error);
                        return;
                    }
                    JsonObject body = parseJson(response.body());
                    if (response.statusCode() < 200 || response.statusCode() >= 300 || !boolValue(body, "ok")) {
                        fail(client, manual, stringValue(body, "error", "Cloud-Download fehlgeschlagen."), null);
                        return;
                    }

                    boolean exists = boolValue(body, "exists");
                    if (!exists || !body.has("profile") || body.get("profile").isJsonNull()) {
                        revision = 0L;
                        initialized = true;
                        inFlight = false;
                        lastSyncedSnapshot = snapshotString();
                        status = "Cloud-Profil wird erstellt";
                        uploadSnapshot(client, manual, 0L, false);
                        return;
                    }

                    applyProfile(client, body.getAsJsonObject("profile"), manual,
                            manual ? "Cloud-Einstellungen geladen." : null);
                }));
    }

    private static void requestRevisionThenUpload(Minecraft client) {
        URI uri = apiUri("/api/user/settings");
        if (uri == null) {
            status = "Serveradresse ung\u00FCltig";
            return;
        }
        String token = accessToken();
        inFlight = true;
        status = "Cloud wird gepr\u00FCft";
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("x-betteruc-token", token)
                .GET()
                .build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> client.execute(() -> {
                    if (error != null) {
                        fail(client, true, "Cloud nicht erreichbar", error);
                        return;
                    }
                    JsonObject body = parseJson(response.body());
                    if (response.statusCode() < 200 || response.statusCode() >= 300 || !boolValue(body, "ok")) {
                        fail(client, true, stringValue(body, "error", "Cloud-Pr\u00FCfung fehlgeschlagen."), null);
                        return;
                    }
                    long latestRevision = 0L;
                    if (boolValue(body, "exists") && body.has("profile") && body.get("profile").isJsonObject()) {
                        latestRevision = longValue(body.getAsJsonObject("profile"), "revision", 0L);
                    }
                    inFlight = false;
                    uploadSnapshot(client, true, latestRevision, true);
                }));
    }

    private static void uploadSnapshot(Minecraft client, boolean manual, long baseRevision, boolean retryOnConflict) {
        URI uri = apiUri("/api/user/settings");
        if (uri == null || inFlight) return;
        String token = accessToken();
        if (token.isBlank()) return;

        JsonObject settings = BetterUCConfig.cloudSettingsSnapshot();
        String sentSnapshot = GSON.toJson(settings);
        JsonObject body = new JsonObject();
        body.addProperty("schemaVersion", SCHEMA_VERSION);
        body.addProperty("baseRevision", baseRevision);
        body.addProperty("modVersion", modVersion());
        body.add("settings", settings);

        inFlight = true;
        status = "Wird gespeichert";
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json")
                .header("x-betteruc-token", token)
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> client.execute(() -> {
                    if (error != null) {
                        fail(client, manual, "Cloud-Speichern fehlgeschlagen", error);
                        return;
                    }
                    JsonObject responseBody = parseJson(response.body());
                    if (response.statusCode() == 409) {
                        inFlight = false;
                        if (responseBody.has("profile") && responseBody.get("profile").isJsonObject()) {
                            JsonObject profile = responseBody.getAsJsonObject("profile");
                            long cloudRevision = longValue(profile, "revision", 0L);
                            if (retryOnConflict) {
                                uploadSnapshot(client, true, cloudRevision, false);
                            } else {
                                applyProfile(client, profile, manual,
                                        "Neuere Cloud-Einstellungen wurden \u00FCbernommen.");
                            }
                        } else {
                            revision = 0L;
                            initialized = true;
                            uploadSnapshot(client, manual, 0L, false);
                        }
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || !boolValue(responseBody, "ok") || !responseBody.has("profile")) {
                        fail(client, manual, stringValue(responseBody, "error", "Cloud-Speichern fehlgeschlagen."), null);
                        return;
                    }

                    JsonObject profile = responseBody.getAsJsonObject("profile");
                    revision = longValue(profile, "revision", baseRevision + 1L);
                    initialized = true;
                    inFlight = false;
                    dirty = false;
                    lastSyncedSnapshot = sentSnapshot;
                    lastSync = "gerade eben";
                    status = "Synchronisiert";
                    if (manual) sendLocalMessage(client, "\u00A7aEinstellungen in der Cloud gespeichert.");
                }));
    }

    private static void applyProfile(Minecraft client, JsonObject profile, boolean manual, String message) {
        if (profile == null || !profile.has("settings") || !profile.get("settings").isJsonObject()) {
            fail(client, manual, "Cloud-Profil ist besch\u00E4digt.", null);
            return;
        }
        applyingRemote = true;
        try {
            BetterUCConfig.applyCloudSettings(profile.getAsJsonObject("settings"));
            BetterUCConfig.save();
            BetterUCFontManager.rebuildAndReload(client);
        } finally {
            applyingRemote = false;
        }
        revision = longValue(profile, "revision", 0L);
        initialized = true;
        inFlight = false;
        dirty = false;
        lastSyncedSnapshot = snapshotString();
        lastSync = "gerade eben";
        status = "Synchronisiert";
        if (manual && message != null) sendLocalMessage(client, "\u00A7a" + message);
    }

    private static void onConfigSaved() {
        if (applyingRemote || !joined || !initialized || !BetterUCConfig.INSTANCE.cloudSettingsEnabled) return;
        String current = snapshotString();
        if (current.equals(lastSyncedSnapshot)) return;
        dirty = true;
        uploadAtMs = System.currentTimeMillis() + UPLOAD_DEBOUNCE_MS;
        status = "Lokale \u00C4nderungen";
    }

    private static void fail(Minecraft client, boolean manual, String message, Throwable error) {
        inFlight = false;
        status = message;
        if (error != null) BetterUCMod.LOGGER.debug("betterUC cloud settings request failed", error);
        if (manual) sendLocalMessage(client, "\u00A7c" + message);
    }

    private static String snapshotString() {
        return GSON.toJson(BetterUCConfig.cloudSettingsSnapshot());
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
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Invalid betterUC cloud API URL", e);
            return null;
        }
    }

    private static String accessToken() {
        return BetterUCConfig.INSTANCE.pingRelayToken == null ? "" : BetterUCConfig.INSTANCE.pingRelayToken.trim();
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

    private static long longValue(JsonObject json, String key, long fallback) {
        try {
            return json != null && json.has(key) ? json.get(key).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        try {
            return json != null && json.has(key) && !json.get(key).isJsonNull()
                    ? json.get(key).getAsString()
                    : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void sendLocalMessage(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal("\u00A7b[betterUC]\u00A7r " + message));
        }
    }
}
