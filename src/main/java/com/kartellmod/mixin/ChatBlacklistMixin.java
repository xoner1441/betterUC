package com.kartellmod.mixin;

import com.kartellmod.KartellMod;
import com.kartellmod.PlayerNameUtil;
import com.kartellmod.KartellSuppressFlags;
import com.kartellmod.ServerGate;
import com.kartellmod.config.KartellConfig;
import com.kartellmod.hud.BankBalanceHud;
import com.kartellmod.hud.CookDrugHud;
import com.kartellmod.hud.HackTimerHud;
import com.kartellmod.hud.PaydayHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatBlacklistMixin {

    private static final Pattern MEMBER_HEADER_PATTERN = Pattern.compile(
            "mitglieder\\s+(?:von|vom|des)?\\s*(.+?)\\s*\\((\\d+)(?:/\\d+)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MEMBER_ENTRY_PATTERN = Pattern.compile("^-?\\s*\\d+\\s*\\|\\s*(.+)$");
    private static final Pattern MEMBER_NAME_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");
    private static final Pattern LEGACY_FORMATTING_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");
    private static final Set<String> MEMBER_NAME_STOPWORDS = new HashSet<>(Arrays.asList(
            "online", "offline", "afk", "rang", "rank", "mitglied", "mitglieder",
            "leader", "coleader", "co", "status", "seite", "page", "ping", "ms",
            "kills", "kill", "k", "tode", "deaths", "death", "kd", "kdr",
            "fraktion", "faction", "von", "vom", "des", "der", "die", "das"
    ));
    private static final Pattern MEMBER_DECORATION_PATTERN = Pattern.compile("^[=\\-\\s:>\\u00BB]+$");
    private static final Pattern MEMBER_NO_RESULTS_PATTERN = Pattern.compile("keine\\s+mitglieder", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLACKLIST_HEADER_PATTERN = Pattern.compile(
            "blacklist\\s+.+\\s*\\(\\d+(?:/\\d+)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BLACKLIST_ENTRY_PATTERN = Pattern.compile(
            "(?:^|[\\u00BB>])\\s*(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{3,16})\\s*\\|(.+)"
    );
    private static final Pattern BLACKLIST_DECORATION_PATTERN = Pattern.compile("^[=\\-\\s:>\\u00BB]+$");
    private static final Pattern BLACKLIST_NO_RESULTS_PATTERN = Pattern.compile(
            "keine\\s+.*blacklist|blacklist\\s+ist\\s+leer",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KILLS_PATTERN = Pattern.compile("(\\d+)\\s*Kills");
    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+)\\$");
    private static final Pattern BLACKLIST_ADD_PATTERN = Pattern.compile(
            "Blacklist[\\]:\\s]+([A-Za-z0-9_]{3,16})\\s+wurde.*auf die Blacklist(?:\\s+gesetzt)?"
    );
    private static final Pattern BLACKLIST_REMOVE_PATTERN = Pattern.compile(
            "Blacklist[\\]:\\s]+([A-Za-z0-9_]{3,16})\\s+wurde.*von der Blacklist"
    );
    private static final Pattern VOGELFREI_TOKEN_PATTERN = Pattern.compile("(?i)\\b\\(?vogelfrei\\)?\\b");
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
    private static final Pattern STATS_HEADER_PATTERN = Pattern.compile("statistiken", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATS_TIMED_LINE_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}:\\d{2}\\s+-\\s+.*");
    private static final Pattern STATS_KEYWORD_PATTERN = Pattern.compile(
            "\\b(status|level|inventar|wanted\\s+punkte|geld|schwarzgeld|verwarnungen|zeit\\s+seit\\s+payday|experience|fraktion|haus|immobilien|beruf|votepoints|treuebonus|spielzeit|k\\s*[/\\\\|]\\s*d)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATS_HOVER_KEYWORD_PATTERN = Pattern.compile(
            "\\b(selfstorage|lagerhaus|buero|b\\u00FCro|kills|tode|k\\s*[/\\\\|]\\s*d|immobilien|details)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATS_KD_LINE_PATTERN = Pattern.compile(
            "^-\\s*k\\s*[/\\\\|._-]\\s*d\\s*:\\s*.*$",
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
        KartellSuppressFlags.cleanupStaleSilentMemberState();
        KartellSuppressFlags.cleanupStaleSilentBlacklistState();
        KartellSuppressFlags.cleanupStaleSilentStatsState();
        if (capturingStats && !KartellSuppressFlags.suppressStatsOutput && !KartellSuppressFlags.activeSilentStatsCapture) {
            capturingStats = false;
            activeStatsCaptureIsSilent = false;
        }

        String raw = message.getString();

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
            KartellSuppressFlags.markSilentStatsRequest();
            forceHideStatsLinesUntilMs = System.currentTimeMillis() + FORCE_HIDE_STATS_WINDOW_MS;
            forceHideDashStatsLinesUntilMs = System.currentTimeMillis() + FORCE_HIDE_DASH_STATS_WINDOW_MS;
            client.player.networkHandler.sendChatCommand("stats");
            KartellMod.LOGGER.info("AFK-Exit: silent /stats refresh requested");
        });
    }

    private void updateMoney(String raw) {
        BankBalanceHud.updateFromChatLine(raw);

        Matcher moneyMatcher = MONEY_PATTERN.matcher(raw);
        if (moneyMatcher.find()) {
            KartellConfig.INSTANCE.currentMoney = parseStatMoneyValue(moneyMatcher.group(1));
        }

        Matcher blackMoneyMatcher = BLACK_MONEY_PATTERN.matcher(raw);
        if (blackMoneyMatcher.find()) {
            KartellConfig.INSTANCE.currentBlackMoney = parseStatMoneyValue(blackMoneyMatcher.group(1));
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
        Matcher addMatcher = BLACKLIST_ADD_PATTERN.matcher(raw);
        if (addMatcher.find()) {
            String name = addMatcher.group(1);
            KartellSuppressFlags.clearPendingModBlReadd(name);
            KartellSuppressFlags.clearRecentBlacklistRemove(name);
            if (KartellConfig.INSTANCE.chatBlacklistPlayers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                KartellConfig.INSTANCE.chatBlacklistPlayers.add(name);
                KartellMod.LOGGER.info("Realtime blacklist add: {}", name);
            }
            requestBlacklistSync();
            return true;
        }

        Matcher removeMatcher = BLACKLIST_REMOVE_PATTERN.matcher(raw);
        if (removeMatcher.find()) {
            String name = removeMatcher.group(1);
            if (KartellSuppressFlags.shouldIgnoreRealtimeRemove(name)) {
                KartellMod.LOGGER.info("Ignoring realtime blacklist remove during /modbl transition: {}", name);
                return true;
            }
            KartellConfig.INSTANCE.chatBlacklistPlayers.removeIf(s -> s.equalsIgnoreCase(name));
            KartellConfig.INSTANCE.manualBlacklistPlayers.removeIf(s -> s.equalsIgnoreCase(name));
            KartellConfig.INSTANCE.vogelfreiPlayers.removeIf(s -> s.equalsIgnoreCase(name));
            KartellConfig.INSTANCE.blacklistReasons.remove(name);
            KartellConfig.INSTANCE.blacklistStats.remove(name);
            KartellSuppressFlags.markRecentBlacklistRemove(name);
            KartellMod.LOGGER.info("Realtime blacklist remove: {}", name);
            requestBlacklistSync();
            return true;
        }

        return false;
    }

    private boolean handleStatsHeader(String raw, CallbackInfo ci) {
        if (raw == null || raw.isBlank()) return false;
        if (!STATS_HEADER_PATTERN.matcher(raw).find()) return false;

        boolean suppressingSilent = KartellSuppressFlags.beginSilentStatsCaptureIfPending();
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
        boolean suppressingOutput = activeStatsCaptureIsSilent || KartellSuppressFlags.isSilentStatsCaptureActive();

        if (trimmed.isEmpty()) {
            if (suppressingOutput) ci.cancel();
            return true;
        }

        if (looksLikeStatsDetailLine(trimmed)) {
            if (suppressingOutput) ci.cancel();
            return true;
        }

        finishStatsCapture();
        return false;
    }

    private boolean handleImplicitSilentStatsStart(String raw, CallbackInfo ci) {
        if (capturingStats) return false;
        if (!KartellSuppressFlags.suppressStatsOutput
                && KartellSuppressFlags.pendingSilentStatsRequests <= 0
                && !KartellSuppressFlags.activeSilentStatsCapture) {
            return false;
        }

        String trimmed = raw == null ? "" : raw.trim();
        if (!looksLikeImplicitStatsDetailLine(trimmed)) return false;

        boolean started = KartellSuppressFlags.activeSilentStatsCapture
                || KartellSuppressFlags.beginSilentStatsCaptureIfPending();
        if (!started) return false;

        capturingStats = true;
        activeStatsCaptureIsSilent = true;
        ci.cancel();
        return true;
    }

    private boolean looksLikeStatsDetailLine(String trimmed) {
        trimmed = normalizeStatsProbeString(trimmed);
        if (trimmed.isEmpty()) return false;
        if (STATS_HEADER_PATTERN.matcher(trimmed).find()) return true;
        if (STATS_KD_LINE_PATTERN.matcher(trimmed).matches()) return true;
        if (startsWithListDash(trimmed) || trimmed.startsWith("=")) return true;
        if (STATS_KEYWORD_PATTERN.matcher(trimmed).find() && trimmed.contains(":")) return true;
        return STATS_TIMED_LINE_PATTERN.matcher(trimmed).matches();
    }

    private boolean looksLikeImplicitStatsDetailLine(String trimmed) {
        trimmed = normalizeStatsProbeString(trimmed);
        if (trimmed.isEmpty()) return false;

        boolean timed = STATS_TIMED_LINE_PATTERN.matcher(trimmed).matches();
        boolean dashPrefixed = startsWithListDash(trimmed);
        boolean hasKeyword = STATS_KEYWORD_PATTERN.matcher(trimmed).find();

        if (timed && hasKeyword) return true;
        return dashPrefixed && hasKeyword;
    }

    private boolean startsWithListDash(String trimmed) {
        return trimmed.startsWith("-")
                || trimmed.startsWith("\u2013")
                || trimmed.startsWith("\u2014")
                || trimmed.startsWith("\u2010")
                || trimmed.startsWith("\u2011")
                || trimmed.startsWith("\u2012")
                || trimmed.startsWith("\u2015")
                || trimmed.startsWith("\u2212")
                || trimmed.startsWith("\uFE58")
                || trimmed.startsWith("\uFE63")
                || trimmed.startsWith("\uFF0D");
    }

    private boolean shouldForceHideStatsLine(String raw) {
        if (raw == null || raw.isBlank()) return false;
        long now = System.currentTimeMillis();
        String trimmed = normalizeStatsProbeString(raw.trim());

        if (now <= forceHideAfkExitTailUntilMs && looksLikeAfkExitTailLine(trimmed)) {
            return true;
        }

        boolean inMainWindow = now <= forceHideStatsLinesUntilMs;
        boolean inDashWindow = now <= forceHideDashStatsLinesUntilMs;
        if (!inMainWindow && !inDashWindow) return false;

        if (trimmed.isEmpty()) return false;
        if (trimmed.toLowerCase(Locale.ROOT).contains("du bist nun nicht mehr im afk-modus")) return false;
        if (trimmed.toLowerCase(Locale.ROOT).contains("deine payday-zeit l")) return false;

        if (inMainWindow) {
            if (STATS_HEADER_PATTERN.matcher(trimmed).find()) return true;
            if (looksLikeStatsDetailLine(trimmed)) return true;
            if (looksLikeImplicitStatsDetailLine(trimmed)) return true;
        }

        if (inDashWindow && looksLikeDashStatTailLine(trimmed)) return true;
        return false;
    }

    private void finishStatsCapture() {
        capturingStats = false;
        if (activeStatsCaptureIsSilent || KartellSuppressFlags.isSilentStatsCaptureActive()) {
            KartellSuppressFlags.finishSilentStatsCapture();
        }
        activeStatsCaptureIsSilent = false;
    }

    private boolean handleMemberHeader(String raw, CallbackInfo ci) {
        Matcher headerMatcher = MEMBER_HEADER_PATTERN.matcher(raw);
        if (!headerMatcher.find()) return false;

        if (capturingMembers) {
            finishMembersCapture(false);
        }

        String factionFromHeader = headerMatcher.group(1);
        String normalizedFaction = KartellConfig.normalizeFactionQuery(factionFromHeader);
        currentMemberFactionQuery = normalizedFaction.isEmpty() ? "kartell" : normalizedFaction;
        expectedMemberEntries = parseExpectedMemberEntries(headerMatcher.group(2));

        capturingMembers = true;
        capturingBlacklist = false;
        boolean suppressingSilent = KartellSuppressFlags.beginSilentMemberCaptureIfPending();
        activeMemberCaptureIsSilent = suppressingSilent;
        tempMembers.clear();
        KartellMod.LOGGER.info("Fraktion header erkannt: {}", currentMemberFactionQuery);

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
        if (!BLACKLIST_HEADER_PATTERN.matcher(raw).find()) return false;

        if (capturingMembers) {
            finishMembersCapture(false);
        }
        capturingBlacklist = true;
        capturingMembers = false;

        boolean suppressingModBl = KartellSuppressFlags.suppressModBlOutput;
        boolean suppressingSilent = !suppressingModBl && KartellSuppressFlags.beginSilentBlacklistCaptureIfPending();
        activeBlacklistCaptureIsSilent = suppressingSilent;
        boolean manualRefreshRequested = KartellSuppressFlags.isManualBlacklistCommandRecent();

        tempBlacklist.clear();
        tempVogelfrei.clear();
        tempReasons.clear();
        tempStats.clear();
        if (!suppressingModBl && !suppressingSilent && manualRefreshRequested) {
            beginLiveBlacklistRefresh();
        } else {
            resetLiveBlacklistRefreshState();
        }
        KartellMod.LOGGER.info("Blacklist header erkannt, lese Eintraege...");

        if (suppressingModBl || suppressingSilent) {
            ci.cancel();
        }
        return true;
    }

    private boolean tryStartManualBlacklistCaptureFromEntry(String raw, Text message, CallbackInfo ci) {
        if (!KartellSuppressFlags.isManualBlacklistCommandRecent()) return false;
        if (KartellSuppressFlags.suppressModBlOutput) return false;
        if (KartellSuppressFlags.isSilentBlacklistCaptureActive()) return false;

        Matcher entryMatcher = BLACKLIST_ENTRY_PATTERN.matcher(raw);
        if (!entryMatcher.find()) return false;

        String name = entryMatcher.group(1);
        String rest = entryMatcher.group(2);
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return false;

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
        KartellMod.LOGGER.info("Blacklist capture started from entry line (manual command)");

        collectBlacklistEntry(name, rest);
        ci.cancel();
        replayFormattedBlacklistLine(raw, name, rest, message, ci);
        return true;
    }

    private static void beginLiveBlacklistRefresh() {
        backupBlacklist.clear();
        backupBlacklist.addAll(KartellConfig.INSTANCE.chatBlacklistPlayers);
        backupVogelfrei.clear();
        backupVogelfrei.addAll(KartellConfig.INSTANCE.vogelfreiPlayers);
        backupReasons.clear();
        backupReasons.putAll(KartellConfig.INSTANCE.blacklistReasons);
        backupStats.clear();
        for (Map.Entry<String, int[]> entry : KartellConfig.INSTANCE.blacklistStats.entrySet()) {
            int[] value = entry.getValue();
            backupStats.put(entry.getKey(), value == null ? null : value.clone());
        }

        KartellConfig.INSTANCE.chatBlacklistPlayers.clear();
        KartellConfig.INSTANCE.vogelfreiPlayers.clear();
        KartellConfig.INSTANCE.blacklistReasons.clear();
        KartellConfig.INSTANCE.blacklistStats.clear();
        liveBlacklistRefreshActive = true;
    }

    private static void restoreLiveBlacklistBackup() {
        KartellConfig.INSTANCE.chatBlacklistPlayers.clear();
        KartellConfig.INSTANCE.chatBlacklistPlayers.addAll(backupBlacklist);
        KartellConfig.INSTANCE.vogelfreiPlayers.clear();
        KartellConfig.INSTANCE.vogelfreiPlayers.addAll(backupVogelfrei);
        KartellConfig.INSTANCE.blacklistReasons.clear();
        KartellConfig.INSTANCE.blacklistReasons.putAll(backupReasons);
        KartellConfig.INSTANCE.blacklistStats.clear();
        for (Map.Entry<String, int[]> entry : backupStats.entrySet()) {
            int[] value = entry.getValue();
            KartellConfig.INSTANCE.blacklistStats.put(entry.getKey(), value == null ? null : value.clone());
        }
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
        boolean suppressingOutput = activeMemberCaptureIsSilent || KartellSuppressFlags.isSilentMemberCaptureActive();
        String trimmed = raw == null ? "" : raw.trim();
        Matcher entryMatcher = MEMBER_ENTRY_PATTERN.matcher(trimmed);
        boolean hasStructuredEntry = entryMatcher.find();
        if (hasStructuredEntry || trimmed.contains("|")) {
            String payload = hasStructuredEntry ? entryMatcher.group(1) : trimmed;
            collectMemberNamesFromPayload(payload);
            if (shouldFinishByExpectedMemberCount()) {
                finishMembersCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        // Some server formats list members without "|" separators, usually as ">> name1, name2".
        boolean looksLikeInlineMemberList = trimmed.contains(",")
                || trimmed.startsWith(">")
                || trimmed.startsWith("\u00BB");
        if (!trimmed.isBlank() && looksLikeInlineMemberList && collectMemberNamesFromPayload(trimmed) > 0) {
            if (shouldFinishByExpectedMemberCount()) {
                finishMembersCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (trimmed.isBlank() || MEMBER_DECORATION_PATTERN.matcher(trimmed).matches()) {
            if (!tempMembers.isEmpty()) {
                finishMembersCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (MEMBER_NO_RESULTS_PATTERN.matcher(trimmed).find()) {
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
        boolean suppressingOutput = activeMemberCaptureIsSilent || KartellSuppressFlags.isSilentMemberCaptureActive();

        capturingMembers = false;
        if (forceApplyEvenIfEmpty || !tempMembers.isEmpty()) {
            KartellConfig.setRemoteMembersForFaction(currentMemberFactionQuery, new ArrayList<>(tempMembers));
            KartellMod.LOGGER.info(
                    "Fraktion geladen: {} Mitglieder ({}) | Gesamt: {}",
                    tempMembers.size(),
                    currentMemberFactionQuery,
                    KartellConfig.INSTANCE.remoteFactionPlayers.size()
            );
        } else {
            KartellMod.LOGGER.info(
                    "Fraktion-Capture ohne gueltige Eintraege beendet ({}) | Gesamt bleibt bei {}",
                    currentMemberFactionQuery,
                    KartellConfig.INSTANCE.remoteFactionPlayers.size()
            );
        }

        tempMembers.clear();
        currentMemberFactionQuery = "kartell";
        expectedMemberEntries = -1;
        if (suppressingOutput) {
            KartellSuppressFlags.finishSilentMemberCapture();
        }
        activeMemberCaptureIsSilent = false;
    }

    private boolean shouldFinishByExpectedMemberCount() {
        return expectedMemberEntries >= 0 && tempMembers.size() >= expectedMemberEntries;
    }

    private int parseExpectedMemberEntries(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(parsed, 0);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int collectMemberNamesFromPayload(String payload) {
        if (payload == null || payload.isBlank()) return 0;
        String sanitized = LEGACY_FORMATTING_PATTERN.matcher(payload).replaceAll("");
        if (sanitized.isBlank()) return 0;

        List<String> extracted = extractLikelyMemberNames(sanitized);
        if (extracted.isEmpty()) return 0;

        int added = 0;
        for (String name : extracted) {
            if (tempMembers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                tempMembers.add(name);
                added++;
            }
        }
        return added;
    }

    private List<String> extractLikelyMemberNames(String payload) {
        List<String> result = new ArrayList<>();
        if (payload == null || payload.isBlank()) return result;
        Set<String> onlineLowercase = PlayerNameUtil.getOnlinePlayerNamesLowercase(MinecraftClient.getInstance());

        // Structured lines are the most common format:
        // "1 | [Rang] Spielername | online"
        if (payload.contains("|")) {
            String[] parts = payload.split("\\|");
            if (parts.length > 0) {
                List<String> primary = extractNamesFromSegment(parts[0]);
                if (!primary.isEmpty()) {
                    return prioritizeOnlineNames(primary, onlineLowercase);
                }
            }
            for (int i = 1; i < parts.length; i++) {
                List<String> fallback = extractNamesFromSegment(parts[i]);
                for (String candidate : fallback) {
                    if (result.stream().noneMatch(s -> s.equalsIgnoreCase(candidate))) {
                        result.add(candidate);
                    }
                }
            }
            return prioritizeOnlineNames(result, onlineLowercase);
        }

        // Inline/fallback formats like ">> name1, name2"
        return prioritizeOnlineNames(extractNamesFromSegment(payload), onlineLowercase);
    }

    private List<String> extractNamesFromSegment(String segment) {
        List<String> names = new ArrayList<>();
        if (segment == null || segment.isBlank()) return names;

        String cleaned = segment
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("[(){}<>]", " ")
                .replace('\u00A0', ' ')
                .trim();

        Matcher matcher = MEMBER_NAME_TOKEN_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!isLikelyMemberName(token)) continue;
            if (names.stream().noneMatch(s -> s.equalsIgnoreCase(token))) {
                names.add(token);
            }
        }
        return names;
    }

    private boolean isLikelyMemberName(String token) {
        if (token == null || token.isBlank()) return false;
        if (!token.matches("[A-Za-z0-9_]{3,16}")) return false;
        String lower = token.toLowerCase(Locale.ROOT);
        return !MEMBER_NAME_STOPWORDS.contains(lower);
    }

    private List<String> prioritizeOnlineNames(List<String> candidates, Set<String> onlineLowercase) {
        if (candidates == null || candidates.isEmpty()) return new ArrayList<>();
        if (onlineLowercase == null || onlineLowercase.isEmpty()) return candidates;

        List<String> onlineMatches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (onlineLowercase.contains(candidate.toLowerCase(Locale.ROOT))) {
                onlineMatches.add(candidate);
            }
        }
        return onlineMatches.isEmpty() ? candidates : onlineMatches;
    }

    private boolean handleCapturingBlacklist(String raw, Text message, CallbackInfo ci) {
        boolean suppressingOutput = isBlacklistOutputSuppressed();
        Matcher entryMatcher = BLACKLIST_ENTRY_PATTERN.matcher(raw);
        if (entryMatcher.find()) {
            String name = entryMatcher.group(1);
            String rest = entryMatcher.group(2);
            if (!name.matches("[A-Za-z0-9_]{3,16}")) return true;

            collectBlacklistEntry(name, rest);
            if (suppressingOutput) {
                ci.cancel();
                return true;
            }
            replayFormattedBlacklistLine(raw, name, rest, message, ci);
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

        if (BLACKLIST_NO_RESULTS_PATTERN.matcher(trimmed).find()) {
            finishBlacklistCapture(true);
            if (suppressingOutput) {
                ci.cancel();
            }
            return true;
        }

        if (BLACKLIST_DECORATION_PATTERN.matcher(trimmed).matches()
                || BLACKLIST_HEADER_PATTERN.matcher(trimmed).find()) {
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
        return KartellSuppressFlags.suppressModBlOutput
                || activeBlacklistCaptureIsSilent
                || KartellSuppressFlags.isSilentBlacklistCaptureActive();
    }

    private void collectBlacklistEntry(String name, String rest) {
        if (tempBlacklist.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
            tempBlacklist.add(name);
            KartellMod.LOGGER.info("Temp blacklist add: {}", name);
        }

        boolean hasVogelfrei = rest != null && VOGELFREI_TOKEN_PATTERN.matcher(rest).find();
        if (hasVogelfrei) {
            if (tempVogelfrei.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                tempVogelfrei.add(name);
            }
        }

        String normalizedReason = "";
        int kills = 0;
        int price = 0;

        if (rest != null) {
            String[] segments = rest.split("\\|");
            if (segments.length > 0) {
                String grundRaw = segments[0].trim()
                        .replaceAll("(?i)\\(?vogelfrei\\)?", "")
                        .trim();
                if (!grundRaw.isEmpty() || VOGELFREI_TOKEN_PATTERN.matcher(segments[0]).find()) {
                    normalizedReason = normalizeReasonSeparator(segments[0]);
                    tempReasons.put(name, normalizedReason);
                }
            }

            Matcher km = KILLS_PATTERN.matcher(rest);
            if (km.find()) kills = Integer.parseInt(km.group(1));
            Matcher pm = PRICE_PATTERN.matcher(rest);
            if (pm.find()) price = Integer.parseInt(pm.group(1));
        }

        tempStats.put(name, new int[]{kills, price});
        if (liveBlacklistRefreshActive) {
            applyLiveBlacklistEntry(name, normalizedReason, kills, price, hasVogelfrei);
        }
    }

    private void applyLiveBlacklistEntry(String name, String normalizedReason, int kills, int price, boolean hasVogelfrei) {
        if (KartellConfig.INSTANCE.chatBlacklistPlayers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
            KartellConfig.INSTANCE.chatBlacklistPlayers.add(name);
        }

        if (normalizedReason != null && !normalizedReason.isBlank()) {
            KartellConfig.INSTANCE.blacklistReasons.put(name, normalizedReason);
        } else {
            KartellConfig.INSTANCE.blacklistReasons.remove(name);
        }
        KartellConfig.INSTANCE.blacklistStats.put(name, new int[]{Math.max(0, kills), Math.max(0, price)});

        if (hasVogelfrei) {
            if (KartellConfig.INSTANCE.vogelfreiPlayers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                KartellConfig.INSTANCE.vogelfreiPlayers.add(name);
            }
        } else {
            KartellConfig.INSTANCE.vogelfreiPlayers.removeIf(s -> s.equalsIgnoreCase(name));
        }
    }

    private void replayFormattedBlacklistLine(String raw, String name, String rest, Text originalMessage, CallbackInfo ci) {
        String replaced = raw;
        if (rest != null) {
            String formattedReason = normalizeReasonSeparator(rest.split("\\|", -1)[0]);
            String formattedRest = rest.replaceFirst("^[^|]*", Matcher.quoteReplacement(formattedReason));
            replaced = raw.replaceFirst(Pattern.quote(rest), Matcher.quoteReplacement(formattedRest));
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
            KartellConfig.INSTANCE.chatBlacklistPlayers.clear();
            KartellConfig.INSTANCE.chatBlacklistPlayers.addAll(tempBlacklist);
            KartellConfig.INSTANCE.vogelfreiPlayers.clear();
            KartellConfig.INSTANCE.vogelfreiPlayers.addAll(tempVogelfrei);
            KartellConfig.INSTANCE.blacklistReasons.clear();
            KartellConfig.INSTANCE.blacklistReasons.putAll(tempReasons);
            KartellConfig.INSTANCE.blacklistStats.clear();
            KartellConfig.INSTANCE.blacklistStats.putAll(tempStats);

            KartellMod.LOGGER.info("Blacklist geladen: {} Eintraege", tempBlacklist.size());
        } else {
            if (liveBlacklistRefreshActive) {
                restoreLiveBlacklistBackup();
                restoredBackup = true;
                KartellMod.LOGGER.info(
                        "Blacklist-Capture ohne Eintraege beendet, vorherige Liste wiederhergestellt ({} Eintraege)",
                        KartellConfig.INSTANCE.chatBlacklistPlayers.size()
                );
            } else {
                KartellMod.LOGGER.info(
                        "Blacklist-Capture ohne Eintraege beendet, vorherige Liste bleibt erhalten ({} Eintraege)",
                        KartellConfig.INSTANCE.chatBlacklistPlayers.size()
                );
            }
        }

        if (KartellSuppressFlags.suppressModBlOutput) {
            KartellSuppressFlags.suppressModBlOutput = false;
            if (KartellSuppressFlags.modBlCallback != null) {
                Runnable cb = KartellSuppressFlags.modBlCallback;
                KartellSuppressFlags.modBlCallback = null;
                MinecraftClient.getInstance().execute(cb);
            }
        } else if (activeBlacklistCaptureIsSilent || KartellSuppressFlags.isSilentBlacklistCaptureActive()) {
            KartellSuppressFlags.finishSilentBlacklistCapture();
        }
        activeBlacklistCaptureIsSilent = false;
        if (!restoredBackup) {
            resetLiveBlacklistRefreshState();
        }
        KartellSuppressFlags.clearManualBlacklistCommandMarker();
    }

    private void appendTimestampIfConfigured(Text message, CallbackInfo ci) {
        String format = KartellConfig.INSTANCE.chatTimestampFormat;
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

    private static String normalizeReasonSeparator(String reasonRaw) {
        if (reasonRaw == null) return "";
        String work = reasonRaw.trim();
        boolean hasVogelfrei = VOGELFREI_TOKEN_PATTERN.matcher(work).find();
        work = work.replaceAll("(?i)\\s*\\(?vogelfrei\\)?\\s*", "").trim();
        work = work.replaceAll("\\s*\\([123]\\s*/\\s*3\\)\\s*", " ").trim();

        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        if (!work.isEmpty()) {
            for (String part : work.split("\\s*(?:,|\\+)\\s*")) {
                String cleaned = cleanReasonToken(part);
                if (!cleaned.isEmpty()) {
                    ordered.putIfAbsent(cleaned.toLowerCase(), cleaned);
                }
            }
        }

        String joined = String.join(" + ", ordered.values());
        if (hasVogelfrei) {
            joined = joined.isEmpty() ? "(Vogelfrei)" : joined + " (Vogelfrei)";
        }
        return joined;
    }

    private static String cleanReasonToken(String raw) {
        if (raw == null) return "";

        String cleaned = raw
                .replace("\\\"", "\"")
                .replace("\\", " ")
                .replaceAll("[\\u0022\\u201C\\u201D\\u201E\\u00AB\\u00BB']", " ")
                .trim();

        return cleaned.replaceAll("\\s+", " ");
    }

    private static Text withTimestampIfEnabled(Text base) {
        String format = KartellConfig.INSTANCE.chatTimestampFormat;
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
            KartellMod.LOGGER.info("Realtime blacklist sync deferred (AFK active)");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRealtimeSyncMs < REALTIME_SYNC_COOLDOWN_MS) return;
        lastRealtimeSyncMs = now;

        if (client == null) return;

        client.execute(() -> {
            if (client.player == null || capturingBlacklist || !ServerGate.isAllowedServer(client)) return;
            KartellSuppressFlags.markSilentBlacklistRequest();
            client.player.networkHandler.sendChatCommand("blacklist");
            KartellMod.LOGGER.info("Realtime blacklist sync requested");
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
            while (messages.size() > KartellConfig.INSTANCE.maxChatHistory) {
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
                || KartellSuppressFlags.suppressStatsOutput
                || KartellSuppressFlags.pendingSilentStatsRequests > 0
                || KartellSuppressFlags.isSilentStatsCaptureActive();
        if (!silentStatsExpected) return false;

        String trimmed = raw == null ? "" : raw.trim();
        if (looksLikeStatsDetailLine(trimmed)) return true;
        if (looksLikeImplicitStatsDetailLine(trimmed)) return true;
        return containsStatsHoverSignal(text);
    }

    private boolean containsStatsHoverSignal(Text text) {
        if (text == null) return false;

        String visible = normalizeStatsProbeString(text.getString());
        if (visible != null && STATS_HOVER_KEYWORD_PATTERN.matcher(visible).find()) {
            return true;
        }

        if (text.getStyle() != null) {
            HoverEvent hover = text.getStyle().getHoverEvent();
            if (hover instanceof HoverEvent.ShowText showText) {
                Text hoverText = showText.value();
                String hoverRaw = hoverText == null ? null : normalizeStatsProbeString(hoverText.getString());
                if (hoverRaw != null && STATS_HOVER_KEYWORD_PATTERN.matcher(hoverRaw).find()) {
                    return true;
                }
            }
        }

        for (Text sibling : text.getSiblings()) {
            if (containsStatsHoverSignal(sibling)) return true;
        }
        return false;
    }

    private boolean looksLikeDashStatTailLine(String trimmedNormalized) {
        if (trimmedNormalized == null || trimmedNormalized.isEmpty()) return false;

        // Accept both direct and timestamp-prefixed stat tails, e.g. "- K/D: 1.56" or "20:42:25 - K/D: 1.56".
        String work = trimmedNormalized;
        if (work.matches("^\\d{1,2}:\\d{2}:\\d{2}\\s+.*$")) {
            work = work.replaceFirst("^\\d{1,2}:\\d{2}:\\d{2}\\s+", "");
        }

        if (!work.startsWith("-")) return false;
        if (STATS_KD_LINE_PATTERN.matcher(work).matches()) return true;

        // Broad fallback for remaining /stats tail lines with dash prefix and metric-like structure.
        return work.matches("^-\\s*[A-Za-zÄÖÜäöüß/._| -]{1,40}\\s*:?\\s*[0-9].*$");
    }

    private boolean looksLikeAfkExitTailLine(String trimmedNormalized) {
        if (trimmedNormalized == null || trimmedNormalized.isEmpty()) return false;

        String work = trimmedNormalized;
        if (work.matches("^\\d{1,2}:\\d{2}:\\d{2}\\s+.*$")) {
            work = work.replaceFirst("^\\d{1,2}:\\d{2}:\\d{2}\\s+", "");
        }

        String lower = work.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("-")) return false;

        // Known noisy tails right after AFK exit.
        if (lower.contains("immobilien")) return true;
        if (lower.matches("^-\\s*k\\s*[/\\\\|._-]?\\s*d(\\s*:?\\s*.*)?$")) return true;

        return false;
    }

    private String normalizeStatsProbeString(String input) {
        if (input == null || input.isEmpty()) return "";
        return input
                .replace('\u2044', '/') // fraction slash
                .replace('\u2215', '/') // division slash
                .replace('\\', '/')
                .replace('|', '/')
                .replace('\u2010', '-') // hyphen
                .replace('\u2011', '-') // non-breaking hyphen
                .replace('\u2012', '-') // figure dash
                .replace('\u2013', '-') // en dash
                .replace('\u2014', '-') // em dash
                .replace('\u2015', '-') // horizontal bar
                .replace('\u2212', '-') // minus sign
                .replace('\uFE58', '-') // small em dash
                .replace('\uFE63', '-') // small hyphen-minus
                .replace('\uFF0D', '-'); // fullwidth hyphen-minus
    }
}
