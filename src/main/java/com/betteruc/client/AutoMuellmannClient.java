package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class AutoMuellmannClient {
    private static final long DROP_INTERVAL_MS = 5000L;
    private static final long SCOREBOARD_REFRESH_MS = 250L;
    private static final Pattern START_PATTERN = Pattern.compile(
            "\\bentleere\\s+bis\\s+zu\\s+(\\d+)\\s+muelltonnen\\b"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private static final EnumMap<WasteType, Integer> remaining = new EnumMap<>(WasteType.class);
    private static boolean jobActive;
    private static boolean sortingActive;
    private static boolean countsCaptured;
    private static int targetBins;
    private static long nextDropAtMs;
    private static long nextScoreboardReadAtMs;

    private AutoMuellmannClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        String clean = key(raw);
        Matcher startMatcher = START_PATTERN.matcher(clean);
        if (clean.contains("muellmann") && startMatcher.find()) {
            reset();
            jobActive = true;
            targetBins = parseNumber(startMatcher.group(1));
            return;
        }

        if (!clean.contains("muellmann")) return;
        if (clean.contains("du hast genug muelltonnen entleert")
                || (clean.contains("bring nun den muell zurueck zur muellhalde")
                && clean.contains("sortier den muell"))) {
            activateSorting(client);
        }
    }

    public static void tick(Minecraft client) {
        if (!sortingActive) return;
        if (client == null || client.player == null || client.level == null
                || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= nextScoreboardReadAtMs) {
            WasteCounts scoreboardCounts = readWasteCounts(client);
            nextScoreboardReadAtMs = now + SCOREBOARD_REFRESH_MS;
            if (scoreboardCounts.complete()) {
                if (!countsCaptured) {
                    remaining.clear();
                    for (WasteType type : WasteType.values()) {
                        remaining.put(type, scoreboardCounts.get(type));
                    }
                    countsCaptured = true;
                    sendMessage(client, "\u00A7a[betterUC] M\u00FCll erkannt: \u00A7f" + countsSummary());
                    if (hasMissingArea()) {
                        sendMessage(client, "\u00A7e[betterUC] Mindestens ein Bereich fehlt. \u00A77Pr\u00FCfe /muellarea status.");
                    }
                } else {
                    for (WasteType type : WasteType.values()) {
                        remaining.compute(type, (ignored, oldValue) -> oldValue == null
                                ? scoreboardCounts.get(type)
                                : Math.min(oldValue, scoreboardCounts.get(type)));
                    }
                }
            }
        }

        if (!countsCaptured) return;
        if (remaining.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            finish(client);
            return;
        }

        WasteType currentArea = currentArea(client);
        if (currentArea == null || remaining.getOrDefault(currentArea, 0) <= 0 || now < nextDropAtMs) return;

        if (ServerCommandUtil.sendAutomatic(client, "dropwaste")) {
            int left = Math.max(0, remaining.getOrDefault(currentArea, 0) - 1);
            remaining.put(currentArea, left);
            nextDropAtMs = now + DROP_INTERVAL_MS;
            if (left == 0) {
                sendMessage(client, "\u00A7a[betterUC] " + currentArea.label + " vollständig abgegeben.");
            }
            if (remaining.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
                finish(client);
            }
        }
    }

    public static int configureArea(Minecraft client, String typeInput, String actionInput) {
        if (client == null || client.player == null || client.level == null) return 0;
        if (!ServerCommandUtil.ensureAllowedServerForManualCommand(client)) return 0;
        if (!PingRelayClient.isConnected()) {
            sendMessage(client, "\u00A7c[betterUC] Keine Relay-Verbindung. Der globale Bereich wurde nicht geändert.");
            return 0;
        }
        if (!PingRelayClient.isAdminSession()) {
            sendMessage(client, "\u00A7c[betterUC] Nur betterUC-Admins dürfen globale Müllbereiche ändern.");
            return 0;
        }

        WasteType type = WasteType.fromInput(typeInput);
        if (type == null) {
            sendMessage(client, "\u00A7c[betterUC] Unbekannte Sorte. \u00A77Nutze glas, metall, abfall oder holz.");
            return 0;
        }

        String action = key(actionInput).replace(" ", "");
        if (action.equals("clear") || action.equals("loeschen")) {
            return PingRelayClient.sendWasteAreaUpdate(type.key, "clear", 0, 0, "") ? 1 : 0;
        }
        if (!action.equals("pos1") && !action.equals("pos2")) {
            sendMessage(client, "\u00A7c[betterUC] Aktion muss pos1, pos2 oder clear sein.");
            return 0;
        }

        String dimension = PingRelayClient.currentDimension(client);
        int x = (int) Math.floor(client.player.getX());
        int z = (int) Math.floor(client.player.getZ());
        return PingRelayClient.sendWasteAreaUpdate(type.key, action, x, z, dimension) ? 1 : 0;
    }

    public static void applyGlobalAreas(JsonObject areas) {
        Map<String, BetterUCConfig.WasteDropArea> updated = new LinkedHashMap<>();
        if (areas != null) {
            for (WasteType type : WasteType.values()) {
                JsonElement element = areas.get(type.key);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject json = element.getAsJsonObject();
                BetterUCConfig.WasteDropArea area = new BetterUCConfig.WasteDropArea();
                area.dimension = jsonString(json, "dimension");
                if (hasNumber(json, "x1") && hasNumber(json, "z1")) {
                    area.x1 = json.get("x1").getAsInt();
                    area.z1 = json.get("z1").getAsInt();
                    area.pos1Set = true;
                }
                if (hasNumber(json, "x2") && hasNumber(json, "z2")) {
                    area.x2 = json.get("x2").getAsInt();
                    area.z2 = json.get("z2").getAsInt();
                    area.pos2Set = true;
                }
                updated.put(type.key, area);
            }
        }
        BetterUCConfig.INSTANCE.wasteDropAreas = updated;
    }

    public static int showAreaStatus(Minecraft client) {
        if (client == null || client.player == null) return 0;
        sendMessage(client, "\u00A7b[betterUC] M\u00FCllmann-Bereiche:");
        for (WasteType type : WasteType.values()) {
            BetterUCConfig.WasteDropArea area = BetterUCConfig.INSTANCE.wasteDropAreas.get(type.key);
            if (area == null || !area.isComplete()) {
                String missing = area == null ? "POS1 und POS2 fehlen"
                        : !area.pos1Set ? "POS1 fehlt" : "POS2 fehlt";
                sendMessage(client, "\u00A77- " + type.label + ": \u00A7c" + missing);
            } else {
                sendMessage(client, "\u00A77- " + type.label + ": \u00A7aX "
                        + Math.min(area.x1, area.x2) + ".." + Math.max(area.x1, area.x2)
                        + " | Z " + Math.min(area.z1, area.z2) + ".." + Math.max(area.z1, area.z2));
            }
        }
        return 1;
    }

    public static void reset() {
        jobActive = false;
        sortingActive = false;
        countsCaptured = false;
        targetBins = 0;
        remaining.clear();
        nextDropAtMs = 0L;
        nextScoreboardReadAtMs = 0L;
    }

    private static void activateSorting(Minecraft client) {
        if (sortingActive) return;
        jobActive = true;
        sortingActive = true;
        countsCaptured = false;
        remaining.clear();
        nextScoreboardReadAtMs = 0L;
        nextDropAtMs = 0L;
        sendMessage(client, "\u00A7b[betterUC] M\u00FCllsortierung aktiv. \u00A77Fahre in einen markierten Bereich.");
    }

    private static boolean hasMissingArea() {
        for (WasteType type : WasteType.values()) {
            BetterUCConfig.WasteDropArea area = BetterUCConfig.INSTANCE.wasteDropAreas.get(type.key);
            if (area == null || !area.isComplete()) return true;
        }
        return false;
    }

    private static WasteType currentArea(Minecraft client) {
        int x = (int) Math.floor(client.player.getX());
        int z = (int) Math.floor(client.player.getZ());
        String dimension = PingRelayClient.currentDimension(client);
        for (WasteType type : WasteType.values()) {
            BetterUCConfig.WasteDropArea area = BetterUCConfig.INSTANCE.wasteDropAreas.get(type.key);
            if (area != null && area.contains(x, z, dimension)) return type;
        }
        return null;
    }

    private static WasteCounts readWasteCounts(Minecraft client) {
        WasteCounts result = new WasteCounts();
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null || !key(objective.getDisplayName().getString()).contains("muellmann")) return result;

        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
        for (PlayerScoreEntry entry : entries) {
            if (entry == null || entry.isHidden()) continue;

            String display = entry.display() == null ? "" : key(entry.display().getString());
            String name = key(entry.ownerName().getString());
            String owner = key(entry.owner());
            for (WasteType type : WasteType.values()) {
                if (result.has(type)) continue;
                String matchingLine = matchingLine(type, display, name, owner);
                if (matchingLine == null) continue;

                int parsed = parseAfterLabel(matchingLine, type.key);
                if (parsed < 0) parsed = parseFirstNumber(key(entry.formatValue(numberFormat).getString()));
                if (parsed < 0) parsed = Math.max(0, entry.value());
                result.put(type, parsed);
            }
        }
        return result;
    }

    private static String matchingLine(WasteType type, String... values) {
        for (String value : values) {
            if (value != null && value.matches(".*\\b" + Pattern.quote(type.key) + "\\b.*")) return value;
        }
        return null;
    }

    private static int parseAfterLabel(String value, String label) {
        int index = value.indexOf(label);
        if (index < 0) return -1;
        return parseFirstNumber(value.substring(index + label.length()));
    }

    private static int parseFirstNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
        if (!matcher.find()) return -1;
        return parseNumber(matcher.group(1));
    }

    private static int parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String countsSummary() {
        StringBuilder summary = new StringBuilder();
        for (WasteType type : WasteType.values()) {
            if (!summary.isEmpty()) summary.append(" | ");
            summary.append(type.label).append(' ').append(remaining.getOrDefault(type, 0));
        }
        return summary.toString();
    }

    private static void finish(Minecraft client) {
        int bins = targetBins;
        reset();
        sendMessage(client, "\u00A7a[betterUC] M\u00FCllsortierung abgeschlossen."
                + (bins > 0 ? " \u00A77M\u00FClltonnen: " + bins : ""));
    }

    private static void sendMessage(Minecraft client, String text) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }

    private static boolean hasNumber(JsonObject json, String name) {
        JsonElement value = json == null ? null : json.get(name);
        return value != null && !value.isJsonNull() && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static String jsonString(JsonObject json, String name) {
        JsonElement value = json == null ? null : json.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String key(String value) {
        return value == null ? "" : value
                .replaceAll("\\u00A7.", "")
                .toLowerCase(Locale.ROOT)
                .replace("\u00E4", "ae")
                .replace("\u00F6", "oe")
                .replace("\u00FC", "ue")
                .replace("\u00DF", "ss")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    public enum WasteType {
        GLAS("glas", "Glas"),
        METALL("metall", "Metall"),
        ABFALL("abfall", "Abfall"),
        HOLZ("holz", "Holz");

        private final String key;
        private final String label;

        WasteType(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private static WasteType fromInput(String value) {
            String normalized = key(value).replace(" ", "");
            for (WasteType type : values()) {
                if (type.key.equals(normalized)) return type;
            }
            return null;
        }
    }

    private static final class WasteCounts {
        private final Map<WasteType, Integer> values = new EnumMap<>(WasteType.class);

        private void put(WasteType type, int value) {
            values.put(type, Math.max(0, value));
        }

        private boolean has(WasteType type) {
            return values.containsKey(type);
        }

        private int get(WasteType type) {
            return values.getOrDefault(type, 0);
        }

        private boolean complete() {
            return values.size() == WasteType.values().length;
        }
    }
}
