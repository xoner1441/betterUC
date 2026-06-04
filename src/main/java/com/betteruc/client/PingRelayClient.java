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
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
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
    private static final long RECONNECT_DELAY_MS = 5000L;
    private static final double PING_RAYCAST_DISTANCE = 128.0D;
    private static final String FALLBACK_RELAY_URL = "ws://65.109.175.203:3000/ws";

    private static WebSocket webSocket;
    private static boolean connecting = false;
    private static boolean connected = false;
    private static String status = "Nicht verbunden";
    private static String role = "user";
    private static long nextReconnectAtMs = 0L;
    private static long lastPingSentMs = 0L;

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

        if (connected || connecting || System.currentTimeMillis() < nextReconnectAtMs) return;
        connect(client);
    }

    public static void onJoin(MinecraftClient client) {
        synchronized (LOCK) {
            ACTIVE_PINGS.clear();
            ONLINE_PLAYERS.clear();
        }
        nextReconnectAtMs = 0L;
        tick(client);
    }

    public static void onDisconnect() {
        disconnect();
        synchronized (LOCK) {
            ACTIVE_PINGS.clear();
            ONLINE_PLAYERS.clear();
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

    public static String roleNameTagForPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        if (player == null) return "";
        return switch (player.role()) {
            case "admin" -> "Admin";
            case "vip" -> "VIP";
            default -> "User";
        };
    }

    public static boolean isAdminPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        return player != null && "admin".equals(player.role());
    }

    public static boolean isVipPlayer(String name, String uuid) {
        RelayPlayer player = findRelayPlayer(name, uuid);
        return player != null && "vip".equals(player.role());
    }

    public static String currentServerId(MinecraftClient client) {
        if (client == null) return "singleplayer";
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return info.address.trim().toLowerCase(Locale.ROOT);
        }
        if (client.isInSingleplayer()) return "singleplayer";
        return "unknown";
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
                    case "vip" -> "VIP verbunden";
                    default -> "Verbunden";
                };
                return;
            }

            if ("presence".equals(type)) {
                updatePresence(json);
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

            synchronized (LOCK) {
                ACTIVE_PINGS.removeIf(existing -> !existing.id().isEmpty() && existing.id().equals(marker.id()));
                ACTIVE_PINGS.add(marker);
                while (ACTIVE_PINGS.size() > 12) {
                    ACTIVE_PINGS.remove(0);
                }
            }
            playPingSound(client, PingType.fromId(marker.pingType()));
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
        synchronized (LOCK) {
            ONLINE_PLAYERS.clear();
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
        synchronized (LOCK) {
            ONLINE_PLAYERS.clear();
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
        synchronized (LOCK) {
            ONLINE_PLAYERS.clear();
            ONLINE_PLAYERS.addAll(nextPlayers);
        }
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
        if (name == null || name.isBlank()) return null;
        return findRelayPlayer(name, "");
    }

    private static RelayPlayer findRelayPlayer(String name, String uuid) {
        if (name == null || name.isBlank()) return null;
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedUuid = uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
        synchronized (LOCK) {
            for (RelayPlayer player : ONLINE_PLAYERS) {
                boolean uuidMatches = !normalizedUuid.isBlank()
                        && player.uuid() != null
                        && player.uuid().equalsIgnoreCase(normalizedUuid);
                if (uuidMatches || player.nameLower().equals(normalizedName)) {
                    return player;
                }
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (connected && client != null && client.player != null
                && normalizedName.equals(playerName(client).toLowerCase(Locale.ROOT))) {
            return new RelayPlayer(normalizedName, playerUuid(client), cleanRole(role), isAdminSession() ? 100L : 50L);
        }
        return null;
    }

    private static String cleanRole(String value) {
        String cleaned = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(cleaned) || "vip".equals(cleaned)) return cleaned;
        return "user";
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
