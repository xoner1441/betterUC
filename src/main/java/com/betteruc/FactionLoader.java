package com.betteruc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FactionLoader {

    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FactionLoader");
                t.setDaemon(true);
                return t;
            });

    public static void start() {
        int interval = Math.max(1, BetterUCConfig.INSTANCE.reloadIntervalMinutes);
        SCHEDULER.scheduleAtFixedRate(FactionLoader::fetch, 0, interval, TimeUnit.MINUTES);
        BetterUCMod.LOGGER.info("FactionLoader started, reloading every {} minutes", interval);
    }

    private static void fetch() {
        String urlStr = BetterUCConfig.INSTANCE.factionUrl;
        if (urlStr == null || urlStr.isEmpty() || urlStr.equals("https://example.com/faction.json")) {
            BetterUCMod.LOGGER.warn("No faction URL configured, skipping fetch.");
            return;
        }

        try {
            BetterUCMod.LOGGER.info("Fetching faction list from {}", urlStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "BetterUCMod/1.0");

                if (conn.getResponseCode() != 200) {
                    BetterUCMod.LOGGER.error("Faction URL returned HTTP {}", conn.getResponseCode());
                    return;
                }

                // Parse JSON - supports two formats:
                // 1. Simple array: ["Player1", "Player2"]
                // 2. Object with "members" key: {"members": ["Player1", "Player2"]}
                JsonElement root;
                try (InputStream stream = conn.getInputStream();
                     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    root = GSON.fromJson(reader, JsonElement.class);
                }

                List<String> players = new ArrayList<>();

                if (root.isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray()) {
                        if (el.isJsonPrimitive()) players.add(el.getAsString());
                    }
                } else if (root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    // Try common keys: members, players, faction, list
                    for (String key : new String[]{"members", "players", "faction", "list", "data"}) {
                        if (obj.has(key) && obj.get(key).isJsonArray()) {
                            JsonArray arr = obj.getAsJsonArray(key);
                            for (JsonElement el : arr) {
                                if (el.isJsonPrimitive()) {
                                    players.add(el.getAsString());
                                } else if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                                    players.add(el.getAsJsonObject().get("name").getAsString());
                                }
                            }
                            break;
                        }
                    }
                }

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        BetterUCConfig.setRemoteMembersForFaction("kartell", players);
                        BetterUCMod.LOGGER.info("Loaded {} faction members from URL", players.size());
                    });
                } else {
                    BetterUCMod.LOGGER.warn("Minecraft client unavailable, skipping remote faction apply");
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            BetterUCMod.LOGGER.error("Failed to fetch faction list", e);
        }
    }

}
