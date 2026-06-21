package com.betteruc.mixin;

import com.betteruc.BetterUCMod;
import com.betteruc.BetterUCSuppressFlags;
import com.betteruc.ServerGate;
import com.betteruc.client.AutoDropDrinkClient;
import com.betteruc.client.AutoFisherClient;
import com.betteruc.client.CarFindTracker;
import com.betteruc.client.ChatCustomizationFormatter;
import com.betteruc.client.ClientScheduler;
import com.betteruc.client.CommunicationDeviceTracker;
import com.betteruc.client.PingRelayClient;
import com.betteruc.client.ServerCommandUtil;
import com.betteruc.client.UserStatsClient;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.CashHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.PaydayHud;
import com.betteruc.hud.PlantageHud;
import com.betteruc.parser.BlacklistParser;
import com.betteruc.parser.StatsLineClassifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

@Mixin(ChatComponent.class)
public class ChatBlacklistMixin {

    @Shadow
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {
    }

    private static final Pattern BLACK_MONEY_PATTERN = Pattern.compile("-\\s*Schwarzgeld:\\s*([0-9.]+)\\$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYDAY_PATTERN = Pattern.compile(
            "Zeit seit PayDay:\\s*(\\d+)\\s*/\\s*(\\d+)\\s*Minuten",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FACTION_STATS_PATTERN = Pattern.compile(
            "^\\s*[-\\u2010-\\u2015\\u2212]?\\s*Fraktion\\s*:\\s*(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAYDAY_HEADER_PATTERN = Pattern.compile(
            "^\\s*[-=]+\\s*PayDay\\s*[-=]+\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BLACKLIST_HEADER_COUNT_PATTERN = Pattern.compile(
            "blacklist.*\\((\\d+)(?:/\\d+)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HACK_TIMER_PATTERN =
            Pattern.compile("\\[Polizeicomputer\\].*Gesch(?:aetzte|\\u00E4tzte) Dauer: (\\d+) Sekunden");

    private static boolean capturingBlacklist = false;
    private static boolean capturingStats = false;
    private static boolean activeStatsCaptureIsSilent = false;
    private static boolean addingTimestamp = false;
    private static int expectedBlacklistEntries = -1;

    private static long lastAfkExitStatsRefreshMs = 0L;
    private static final long AFK_EXIT_STATS_REFRESH_COOLDOWN_MS = 3000L;
    private static long forceHideStatsLinesUntilMs = 0L;
    private static final long FORCE_HIDE_STATS_WINDOW_MS = 8000L;
    private static long forceHideDashStatsLinesUntilMs = 0L;
    private static final long FORCE_HIDE_DASH_STATS_WINDOW_MS = 12000L;
    private static long forceHideAfkExitTailUntilMs = 0L;
    private static final long FORCE_HIDE_AFK_EXIT_TAIL_WINDOW_MS = 15000L;
    private static long lastSuppressedStatsLineMs = 0L;
    private static final long LINGERING_STATS_CLEANUP_WINDOW_MS = 15000L;

    private static final List<String> tempBlacklist = new ArrayList<>();
    private static final List<String> tempVogelfrei = new ArrayList<>();
    private static final Map<String, String> tempReasons = new LinkedHashMap<>();
    private static final Map<String, int[]> tempStats = new LinkedHashMap<>();
    private static final Map<String, String> tempEntryRests = new LinkedHashMap<>();

    @Inject(
            method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void scanForBlacklistTextOnly(Component message, CallbackInfo ci) {
        scanForBlacklistInternal(message, ci);
    }

    @Inject(
            method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void scanForServerSystemMessage(Component message, CallbackInfo ci) {
        scanForBlacklistInternal(message, ci);
    }

    @Inject(
            method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void scanForBlacklistWithSignature(Component message, MessageSignature signatureData, GuiMessageTag indicator, CallbackInfo ci) {
        scanForBlacklistInternal(message, ci);
    }

    private void scanForBlacklistInternal(Component message, CallbackInfo ci) {
        if (addingTimestamp) return;
        if (!ServerGate.isAllowedServer(Minecraft.getInstance())) return;
        BetterUCSuppressFlags.cleanupStaleSilentStatsState();
        if (capturingStats && !BetterUCSuppressFlags.suppressStatsOutput && !BetterUCSuppressFlags.activeSilentStatsCapture) {
            capturingStats = false;
            activeStatsCaptureIsSilent = false;
        }

        String raw = message.getString();
        AutoDropDrinkClient.handleChatLine(Minecraft.getInstance(), raw);
        AutoFisherClient.handleChatLine(Minecraft.getInstance(), raw);
        CarFindTracker.handleIncomingChat(Minecraft.getInstance(), raw);
        CommunicationDeviceTracker.handleChatLine(Minecraft.getInstance(), raw);
        PlantageHud.handleChatMessage(Minecraft.getInstance(), raw);

        if (BetterUCSuppressFlags.consumeBlacklistInfoLocalMessageBypass()) {
            appendTimestampIfConfigured(message, ci);
            return;
        }

        if (handleHackTimer(raw)) return;
        handlePaydayReset(raw);
        updateMoney(raw);

        boolean wpsHqCustomizationEnabled = BetterUCConfig.INSTANCE.chatCustomizationEnabled;
        boolean reinfCustomizationEnabled = BetterUCConfig.INSTANCE.reinfCustomizationEnabled;
        if (wpsHqCustomizationEnabled || reinfCustomizationEnabled) {
            ChatCustomizationFormatter.Result customized = ChatCustomizationFormatter.transform(
                    raw,
                    wpsHqCustomizationEnabled,
                    reinfCustomizationEnabled
            );
            if (customized != null && customized.cancelOriginal()) {
                appendCustomMessages(customized.replacementMessages(), ci);
                return;
            }
        } else {
            ChatCustomizationFormatter.clearPending();
        }

        if (isSilentStatsBlankLine(raw)) {
            ci.cancel();
            return;
        }

        if (StatsLineClassifier.isStandaloneKdStatsLine(raw)) {
            ci.cancel();
            return;
        }

        if (shouldForceHideStatsMessage(message)) {
            markStatsLineSuppressed();
            ci.cancel();
            return;
        }

        if (handleStatsHeader(raw, ci)) return;
        if (handleImplicitSilentStatsStart(raw, ci)) return;
        if (capturingStats && handleCapturingStats(raw, ci)) return;
        if (handleRealtimeBlacklistMessages(raw)) return;
        if (handleBlacklistHeader(raw, ci)) return;
        if (capturingBlacklist && handleCapturingBlacklist(raw, ci)) return;

        appendTimestampIfConfigured(message, ci);
    }

    private boolean handleHackTimer(String raw) {
        Matcher hackMatcher = HACK_TIMER_PATTERN.matcher(raw);
        if (!hackMatcher.find()) return false;
        HackTimerHud.start(Integer.parseInt(hackMatcher.group(1)));
        return true;
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
        }
    }

    private void requestSilentStatsRefreshOnAfkExit() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        if (!ServerGate.isAllowedServer(client)) return;
        long now = System.currentTimeMillis();
        if (now - lastAfkExitStatsRefreshMs < AFK_EXIT_STATS_REFRESH_COOLDOWN_MS) return;
        lastAfkExitStatsRefreshMs = now;

        client.execute(() -> {
            if (client.player == null || !ServerGate.isAllowedServer(client)) return;
            if (!ServerCommandUtil.isAutomaticSendReady(client)) return;
            if (ServerCommandUtil.sendAutomatic(client, "stats")) {
                BetterUCSuppressFlags.markSilentStatsRequest();
                forceHideStatsLinesUntilMs = System.currentTimeMillis() + FORCE_HIDE_STATS_WINDOW_MS;
                forceHideDashStatsLinesUntilMs = System.currentTimeMillis() + FORCE_HIDE_DASH_STATS_WINDOW_MS;
                ClientScheduler.runDelayedOnClient(client, BetterUCSuppressFlags.SILENT_STATS_TIMEOUT_MS,
                        BetterUCSuppressFlags::cleanupStaleSilentStatsState);
                BetterUCMod.LOGGER.info("AFK-Exit: silent /stats refresh requested");
            }
        });
    }

    private void updateMoney(String raw) {
        BankBalanceHud.updateFromChatLine(raw);
        CashHud.updateFromStatsLine(raw);
        updateCurrentFaction(raw);

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

        UserStatsClient.handleChatLine(Minecraft.getInstance(), raw);
    }

    private void updateCurrentFaction(String raw) {
        if (raw == null || raw.isBlank()) return;
        Matcher factionMatcher = FACTION_STATS_PATTERN.matcher(raw.trim());
        if (!factionMatcher.find()) return;
        if (BetterUCConfig.updateCurrentPlayerFactionFromStats(factionMatcher.group(1))) {
            PingRelayClient.refreshIdentity(Minecraft.getInstance());
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
        long mainWindowUntilMs = Math.max(
                forceHideStatsLinesUntilMs,
                BetterUCSuppressFlags.forceHideStatsOutputUntilMs
        );
        long dashWindowUntilMs = Math.max(
                forceHideDashStatsLinesUntilMs,
                BetterUCSuppressFlags.forceHideDashStatsOutputUntilMs
        );
        return StatsLineClassifier.shouldForceHideLine(
                raw,
                System.currentTimeMillis(),
                forceHideAfkExitTailUntilMs,
                mainWindowUntilMs,
                dashWindowUntilMs
        );
    }

    private boolean shouldForceHideStatsMessage(Component text) {
        if (text == null) return false;
        if (shouldForceHideStatsLine(text.getString())) return true;
        if (isSilentStatsBlankLine(text.getString())) return true;

        return false;
    }

    private boolean isSilentStatsBlankLine(String raw) {
        if (raw == null || !raw.trim().isEmpty()) return false;
        return capturingStats
                || activeStatsCaptureIsSilent
                || BetterUCSuppressFlags.suppressStatsOutput
                || BetterUCSuppressFlags.pendingSilentStatsRequests > 0
                || BetterUCSuppressFlags.isSilentStatsCaptureActive()
                || isStatsHideWindowActive();
    }

    private boolean isStatsHideWindowActive() {
        long now = System.currentTimeMillis();
        return now <= Math.max(forceHideStatsLinesUntilMs, BetterUCSuppressFlags.forceHideStatsOutputUntilMs)
                || now <= Math.max(forceHideDashStatsLinesUntilMs, BetterUCSuppressFlags.forceHideDashStatsOutputUntilMs)
                || now <= forceHideAfkExitTailUntilMs
                || now - lastSuppressedStatsLineMs <= LINGERING_STATS_CLEANUP_WINDOW_MS;
    }

    private static void markStatsLineSuppressed() {
        lastSuppressedStatsLineMs = System.currentTimeMillis();
    }

    private void finishStatsCapture() {
        capturingStats = false;
        if (activeStatsCaptureIsSilent || BetterUCSuppressFlags.isSilentStatsCaptureActive()) {
            BetterUCSuppressFlags.finishSilentStatsCapture();
        }
        activeStatsCaptureIsSilent = false;
    }

    private boolean handleBlacklistHeader(String raw, CallbackInfo ci) {
        if (!BlacklistParser.isHeader(raw)) return false;

        if (!BetterUCSuppressFlags.suppressModBlOutput) {
            return false;
        }

        capturingBlacklist = true;

        tempBlacklist.clear();
        tempVogelfrei.clear();
        tempReasons.clear();
        tempStats.clear();
        tempEntryRests.clear();
        expectedBlacklistEntries = parseExpectedBlacklistEntries(raw);
        BetterUCMod.LOGGER.info("Blacklist header erkannt, lese Eintraege...");

        ci.cancel();
        return true;
    }

    private boolean handleCapturingBlacklist(String raw, CallbackInfo ci) {
        boolean suppressingOutput = isBlacklistOutputSuppressed();
        BlacklistParser.Entry entry = BlacklistParser.parseEntry(raw);
        if (entry != null) {
            collectBlacklistEntry(entry);
            if (expectedBlacklistEntries >= 0 && tempBlacklist.size() >= expectedBlacklistEntries) {
                finishBlacklistCapture(false);
            }
            if (suppressingOutput) {
                ci.cancel();
            }
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
        return BetterUCSuppressFlags.suppressModBlOutput;
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
        if (entry.rest() != null && !entry.rest().isBlank()) {
            tempEntryRests.put(name, entry.rest().trim());
        }
        completePendingBlacklistInfoIfMatched(entry);
    }

    private void completePendingBlacklistInfoIfMatched(BlacklistParser.Entry entry) {
        if (!BetterUCSuppressFlags.completeBlacklistInfoLookupIfMatches(entry.name())) return;

        applySingleBlacklistEntry(entry);
        if (BetterUCSuppressFlags.modBlCallback != null) {
            Runnable cb = BetterUCSuppressFlags.modBlCallback;
            BetterUCSuppressFlags.modBlCallback = null;
            Minecraft.getInstance().execute(cb);
        }
    }

    private void applySingleBlacklistEntry(BlacklistParser.Entry entry) {
        String name = entry.name();
        BetterUCConfig.addChatBlacklistPlayer(name);

        if (!entry.normalizedReason().isBlank()) {
            BetterUCConfig.INSTANCE.blacklistReasons.put(name, entry.normalizedReason());
        }
        BetterUCConfig.INSTANCE.blacklistStats.put(name, new int[]{Math.max(0, entry.kills()), Math.max(0, entry.price())});
        if (entry.rest() != null && !entry.rest().isBlank()) {
            BetterUCConfig.INSTANCE.blacklistEntryRests.put(name, entry.rest().trim());
        }

        if (entry.hasVogelfrei()) {
            BetterUCConfig.addVogelfreiPlayer(name);
        } else {
            BetterUCConfig.removeVogelfreiPlayer(name);
        }
        BetterUCConfig.refreshBlacklistNameCaches();
    }

    private void finishBlacklistCapture(boolean forceApplyEvenIfEmpty) {
        capturingBlacklist = false;

        if (forceApplyEvenIfEmpty || !tempBlacklist.isEmpty()) {
            BetterUCConfig.INSTANCE.chatBlacklistPlayers.clear();
            BetterUCConfig.INSTANCE.chatBlacklistPlayers.addAll(tempBlacklist);
            BetterUCConfig.INSTANCE.vogelfreiPlayers.clear();
            BetterUCConfig.INSTANCE.vogelfreiPlayers.addAll(tempVogelfrei);
            BetterUCConfig.INSTANCE.blacklistReasons.clear();
            BetterUCConfig.INSTANCE.blacklistReasons.putAll(tempReasons);
            BetterUCConfig.INSTANCE.blacklistStats.clear();
            BetterUCConfig.INSTANCE.blacklistStats.putAll(tempStats);
            BetterUCConfig.INSTANCE.blacklistEntryRests.clear();
            BetterUCConfig.INSTANCE.blacklistEntryRests.putAll(tempEntryRests);
            BetterUCConfig.markBlacklistSyncComplete();

            BetterUCMod.LOGGER.info("Blacklist geladen: {} Eintraege", tempBlacklist.size());
        } else {
            BetterUCMod.LOGGER.info(
                    "Blacklist-Capture ohne Eintraege beendet, vorherige Liste bleibt erhalten ({} Eintraege)",
                    BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
            );
        }

        if (BetterUCSuppressFlags.suppressModBlOutput) {
            BetterUCSuppressFlags.suppressModBlOutput = false;
            if (BetterUCSuppressFlags.modBlCallback != null) {
                Runnable cb = BetterUCSuppressFlags.modBlCallback;
                BetterUCSuppressFlags.modBlCallback = null;
                Minecraft.getInstance().execute(cb);
            }
        }
        BetterUCSuppressFlags.clearBlacklistInfoLookup();
        expectedBlacklistEntries = -1;
    }

    private int parseExpectedBlacklistEntries(String raw) {
        Matcher matcher = BLACKLIST_HEADER_COUNT_PATTERN.matcher(raw == null ? "" : raw);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void appendTimestampIfConfigured(Component message, CallbackInfo ci) {
        if (!BetterUCConfig.INSTANCE.chatTimestampsEnabled) return;
        String format = BetterUCConfig.INSTANCE.chatTimestampFormat;
        if (format == null || format.isEmpty()) return;

        try {
            String timestamp = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern(format));
            Component newMessage = Component.literal("\u00A77" + timestamp + " ").append(message);
            ci.cancel();
            addingTimestamp = true;
            ((ChatComponent) (Object) this).addClientSystemMessage(newMessage);
        } catch (Exception ignored) {
        } finally {
            addingTimestamp = false;
        }
    }

    private void appendCustomMessages(List<Component> messages, CallbackInfo ci) {
        ci.cancel();
        if (messages == null || messages.isEmpty()) return;

        addingTimestamp = true;
        try {
            for (Component line : messages) {
                ((ChatComponent) (Object) this).addClientSystemMessage(withTimestampIfEnabled(line));
            }
        } finally {
            addingTimestamp = false;
        }
    }

    private static Component withTimestampIfEnabled(Component base) {
        if (!BetterUCConfig.INSTANCE.chatTimestampsEnabled) return base;
        String format = BetterUCConfig.INSTANCE.chatTimestampFormat;
        if (format == null || format.isEmpty()) return base;

        try {
            String timestamp = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern(format));
            return Component.literal("\u00A77" + timestamp + " ").append(base);
        } catch (Exception ignored) {
            return base;
        }
    }

    @Inject(method = "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void suppressStatsAtLineLevel(GuiMessage line, CallbackInfo ci) {
        if (line == null) return;
        Component content = line.content();
        if (content == null) return;
        if (shouldSuppressSilentStatsLine(content)) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void suppressStatsAtVisibleLineLevel(GuiMessage line, CallbackInfo ci) {
        if (line == null) return;
        Component content = line.content();
        if (content == null) return;
        if (shouldSuppressSilentStatsLine(content)) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V", at = @At("TAIL"), require = 0)
    private void extendMessageHistory(GuiMessage message, CallbackInfo ci) {
        if (allMessages == null) return;

        boolean removedStatsLine = allMessages.removeIf(this::isLingeringSilentStatsLine);
        if (removedStatsLine) {
            markStatsLineSuppressed();
            refreshTrimmedMessages();
        }
        while (allMessages.size() > BetterUCConfig.INSTANCE.maxChatHistory) {
            allMessages.remove(allMessages.size() - 1);
        }
    }

    private boolean isLingeringSilentStatsLine(GuiMessage line) {
        if (line == null) return false;
        Component content = line.content();
        if (content == null) return false;
        if (shouldSuppressSilentStatsLine(content)) return true;
        return isStatsHideWindowActive() && StatsLineClassifier.isKdStatsLine(content.getString());
    }

    private boolean shouldSuppressSilentStatsLine(Component text) {
        if (text == null) return false;
        String raw = text.getString();
        if (shouldForceHideStatsMessage(text)) {
            markStatsLineSuppressed();
            return true;
        }
        if (StatsLineClassifier.isStandaloneKdStatsLine(raw)) return true;

        boolean silentStatsExpected = capturingStats
                || activeStatsCaptureIsSilent
                || BetterUCSuppressFlags.suppressStatsOutput
                || BetterUCSuppressFlags.pendingSilentStatsRequests > 0
                || BetterUCSuppressFlags.isSilentStatsCaptureActive();
        if (!silentStatsExpected) return false;

        String trimmed = raw == null ? "" : raw.trim();
        if (StatsLineClassifier.isDetailLine(trimmed)) return true;
        if (StatsLineClassifier.isImplicitDetailLine(trimmed)) return true;
        return false;
    }

}
