package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
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
    private static final long RECONNECT_DELAY_MS = 5000L;
    private static final double PING_RAYCAST_DISTANCE = 128.0D;

    private static WebSocket webSocket;
    private static boolean connecting = false;
    private static boolean connected = false;
    private static String status = "Nicht verbunden";
    private static long nextReconnectAtMs = 0L;

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

        if (BetterUCConfig.INSTANCE.pingRelayToken == null || BetterUCConfig.INSTANCE.pingRelayToken.isBlank()) {
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
        }
        nextReconnectAtMs = 0L;
        tick(client);
    }

    public static void onDisconnect() {
        disconnect();
        synchronized (LOCK) {
            ACTIVE_PINGS.clear();
        }
    }

    public static boolean sendPingAtCrosshair(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return false;
        if (!connected || webSocket == null) {
            sendLocalMessage(client, "Ping System ist nicht verbunden (" + status + ").");
            return false;
        }

        PingTarget target = targetFromCrosshair(client);
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "ping");
        payload.addProperty("sender", playerName(client));
        payload.addProperty("server", currentServerId(client));
        payload.addProperty("channel", channel());
        payload.addProperty("dimension", currentDimension(client));
        payload.addProperty("x", target.pos.x);
        payload.addProperty("y", target.pos.y);
        payload.addProperty("z", target.pos.z);
        payload.addProperty("label", target.label);
        payload.addProperty("color", BetterUCConfig.INSTANCE.pingRelayColor);

        try {
            webSocket.sendText(GSON.toJson(payload), true);
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

    public static List<PingMarker> activePings() {
        cleanupExpired();
        synchronized (LOCK) {
            return new ArrayList<>(ACTIVE_PINGS);
        }
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
        URI uri = relayUri(client);
        if (uri == null) {
            status = "Server ungültig";
            nextReconnectAtMs = System.currentTimeMillis() + RECONNECT_DELAY_MS;
            return;
        }

        connecting = true;
        status = "Verbinde...";
        HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .header("X-BetterUC-Token", BetterUCConfig.INSTANCE.pingRelayToken.trim())
                .buildAsync(uri, new RelayListener(client))
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        markDisconnected("Offline");
                        BetterUCMod.LOGGER.debug("betterUC ping relay connection failed", error);
                        return;
                    }

                    webSocket = socket;
                });
    }

    private static URI relayUri(MinecraftClient client) {
        try {
            String raw = BetterUCConfig.INSTANCE.pingRelayUrl.trim();
            if (raw.startsWith("http://")) raw = "ws://" + raw.substring("http://".length());
            if (raw.startsWith("https://")) raw = "wss://" + raw.substring("https://".length());
            if (!raw.startsWith("ws://") && !raw.startsWith("wss://")) raw = "ws://" + raw;
            if (!raw.contains("/ws")) {
                raw = raw.endsWith("/") ? raw + "ws" : raw + "/ws";
            }

            String separator = raw.contains("?") ? "&" : "?";
            String url = raw + separator
                    + "name=" + encode(playerName(client))
                    + "&server=" + encode(currentServerId(client))
                    + "&channel=" + encode(channel());
            return URI.create(url);
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Invalid betterUC ping relay URL", e);
            return null;
        }
    }

    private static void sendHello(WebSocket socket, MinecraftClient client) {
        if (socket == null || client == null || client.player == null) return;
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("name", playerName(client));
        hello.addProperty("server", currentServerId(client));
        hello.addProperty("channel", channel());
        socket.sendText(GSON.toJson(hello), true);
    }

    private static void handleMessage(MinecraftClient client, String raw) {
        try {
            JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
            String type = stringValue(json, "type", "");
            if ("welcome".equals(type) || "hello_ack".equals(type)) {
                connected = true;
                status = "Verbunden";
                return;
            }

            if (!"ping".equals(type)) return;

            PingMarker marker = new PingMarker(
                    stringValue(json, "id", ""),
                    stringValue(json, "sender", "Unbekannt"),
                    stringValue(json, "label", "Ping"),
                    stringValue(json, "dimension", "unknown"),
                    doubleValue(json, "x", 0.0D),
                    doubleValue(json, "y", 0.0D),
                    doubleValue(json, "z", 0.0D),
                    stringValue(json, "color", BetterUCConfig.INSTANCE.pingRelayColor),
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
        status = newStatus;
        nextReconnectAtMs = System.currentTimeMillis() + RECONNECT_DELAY_MS;
    }

    private static String channel() {
        String raw = BetterUCConfig.INSTANCE.pingRelayChannel == null ? "" : BetterUCConfig.INSTANCE.pingRelayChannel.trim();
        return raw.isEmpty() ? "global" : raw.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private static String playerName(MinecraftClient client) {
        if (client == null || client.player == null) return "unknown";
        String name = client.player.getName().getString();
        return name == null || name.isBlank() ? "unknown" : name;
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
