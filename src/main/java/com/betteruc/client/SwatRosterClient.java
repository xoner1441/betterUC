package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.ServerGate;
import com.betteruc.parser.SwatRosterParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SwatRosterClient {
    private static final String OWNER_NAME = "FABI1441";
    private static final int JOIN_DELAY_TICKS = 320;
    private static final long CAPTURE_TIMEOUT_MS = 7_000L;

    private static final Map<String, SwatRosterParser.Member> capturedMembers = new LinkedHashMap<>();
    private static int requestDelayTicks = -1;
    private static int expectedMemberCount;
    private static int slotLimit = 13;
    private static long captureDeadlineMs;
    private static boolean awaitingHeader;
    private static boolean capturing;
    private static JsonObject pendingUpload;

    private SwatRosterClient() {
    }

    public static void onJoin(Minecraft client) {
        reset();
        if (isOwner(client) && ServerGate.isAllowedServer(client)) {
            requestDelayTicks = JOIN_DELAY_TICKS;
        }
    }

    public static void reset() {
        requestDelayTicks = -1;
        expectedMemberCount = 0;
        slotLimit = 13;
        captureDeadlineMs = 0L;
        awaitingHeader = false;
        capturing = false;
        capturedMembers.clear();
        pendingUpload = null;
    }

    public static void tick(Minecraft client) {
        if (!isOwner(client) || !ServerGate.isAllowedServer(client)) return;

        if ((awaitingHeader || capturing) && System.currentTimeMillis() > captureDeadlineMs) {
            if (capturing && !capturedMembers.isEmpty()) finishCapture();
            awaitingHeader = false;
            capturing = false;
        }

        if (pendingUpload != null && UserPanelClient.uploadSwatRoster(client, pendingUpload)) {
            pendingUpload = null;
        }

        if (requestDelayTicks < 0) return;
        if (requestDelayTicks > 0) {
            requestDelayTicks--;
            return;
        }
        if (!BetterUCAuthClient.usesAutomaticSession() || !ServerCommandUtil.isAutomaticSendReady(client)) return;
        if (ServerCommandUtil.sendAutomatic(client, "sfinfoall SWAT")) {
            awaitingHeader = true;
            captureDeadlineMs = System.currentTimeMillis() + CAPTURE_TIMEOUT_MS;
            requestDelayTicks = -1;
            BetterUCMod.LOGGER.info("Requested silent SWAT roster refresh");
        }
    }

    public static boolean handleChatLine(Minecraft client, String raw) {
        if (!isOwner(client) || (!awaitingHeader && !capturing)) return false;

        SwatRosterParser.Header header = SwatRosterParser.parseHeader(raw);
        if (header != null && awaitingHeader) {
            expectedMemberCount = header.memberCount();
            slotLimit = Math.max(1, header.slotLimit());
            capturedMembers.clear();
            awaitingHeader = false;
            capturing = true;
            captureDeadlineMs = System.currentTimeMillis() + CAPTURE_TIMEOUT_MS;
            if (expectedMemberCount == 0) finishCapture();
            return true;
        }

        if (!capturing) return false;
        List<SwatRosterParser.Member> members = SwatRosterParser.parseMemberRow(raw);
        if (members.isEmpty()) return false;
        for (SwatRosterParser.Member member : members) {
            String key = member.username().toLowerCase(Locale.ROOT);
            SwatRosterParser.Member previous = capturedMembers.get(key);
            if (previous == null || rolePriority(member.role()) > rolePriority(previous.role())) {
                capturedMembers.put(key, member);
            }
        }
        captureDeadlineMs = System.currentTimeMillis() + CAPTURE_TIMEOUT_MS;
        if (capturedMembers.size() >= expectedMemberCount) finishCapture();
        return true;
    }

    private static void finishCapture() {
        JsonArray members = new JsonArray();
        for (SwatRosterParser.Member member : capturedMembers.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("username", member.username());
            entry.addProperty("factionRank", member.factionRank());
            entry.addProperty("role", member.role());
            members.add(entry);
        }
        JsonObject roster = new JsonObject();
        roster.addProperty("slotLimit", slotLimit);
        roster.add("members", members);
        pendingUpload = roster;
        awaitingHeader = false;
        capturing = false;
        BetterUCMod.LOGGER.info("Captured {} SWAT members for roster upload", capturedMembers.size());
    }

    private static int rolePriority(String role) {
        return switch (role == null ? "" : role.toLowerCase(Locale.ROOT)) {
            case "leader" -> 2;
            case "supervisor" -> 1;
            default -> 0;
        };
    }

    private static boolean isOwner(Minecraft client) {
        if (client == null || client.getUser() == null) return false;
        String name = client.getUser().getName();
        return name != null && OWNER_NAME.equalsIgnoreCase(name.trim());
    }
}
