package com.betteruc.client;

import com.betteruc.ServerGate;
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
        if (!ServerCommandUtil.ensureAllowedServerForManualCommand(client)) return;

        int drinks = findDrinkCount(client);
        if (drinks <= 0) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7c[betterUC] Keine offenen Getränke im Lieferjunge-Scoreboard gefunden."
            ));
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
        plannedDrops = drinks;
        sentDrops = 0;
        nextDropAtMs = 0L;
        client.player.sendSystemMessage(Component.literal(
                "\u00A7a[betterUC] Auto-Dropdrink gestartet: \u00A7f"
                        + plannedDrops + " Getränke"
        ));
    }

    public static void tick(Minecraft client) {
        if (!running) return;
        if (client == null || client.player == null || client.level == null
                || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        int currentDrinks = findDrinkCount(client);
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

        if (ServerCommandUtil.send(client, "dropdrink", false)) {
            sentDrops++;
            nextDropAtMs = now + DROP_INTERVAL_MS;
        } else {
            reset();
        }
    }

    public static void reset() {
        jobHintSeen = false;
        running = false;
        plannedDrops = 0;
        sentDrops = 0;
        nextDropAtMs = 0L;
    }

    private static void finish(Minecraft client) {
        int total = Math.max(plannedDrops, sentDrops);
        reset();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7a[betterUC] Auto-Dropdrink abgeschlossen: \u00A7f"
                            + total + " Getränke"
            ));
        }
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
        reset();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7c[betterUC] Auto-Dropdrink gestoppt: \u00A7f" + reason
            ));
        }
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
