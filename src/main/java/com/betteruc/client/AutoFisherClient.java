package com.betteruc.client;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public final class AutoFisherClient {
    private static final long COMMAND_DELAY_MS = 250L;
    private static final long COMMAND_COOLDOWN_MS = 3_000L;

    private static long lastFindSwarmAtMs;
    private static long lastCatchFishAtMs;
    private static long lastDropFishAtMs;
    private static boolean awaitingDropFishAtPier;

    private AutoFisherClient() {
    }

    public static void handleChatLine(MinecraftClient client, String raw) {
        String clean = key(raw);
        if (awaitingDropFishAtPier && clean.contains("du hast dein ziel erreicht")) {
            awaitingDropFishAtPier = false;
            sendWithCooldown(client, "dropfish", CommandType.DROP_FISH);
            return;
        }

        if (!clean.contains("fischer")) return;

        if (clean.contains("findschwarm")
                && clean.contains("naechsten")
                && clean.contains("fischschwarm")) {
            sendWithCooldown(client, "findschwarm", CommandType.FIND_SWARM);
            return;
        }

        if (clean.contains("catchfish")
                && clean.contains("netz")
                && clean.contains("wirf")) {
            sendWithCooldown(client, "catchfish", CommandType.CATCH_FISH);
            return;
        }

        if (clean.contains("frischen fisch gefangen")
                || clean.contains("fischernetz verloren")) {
            sendWithCooldown(client, "findschwarm", CommandType.FIND_SWARM);
            return;
        }

        if (clean.contains("keine netze mehr")
                && clean.contains("gefangenen fisch")
                && clean.contains("steg")) {
            awaitingDropFishAtPier = true;
        }
    }

    public static void reset() {
        lastFindSwarmAtMs = 0L;
        lastCatchFishAtMs = 0L;
        lastDropFishAtMs = 0L;
        awaitingDropFishAtPier = false;
    }

    private static void sendWithCooldown(MinecraftClient client, String command, CommandType type) {
        long now = System.currentTimeMillis();
        long lastSentAt = switch (type) {
            case FIND_SWARM -> lastFindSwarmAtMs;
            case CATCH_FISH -> lastCatchFishAtMs;
            case DROP_FISH -> lastDropFishAtMs;
        };
        if (now - lastSentAt < COMMAND_COOLDOWN_MS) return;

        switch (type) {
            case FIND_SWARM -> lastFindSwarmAtMs = now;
            case CATCH_FISH -> lastCatchFishAtMs = now;
            case DROP_FISH -> lastDropFishAtMs = now;
        }

        ClientScheduler.runDelayedOnClient(client, COMMAND_DELAY_MS,
                () -> ServerCommandUtil.send(client, command, false));
    }

    private enum CommandType {
        FIND_SWARM,
        CATCH_FISH,
        DROP_FISH
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
