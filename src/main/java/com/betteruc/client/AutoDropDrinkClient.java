package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Locale;
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

public final class AutoDropDrinkClient {
    private static final long DROP_INTERVAL_MS = 2500L;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private static boolean jobHintSeen;
    private static boolean running;
    private static int plannedDrops;
    private static int sentDrops;
    private static long nextDropAtMs;
    private static boolean dropAreaVisitTriggered;
    private static boolean automaticRun;

    private AutoDropDrinkClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        String clean = key(raw);
        if (clean.contains("lieferant")
                && clean.contains("weinflaschen")
                && clean.contains("dropdrink")) {
            jobHintSeen = true;
        }
        if (running && clean.contains("du bist nicht in der kneipe")) {
            stopAfterServerError(client, "nicht in der Kneipe");
        }
    }

    public static void start(Minecraft client) {
        if (client == null || client.player == null) return;
        if (!AutomationController.isDropDrinkEnabled()) {
            AutomationController.sendDropDrinkDisabledMessage(client);
            return;
        }
        if (!ServerCommandUtil.ensureAllowedServerForManualCommand(client)) return;

        int drinks = findDrinkCount(client);
        startWithDrinkCount(client, drinks, false);
    }

    private static void startWithDrinkCount(Minecraft client, int drinks, boolean automatic) {
        if (drinks <= 0) {
            if (!automatic) {
                client.player.sendSystemMessage(Component.literal(
                        "\u00A7c[betterUC] Keine offenen Getränke im Lieferjunge-Scoreboard gefunden."
                ));
            }
            return;
        }

        if (running) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7e[betterUC] Auto-Dropdrink läuft bereits: \u00A7f"
                            + sentDrops + "/" + plannedDrops
            ));
            return;
        }

        running = true;
        automaticRun = automatic;
        plannedDrops = drinks;
        sentDrops = 0;
        nextDropAtMs = 0L;
        client.player.sendSystemMessage(Component.literal(
                (automatic
                        ? "\u00A7a[betterUC] Abgabebereich erkannt – Auto-Dropdrink gestartet: \u00A7f"
                        : "\u00A7a[betterUC] Auto-Dropdrink gestartet: \u00A7f")
                        + plannedDrops + " Getränke"
        ));
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null
                || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }
        if (!AutomationController.isDropDrinkEnabled()) {
            reset();
            return;
        }

        int detectedDrinks = findDrinkCount(client);
        boolean insideDropArea = isInsideDropArea(client);
        if (!insideDropArea) {
            dropAreaVisitTriggered = false;
        } else if (running) {
            // A manually started run inside the area also consumes this visit,
            // preventing a second automatic run while the scoreboard catches up.
            dropAreaVisitTriggered = true;
        }
        boolean freshEntry = shouldAutoStartInArea(
                insideDropArea,
                dropAreaVisitTriggered,
                running,
                detectedDrinks
        );
        if (freshEntry) {
            dropAreaVisitTriggered = true;
            startWithDrinkCount(client, detectedDrinks, true);
        }
        if (!running) return;

        int currentDrinks = detectedDrinks;
        if (currentDrinks <= 0) {
            finish(client);
            return;
        }

        if (sentDrops >= plannedDrops) {
            finish(client);
            return;
        }

        long now = System.currentTimeMillis();
        if (nextDropAtMs > now) return;
        if (automaticRun && !ServerCommandUtil.isAutomaticSendReady(client)) return;

        boolean sent = automaticRun
                ? ServerCommandUtil.sendAutomatic(client, "dropdrink")
                : ServerCommandUtil.send(client, "dropdrink", false);
        if (sent) {
            sentDrops++;
            nextDropAtMs = now + DROP_INTERVAL_MS;
        } else {
            resetRunState();
        }
    }

    public static void reset() {
        resetRunState();
        dropAreaVisitTriggered = false;
    }

    private static void resetRunState() {
        jobHintSeen = false;
        running = false;
        automaticRun = false;
        plannedDrops = 0;
        sentDrops = 0;
        nextDropAtMs = 0L;
    }

    private static void finish(Minecraft client) {
        int total = Math.max(plannedDrops, sentDrops);
        resetRunState();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-Dropdrink abgeschlossen: \u00A7f"
                            + total + " Getränke"
            ));
        }
    }

    public static int configureArea(Minecraft client, String actionInput) {
        if (client == null || client.player == null || client.level == null) return 0;
        if (!ServerCommandUtil.ensureAllowedServerForManualCommand(client)) return 0;
        if (!PingRelayClient.isConnected()) {
            sendMessage(client, "\u00A7c[betterUC] Keine Relay-Verbindung. Der globale Bereich wurde nicht geändert.");
            return 0;
        }
        if (!PingRelayClient.isAdminSession()) {
            sendMessage(client, "\u00A7c[betterUC] Nur betterUC-Admins dürfen den globalen Abgabebereich ändern.");
            return 0;
        }

        String action = key(actionInput).replace(" ", "");
        if (action.equals("clear") || action.equals("loeschen")) {
            return PingRelayClient.sendDropDrinkAreaUpdate("clear", 0, 0, 0, "") ? 1 : 0;
        }
        if (!action.equals("pos1") && !action.equals("pos2")) {
            sendMessage(client, "\u00A7c[betterUC] Aktion muss pos1, pos2 oder clear sein.");
            return 0;
        }

        return PingRelayClient.sendDropDrinkAreaUpdate(
                action,
                (int) Math.floor(client.player.getX()),
                (int) Math.floor(client.player.getY()),
                (int) Math.floor(client.player.getZ()),
                PingRelayClient.currentDimension(client)
        ) ? 1 : 0;
    }

    public static void applyGlobalAreas(JsonObject areas) {
        BetterUCConfig.INSTANCE.dropDrinkArea = parseGlobalArea(areas);
        dropAreaVisitTriggered = false;
    }

    static BetterUCConfig.WasteDropArea parseGlobalArea(JsonObject areas) {
        BetterUCConfig.WasteDropArea area = new BetterUCConfig.WasteDropArea();
        JsonElement element = areas == null ? null : areas.get("dropdrink");
        if (element != null && element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            area.dimension = jsonString(json, "dimension");
            if (hasNumber(json, "x1") && hasNumber(json, "y1") && hasNumber(json, "z1")) {
                area.x1 = json.get("x1").getAsInt();
                area.y1 = json.get("y1").getAsInt();
                area.z1 = json.get("z1").getAsInt();
                area.pos1Set = true;
            }
            if (hasNumber(json, "x2") && hasNumber(json, "y2") && hasNumber(json, "z2")) {
                area.x2 = json.get("x2").getAsInt();
                area.y2 = json.get("y2").getAsInt();
                area.z2 = json.get("z2").getAsInt();
                area.pos2Set = true;
            }
        }
        return area;
    }

    public static int showAreaStatus(Minecraft client) {
        if (client == null || client.player == null) return 0;
        BetterUCConfig.WasteDropArea area = BetterUCConfig.INSTANCE.dropDrinkArea;
        if (area == null || !area.isComplete()) {
            String missing = area == null || (!area.pos1Set && !area.pos2Set)
                    ? "POS1 und POS2 fehlen"
                    : !area.pos1Set ? "POS1 fehlt" : "POS2 fehlt";
            sendMessage(client, "\u00A7b[betterUC] Globaler Dropdrink-Bereich: \u00A7c" + missing);
            return 1;
        }
        sendMessage(client, "\u00A7b[betterUC] Globaler Dropdrink-Bereich: \u00A7aX "
                + Math.min(area.x1, area.x2) + ".." + Math.max(area.x1, area.x2)
                + " | Y " + Math.min(area.y1, area.y2) + ".." + Math.max(area.y1, area.y2)
                + " | Z " + Math.min(area.z1, area.z2) + ".." + Math.max(area.z1, area.z2)
                + " | " + area.dimension);
        return 1;
    }

    static boolean shouldAutoStartInArea(
            boolean inside,
            boolean alreadyTriggeredThisVisit,
            boolean currentlyRunning,
            int drinks
    ) {
        return inside && !alreadyTriggeredThisVisit && !currentlyRunning && drinks > 0;
    }

    private static boolean isInsideDropArea(Minecraft client) {
        BetterUCConfig.WasteDropArea area = BetterUCConfig.INSTANCE.dropDrinkArea;
        if (area == null || !area.isComplete()) return false;
        return area.contains(
                (int) Math.floor(client.player.getX()),
                (int) Math.floor(client.player.getY()),
                (int) Math.floor(client.player.getZ()),
                PingRelayClient.currentDimension(client)
        );
    }

    private static int findDrinkCount(Minecraft client) {
        if (client == null || client.level == null) return -1;

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        String title = key(objective.getDisplayName().getString());
        boolean deliverySidebar = title.contains("lieferjunge");
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
        int found = -1;

        for (PlayerScoreEntry entry : entries) {
            if (entry == null || entry.isHidden()) continue;

            String display = entry.display() == null ? "" : key(entry.display().getString());
            String name = key(entry.ownerName().getString());
            String owner = key(entry.owner());
            if (!display.contains("getraenke") && !name.contains("getraenke") && !owner.contains("getraenke")) {
                continue;
            }

            int parsed = parseDrinkLine(display);
            if (parsed < 0) parsed = parseDrinkLine(name);
            if (parsed < 0) parsed = parseDrinkLine(owner);
            if (parsed < 0) parsed = parseFirstNumber(key(entry.formatValue(numberFormat).getString()));
            found = parsed >= 0 ? parsed : entry.value();
            break;
        }

        if (found >= 0 && (deliverySidebar || jobHintSeen)) {
            return found;
        }
        return deliverySidebar ? found : -1;
    }

    private static int parseFirstNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int parseDrinkLine(String value) {
        String normalized = value == null ? "" : value;
        if (!normalized.contains("getraenke")) return -1;
        return parseFirstNumber(normalized.substring(normalized.indexOf("getraenke")));
    }

    private static void stopAfterServerError(Minecraft client, String reason) {
        resetRunState();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7c[betterUC] Auto-Dropdrink gestoppt: \u00A7f" + reason
            ));
        }
    }

    private static void sendMessage(Minecraft client, String text) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }

    private static boolean hasNumber(JsonObject json, String name) {
        JsonElement value = json == null ? null : json.get(name);
        return value != null && !value.isJsonNull()
                && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static String jsonString(JsonObject json, String name) {
        JsonElement value = json == null ? null : json.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String key(String value) {
        return value == null ? "" : value
                .replaceAll("\u00A7.", "")
                .toLowerCase(Locale.ROOT)
                .replace("\u00E4", "ae")
                .replace("\u00F6", "oe")
                .replace("\u00FC", "ue")
                .replace("\u00DF", "ss")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
