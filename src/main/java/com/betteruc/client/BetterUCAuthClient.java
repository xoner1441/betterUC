package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;

import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Establishes a betterUC session from the Minecraft session without exposing
 * the Minecraft access token to the betterUC platform.
 */
public final class BetterUCAuthClient {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final MinecraftSessionService MINECRAFT_SESSION_SERVICE =
            new YggdrasilAuthenticationService(Proxy.NO_PROXY).createMinecraftSessionService();
    private static final long RETRY_DELAY_MS = 15_000L;
    private static final long MAX_RETRY_DELAY_MS = 5 * 60_000L;
    private static final long EXPIRY_MARGIN_MS = 60_000L;

    private static volatile boolean joined;
    private static volatile boolean inFlight;
    private static volatile boolean authenticationAllowed;
    private static volatile boolean minecraftSessionBlocked;
    private static volatile int transientFailures;
    private static volatile long attemptGeneration;
    private static volatile long nextRetryAtMs;
    private static volatile String status = "Nicht verbunden";

    private BetterUCAuthClient() {
    }

    public static void onJoin(Minecraft client) {
        joined = true;
        authenticationAllowed = false;
        attemptGeneration++;
        inFlight = false;
        nextRetryAtMs = 0L;
        status = hasValidSession(client)
                ? "Automatisch angemeldet"
                : minecraftSessionBlocked
                        ? blockedStatus()
                        : "Anmeldung nach Servertrennung";
    }

    public static void onDisconnect() {
        joined = false;
        authenticationAllowed = false;
        attemptGeneration++;
        inFlight = false;
        nextRetryAtMs = 0L;
        status = hasLocallyUsableSession()
                ? "Automatisch angemeldet"
                : minecraftSessionBlocked ? blockedStatus() : "Automatische Anmeldung";
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        if (hasValidSession(client)) {
            if (!inFlight) status = "Automatisch angemeldet";
            return;
        }
        clearInvalidSession(client, false);
        boolean safe = isAuthenticationSafe(client);
        if (!safe) {
            if (authenticationAllowed) {
                authenticationAllowed = false;
                attemptGeneration++;
                inFlight = false;
            }
            if (!minecraftSessionBlocked) status = "Anmeldung nach Servertrennung";
            else status = blockedStatus();
            return;
        }
        authenticationAllowed = true;
        if (minecraftSessionBlocked) {
            status = blockedStatus();
            return;
        }
        if (inFlight || System.currentTimeMillis() < nextRetryAtMs) return;
        authenticate(client);
    }

    public static String credential() {
        if (hasLocallyUsableSession()) {
            return safe(BetterUCConfig.INSTANCE.pingRelaySessionToken);
        }
        return legacyCredential();
    }

    public static boolean hasCredential() {
        return !credential().isBlank();
    }

    public static boolean usesAutomaticSession() {
        return hasLocallyUsableSession();
    }

    public static String statusLabel() {
        return status;
    }

    public static void invalidateSession(Minecraft client) {
        clearInvalidSession(client, true);
        nextRetryAtMs = 0L;
        status = "Sitzung wird erneuert";
    }

    public static void renew(Minecraft client) {
        clearInvalidSession(client, true);
        minecraftSessionBlocked = false;
        transientFailures = 0;
        nextRetryAtMs = 0L;
        status = "Sitzung wird erneuert";
        tick(client);
    }

    private static void authenticate(Minecraft client) {
        User user = client.getUser();
        if (user == null || user.getProfileId() == null || safe(user.getName()).isBlank()
                || safe(user.getAccessToken()).isBlank()) {
            blockMinecraftSession("Minecraft-Anmeldung nicht verfügbar", null);
            return;
        }

        URI challengeUri = apiUri("/api/auth/challenge");
        URI completeUri = apiUri("/api/auth/complete");
        if (challengeUri == null || completeUri == null) {
            fail("Relay-Adresse ungültig", null);
            return;
        }

        String name = user.getName();
        UUID uuid = user.getProfileId();
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", name);
        requestBody.addProperty("uuid", uuid.toString());
        requestBody.addProperty("version", modVersion());
        requestBody.addProperty("gameVersion", gameVersion());

        long generation = ++attemptGeneration;
        inFlight = true;
        status = "Minecraft-Sitzung wird bestätigt";
        HttpRequest challengeRequest = jsonPost(challengeUri, requestBody);
        HTTP_CLIENT.sendAsync(challengeRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    ensureAttemptAllowed(client, generation);
                    JsonObject body = responseBody(response);
                    if (response.statusCode() < 200 || response.statusCode() >= 300 || !boolValue(body, "ok")) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                stringValue(body, "error", "Challenge konnte nicht erstellt werden.")));
                    }
                    AuthChallenge challenge = new AuthChallenge(
                            stringValue(body, "challengeId", ""),
                            stringValue(body, "serverId", "")
                    );
                    if (challenge.id().isBlank() || challenge.serverId().isBlank()) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Ungültige Anmelde-Challenge."));
                    }
                    return CompletableFuture.supplyAsync(() -> {
                        ensureAttemptAllowed(client, generation);
                        try {
                            // Only Mojang receives the Minecraft access token.
                            MINECRAFT_SESSION_SERVICE.joinServer(uuid, user.getAccessToken(), challenge.serverId());
                            return challenge;
                        } catch (Exception error) {
                            throw new CompletionException(error);
                        }
                    });
                })
                .thenCompose(challenge -> {
                    ensureAttemptAllowed(client, generation);
                    JsonObject completeBody = new JsonObject();
                    completeBody.addProperty("challengeId", challenge.id());
                    completeBody.addProperty("version", modVersion());
                    completeBody.addProperty("gameVersion", gameVersion());
                    return HTTP_CLIENT.sendAsync(
                            jsonPost(completeUri, completeBody),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                    );
                })
                .whenComplete((response, error) -> client.execute(() -> {
                    if (generation != attemptGeneration) return;
                    if (error != null) {
                        fail("Automatische Anmeldung fehlgeschlagen", error);
                        return;
                    }
                    JsonObject body = responseBody(response);
                    String sessionToken = stringValue(body, "sessionToken", "");
                    long expiresAt = longValue(body, "expiresAt", 0L);
                    if (response.statusCode() < 200 || response.statusCode() >= 300
                            || !boolValue(body, "ok") || sessionToken.isBlank() || expiresAt <= System.currentTimeMillis()) {
                        fail(stringValue(body, "error", "Automatische Anmeldung fehlgeschlagen."), null);
                        return;
                    }

                    BetterUCConfig.INSTANCE.pingRelaySessionToken = sessionToken;
                    BetterUCConfig.INSTANCE.pingRelaySessionExpiresAt = expiresAt;
                    BetterUCConfig.INSTANCE.pingRelaySessionUuid = uuid.toString();
                    BetterUCConfig.INSTANCE.pingRelaySessionName = name;
                    BetterUCConfig.save();
                    inFlight = false;
                    transientFailures = 0;
                    nextRetryAtMs = 0L;
                    status = "Automatisch angemeldet";

                    // Reconnect immediately if the relay was waiting for a credential.
                    if (joined && client.player != null && client.getConnection() != null) {
                        PingRelayClient.onDisconnect();
                        PingRelayClient.onJoin(client);
                        CloudSettingsClient.onJoin(client);
                    }
                }));
    }

    private static HttpRequest jsonPost(URI uri, JsonObject body) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("content-type", "application/json")
                .header("x-betteruc-version", modVersion())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
    }

    private static void fail(String message, Throwable error) {
        inFlight = false;
        if (isDeferred(error)) {
            nextRetryAtMs = 0L;
            status = "Anmeldung nach Servertrennung";
            return;
        }
        if (isCredentialFailure(error)) {
            blockMinecraftSession(message, error);
            return;
        }
        transientFailures++;
        nextRetryAtMs = System.currentTimeMillis() + retryDelayMs(transientFailures);
        status = legacyCredential().isBlank() ? message : "Migration über bisherigen Zugang";
        if (error != null) BetterUCMod.LOGGER.debug("betterUC automatic authentication failed", error);
    }

    private static void blockMinecraftSession(String message, Throwable error) {
        inFlight = false;
        minecraftSessionBlocked = true;
        nextRetryAtMs = Long.MAX_VALUE;
        status = blockedStatus();
        if (error != null) BetterUCMod.LOGGER.debug(message, error);
    }

    private static String blockedStatus() {
        return legacyCredential().isBlank()
                ? "Minecraft-Sitzung im Launcher erneuern"
                : "Bisheriger Zugang aktiv; Minecraft-Sitzung erneuern";
    }

    private static boolean hasValidSession(Minecraft client) {
        if (!hasLocallyUsableSession()) return false;
        if (client == null || client.getUser() == null || client.getUser().getProfileId() == null) return true;
        return client.getUser().getProfileId().toString()
                .equalsIgnoreCase(safe(BetterUCConfig.INSTANCE.pingRelaySessionUuid));
    }

    private static boolean hasLocallyUsableSession() {
        return !safe(BetterUCConfig.INSTANCE.pingRelaySessionToken).isBlank()
                && BetterUCConfig.INSTANCE.pingRelaySessionExpiresAt > System.currentTimeMillis() + EXPIRY_MARGIN_MS
                && !safe(BetterUCConfig.INSTANCE.pingRelaySessionUuid).isBlank();
    }

    private static void clearInvalidSession(Minecraft client, boolean save) {
        boolean hadSession = !safe(BetterUCConfig.INSTANCE.pingRelaySessionToken).isBlank();
        BetterUCConfig.INSTANCE.pingRelaySessionToken = "";
        BetterUCConfig.INSTANCE.pingRelaySessionExpiresAt = 0L;
        BetterUCConfig.INSTANCE.pingRelaySessionUuid = "";
        BetterUCConfig.INSTANCE.pingRelaySessionName = "";
        if (save && hadSession) BetterUCConfig.save();
        if (client != null && !joined) status = "Nicht verbunden";
    }

    private static String legacyCredential() {
        return safe(BetterUCConfig.INSTANCE.pingRelayToken);
    }

    private static URI apiUri(String path) {
        try {
            String raw = safe(BetterUCConfig.INSTANCE.pingRelayUrl);
            if (raw.isBlank()) raw = BetterUCConfig.DEFAULT_PING_RELAY_URL;
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
            BetterUCMod.LOGGER.debug("Invalid betterUC authentication API URL", error);
            return null;
        }
    }

    private static JsonObject responseBody(HttpResponse<String> response) {
        try {
            return JsonParser.parseString(response == null || response.body() == null ? "{}" : response.body())
                    .getAsJsonObject();
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

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(BetterUCMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String gameVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static boolean isAuthenticationSafe(Minecraft client) {
        return isAuthenticationSafeState(
                joined,
                client.player != null,
                client.getConnection() != null,
                client.getCurrentServer() != null,
                client.isLocalServer(),
                client.gui.screen() instanceof ConnectScreen
        );
    }

    static boolean isAuthenticationSafeState(
            boolean joined,
            boolean playerPresent,
            boolean hasConnection,
            boolean hasCurrentServer,
            boolean localServer,
            boolean connectionScreen
    ) {
        return !joined && !playerPresent && !hasConnection && !hasCurrentServer && !localServer && !connectionScreen;
    }

    private static void ensureAttemptAllowed(Minecraft client, long generation) {
        if (generation != attemptGeneration || !authenticationAllowed || !isAuthenticationSafe(client)) {
            throw new CompletionException(new AuthenticationDeferredException());
        }
    }

    private static boolean isDeferred(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof AuthenticationDeferredException) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }

    static boolean isCredentialFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String type = current.getClass().getName().toLowerCase(Locale.ROOT);
            String message = safe(current.getMessage()).toLowerCase(Locale.ROOT);
            if (type.contains("invalidcredentials") || type.contains("forbiddenoperation")
                    || message.contains("invalid token") || message.contains("token invalid")
                    || message.contains("token is invalid") || message.contains("access token invalid")
                    || message.contains("access token revoked") || message.contains("invalid session")) {
                return true;
            }
            if (current.getCause() == current) break;
        }
        return false;
    }

    static long retryDelayMs(int failures) {
        int exponent = Math.min(5, Math.max(0, failures - 1));
        return Math.min(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS << exponent);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AuthenticationDeferredException extends RuntimeException {
        private AuthenticationDeferredException() {
            super("Minecraft connection started while betterUC authentication was pending");
        }
    }

    private record AuthChallenge(String id, String serverId) {
    }
}
