package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.PlayerNameUtil;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

public final class PingRelayClient {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final Object LOCK = new Object();
    private static final List<PingMarker> ACTIVE_PINGS = new ArrayList<>();
    private static final List<RelayPlayer> ONLINE_PLAYERS = new ArrayList<>();
    private static final List<RelayPlayer> GLOBAL_ONLINE_PLAYERS = new ArrayList<>();
    private static final long RECONNECT_DELAY_MS = 5000L;
    private static final long ONLINE_PLAYERS_REFRESH_MS = 10000L;
    private static final double PING_RAYCAST_DISTANCE = 128.0D;
    private static final double MAX_RECEIVE_PING_DISTANCE = 128.0D;
    private static final String FALLBACK_RELAY_URL = "ws://65.109.175.203:3000/ws";

    private static WebSocket webSocket;
    private static boolean connecting = false;
    private static boolean connected = false;
    private static String status = "Nicht verbunden";
    private static String role = "user";
    private static long nextReconnectAtMs = 0L;
    private static long lastPingSentMs = 0L;
    private static long lastOnlinePlayersRefreshMs = 0L;
    private static boolean onlinePlayersRefreshInFlight = false;
    private static boolean connectedViaFallbackRelay = false;

    private PingRelayClient() {
    }

    public static void tick(MinecraftClient client) {
        cleanupExpired();

        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            disconnect();
            return;
        }

        if (!BetterUCConfig.INSTANCE.pingRelayEnabled) {
            disconnect();
            status = "Deaktiviert";
            return;
        }

        if (!hasRelayCredential()) {
            disconnect();
            status = "Zugangscode fehlt";
            return;
        }

        if (BetterUCConfig.INSTANCE.pingRelayUrl == null || BetterUCConfig.INSTANCE.pingRelayUrl.isBlank()) {
            disconnect();
            status = "Server fehlt";
            return;
        }

        if (connected) {
            refreshOnlinePlayersFromApi(client);
            return;
        }
        if (connecting || System.currentTimeMillis() < nextReconnectAtMs) return;
        connect(client);
    }

    public static void onJoin(MinecraftClient client) {
        synchronized (LOCK) {
            ACTIVE_PINGS.clear();
            ONLINE_PLAYERS.clear();
            GLOBAL_ONLINE_PLAYERS.clear();
        }
        nextReconnectAtMs = 0L;
        tick(client);
    }

    public static void onDisconnect() {
        disconnect();
        synchronized (LOCK) {
            ACTIVE_PINGS.clear();
            ONLINE_PLAYERS.clear();
            GLOBAL_ONLINE_PLAYERS.clear();
        }
    }

    public static boolean sendPingAtCrosshair(MinecraftClient client) {
        return sendPingAtCrosshair(client, PingType.NORMAL);
    }

    public static boolean sendPingAtCrosshair(MinecraftClient client, PingType pingType) {
        PingType safeType = pingType == null ? PingType.NORMAL : pingType;
        if (client == null || client.player == null || client.world == null) return false;
        if (!CommunicationDeviceTracker.canPing()) {
            sendLocalMessage(client, CommunicationDeviceTracker.blockMessage());
            return false;
        }
        if (!connected || webSocket == null) {
            sendLocalMessage(client, "Ping System ist nicht verbunden (" + status + ").");
            return false;
        }
        String scope = pingScope();
        if ("faction".equals(scope) && currentFaction().isBlank()) {
            sendLocalMessage(client, "Fraktion noch nicht erkannt. Bitte /stats aktualisieren.");
            return false;
        }
        long now = System.currentTimeMillis();
        int cooldownMs = Math.max(0, Math.min(10000, BetterUCConfig.INSTANCE.pingCooldownMs));
        long remainingMs = lastPingSentMs + cooldownMs - now;
        if (remainingMs > 0L) {
            sendLocalMessage(client, "Ping Cooldown: " + cooldownLabel(remainingMs) + ".");
            return false;
        }

        PingTarget target = targetFromCrosshair(client);
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "ping");
        payload.addProperty("pingType", safeType.id());
        payload.addProperty("sender", playerName(client));
        payload.addProperty("server", currentServerId(client));
        payload.addProperty("channel", channel());
        payload.addProperty("scope", scope);
        payload.addProperty("dimension", currentDimension(client));
        payload.addProperty("x", target.pos.x);
        payload.addProperty("y", target.pos.y);
        payload.addProperty("z", target.pos.z);
        payload.addProperty("label", target.label);
        payload.addProperty("color", colorForType(safeType));

        try {
            webSocket.sendText(GSON.toJson(payload), true);
            lastPingSentMs = now;
            playPingSelectionSound(client, safeType);
            return true;
        } catch (Exception e) {
            markDisconnected("Senden fehlgeschlagen");
            BetterUCMod.LOGGER.warn("Failed to send betterUC ping", e);
            sendLocalMessage(client, "Ping konnte nicht gesendet werden.");
            return false;
        }
    }

    public static boolean isConnected() {
        return connected;
    }

    public static String statusLabel() {
        return status;
    }

    public static boolean isAdminSession() {
        return connected && "admin".equals(role);
    }

    public static String roleLabel() {
        return switch (role) {
            case "admin" -> "Admin";
            case "helper" -> "Helper";
            case "partner" -> "Partner";
            case "vip" -> "VIP";
            default -> "Spieler";
        };
    }

    public static String pingSoundLabel(String id) {
        return PingSound.fromId(id).label();
    }

    public static String nextPingSoundId(String id) {
        PingSound[] sounds = PingSound.values();
        PingSound current = PingSound.fromId(id);
        int next = (current.ordinal() + 1) % sounds.length;
        return sounds[next].id();
    }

    public static int pingCooldownDurationMs() {
        return Math.max(0, Math.min(10000, BetterUCConfig.INSTANCE.pingCooldownMs));
    }

    public static long pingCooldownRemainingMs() {
        int cooldownMs = pingCooldownDurationMs();
        if (cooldownMs <= 0) return 0L;
        return Math.max(0L, lastPingSentMs + cooldownMs - System.currentTimeMillis());
    }

    public static void refreshIdentity(MinecraftClient client) {
        WebSocket socket = webSocket;
        if (!connected || socket == null || client == null || client.player == null) return;
        try {
            sendHello(socket, client);
        } catch (Exception e) {
            BetterUCMod.LOGGER.debug("Could not refresh betterUC ping relay identity", e);
        }
    }

    public static List<PingMarker> activePings() {
        cleanupExpired();
        synchronized (LOCK) {
            return new ArrayList<>(ACTIVE_PINGS);
        }
    }

    public static boolean hasBetterUCBadge(PlayerListEntry entry) {
        return findRelayPlayer(entry) != null;
    }

    public static boolean hasAdminBadge(PlayerListEntry entry) {
        RelayPlayer player = findRelayPlayer(entry);
        return player != null && "admin".equals(player.role());
    }

    public static boolean hasVipBadge(PlayerListEntry entry) {
        RelayPlayer player = findRelayPlayer(entry);
        return player != null && "vip".equals(player.role());
    }

    public static boolean hasPartnerBadge(PlayerListEntry entry) {
        RelayPlayer player = findRelayPlayer(entry);
        return player != null && "partner".equals(player.role());
    }

    public static boolean hasHelperBadge(PlayerListEntry entry) {
        RelayPlayer player = findRelayPlayer(entry);
        return player != null && "helper".equals(player.role());
    }

    public static String roleNameTagForPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        if (player == null) return "";
        return switch (player.role()) {
            case "admin" -> "Admin";
            case "helper" -> "Helper";
            case "partner" -> "Partner";
            case "vip" -> "VIP";
            default -> "";
        };
    }

    public static void showOnlineCommandList(MinecraftClient client) {
        if (client == null || client.player == null) return;
        if (!connected) {
            sendLocalMessage(client, "Online-Liste nicht verfuegbar: Relay ist nicht verbunden.");
            return;
        }

        String sessionRole = cleanRole(role);
        if (!"admin".equals(sessionRole) && !"helper".equals(sessionRole)) {
            sendLocalMessage(client, "Keine Berechtigung. /buonline ist nur fuer Helper und Admins.");
            return;
        }

        URI uri = playersApiUri();
        String token = accessToken();
        if (uri == null || token.isBlank()) {
            sendLocalMessage(client, "Online-Liste nicht verfuegbar: Zugangscode oder Relay-Adresse fehlt.");
            return;
        }

        sendLocalMessage(client, "Online-Liste wird geladen...");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("X-BetterUC-Token", token)
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> client.execute(() -> {
                    if (client.player == null) return;
                    if (error != null || response == null) {
                        sendLocalMessage(client, "Online-Liste konnte nicht geladen werden.");
                        return;
                    }
                    if (response.statusCode() == 401) {
                        sendLocalMessage(client, "Online-Liste konnte nicht geladen werden: Access Code ungueltig.");
                        return;
                    }
                    if (response.statusCode() != 200) {
                        sendLocalMessage(client, "Online-Liste konnte nicht geladen werden: HTTP " + response.statusCode() + ".");
                        return;
                    }

                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray players = json.has("players") && json.get("players").isJsonArray()
                                ? json.getAsJsonArray("players")
                                : new JsonArray();
                        List<OnlineListEntry> entries = parseOnlineListEntries(players);
                        renderOnlineList(client, entries);
                    } catch (Exception e) {
                        BetterUCMod.LOGGER.debug("Ignored invalid betterUC online list response", e);
                        sendLocalMessage(client, "Online-Liste konnte nicht gelesen werden.");
                    }
                }));
    }

    public static String tabBadgeRoleForRenderedText(String renderedText) {
        if (renderedText == null || renderedText.isBlank()) return "";
        String normalizedText = renderedText.toLowerCase(Locale.ROOT).trim();
        RelayPlayer bestMatch = null;
        synchronized (LOCK) {
            for (RelayPlayer player : ONLINE_PLAYERS) {
                if (looksLikeTabListName(normalizedText, player.nameLower())
                        && (bestMatch == null || player.nameLower().length() > bestMatch.nameLower().length())) {
                    bestMatch = player;
                }
            }
            for (RelayPlayer player : GLOBAL_ONLINE_PLAYERS) {
                if (looksLikeTabListName(normalizedText, player.nameLower())
                        && (bestMatch == null
                        || player.priority() > bestMatch.priority()
                        || player.nameLower().length() > bestMatch.nameLower().length())) {
                    bestMatch = player;
                }
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null
                && looksLikeTabListName(normalizedText, playerName(client).toLowerCase(Locale.ROOT))) {
            RelayPlayer localPlayer = findRelayPlayer(playerName(client), playerUuid(client));
            if (localPlayer != null
                    && (bestMatch == null || localPlayer.priority() >= bestMatch.priority())) {
                bestMatch = localPlayer;
            }
        }

        return bestMatch == null ? "" : cleanRole(bestMatch.role());
    }

    private static boolean looksLikeTabListName(String renderedText, String playerNameLower) {
        if (renderedText == null || playerNameLower == null || renderedText.isBlank() || playerNameLower.isBlank()) {
            return false;
        }
        if (!containsMinecraftName(renderedText, playerNameLower)) {
            return false;
        }

        int index = renderedText.indexOf(playerNameLower);
        if (index < 0) {
            return false;
        }

        String before = renderedText.substring(0, index);
        String after = renderedText.substring(index + playerNameLower.length());

        // Tablist rows may contain short client icons/prefixes, but chat lines contain
        // message text around the name. Keep this strict so the bUC badge cannot leak
        // into chat or scoreboard text when another mod changes render order.
        return before.length() <= 8
                && after.length() <= 4
                && !after.contains(":")
                && !after.contains(">")
                && !after.contains("»")
                && !after.contains(" sagt")
                && !after.contains(" ");
    }

    public static boolean isAdminPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        return player != null && "admin".equals(player.role());
    }

    public static boolean isVipPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        return player != null && "vip".equals(player.role());
    }

    public static boolean isPartnerPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        return player != null && "partner".equals(player.role());
    }

    public static boolean isHelperPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        return player != null && "helper".equals(player.role());
    }

    public static String currentServerId(MinecraftClient client) {
        if (client == null) return "singleplayer";
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return normalizeServerId(info.address);
        }
        if (client.isInSingleplayer()) return "singleplayer";
        return "unknown";
    }

    private static String normalizeServerId(String address) {
        String raw = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) return "unknown";
        if (raw.contains("unicacity.eu")) return "unicacity.eu";
        return raw.replaceFirst("^([a-z]+://)", "")
                .replaceFirst("/.*$", "")
                .replaceFirst(":\\d+$", "");
    }

    public static String currentDimension(MinecraftClient client) {
        if (client == null || client.world == null) return "unknown";
        return client.world.getRegistryKey().getValue().toString();
    }

    private static void connect(MinecraftClient client) {
        connect(client, false);
    }

    private static void connect(MinecraftClient client, boolean fallbackAttempt) {
        URI uri = relayUri(client, fallbackAttempt);
        if (uri == null) {
            status = "Server ungültig";
            nextReconnectAtMs = System.currentTimeMillis() + RECONNECT_DELAY_MS;
            return;
        }

        connecting = true;
        status = "Verbinde...";
        WebSocket.Builder builder = HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(6));
        String token = accessToken();
        if (!token.isBlank()) {
            builder.header("X-BetterUC-Token", token);
        }
        builder.buildAsync(uri, new RelayListener(client))
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        String connectionStatus = statusFromConnectionError(error);
                        if (connectionStatus != null) {
                            markDisconnected(connectionStatus);
                            BetterUCMod.LOGGER.debug("betterUC ping relay connection failed", error);
                            return;
                        }

                        if (!fallbackAttempt && shouldTryFallbackRelay()) {
                            webSocket = null;
                            connected = false;
                            connecting = false;
                            BetterUCMod.LOGGER.debug("betterUC ping relay domain connection failed, trying temporary fallback", error);
                            connect(client, true);
                            return;
                        }

                        markDisconnected("Offline");
                        BetterUCMod.LOGGER.debug("betterUC ping relay connection failed", error);
                        return;
                    }

                    webSocket = socket;
                    connectedViaFallbackRelay = fallbackAttempt;
                });
    }

    private static URI relayUri(MinecraftClient client, boolean fallbackAttempt) {
        try {
            String raw = fallbackAttempt ? FALLBACK_RELAY_URL : BetterUCConfig.INSTANCE.pingRelayUrl.trim();
            if (raw.startsWith("http://")) raw = "ws://" + raw.substring("http://".length());
            if (raw.startsWith("https://")) raw = "wss://" + raw.substring("https://".length());
            if (!raw.startsWith("ws://") && !raw.startsWith("wss://")) raw = "ws://" + raw;
            if (!raw.contains("/ws")) {
                raw = raw.endsWith("/") ? raw + "ws" : raw + "/ws";
            }

            String separator = raw.contains("?") ? "&" : "?";
            String url = raw + separator
                    + "name=" + encode(playerName(client))
                    + "&uuid=" + encode(playerUuid(client))
                    + "&server=" + encode(currentServerId(client))
                    + "&channel=" + encode(channel())
                    + "&faction=" + encode(currentFaction())
                    + "&version=" + encode(modVersion());
            return URI.create(url);
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Invalid betterUC ping relay URL", e);
            return null;
        }
    }

    private static boolean shouldTryFallbackRelay() {
        String raw = BetterUCConfig.INSTANCE.pingRelayUrl == null
                ? ""
                : BetterUCConfig.INSTANCE.pingRelayUrl.trim().toLowerCase(Locale.ROOT);
        return raw.equals(BetterUCConfig.DEFAULT_PING_RELAY_URL)
                || raw.equals("ping.betteruc.de")
                || raw.equals("ping.betteruc.de/ws")
                || raw.equals("https://ping.betteruc.de")
                || raw.equals("https://ping.betteruc.de/ws")
                || raw.equals("wss://ping.betteruc.de")
                || raw.equals("wss://ping.betteruc.de/ws");
    }

    private static void sendHello(WebSocket socket, MinecraftClient client) {
        if (socket == null || client == null || client.player == null) return;
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("name", playerName(client));
        hello.addProperty("uuid", playerUuid(client));
        hello.addProperty("server", currentServerId(client));
        hello.addProperty("channel", channel());
        hello.addProperty("faction", currentFaction());
        hello.addProperty("version", modVersion());
        socket.sendText(GSON.toJson(hello), true);
    }

    private static void handleMessage(MinecraftClient client, String raw) {
        try {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            String type = stringValue(json, "type", "");
            if ("welcome".equals(type) || "hello_ack".equals(type)) {
                connected = true;
                role = stringValue(json, "role", role).trim().toLowerCase(Locale.ROOT);
                status = switch (role) {
                    case "admin" -> "Admin verbunden";
                    case "helper" -> "Helper verbunden";
                    case "partner" -> "Partner verbunden";
                    case "vip" -> "VIP verbunden";
                    default -> "Verbunden";
                };
                return;
            }

            if ("presence".equals(type)) {
                updatePresence(json);
                refreshOnlinePlayersFromApi(client);
                return;
            }

            if (!"ping".equals(type)) return;

            PingMarker marker = new PingMarker(
                    stringValue(json, "id", ""),
                    stringValue(json, "sender", "Unbekannt"),
                    stringValue(json, "label", "Ping"),
                    PingType.fromId(stringValue(json, "pingType", PingType.NORMAL.id())).id(),
                    stringValue(json, "dimension", "unknown"),
                    doubleValue(json, "x", 0.0D),
                    doubleValue(json, "y", 0.0D),
                    doubleValue(json, "z", 0.0D),
                    stringValue(json, "color", colorForType(PingType.fromId(stringValue(json, "pingType", PingType.NORMAL.id())))),
                    longValue(json, "createdAt", System.currentTimeMillis()),
                    longValue(json, "expiresAt", System.currentTimeMillis() + BetterUCConfig.INSTANCE.pingRelayTtlSeconds * 1000L)
            );

            if (!shouldAcceptMarker(client, marker)) {
                return;
            }

            synchronized (LOCK) {
                ACTIVE_PINGS.removeIf(existing -> !existing.id().isEmpty() && existing.id().equals(marker.id()));
                ACTIVE_PINGS.add(marker);
                while (ACTIVE_PINGS.size() > 12) {
                    ACTIVE_PINGS.remove(0);
                }
            }
            if (!isOwnMarker(client, marker)) {
                playPingSound(client, PingType.fromId(marker.pingType()));
            }
        } catch (Exception e) {
            BetterUCMod.LOGGER.debug("Ignored invalid betterUC relay message: {}", raw);
        }
    }

    private static PingTarget targetFromCrosshair(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            return new PingTarget(entityHit.getPos(), entity.getName().getString());
        }

        HitResult longHit = client.player.raycast(PING_RAYCAST_DISTANCE, 1.0F, false);
        if (longHit instanceof BlockHitResult blockHit && blockHit.getType() != HitResult.Type.MISS) {
            return new PingTarget(blockHit.getPos(), "Block");
        }

        Vec3d fallback = client.player.getEyePos().add(client.player.getRotationVec(1.0F).multiply(PING_RAYCAST_DISTANCE));
        return new PingTarget(fallback, "Position");
    }

    private static boolean shouldAcceptMarker(MinecraftClient client, PingMarker marker) {
        if (client == null || client.player == null || client.world == null || marker == null) return false;
        if (!sameDimension(currentDimension(client), marker.dimension())) return false;
        return distanceToPlayer(client, marker) <= effectiveReceiveDistance();
    }

    private static double effectiveReceiveDistance() {
        return Math.min(MAX_RECEIVE_PING_DISTANCE, Math.max(0, BetterUCConfig.INSTANCE.pingRelayMaxDistance));
    }

    private static double distanceToPlayer(MinecraftClient client, PingMarker marker) {
        double dx = marker.x() - client.player.getX();
        double dy = marker.y() - client.player.getY();
        double dz = marker.z() - client.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isOwnMarker(MinecraftClient client, PingMarker marker) {
        return client != null
                && client.player != null
                && marker != null
                && marker.sender().equalsIgnoreCase(playerName(client));
    }

    private static boolean sameDimension(String current, String marker) {
        String currentNormalized = normalizeDimension(current);
        String markerNormalized = normalizeDimension(marker);
        return !currentNormalized.isEmpty() && currentNormalized.equals(markerNormalized);
    }

    private static String normalizeDimension(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            Iterator<PingMarker> iterator = ACTIVE_PINGS.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().expiresAt() <= now) {
                    iterator.remove();
                }
            }
        }
    }

    private static void disconnect() {
        WebSocket socket = webSocket;
        webSocket = null;
        connecting = false;
        connected = false;
        role = "user";
        connectedViaFallbackRelay = false;
        onlinePlayersRefreshInFlight = false;
        synchronized (LOCK) {
            ONLINE_PLAYERS.clear();
            GLOBAL_ONLINE_PLAYERS.clear();
        }
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
    }

    private static void markDisconnected(String newStatus) {
        webSocket = null;
        connected = false;
        connecting = false;
        role = "user";
        connectedViaFallbackRelay = false;
        onlinePlayersRefreshInFlight = false;
        synchronized (LOCK) {
            ONLINE_PLAYERS.clear();
            GLOBAL_ONLINE_PLAYERS.clear();
        }
        status = newStatus;
        nextReconnectAtMs = System.currentTimeMillis() + RECONNECT_DELAY_MS;
    }

    private static String statusFromConnectionError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof WebSocketHandshakeException handshakeException) {
                int code = handshakeException.getResponse().statusCode();
                if (code == 401 || code == 403) {
                    return "Access Code ungültig";
                }
                if (code == 404) {
                    return "Relay-Route fehlt";
                }
                if (code >= 500) {
                    return "Relay-Fehler";
                }
            }
        }
        return null;
    }

    private static void updatePresence(JsonObject json) {
        JsonArray players = json.has("players") && json.get("players").isJsonArray()
                ? json.getAsJsonArray("players")
                : new JsonArray();
        List<RelayPlayer> nextPlayers = parseRelayPlayers(players);
        synchronized (LOCK) {
            ONLINE_PLAYERS.clear();
            ONLINE_PLAYERS.addAll(nextPlayers);
        }
    }

    private static void refreshOnlinePlayersFromApi(MinecraftClient client) {
        if (!connected || client == null || client.player == null || onlinePlayersRefreshInFlight) return;
        long now = System.currentTimeMillis();
        if (now - lastOnlinePlayersRefreshMs < ONLINE_PLAYERS_REFRESH_MS) return;
        URI uri = playersApiUri();
        if (uri == null) return;
        String token = accessToken();
        if (token.isBlank()) return;

        lastOnlinePlayersRefreshMs = now;
        onlinePlayersRefreshInFlight = true;
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("X-BetterUC-Token", token)
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    onlinePlayersRefreshInFlight = false;
                    if (error != null || response == null || response.statusCode() != 200) return;
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonArray players = json.has("players") && json.get("players").isJsonArray()
                                ? json.getAsJsonArray("players")
                                : new JsonArray();
                        List<RelayPlayer> nextPlayers = parseRelayPlayers(players);
                        synchronized (LOCK) {
                            GLOBAL_ONLINE_PLAYERS.clear();
                            GLOBAL_ONLINE_PLAYERS.addAll(nextPlayers);
                        }
                    } catch (Exception e) {
                        BetterUCMod.LOGGER.debug("Ignored invalid betterUC online players response");
                    }
                });
    }

    private static URI playersApiUri() {
        try {
            String raw = connectedViaFallbackRelay
                    ? FALLBACK_RELAY_URL
                    : BetterUCConfig.INSTANCE.pingRelayUrl.trim();
            if (raw.startsWith("wss://")) raw = "https://" + raw.substring("wss://".length());
            else if (raw.startsWith("ws://")) raw = "http://" + raw.substring("ws://".length());
            else if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "https://" + raw;

            int queryIndex = raw.indexOf('?');
            if (queryIndex >= 0) raw = raw.substring(0, queryIndex);
            raw = raw.replaceFirst("/+$", "");
            if (raw.endsWith("/ws")) {
                raw = raw.substring(0, raw.length() - "/ws".length());
            }
            return URI.create(raw + "/api/players");
        } catch (Exception e) {
            return null;
        }
    }

    private static List<RelayPlayer> parseRelayPlayers(JsonArray players) {
        List<RelayPlayer> nextPlayers = new ArrayList<>();
        for (JsonElement element : players) {
            if (!element.isJsonObject()) continue;
            JsonObject player = element.getAsJsonObject();
            String name = stringValue(player, "name", "");
            if (name.isBlank()) continue;
            nextPlayers.add(new RelayPlayer(
                    name.toLowerCase(Locale.ROOT),
                    stringValue(player, "uuid", ""),
                    cleanRole(stringValue(player, "role", "user")),
                    longValue(player, "priority", 50L)
            ));
        }
        return nextPlayers;
    }

    private static List<OnlineListEntry> parseOnlineListEntries(JsonArray players) {
        List<OnlineListEntry> entries = new ArrayList<>();
        for (JsonElement element : players) {
            if (!element.isJsonObject()) continue;
            JsonObject player = element.getAsJsonObject();
            String name = stringValue(player, "name", "").trim();
            if (name.isBlank()) continue;
            entries.add(new OnlineListEntry(
                    name,
                    cleanDisplayLabel(stringValue(player, "faction", "")),
                    cleanDisplayLabel(stringValue(player, "version", "")),
                    cleanRole(stringValue(player, "role", "user")),
                    longValue(player, "priority", 50L)
            ));
        }
        entries.sort((a, b) -> {
            int priorityCompare = Long.compare(b.priority(), a.priority());
            if (priorityCompare != 0) return priorityCompare;
            return a.name().compareToIgnoreCase(b.name());
        });
        return entries;
    }

    private static void renderOnlineList(MinecraftClient client, List<OnlineListEntry> entries) {
        MutableText header = Text.literal("[betterUC] ").formatted(Formatting.GRAY)
                .append(Text.literal("Online Mod-User: ").formatted(Formatting.AQUA))
                .append(Text.literal(String.valueOf(entries.size())).formatted(Formatting.WHITE, Formatting.BOLD));
        sendText(client, header);

        sendText(client, Text.literal("User | Fraktion | Mod-Version").formatted(Formatting.DARK_GRAY));
        if (entries.isEmpty()) {
            sendText(client, Text.literal("Keine verbundenen Mod-User gefunden.").formatted(Formatting.GRAY));
            return;
        }

        for (OnlineListEntry entry : entries) {
            String faction = entry.faction().isBlank() ? "unbekannt" : entry.faction();
            String version = versionLabel(entry.version());
            MutableText line = Text.literal("- ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(entry.name()).formatted(roleFormatting(entry.role())))
                    .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(faction).formatted(Formatting.AQUA))
                    .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(version).formatted(Formatting.GRAY));
            sendText(client, line);
        }
    }

    private static Formatting roleFormatting(String role) {
        return switch (cleanRole(role)) {
            case "admin" -> Formatting.RED;
            case "helper" -> Formatting.YELLOW;
            case "partner" -> Formatting.AQUA;
            case "vip" -> Formatting.DARK_PURPLE;
            default -> Formatting.WHITE;
        };
    }

    private static String versionLabel(String version) {
        String clean = version == null ? "" : version.trim();
        if (clean.isBlank()) return "unbekannt";
        return clean.toLowerCase(Locale.ROOT).startsWith("v") ? clean : "v" + clean;
    }

    private static String cleanDisplayLabel(String value) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }

    private static String channel() {
        String raw = BetterUCConfig.INSTANCE.pingRelayChannel == null ? "" : BetterUCConfig.INSTANCE.pingRelayChannel.trim();
        return raw.isEmpty() ? "global" : raw.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private static String pingScope() {
        String raw = BetterUCConfig.INSTANCE.pingRelayScope == null ? "" : BetterUCConfig.INSTANCE.pingRelayScope.trim();
        return "faction".equalsIgnoreCase(raw) ? "faction" : "global";
    }

    private static boolean hasRelayCredential() {
        return !accessToken().isBlank();
    }

    private static String accessToken() {
        return BetterUCConfig.INSTANCE.pingRelayToken == null ? "" : BetterUCConfig.INSTANCE.pingRelayToken.trim();
    }

    private static RelayPlayer findRelayPlayer(PlayerListEntry entry) {
        if (entry == null) return null;
        String name = PlayerNameUtil.resolveProfileName(entry.getProfile());
        String uuid = PlayerNameUtil.resolveProfileUuid(entry.getProfile());
        if ((name == null || name.isBlank()) && uuid.isBlank()) return null;
        return findRelayPlayer(name, uuid);
    }

    private static RelayPlayer findRelayPlayer(String name, String uuid) {
        if ((name == null || name.isBlank()) && (uuid == null || uuid.isBlank())) return null;
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String normalizedUuid = uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
        synchronized (LOCK) {
            for (RelayPlayer player : ONLINE_PLAYERS) {
                boolean uuidMatches = !normalizedUuid.isBlank()
                        && player.uuid() != null
                        && player.uuid().equalsIgnoreCase(normalizedUuid);
                boolean nameMatches = !normalizedName.isBlank() && player.nameLower().equals(normalizedName);
                if (uuidMatches || nameMatches) {
                    return player;
                }
            }
            for (RelayPlayer player : GLOBAL_ONLINE_PLAYERS) {
                boolean uuidMatches = !normalizedUuid.isBlank()
                        && player.uuid() != null
                        && player.uuid().equalsIgnoreCase(normalizedUuid);
                boolean nameMatches = !normalizedName.isBlank() && player.nameLower().equals(normalizedName);
                if (uuidMatches || nameMatches) {
                    return player;
                }
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (connected && client != null && client.player != null
                && normalizedName.equals(playerName(client).toLowerCase(Locale.ROOT))) {
            return new RelayPlayer(normalizedName, playerUuid(client), cleanRole(role), priorityForRole(role));
        }
        return null;
    }

    private static boolean containsMinecraftName(String text, String name) {
        if (text == null || name == null || text.isBlank() || name.isBlank()) return false;
        int index = text.indexOf(name);
        while (index >= 0) {
            int before = index - 1;
            int after = index + name.length();
            boolean cleanStart = before < 0 || !isMinecraftNameChar(text.charAt(before));
            boolean cleanEnd = after >= text.length() || !isMinecraftNameChar(text.charAt(after));
            if (cleanStart && cleanEnd) return true;
            index = text.indexOf(name, index + 1);
        }
        return false;
    }

    private static boolean isMinecraftNameChar(char value) {
        return value == '_' || (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9');
    }

    private static String cleanRole(String value) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(cleaned) || "helper".equals(cleaned) || "partner".equals(cleaned) || "vip".equals(cleaned)) return cleaned;
        return "user";
    }

    private static long priorityForRole(String value) {
        return switch (cleanRole(value)) {
            case "admin" -> 100L;
            case "helper" -> 85L;
            case "partner" -> 80L;
            case "vip" -> 75L;
            default -> 50L;
        };
    }

    private static String currentFaction() {
        String raw = BetterUCConfig.INSTANCE.currentPlayerFaction == null ? "" : BetterUCConfig.INSTANCE.currentPlayerFaction.trim();
        return raw.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
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
                .getModContainer("betteruc")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String colorForType(PingType type) {
        return switch (type == null ? PingType.NORMAL : type) {
            case DANGER -> BetterUCConfig.INSTANCE.pingDangerColor;
            case GATHER -> BetterUCConfig.INSTANCE.pingGatherColor;
            default -> BetterUCConfig.INSTANCE.pingNormalColor;
        };
    }

    private static String cooldownLabel(long remainingMs) {
        if (remainingMs >= 1000L) {
            return String.format(Locale.ROOT, "%.1fs", remainingMs / 1000.0D);
        }
        return remainingMs + "ms";
    }

    private static void playPingSound(MinecraftClient client, PingType type) {
        if (!BetterUCConfig.INSTANCE.pingSoundEnabled || client == null || client.player == null) return;
        float pitch = switch (type == null ? PingType.NORMAL : type) {
            case DANGER -> 0.75F;
            case GATHER -> 1.35F;
            default -> 1.0F;
        };
        client.player.playSound(PingSound.fromId(BetterUCConfig.INSTANCE.pingSoundId).sound(), 0.45F, pitch);
    }

    private static void playPingSelectionSound(MinecraftClient client, PingType type) {
        if (!BetterUCConfig.INSTANCE.pingSoundEnabled || client == null || client.player == null) return;
        float pitch = switch (type == null ? PingType.NORMAL : type) {
            case DANGER -> 0.95F;
            case GATHER -> 1.55F;
            default -> 1.2F;
        };
        client.player.playSound(PingSound.fromId(BetterUCConfig.INSTANCE.pingSoundId).sound(), 0.22F, pitch);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static double doubleValue(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) ? json.get(key).getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject json, String key, long fallback) {
        try {
            return json.has(key) ? json.get(key).getAsLong() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void sendLocalMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[betterUC] " + message), false);
        }
    }

    private static void sendText(MinecraftClient client, Text message) {
        if (client != null && client.player != null && message != null) {
            client.player.sendMessage(message, false);
        }
    }

    public record PingMarker(
            String id,
            String sender,
            String label,
            String pingType,
            String dimension,
            double x,
            double y,
            double z,
            String color,
            long createdAt,
            long expiresAt
    ) {
    }

    private record PingTarget(Vec3d pos, String label) {
    }

    private record RelayPlayer(String nameLower, String uuid, String role, long priority) {
    }

    private record OnlineListEntry(String name, String faction, String version, String role, long priority) {
    }

    public enum PingType {
        NORMAL("normal", "Normal"),
        DANGER("danger", "Gefahr"),
        GATHER("gather", "Sammeln");

        private final String id;
        private final String label;

        PingType(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public static PingType fromId(String id) {
            String cleaned = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            for (PingType type : values()) {
                if (type.id.equals(cleaned)) return type;
            }
            return NORMAL;
        }
    }

    private enum PingSound {
        PLING("pling", "Pling", SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()),
        BELL("bell", "Glocke", SoundEvents.BLOCK_NOTE_BLOCK_BELL.value()),
        CHIME("chime", "Chime", SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value()),
        BIT("bit", "Bit", SoundEvents.BLOCK_NOTE_BLOCK_BIT.value()),
        BANJO("banjo", "Banjo", SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value()),
        COWBELL("cowbell", "Cowbell", SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL.value());

        private final String id;
        private final String label;
        private final SoundEvent sound;

        PingSound(String id, String label, SoundEvent sound) {
            this.id = id;
            this.label = label;
            this.sound = sound;
        }

        private String id() {
            return id;
        }

        private String label() {
            return label;
        }

        private SoundEvent sound() {
            return sound;
        }

        private static PingSound fromId(String id) {
            String cleaned = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            for (PingSound sound : values()) {
                if (sound.id.equals(cleaned)) return sound;
            }
            return PLING;
        }
    }

    private static final class RelayListener implements WebSocket.Listener {
        private final MinecraftClient client;

        private RelayListener(MinecraftClient client) {
            this.client = client;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            connecting = false;
            connected = true;
            status = "Verbunden";
            PingRelayClient.webSocket = webSocket;
            sendHello(webSocket, client);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last && data.length() <= 4096) {
                String raw = data.toString();
                client.execute(() -> handleMessage(client, raw));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            markDisconnected("Getrennt");
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            markDisconnected("Fehler");
            BetterUCMod.LOGGER.debug("betterUC ping relay error", error);
        }
    }
}
