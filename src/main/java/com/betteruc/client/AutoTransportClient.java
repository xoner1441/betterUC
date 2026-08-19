package com.betteruc.client;

import com.betteruc.ServerGate;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class AutoTransportClient {
    private static final String START_MESSAGE = "transport waehle jetzt dein lieferziel";
    private static final String TARGET_PREFIX = "transport ziel gesetzt ";
    private static final String ARRIVAL_MESSAGE = "navi hier kannst du abliefern droptransport";
    private static final long JOB_TIMEOUT_MS = 30L * 60L * 1_000L;
    private static final long SCOREBOARD_WAIT_MS = 10_000L;
    private static final long DROP_INTERVAL_MS = 10_000L;
    private static final long DROP_CONFIRMATION_TIMEOUT_MS = 15_000L;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private static boolean jobHintSeen;
    private static boolean targetSelected;
    private static boolean awaitingScoreboard;
    private static boolean running;
    private static int observedCrates;
    private static boolean awaitingCrateDecrease;
    private static long jobExpiresAtMs;
    private static long scoreboardWaitUntilMs;
    private static long nextDropAtMs;
    private static long dropConfirmationUntilMs;

    private AutoTransportClient() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (!AutomationController.isTransportEnabled()) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        expireJob(now);
        String clean = key(raw);

        if (START_MESSAGE.equals(clean)) {
            reset();
            jobHintSeen = true;
            jobExpiresAtMs = now + JOB_TIMEOUT_MS;
            return;
        }

        if (jobHintSeen && clean.startsWith(TARGET_PREFIX) && clean.length() > TARGET_PREFIX.length()) {
            targetSelected = true;
            jobExpiresAtMs = now + JOB_TIMEOUT_MS;
            return;
        }

        if (targetSelected && ARRIVAL_MESSAGE.equals(clean)) {
            awaitingScoreboard = true;
            scoreboardWaitUntilMs = now + SCOREBOARD_WAIT_MS;
            tick(client);
            return;
        }

        if (running && (clean.contains("transport du hast deine letzte kiste")
                || clean.contains("transport auto drop gestartet")
                || clean.contains("transport auto drop beendet"))) {
            reset();
        }
    }

    public static void tick(Minecraft client) {
        if (!AutomationController.isTransportEnabled()) {
            reset();
            return;
        }
        if (client == null || client.player == null || client.level == null
                || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        expireJob(now);

        if (awaitingScoreboard) {
            int crates = findCrateCount(client);
            if (crates > 0) {
                awaitingScoreboard = false;
                running = true;
                observedCrates = crates;
                awaitingCrateDecrease = false;
                nextDropAtMs = 0L;
            } else if (crates == 0 || now > scoreboardWaitUntilMs) {
                reset();
                return;
            } else {
                return;
            }
        }

        if (!running) return;

        int currentCrates = findCrateCount(client);
        if (currentCrates == 0) {
            reset();
            return;
        }
        if (currentCrates < 0) return;

        if (awaitingCrateDecrease) {
            if (currentCrates < observedCrates) {
                observedCrates = currentCrates;
                awaitingCrateDecrease = false;
                dropConfirmationUntilMs = 0L;
                nextDropAtMs = now + DROP_INTERVAL_MS;
            } else if (now <= dropConfirmationUntilMs) {
                return;
            } else {
                awaitingCrateDecrease = false;
                dropConfirmationUntilMs = 0L;
                nextDropAtMs = now;
            }
        } else {
            observedCrates = currentCrates;
        }
        if (now < nextDropAtMs) return;

        if (ServerCommandUtil.sendAutomatic(client, "droptransport")) {
            awaitingCrateDecrease = true;
            observedCrates = currentCrates;
            dropConfirmationUntilMs = now + DROP_CONFIRMATION_TIMEOUT_MS;
        }
    }

    public static void reset() {
        jobHintSeen = false;
        targetSelected = false;
        awaitingScoreboard = false;
        running = false;
        observedCrates = 0;
        awaitingCrateDecrease = false;
        jobExpiresAtMs = 0L;
        scoreboardWaitUntilMs = 0L;
        nextDropAtMs = 0L;
        dropConfirmationUntilMs = 0L;
    }

    static boolean isStartMessage(String raw) {
        return START_MESSAGE.equals(key(raw));
    }

    static boolean isTargetMessage(String raw) {
        String clean = key(raw);
        return clean.startsWith(TARGET_PREFIX) && clean.length() > TARGET_PREFIX.length();
    }

    static boolean isArrivalMessage(String raw) {
        return ARRIVAL_MESSAGE.equals(key(raw));
    }

    static int parseCrateLine(String raw) {
        String clean = key(raw);
        if (!containsCrateLabel(clean)) return -1;
        Matcher matcher = NUMBER_PATTERN.matcher(clean);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int findCrateCount(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return -1;

        String title = key(objective.getDisplayName().getString());
        boolean transportSidebar = title.contains("transport");
        if (!transportSidebar && !jobHintSeen) return -1;

        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
        for (PlayerScoreEntry entry : entries) {
            if (entry == null || entry.isHidden()) continue;

            String display = entry.display() == null ? "" : entry.display().getString();
            String name = entry.ownerName().getString();
            String owner = entry.owner();
            if (!containsCrateLabel(key(display))
                    && !containsCrateLabel(key(name))
                    && !containsCrateLabel(key(owner))) {
                continue;
            }

            int parsed = parseCrateLine(display);
            if (parsed < 0) parsed = parseCrateLine(name);
            if (parsed < 0) parsed = parseCrateLine(owner);
            if (parsed < 0) {
                parsed = parseFirstNumber(entry.formatValue(numberFormat).getString());
            }
            return parsed >= 0 ? parsed : Math.max(0, entry.value());
        }
        return -1;
    }

    private static boolean containsCrateLabel(String value) {
        return value != null && (value.contains("kiste") || value.contains("kisten"));
    }

    private static int parseFirstNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(key(value));
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void expireJob(long now) {
        if (jobExpiresAtMs > 0L && now > jobExpiresAtMs) {
            reset();
        }
        if (awaitingScoreboard && now > scoreboardWaitUntilMs) {
            reset();
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
