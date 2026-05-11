package com.betteruc.mixin;

import com.betteruc.BetterUCMod;
import com.betteruc.BetterUCSuppressFlags;
import com.betteruc.ServerGate;
import com.betteruc.client.AutoCarController;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.CookDrugHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.PaydayHud;
import com.betteruc.hud.PlantageHud;
import com.betteruc.parser.BlacklistParser;
import com.betteruc.parser.MemberInfoParser;
import com.betteruc.parser.StatsLineClassifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatBlacklistMixin {

    private static final Pattern MONEY_PATTERN = Pattern.compile("-\\s*Geld:\\s*([0-9.]+)\\$");
    private static final Pattern BLACK_MONEY_PATTERN = Pattern.compile("-\\s*Schwarzgeld:\\s*([0-9.]+)\\$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYDAY_PATTERN = Pattern.compile(
            "Zeit seit PayDay:\\s*(\\d+)\\s*/\\s*(\\d+)\\s*Minuten",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAYDAY_HEADER_PATTERN = Pattern.compile(
            "^\\s*[-=]+\\s*PayDay\\s*[-=]+\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HACK_TIMER_PATTERN =
            Pattern.compile("\\[Polizeicomputer\\].*Gesch(?:aetzte|\\u00E4tzte) Dauer: (\\d+) Sekunden");
    private static final String COOKDRUG_START_KEY = "das pseudoephedrin aus der medizin muss nun einige minuten kochen";
    private static final String COOKDRUG_DONE_KEY = "die kristalle sind fertig gekocht";

    private static boolean capturingMembers = false;
    private static boolean capturingBlacklist = false;
    private static boolean capturingStats = false;
    private static boolean activeMemberCaptureIsSilent = false;
    private static boolean activeBlacklistCaptureIsSilent = false;
    private static boolean activeStatsCaptureIsSilent = false;
    private static boolean addingTimestamp = false;
    private static String currentMemberFactionQuery = "kartell";
    private static int expectedMemberEntries = -1;

    private static long lastRealtimeSyncMs = 0L;
    private static final long REALTIME_SYNC_COOLDOWN_MS = 1200L;
    private static boolean pendingRealtimeBlacklistSyncAfterAfk = false;
    private static long lastAfkExitStatsRefreshMs = 0L;
    private static final long AFK_EXIT_STATS_REFRESH_COOLDOWN_MS = 3000L;
    private static long forceHideStatsLinesUntilMs = 0L;
    private static final long FORCE_HIDE_STATS_WINDOW_MS = 8000L;
    private static long forceHideDashStatsLinesUntilMs = 0L;
    private static final long FORCE_HIDE_DASH_STATS_WINDOW_MS = 12000L;
    private static long forceHideAfkExitTailUntilMs = 0L;
    private static final long FORCE_HIDE_AFK_EXIT_TAIL_WINDOW_MS = 15000L;

    private static final List<String> tempBlacklist = new ArrayList<>();
    private static final List<String> tempVogelfrei = new ArrayList<>();
    private static final Map<String, String> tempReasons = new LinkedHashMap<>();
    private static final Map<String, int[]> tempStats = new LinkedHashMap<>();
    private static final List<String> tempMembers = new ArrayList<>();
    private static boolean liveBlacklistRefreshActive = false;
    private static final List<String> backupBlacklist = new ArrayList<>();
    private static final List<String> backupVogelfrei = new ArrayList<>();
    private static final Map<String, String> backupReasons = new LinkedHashMap<>();
    private static final Map<String, int[]> backupStats = new LinkedHashMap<>();

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void scanForBlacklistTextOnly(Text message, CallbackInfo ci) {
        scanForBlacklistInternal(message, ci);
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void scanForBlacklistWithSignature(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        scanForBlacklistInternal(message, ci);
    }

    private void scanForBlacklistInternal(Text message, CallbackInfo ci) {
        if (addingTimestamp) return;
        if (!ServerGate.isAllowedServer(MinecraftClient.getInstance())) return;
        BetterUCSuppressFlags.cleanupStaleSilentMemberState();
        BetterUCSuppressFlags.cleanupStaleSilentBlacklistState();
        BetterUCSuppressFlags.cleanupStaleSilentStatsState();
        if (capturingStats && !BetterUCSuppressFlags.suppressStatsOutput && !BetterUCSuppressFlags.activeSilentStatsCapture) {
            capturingStats = false;
            activeStatsCaptureIsSilent = false;
        }

        String raw = message.getString();
        AutoCarController.handleIncomingChatForCarFind(MinecraftClient.getInstance(), raw);
        PlantageHud.handleChatMessage(MinecraftClient.getInstance(), raw);

        if (handleHackTimer(raw)) return;
        handleCookDrugTimer(raw);
        handlePaydayReset(raw);
        updateMoney(raw);

        if (shouldForceHideStatsLine(raw)) {
            ci.cancel();
            return;
        }

        if (handleStatsHeader(raw, ci)) return;
        if (handleImplicitSilentStatsStart(raw, ci)) return;
        if (capturingStats && handleCapturingStats(raw, ci)) return;
        if (handleRealtimeBlacklistMessages(raw)) return;
        if (handleMemberHeader(raw, ci)) return;
        if (handleBlacklistHeader(raw, ci)) return;
        if (!capturingBlacklist && tryStartManualBlacklistCaptureFromEntry(raw, message, ci)) return;
        if (capturingMembers && handleCapturingMembers(raw, ci)) return;
        if (capturingBlacklist && handleCapturingBlacklist(raw, message, ci)) return;

        appendTimestampIfConfigured(message, ci);
    }

    private boolean handleHackTimer(String raw) {
        Matcher hackMatcher = HACK_TIMER_PATTERN.matcher(raw);
        if (!hackMatcher.find()) return false;
        HackTimerHud.start(Integer.parseInt(hackMatcher.group(1)));
        return true;
    }

    private void handleCookDrugTimer(String raw) {
        if (raw == null || raw.isBlank()) return;
        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.contains(COOKDRUG_START_KEY)) {
            CookDrugHud.startDefault();
            return;
        }
        if (lower.contains(COOKDRUG_DONE_KEY)) {
            CookDrugHud.clear();
        }
    }

    private void handlePaydayReset(String raw) {
        if (raw == null || raw.isBlank()) return;
        if (PAYDAY_HEADER_PATTERN.matcher(raw.trim()).matches()) {
            PaydayHud.resetForNewPayday();
        }

        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("du bist nun im afk-modus")) {
            PaydayHud.setPausedByAfk(true);
            return;
        }
        if (lower.contains("du bist nun nicht mehr im afk-modus")
                || (lower.contains("payday-zeit")
                && (lower.contains("laeuft nun weiter") || lower.contains("l\u00e4uft nun weiter")))) {
            PaydayHud.setPausedByAfk(false);
            forceHideAfkExitTailUntilMs = System.currentTimeMillis() + FORCE_HIDE_AFK_EXIT_TAIL_WINDOW_MS;
            requestSilentStatsRefreshOnAfkExit();
            requestPendingBlacklistSyncAfterAfkExit();
        }
    }

    private void requestSilentStatsRefreshOnAfkExit() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (!ServerGate.isAllowedServer(client)) return;
        long now = System.currentTimeMillis();
        if (now - lastAfkExitStatsRefreshMs < AFK_EXIT_STATS_REFRESH_COOLDOWN_MS) return;
        lastAfkExitStatsRefreshMs = now;

        client.execute(() -> {
            if (client.player == null || !ServerGate.isAllowedServer(client)) return;
            BetterUCSuppressFlags.markSilentStatsRequest();
            forceHideStatsLinesUntilMs = System.currentTimeMillis() + FORCE_HIDE_STATS_WINDOW_MS;
            forceHideDashStatsLinesUntilMs = System.currentTimeMillis() + FORCE_HIDE_DASH_STATS_WINDOW_MS;
            client.player.networkHandler.sendChatCommand("stats");
            BetterUCMod.LOGGER.info("AFK-Exit: silent /stats refresh requested");
        });
    }

    private void updateMoney(String raw) {
        BankBalanceHud.updateFromChatLine(raw);

        Matcher moneyMatcher = MONEY_PATTERN.matcher(raw);
        if (moneyMatcher.find()) {
            BetterUCConfig.INSTANCE.currentMoney = parseStatMoneyValue(moneyMatcher.group(1));
        }

        Matcher blackMoneyMatcher = BLACK_MONEY_PATTERN.matcher(raw);
        if (blackMoneyMatcher.find()) {
            BetterUCConfig.INSTANCE.currentBlackMoney = parseStatMoneyValue(blackMoneyMatcher.group(1));
        }

        Matcher paydayMatcher = PAYDAY_PATTERN.matcher(raw);
        if (paydayMatcher.find()) {
            int current = Integer.parseInt(paydayMatcher.group(1));
            int total = Integer.parseInt(paydayMatcher.group(2));
            PaydayHud.updateFromStats(current, total);
        }
    }

    private int parseStatMoneyValue(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean handleRealtimeBlacklistMessages(String raw) {
        String addedName = BlacklistParser.parseRealtimeAdd(raw);
        if (addedName != null) {
            String name = addedName;
            BetterUCSuppressFlags.clearPendingModBlReadd(name);
            BetterUCSuppressFlags.clearRecentBlacklistRemove(name);
            if (BetterUCConfig.addChatBlacklistPlayer(name)) {
                BetterUCMod.LOGGER.info("Realtime blacklist add: {}", name);
            }
            requestBlacklistSync();
            return true;
        }

        String removedName = BlacklistParser.parseRealtimeRemove(raw);
        if (removedName != null) {
            String name = removedName;
            if (BetterUCSuppressFlags.shouldIgnoreRealtimeRemove(name)) {
                BetterUCMod.LOGGER.info("Ignoring realtime blacklist remove during /modbl transition: {}", name);
                return true;
            }
            BetterUCConfig.removeBlacklistPlayerEverywhere(name);
            BetterUCSuppressFlags.markRecentBlacklistRemove(name);
            BetterUCMod.LOGGER.info("Realtime blacklist remove: {}", name);
            requestBlacklistSync();
            return true;
        }

        return false;
    }

    private boolean handleStatsHeader(String raw, CallbackInfo ci) {
        if (raw == null || raw.isBlank()) return false;
        if (!StatsLineClassifier.isHeader(raw)) return false;

        boolean suppressingSilent = BetterUCSuppressFlags.beginSilentStatsCaptureIfPending();
        if (!suppressingSilent) {
            return false;
        }

        capturingStats = true;
        activeStatsCaptureIsSilent = true;
        ci.cancel();
        return true;
    }

    private boolean handleCapturingStats(String raw, CallbackInfo ci) {
        String trimmed = raw == null ? "" : raw.trim();
        boolean suppressingOutput = activeStatsCaptureIsSilent || BetterUCSuppressFlags.isSilentStatsCaptureActive();

        if (trimmed.isEmpty()) {
            if (suppressingOutput) ci.cancel();
            return true;
        }

        if (StatsLineClassifier.isDetailLine(trimmed)) {
            if (suppressingOutput) ci.cancel();
            return true;
        }

        finishStatsCapture();
        return false;
    }

    private boolean handleImplicitSilentStatsStart(String raw, CallbackInfo ci) {
        if (capturingStats) return false;
        if (!BetterUCSuppressFlags.suppressStatsOutput
                && BetterUCSuppressFlags.pendingSilentStatsRequests <= 0
                && !BetterUCSuppressFlags.activeSilentStatsCapture) {
            return false;
        }

        String trimmed = raw == null ? "" : raw.trim();
        if (!StatsLineClassifier.isImplicitDetailLine(trimmed)) return false;

        boolean started = BetterUCSuppressFlags.activeSilentStatsCapture
                || BetterUCSuppressFlags.beginSilentStatsCaptureIfPending();
        if (!started) return false;

        capturingStats = true;
        activeStatsCaptureIsSilent = true;
        ci.cancel();
        return true;
    }

    private boolean shouldForceHideStatsLine(String raw) {
        return StatsLineClassifier.shouldForceHideLine(
                raw,
                System.currentTimeMillis(),
                forceHideAfkExitTailUntilMs,
                forceHideStatsLinesUntilMs,
                forceHideDashStatsLinesUntilMs
        );
    }

    private void finishStatsCapture() {
        capturingStats = false;
        if (activeStatsCaptureIsSilent || BetterUCSuppressFlags.isSilentStatsCaptureActive()) {
            BetterUCSuppressFlags.finishSilentStatsCapture();
        }
        activeStatsCaptureIsSilent = false;
    }

    private boolean handleMemberHeader(String raw, CallbackInfo ci) {
        MemberInfoParser.Header header = MemberInfoParser.parseHeader(raw);
        if (header == null) return false;

        if (capturingMembers) {
            finishMembersCapture(false);
        }

        String normalizedFaction = BetterUCConfig.normalizeFactionQuery(header.factionName());
        currentMemberFactionQuery = normalizedFaction.isEmpty() ? "kartell" : normalizedFaction;
        expectedMemberEntries = header.expectedEntries();

        capturingMembers = true;
        capturingBlacklist = false;
        boolean suppressingSilent = BetterUCSuppressFlags.beginSilentMemberCaptureIfPending();
        activeMemberCaptureIsSilent = suppressingSilent;
        tempMembers.clear();
        BetterUCMod.LOGGER.info("Fraktion header erkannt: {}", currentMemberFactionQuery);

        if (expectedMemberEntries == 0) {
            finishMembersCapture(true);
            if (suppressingSilent) {
                ci.cancel();
            }
            return true;
        }

        if (suppressingSilent) {
            ci.cancel();
        }
        return true;
    }

    private boolean handleBlacklistHeader(String raw, CallbackInfo ci) {
        if (!BlacklistParser.isHeader(raw)) return false;

        if (capturingMembers) {
            finishMembersCapture(false);
        }
        capturingBlacklist = true;
        capturingMembers = false;

        boolean suppressingModBl = BetterUCSuppressFlags.suppressModBlOutput;
        boolean suppressingSilent = !suppressingModBl && BetterUCSuppressFlags.beginSilentBlacklistCaptureIfPending();
        activeBlacklistCaptureIsSilent = suppressingSilent;
        boolean manualRefreshRequested = BetterUCSuppressFlags.isManualBlacklistCommandRecent();

        tempBlacklist.clear();
        tempVogelfrei.clear();
        tempReasons.clear();
        tempStats.clear();
        if (!suppressingModBl && !suppressingSilent && manualRefreshRequested) {
            beginLiveBlacklistRefresh();
        } else {
            resetLiveBlacklistRefreshState();
        }
        BetterUCMod.LOGGER.info("Blacklist header erkannt, lese Eintraege...");

        if (suppressingModBl || suppressingSilent) {
            ci.cancel();
        }
        return true;
    }

    private boolean tryStartManualBlacklistCaptureFromEntry(String raw, Text message, CallbackInfo ci) {
        if (!BetterUCSuppressFlags.isManualBlacklistCommandRecent()) return false;
        if (BetterUCSuppressFlags.suppressModBlOutput) return false;
        if (BetterUCSuppressFlags.isSilentBlacklistCaptureActive()) return false;

        BlacklistParser.Entry entry = BlacklistParser.parseEntry(raw);
        if (entry == null) return false;

        if (capturingMembers) {
            finishMembersCapture(false);
        }
        capturingBlacklist = true;
        capturingMembers = false;
        activeBlacklistCaptureIsSilent = false;
        tempBlacklist.clear();
        tempVogelfrei.clear();
        tempReasons.clear();
        tempStats.clear();
        beginLiveBlacklistRefresh();
        BetterUCMod.LOGGER.info("Blacklist capture started from entry line (manual command)");

        collectBlacklistEntry(entry);
        ci.cancel();
        replayFormattedBlacklistLine(raw, entry, message, ci);
        return true;
    }

    private static void beginLiveBlacklistRefresh() {
        backupBlacklist.clear();
        backupBlacklist.addAll(BetterUCConfig.INSTANCE.chatBlacklistPlayers);
        backupVogelfrei.clear();
        backupVogelfrei.addAll(BetterUCConfig.INSTANCE.vogelfreiPlayers);
        backupReasons.clear();
        backupReasons.putAll(BetterUCConfig.INSTANCE.blacklistReasons);
        backupStats.clear();
        for (Map.Entry<String, int[]> entry : BetterUCConfig.INSTANCE.blacklistStats.entrySet()) {
            int[] value = entry.getValue();
            backupStats.put(entry.getKey(), value == null ? null : value.clone());
        }

        BetterUCConfig.INSTANCE.chatBlacklistPlayers.clear();
        BetterUCConfig.INSTANCE.vogelfreiPlayers.clear();
        BetterUCConfig.INSTANCE.blacklistReasons.clear();
        BetterUCConfig.INSTANCE.blacklistStats.clear();
        BetterUCConfig.refreshBlacklistNameCaches();
        liveBlacklistRefreshActive = true;
    }

    private static void restoreLiveBlacklistBackup() {
        BetterUCConfig.INSTANCE.chatBlacklistPlayers.clear();
        BetterUCConfig.INSTANCE.chatBlacklistPlayers.addAll(backupBlacklist);
        BetterUCConfig.INSTANCE.vogelfreiPlayers.clear();
        BetterUCConfig.INSTANCE.vogelfreiPlayers.addAll(backupVogelfrei);
        BetterUCConfig.INSTANCE.blacklistReasons.clear();
        BetterUCConfig.INSTANCE.blacklistReasons.putAll(backupReasons);
        BetterUCConfig.INSTANCE.blacklistStats.clear();
        for (Map.Entry<String, int[]> entry : backupStats.entrySet()) {
            int[] value = entry.getValue();
            BetterUCConfig.INSTANCE.blacklistStats.put(entry.getKey(), value == null ? null : value.clone());
        }
        BetterUCConfig.refreshBlacklistNameCaches();
        resetLiveBlacklistRefreshState();
    }

    private static void resetLiveBlacklistRefreshState() {
        liveBlacklistRefreshActive = false;
        backupBlacklist.clear();
        backupVogelfrei.clear();
        backupReasons.clear();
        backupStats.clear();
    }

    private boolean handleCapturingMembers(String raw, CallbackInfo ci) {
        boolean suppressingOutput = activeMemberCaptureIsSilent || BetterUCSuppressFlags.isSilentMemberCaptureActive();
        MemberInfoParser.ParsedLine parsed = MemberInfoParser.parseLine(raw, MinecraftClient.getInstance());
        if (parsed.type() == MemberInfoParser.Type.NAMES) {
            addTempMembers(parsed.names());
            BetterUCConfig.mergeRemoteMembersForFaction(currentMemberFactionQuery, new ArrayList<>(tempMembers));
            if (MemberInfoParser.shouldFinishByExpectedMemberCount(expectedMemberEntries, tempMembers.size())) {
                finishMembersCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (parsed.type() == MemberInfoParser.Type.DECORATION) {
            if (!tempMembers.isEmpty()) {
                finishMembersCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (parsed.type() == MemberInfoParser.Type.NO_RESULTS) {
            finishMembersCapture(true);
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        finishMembersCapture(false);
        return true;
    }

    private void finishMembersCapture(boolean forceApplyEvenIfEmpty) {
        boolean suppressingOutput = activeMemberCaptureIsSilent || BetterUCSuppressFlags.isSilentMemberCaptureActive();

        capturingMembers = false;
        if (forceApplyEvenIfEmpty || !tempMembers.isEmpty()) {
            BetterUCConfig.setRemoteMembersForFaction(currentMemberFactionQuery, new ArrayList<>(tempMembers));
            BetterUCMod.LOGGER.info(
                    "Fraktion geladen: {} Mitglieder ({}) | Gesamt: {}",
                    tempMembers.size(),
                    currentMemberFactionQuery,
                    BetterUCConfig.INSTANCE.remoteFactionPlayers.size()
            );
        } else {
            BetterUCMod.LOGGER.info(
                    "Fraktion-Capture ohne gueltige Eintraege beendet ({}) | Gesamt bleibt bei {}",
                    currentMemberFactionQuery,
                    BetterUCConfig.INSTANCE.remoteFactionPlayers.size()
            );
        }

        tempMembers.clear();
        currentMemberFactionQuery = "kartell";
        expectedMemberEntries = -1;
        if (suppressingOutput) {
            BetterUCSuppressFlags.finishSilentMemberCapture();
        }
        activeMemberCaptureIsSilent = false;
    }

    private void addTempMembers(List<String> names) {
        if (names == null || names.isEmpty()) return;
        for (String name : names) {
            if (tempMembers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                tempMembers.add(name);
            }
        }
    }

    private boolean handleCapturingBlacklist(String raw, Text message, CallbackInfo ci) {
        boolean suppressingOutput = isBlacklistOutputSuppressed();
        BlacklistParser.Entry entry = BlacklistParser.parseEntry(raw);
        if (entry != null) {
            collectBlacklistEntry(entry);
            if (suppressingOutput) {
                ci.cancel();
                return true;
            }
            replayFormattedBlacklistLine(raw, entry, message, ci);
            return true;
        }

        String trimmed = raw == null ? "" : raw.trim();

        if (trimmed.isEmpty()) {
            if (!tempBlacklist.isEmpty()) {
                finishBlacklistCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (BlacklistParser.isNoResults(trimmed)) {
            finishBlacklistCapture(true);
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (BlacklistParser.isDecoration(trimmed)) {
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (tempBlacklist.isEmpty()) {
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        finishBlacklistCapture(false);
        return true;
    }

    private boolean isBlacklistOutputSuppressed() {
        return BetterUCSuppressFlags.suppressModBlOutput
                || activeBlacklistCaptureIsSilent
                || BetterUCSuppressFlags.isSilentBlacklistCaptureActive();
    }

    private void collectBlacklistEntry(BlacklistParser.Entry entry) {
        String name = entry.name();
        if (tempBlacklist.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
            tempBlacklist.add(name);
            BetterUCMod.LOGGER.info("Temp blacklist add: {}", name);
        }

        if (entry.hasVogelfrei()) {
            if (tempVogelfrei.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                tempVogelfrei.add(name);
            }
        }

        if (!entry.normalizedReason().isBlank()) {
            tempReasons.put(name, entry.normalizedReason());
        }
        tempStats.put(name, new int[]{entry.kills(), entry.price()});
        if (liveBlacklistRefreshActive) {
            applyLiveBlacklistEntry(entry);
        }
    }

    private void applyLiveBlacklistEntry(BlacklistParser.Entry entry) {
        String name = entry.name();
        BetterUCConfig.addChatBlacklistPlayer(name);

        if (!entry.normalizedReason().isBlank()) {
            BetterUCConfig.INSTANCE.blacklistReasons.put(name, entry.normalizedReason());
        } else {
            BetterUCConfig.INSTANCE.blacklistReasons.remove(name);
        }
        BetterUCConfig.INSTANCE.blacklistStats.put(name, new int[]{Math.max(0, entry.kills()), Math.max(0, entry.price())});

        if (entry.hasVogelfrei()) {
            BetterUCConfig.addVogelfreiPlayer(name);
        } else {
            BetterUCConfig.removeVogelfreiPlayer(name);
        }
        BetterUCConfig.refreshBlacklistNameCaches();
    }

    private void replayFormattedBlacklistLine(String raw, BlacklistParser.Entry entry, Text originalMessage, CallbackInfo ci) {
        String replaced = raw;
        if (entry.rest() != null) {
            String formattedRest = BlacklistParser.rewriteFormattedRest(entry.rest());
            replaced = raw.replaceFirst(Pattern.quote(entry.rest()), Matcher.quoteReplacement(formattedRest));
            replaced = replaced.replace("\\\"", "").replace("\"", "");
        }

        MutableText rewritten = Text.literal(replaced);
        TextColor baseColor = findPreferredColor(originalMessage);
        if (baseColor != null) {
            rewritten = rewritten.styled(style -> style.withColor(baseColor));
        } else {
            rewritten = rewritten.setStyle(originalMessage.getStyle());
        }

        ci.cancel();
        addingTimestamp = true;
        try {
            ((ChatHud) (Object) this).addMessage(withTimestampIfEnabled(
                    rewritten
            ));
        } finally {
            addingTimestamp = false;
        }
    }

    private static TextColor findPreferredColor(Text text) {
        if (text == null) return null;

        TextColor own = text.getStyle().getColor();
        if (own != null && !isNeutralColor(own) && isGreenLike(own)) {
            return own;
        }

        TextColor greenish = findBestGreenColor(text);
        if (greenish != null) return greenish;

        TextColor nonNeutral = findFirstNonNeutralColor(text);
        if (nonNeutral != null) return nonNeutral;

        return own != null ? own : findFirstColor(text);
    }

    private static TextColor findFirstNonNeutralColor(Text text) {
        if (text == null) return null;

        TextColor own = text.getStyle().getColor();
        if (own != null && !isNeutralColor(own)) return own;

        for (Text sibling : text.getSiblings()) {
            TextColor child = findFirstNonNeutralColor(sibling);
            if (child != null) return child;
        }
        return null;
    }

    private static TextColor findFirstColor(Text text) {
        if (text == null) return null;

        TextColor color = text.getStyle().getColor();
        if (color != null) return color;

        for (Text sibling : text.getSiblings()) {
            TextColor child = findFirstColor(sibling);
            if (child != null) return child;
        }
        return null;
    }

    private static TextColor findBestGreenColor(Text text) {
        if (text == null) return null;

        TextColor best = null;
        int bestScore = Integer.MIN_VALUE;

        TextColor own = text.getStyle().getColor();
        if (own != null && !isNeutralColor(own)) {
            int score = greenScore(own);
            if (score > bestScore) {
                bestScore = score;
                best = own;
            }
        }

        for (Text sibling : text.getSiblings()) {
            TextColor candidate = findBestGreenColor(sibling);
            if (candidate == null) continue;
            int score = greenScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // Only accept if it is actually green-leaning.
        if (best != null && !isGreenLike(best)) return null;
        return best;
    }

    private static boolean isNeutralColor(TextColor color) {
        if (color == null) return true;
        int rgb = color.getRgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Math.abs(r - g) < 12 && Math.abs(g - b) < 12 && Math.abs(r - b) < 12;
    }

    private static boolean isGreenLike(TextColor color) {
        if (color == null) return false;
        int rgb = color.getRgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return g >= r + 24 && g >= b + 24;
    }

    private static int greenScore(TextColor color) {
        int rgb = color.getRgb();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (g * 3) - (r + b);
    }

    private void finishBlacklistCapture(boolean forceApplyEvenIfEmpty) {
        capturingBlacklist = false;
        boolean restoredBackup = false;

        if (forceApplyEvenIfEmpty || !tempBlacklist.isEmpty()) {
            BetterUCConfig.INSTANCE.chatBlacklistPlayers.clear();
            BetterUCConfig.INSTANCE.chatBlacklistPlayers.addAll(tempBlacklist);
            BetterUCConfig.INSTANCE.vogelfreiPlayers.clear();
            BetterUCConfig.INSTANCE.vogelfreiPlayers.addAll(tempVogelfrei);
            BetterUCConfig.INSTANCE.blacklistReasons.clear();
            BetterUCConfig.INSTANCE.blacklistReasons.putAll(tempReasons);
            BetterUCConfig.INSTANCE.blacklistStats.clear();
            BetterUCConfig.INSTANCE.blacklistStats.putAll(tempStats);
            BetterUCConfig.markBlacklistSyncComplete();

            BetterUCMod.LOGGER.info("Blacklist geladen: {} Eintraege", tempBlacklist.size());
        } else {
            if (liveBlacklistRefreshActive) {
                restoreLiveBlacklistBackup();
                restoredBackup = true;
                BetterUCMod.LOGGER.info(
                        "Blacklist-Capture ohne Eintraege beendet, vorherige Liste wiederhergestellt ({} Eintraege)",
                        BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
                );
            } else {
                BetterUCMod.LOGGER.info(
                        "Blacklist-Capture ohne Eintraege beendet, vorherige Liste bleibt erhalten ({} Eintraege)",
                        BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
                );
            }
        }

        if (BetterUCSuppressFlags.suppressModBlOutput) {
            BetterUCSuppressFlags.suppressModBlOutput = false;
            if (BetterUCSuppressFlags.modBlCallback != null) {
                Runnable cb = BetterUCSuppressFlags.modBlCallback;
                BetterUCSuppressFlags.modBlCallback = null;
                MinecraftClient.getInstance().execute(cb);
            }
        } else if (activeBlacklistCaptureIsSilent || BetterUCSuppressFlags.isSilentBlacklistCaptureActive()) {
            BetterUCSuppressFlags.finishSilentBlacklistCapture();
        }
        activeBlacklistCaptureIsSilent = false;
        if (!restoredBackup) {
            resetLiveBlacklistRefreshState();
        }
        BetterUCSuppressFlags.clearManualBlacklistCommandMarker();
    }

    private void appendTimestampIfConfigured(Text message, CallbackInfo ci) {
        String format = BetterUCConfig.INSTANCE.chatTimestampFormat;
        if (format == null || format.isEmpty()) return;

        try {
            String timestamp = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern(format));
            Text newMessage = Text.literal("\u00A77" + timestamp + " ").append(message);
            ci.cancel();
            addingTimestamp = true;
            ((ChatHud) (Object) this).addMessage(newMessage);
        } catch (Exception ignored) {
        } finally {
            addingTimestamp = false;
        }
    }

    private static Text withTimestampIfEnabled(Text base) {
        String format = BetterUCConfig.INSTANCE.chatTimestampFormat;
        if (format == null || format.isEmpty()) return base;

        try {
            String timestamp = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern(format));
            return Text.literal("\u00A77" + timestamp + " ").append(base);
        } catch (Exception ignored) {
            return base;
        }
    }

    private static void requestBlacklistSync() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ServerGate.isAllowedServer(client)) return;
        if (PaydayHud.isPausedByAfk()) {
            pendingRealtimeBlacklistSyncAfterAfk = true;
            BetterUCMod.LOGGER.info("Realtime blacklist sync deferred (AFK active)");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRealtimeSyncMs < REALTIME_SYNC_COOLDOWN_MS) return;
        lastRealtimeSyncMs = now;

        if (client == null) return;

        client.execute(() -> {
            if (client.player == null || capturingBlacklist || !ServerGate.isAllowedServer(client)) return;
            BetterUCSuppressFlags.markSilentBlacklistRequest();
            client.player.networkHandler.sendChatCommand("blacklist");
            BetterUCMod.LOGGER.info("Realtime blacklist sync requested");
        });
    }

    private static void requestPendingBlacklistSyncAfterAfkExit() {
        if (!pendingRealtimeBlacklistSyncAfterAfk) return;
        pendingRealtimeBlacklistSyncAfterAfk = false;
        requestBlacklistSync();
    }

    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void suppressStatsAtLineLevel(ChatHudLine line, CallbackInfo ci) {
        if (line == null) return;
        Text content = line.content();
        if (content == null) return;
        if (shouldSuppressSilentStatsLine(content)) {
            ci.cancel();
        }
    }

    @Inject(method = "addVisibleMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void suppressStatsAtVisibleLineLevel(ChatHudLine line, CallbackInfo ci) {
        if (line == null) return;
        Text content = line.content();
        if (content == null) return;
        if (shouldSuppressSilentStatsLine(content)) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("TAIL"), require = 0)
    private void extendMessageHistory(net.minecraft.client.gui.hud.ChatHudLine message, CallbackInfo ci) {
        try {
            java.lang.reflect.Field f = ChatHud.class.getDeclaredField("messages");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> messages = (java.util.List<Object>) f.get(this);
            while (messages.size() > BetterUCConfig.INSTANCE.maxChatHistory) {
                messages.remove(messages.size() - 1);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean shouldSuppressSilentStatsLine(Text text) {
        if (text == null) return false;
        String raw = text.getString();
        if (shouldForceHideStatsLine(raw)) return true;

        boolean silentStatsExpected = capturingStats
                || activeStatsCaptureIsSilent
                || BetterUCSuppressFlags.suppressStatsOutput
                || BetterUCSuppressFlags.pendingSilentStatsRequests > 0
                || BetterUCSuppressFlags.isSilentStatsCaptureActive();
        if (!silentStatsExpected) return false;

        String trimmed = raw == null ? "" : raw.trim();
        if (StatsLineClassifier.isDetailLine(trimmed)) return true;
        if (StatsLineClassifier.isImplicitDetailLine(trimmed)) return true;
        return StatsLineClassifier.containsHoverSignal(text);
    }

}
