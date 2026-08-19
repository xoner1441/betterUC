package com.betteruc.client;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.config.SecondChatFilterConfig;
import com.betteruc.config.SecondChatTabConfig;
import com.betteruc.config.SecondChatWindowConfig;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class SecondChatManager {

    public static final String MAIN_TAB_ID = "main";
    public static final String PRIMARY_WINDOW_ID = "primary";
    public static final int MAX_TABS = 8;
    public static final int MAX_WINDOWS = 4;
    private static final long HQ_BLOCK_WINDOW_MS = 2_500L;
    private static final long PAYDAY_BLOCK_WINDOW_MS = 8_000L;
    private static final int WANTED_INFO_POINTS = 1;
    private static final int WANTED_INFO_REASON = 1 << 1;
    private static final int WANTED_INFO_SINCE = 1 << 2;
    private static final int WANTED_INFO_OFFICER = 1 << 3;
    private static final int WANTED_INFO_ALL_DETAILS = WANTED_INFO_POINTS
            | WANTED_INFO_REASON
            | WANTED_INFO_SINCE
            | WANTED_INFO_OFFICER;
    private static final Pattern WANTED_LIST_ENTRY_PATTERN = Pattern.compile(
            "^(?:\\[[a-z0-9_]+\\] )*[a-z0-9_]{1,32} \\d+ wps(?: .+)?$"
    );
    private static final Map<String, Deque<Entry>> HISTORIES = new LinkedHashMap<>();
    private static final Map<String, Integer> UNREAD_COUNTS = new LinkedHashMap<>();
    private static final Map<String, Long> FIRST_UNREAD_AT = new LinkedHashMap<>();
    private static final Map<String, Long> ATTENTION_UNTIL = new LinkedHashMap<>();
    private static final Map<String, Integer> SCROLL_OFFSETS = new LinkedHashMap<>();
    private static final Map<String, Boolean> NEW_WHILE_SCROLLED = new LinkedHashMap<>();
    private static long revision;
    private static long lastMentionSoundAtMs;
    private static long hqContinuationUntilMs;
    private static int hqContinuationLinesRemaining;
    private static long wantedInfoContinuationUntilMs;
    private static int wantedInfoDetailsRemaining;
    private static long paydayContinuationUntilMs;
    private static boolean paydayBlockActive;

    private SecondChatManager() {
    }

    public static RouteResult route(Component message) {
        return route(message, false, false);
    }

    public static RouteResult route(Component message, boolean knownReinforcement) {
        return route(message, knownReinforcement, false);
    }

    public static RouteResult route(Component message, boolean knownReinforcement, boolean knownHq) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (!config.secondChatEnabled || message == null) {
            return RouteResult.KEEP_MAIN;
        }

        String raw = message.getString();
        if (raw == null || raw.isBlank()) {
            return RouteResult.KEEP_MAIN;
        }

        String normalized = normalize(raw);
        Minecraft client = Minecraft.getInstance();
        String playerName = client.player == null ? "" : normalize(client.player.getName().getString());
        boolean ownNameMatch = !playerName.isBlank() && containsWord(normalized, playerName);
        boolean hqMatch = knownHq || classifyHqOrWps(normalized);
        boolean paydayMatch = classifyPayday(normalized);
        boolean reinforcementMatch = knownReinforcement || isReinforcementLine(normalized);
        boolean suppressMain = false;
        boolean shouldPlaySound = false;

        for (SecondChatTabConfig tab : tabs()) {
            FilterResult result = evaluateFilters(
                    tab, raw, normalized, ownNameMatch, hqMatch, paydayMatch, reinforcementMatch);
            suppressMain |= result.suppressMain;
            shouldPlaySound |= result.playSound;
            if (result.display) {
                boolean mentioned = ownNameMatch
                        || matchesTerms(raw, normalized, tab.mentionTerms, false, "contains");
                boolean highlighted = result.highlighted || mentioned;
                int highlightColor = mentioned ? tab.mentionColor : result.highlightColor;
                String tooltip = mentioned && result.tooltip.isBlank()
                        ? "Erwähnung"
                        : result.tooltip;
                append(tab, message.copy(), highlighted, highlightColor, tooltip, mentioned);
                shouldPlaySound |= mentioned && tab.mentionSound;
            }
        }

        if (shouldPlaySound && config.secondChatMentionSoundEnabled) {
            playMentionSound(client);
        }
        return suppressMain ? RouteResult.HIDE_MAIN : RouteResult.KEEP_MAIN;
    }

    public static void logSuppressedMessage(Component message) {
        if (message == null) return;
        String text = message.getString();
        if (text == null || text.isBlank()) return;
        BetterUCMod.LOGGER.info("[betterUC Second Chat] {}", singleLineForLog(text));
    }

    static String singleLineForLog(String text) {
        return (text == null ? "" : text)
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    public static synchronized List<Entry> snapshot() {
        return snapshot(BetterUCConfig.INSTANCE.secondChatActiveTabId);
    }

    public static synchronized List<Entry> snapshot(String tabId) {
        Deque<Entry> history = HISTORIES.get(tabId);
        return history == null ? List.of() : List.copyOf(history);
    }

    public static synchronized void clear() {
        clear(BetterUCConfig.INSTANCE.secondChatActiveTabId);
    }

    public static synchronized void clear(String tabId) {
        Deque<Entry> history = HISTORIES.get(tabId);
        if (history != null && !history.isEmpty()) {
            history.clear();
            revision++;
        }
        UNREAD_COUNTS.remove(tabId);
        FIRST_UNREAD_AT.remove(tabId);
        ATTENTION_UNTIL.remove(tabId);
        SCROLL_OFFSETS.remove(tabId);
        NEW_WHILE_SCROLLED.remove(tabId);
    }

    public static synchronized void handleMainChatCleared() {
        boolean changed = false;
        for (SecondChatTabConfig tab : tabs()) {
            if (tab.antiChatClear) {
                continue;
            }
            Deque<Entry> history = HISTORIES.get(tab.id);
            if (history != null && !history.isEmpty()) {
                history.clear();
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
    }

    public static synchronized long revision() {
        return revision;
    }

    public static List<SecondChatTabConfig> tabs() {
        BetterUCConfig.sanitizeSecondChat();
        return List.copyOf(BetterUCConfig.INSTANCE.secondChatTabs);
    }

    public static SecondChatTabConfig activeTab() {
        return activeTab(PRIMARY_WINDOW_ID);
    }

    public static SecondChatTabConfig activeTab(String windowId) {
        if (PRIMARY_WINDOW_ID.equals(windowId)) {
            SecondChatTabConfig tab = findTab(BetterUCConfig.INSTANCE.secondChatActiveTabId);
            return tab != null && PRIMARY_WINDOW_ID.equals(tab.windowId) ? tab : null;
        }
        SecondChatWindowConfig window = findWindow(windowId);
        SecondChatTabConfig tab = window == null ? null : findTab(window.activeTabId);
        return tab != null && windowId.equals(tab.windowId) ? tab : null;
    }

    public static SecondChatTabConfig findTab(String id) {
        if (id == null || MAIN_TAB_ID.equals(id)) {
            return null;
        }
        for (SecondChatTabConfig tab : BetterUCConfig.INSTANCE.secondChatTabs) {
            if (id.equals(tab.id)) {
                return tab;
            }
        }
        return null;
    }

    public static boolean isMainTabActive() {
        return activeTab() == null;
    }

    public static void selectTab(String id) {
        selectTab(PRIMARY_WINDOW_ID, id);
    }

    public static void selectTab(String windowId, String id) {
        if (PRIMARY_WINDOW_ID.equals(windowId)) {
            String target = findTab(id) == null ? MAIN_TAB_ID : id;
            if (!target.equals(BetterUCConfig.INSTANCE.secondChatActiveTabId)) {
                BetterUCConfig.INSTANCE.secondChatActiveTabId = target;
                revision++;
                BetterUCConfig.save();
            }
            markRead(target);
            return;
        }
        SecondChatWindowConfig window = findWindow(windowId);
        SecondChatTabConfig target = findTab(id);
        if (window == null || target == null || !windowId.equals(target.windowId)) {
            return;
        }
        if (!target.id.equals(window.activeTabId)) {
            window.activeTabId = target.id;
            revision++;
            BetterUCConfig.save();
        }
        markRead(target.id);
    }

    public static SecondChatTabConfig createTab() {
        return createTab(PRIMARY_WINDOW_ID);
    }

    public static SecondChatTabConfig createTab(String windowId) {
        BetterUCConfig.sanitizeSecondChat();
        if (BetterUCConfig.INSTANCE.secondChatTabs.size() >= MAX_TABS) {
            return null;
        }
        int number = BetterUCConfig.INSTANCE.secondChatTabs.size() + 2;
        SecondChatTabConfig tab = new SecondChatTabConfig("Chat " + number);
        tab.windowId = findWindow(windowId) == null ? PRIMARY_WINDOW_ID : windowId;
        tab.sanitize(number - 2);
        BetterUCConfig.INSTANCE.secondChatTabs.add(tab);
        if (PRIMARY_WINDOW_ID.equals(tab.windowId)) {
            BetterUCConfig.INSTANCE.secondChatActiveTabId = tab.id;
        } else {
            findWindow(tab.windowId).activeTabId = tab.id;
        }
        revision++;
        BetterUCConfig.save();
        return tab;
    }

    public static SecondChatTabConfig createWindow() {
        BetterUCConfig.sanitizeSecondChat();
        if (BetterUCConfig.INSTANCE.secondChatWindows.size() >= MAX_WINDOWS
                || BetterUCConfig.INSTANCE.secondChatTabs.size() >= MAX_TABS) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        int screenWidth = ClientCompat.scaledWindowWidth(client, 854);
        SecondChatWindowConfig window = new SecondChatWindowConfig();
        window.x = Math.max(8, screenWidth - window.width - 8);
        window.y = 42 + BetterUCConfig.INSTANCE.secondChatWindows.size() * 24;
        int number = BetterUCConfig.INSTANCE.secondChatTabs.size() + 2;
        SecondChatTabConfig tab = new SecondChatTabConfig("Chat " + number);
        tab.windowId = window.id;
        tab.sanitize(number - 2);
        window.activeTabId = tab.id;
        BetterUCConfig.INSTANCE.secondChatTabs.add(tab);
        BetterUCConfig.INSTANCE.secondChatWindows.add(window);
        revision++;
        BetterUCConfig.save();
        return tab;
    }

    public static SecondChatTabConfig duplicateTab(String id) {
        BetterUCConfig.sanitizeSecondChat();
        SecondChatTabConfig source = findTab(id);
        if (source == null || BetterUCConfig.INSTANCE.secondChatTabs.size() >= MAX_TABS) {
            return null;
        }
        SecondChatTabConfig copy = source.copyAs(uniqueTabName(source.name + " Kopie"));
        copy.windowId = source.windowId;
        copy.sanitize(BetterUCConfig.INSTANCE.secondChatTabs.size());
        int sourceIndex = BetterUCConfig.INSTANCE.secondChatTabs.indexOf(source);
        BetterUCConfig.INSTANCE.secondChatTabs.add(sourceIndex + 1, copy);
        if (PRIMARY_WINDOW_ID.equals(copy.windowId)) {
            BetterUCConfig.INSTANCE.secondChatActiveTabId = copy.id;
        } else {
            SecondChatWindowConfig window = findWindow(copy.windowId);
            if (window != null) {
                window.activeTabId = copy.id;
            }
        }
        revision++;
        BetterUCConfig.save();
        return copy;
    }

    public static boolean moveTabToWindow(String tabId, String targetWindowId) {
        SecondChatTabConfig tab = findTab(tabId);
        if (tab == null) {
            return false;
        }
        String target = targetWindowId == null ? PRIMARY_WINDOW_ID : targetWindowId;
        if (!PRIMARY_WINDOW_ID.equals(target) && findWindow(target) == null) {
            return false;
        }
        String previousWindowId = tab.windowId;
        tab.windowId = target;
        repairActiveSelection(previousWindowId, tab.id);
        if (PRIMARY_WINDOW_ID.equals(target)) {
            BetterUCConfig.INSTANCE.secondChatActiveTabId = tab.id;
        } else {
            findWindow(target).activeTabId = tab.id;
        }
        removeEmptyWindow(previousWindowId);
        revision++;
        BetterUCConfig.save();
        return true;
    }

    public static boolean moveTabToNewWindow(String tabId) {
        BetterUCConfig.sanitizeSecondChat();
        SecondChatTabConfig tab = findTab(tabId);
        if (tab == null || BetterUCConfig.INSTANCE.secondChatWindows.size() >= MAX_WINDOWS) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        int screenWidth = ClientCompat.scaledWindowWidth(client, 854);
        SecondChatWindowConfig window = new SecondChatWindowConfig();
        window.x = Math.max(8, screenWidth - window.width - 8);
        window.y = 42 + BetterUCConfig.INSTANCE.secondChatWindows.size() * 24;
        window.activeTabId = tab.id;
        String previousWindowId = tab.windowId;
        tab.windowId = window.id;
        repairActiveSelection(previousWindowId, tab.id);
        BetterUCConfig.INSTANCE.secondChatWindows.add(window);
        removeEmptyWindow(previousWindowId);
        revision++;
        BetterUCConfig.save();
        return true;
    }

    public static boolean reorderTab(String tabId, String beforeTabId) {
        SecondChatTabConfig moving = findTab(tabId);
        SecondChatTabConfig target = findTab(beforeTabId);
        if (moving == null || target == null || moving == target
                || !moving.windowId.equals(target.windowId)) {
            return false;
        }
        List<SecondChatTabConfig> tabs = BetterUCConfig.INSTANCE.secondChatTabs;
        tabs.remove(moving);
        int targetIndex = tabs.indexOf(target);
        tabs.add(Math.max(0, targetIndex), moving);
        revision++;
        BetterUCConfig.save();
        return true;
    }

    public static boolean moveTabBy(String tabId, int direction) {
        SecondChatTabConfig moving = findTab(tabId);
        if (moving == null || direction == 0) {
            return false;
        }
        List<SecondChatTabConfig> windowTabs = tabsForWindow(moving.windowId);
        int currentIndex = windowTabs.indexOf(moving);
        int targetIndex = currentIndex + Integer.signum(direction);
        if (currentIndex < 0 || targetIndex < 0 || targetIndex >= windowTabs.size()) {
            return false;
        }
        SecondChatTabConfig neighbor = windowTabs.get(targetIndex);
        if (direction < 0) {
            return reorderTab(moving.id, neighbor.id);
        }
        return reorderTab(neighbor.id, moving.id);
    }

    public static List<SecondChatWindowConfig> windows() {
        BetterUCConfig.sanitizeSecondChat();
        return List.copyOf(BetterUCConfig.INSTANCE.secondChatWindows);
    }

    public static SecondChatWindowConfig findWindow(String id) {
        if (id == null || PRIMARY_WINDOW_ID.equals(id)) {
            return null;
        }
        for (SecondChatWindowConfig window : BetterUCConfig.INSTANCE.secondChatWindows) {
            if (id.equals(window.id)) {
                return window;
            }
        }
        return null;
    }

    public static List<SecondChatTabConfig> tabsForWindow(String windowId) {
        String target = windowId == null ? PRIMARY_WINDOW_ID : windowId;
        return tabs().stream().filter(tab -> target.equals(tab.windowId)).toList();
    }

    public static boolean deleteTab(String id) {
        SecondChatTabConfig tab = findTab(id);
        if (tab == null) {
            return false;
        }
        BetterUCConfig.INSTANCE.secondChatTabs.remove(tab);
        synchronized (SecondChatManager.class) {
            HISTORIES.remove(id);
            clearRuntimeState(id);
            revision++;
        }
        if (PRIMARY_WINDOW_ID.equals(tab.windowId)) {
            BetterUCConfig.INSTANCE.secondChatActiveTabId = MAIN_TAB_ID;
        } else {
            SecondChatWindowConfig window = findWindow(tab.windowId);
            List<SecondChatTabConfig> remaining = tabsForWindow(tab.windowId);
            if (window != null && remaining.isEmpty()) {
                BetterUCConfig.INSTANCE.secondChatWindows.remove(window);
            } else if (window != null) {
                window.activeTabId = remaining.get(0).id;
            }
        }
        BetterUCConfig.save();
        return true;
    }

    public static boolean deleteWindow(String windowId) {
        SecondChatWindowConfig window = findWindow(windowId);
        if (window == null) {
            return false;
        }
        List<SecondChatTabConfig> removed = tabsForWindow(windowId);
        BetterUCConfig.INSTANCE.secondChatTabs.removeAll(removed);
        BetterUCConfig.INSTANCE.secondChatWindows.remove(window);
        synchronized (SecondChatManager.class) {
            for (SecondChatTabConfig tab : removed) {
                HISTORIES.remove(tab.id);
                clearRuntimeState(tab.id);
            }
            revision++;
        }
        BetterUCConfig.save();
        return true;
    }

    public static String nextMode(String current) {
        return switch (current == null ? "" : current.toLowerCase(Locale.ROOT)) {
            case "copy" -> "move";
            case "move" -> "highlight";
            case "highlight" -> "ignore";
            case "ignore" -> "off";
            default -> "copy";
        };
    }

    public static String modeLabel(String mode) {
        return switch (mode == null ? "" : mode.toLowerCase(Locale.ROOT)) {
            case "copy" -> "Kopieren";
            case "move" -> "Verschieben";
            case "highlight" -> "Hervorheben";
            case "ignore" -> "Ignorieren";
            default -> "Aus";
        };
    }

    public static synchronized int unreadCount(String tabId) {
        return UNREAD_COUNTS.getOrDefault(tabId, 0);
    }

    public static synchronized long firstUnreadAt(String tabId) {
        return FIRST_UNREAD_AT.getOrDefault(tabId, 0L);
    }

    public static synchronized boolean needsAttention(String tabId) {
        return ATTENTION_UNTIL.getOrDefault(tabId, 0L) > System.currentTimeMillis();
    }

    public static synchronized int scrollOffset(String tabId) {
        return Math.max(0, SCROLL_OFFSETS.getOrDefault(tabId, 0));
    }

    public static synchronized void scrollBy(String tabId, int amount, int maximum) {
        if (tabId == null) return;
        int next = Math.max(0, Math.min(maximum, scrollOffset(tabId) + amount));
        SCROLL_OFFSETS.put(tabId, next);
        if (next == 0) {
            NEW_WHILE_SCROLLED.remove(tabId);
        }
        revision++;
    }

    public static synchronized boolean hasNewWhileScrolled(String tabId) {
        return NEW_WHILE_SCROLLED.getOrDefault(tabId, false);
    }

    public static synchronized void jumpToNewest(String tabId) {
        SCROLL_OFFSETS.remove(tabId);
        NEW_WHILE_SCROLLED.remove(tabId);
        FIRST_UNREAD_AT.remove(tabId);
        revision++;
    }

    public static synchronized void clearFirstUnread(String tabId) {
        FIRST_UNREAD_AT.remove(tabId);
    }

    public static synchronized void setScrollOffset(String tabId, int offset, int maximum) {
        if (tabId == null) return;
        int next = Math.max(0, Math.min(maximum, offset));
        if (next == 0) {
            SCROLL_OFFSETS.remove(tabId);
            NEW_WHILE_SCROLLED.remove(tabId);
        } else {
            SCROLL_OFFSETS.put(tabId, next);
        }
        revision++;
    }

    public static boolean testFilter(SecondChatFilterConfig filter, String sample) {
        if (filter == null || sample == null || sample.isBlank()) {
            return false;
        }
        String normalized = normalize(sample);
        Minecraft client = Minecraft.getInstance();
        String playerName = client.player == null ? "" : normalize(client.player.getName().getString());
        return matchesFilter(filter, sample, normalized,
                !playerName.isBlank() && containsWord(normalized, playerName),
                isHqOrWpsLine(normalized) || formattedHqContinuationLines(normalized) >= 0,
                isPaydayLine(normalized),
                isReinforcementLine(normalized));
    }

    public static SecondChatFilterConfig addPreset(String tabId, String preset) {
        SecondChatTabConfig tab = findTab(tabId);
        if (tab == null || tab.filters.size() >= 24) return null;
        SecondChatFilterConfig filter = switch (preset == null ? "" : preset) {
            case "hq" -> new SecondChatFilterConfig("WPS / HQ", "hq", "copy");
            case "reinf" -> new SecondChatFilterConfig("Reinforcements", "reinf", "copy");
            case "payday" -> new SecondChatFilterConfig("PayDay", "payday", "copy");
            case "advertising" -> new SecondChatFilterConfig("Werbung", "custom", "copy");
            default -> null;
        };
        if (filter == null) return null;
        if ("advertising".equals(preset)) {
            filter.includeText = "Werbung;Verkaufe;Verkauf;Suche;Kaufe;Angebot";
        }
        filter.sanitize(tab.filters.size());
        tab.filters.add(filter);
        BetterUCConfig.save();
        revision++;
        return filter;
    }

    private static synchronized void append(
            SecondChatTabConfig tab,
            Component message,
            boolean highlighted,
            int highlightColor,
            String tooltip,
            boolean mentioned
    ) {
        Deque<Entry> history = HISTORIES.computeIfAbsent(tab.id, ignored -> new ArrayDeque<>());
        Entry previous = history.peekLast();
        if (tab.combineEqualMessages && previous != null
                && previous.highlighted == highlighted
                && previous.highlightColor == highlightColor
                && previous.tooltip.equals(tooltip)
                && previous.message.getString().equals(message.getString())) {
            history.removeLast();
            history.addLast(new Entry(
                    previous.message,
                    highlighted,
                    highlightColor,
                    tooltip,
                    System.currentTimeMillis(),
                    previous.repeatCount + 1
            ));
        } else {
            history.addLast(new Entry(
                    message,
                    highlighted,
                    highlightColor,
                    tooltip,
                    System.currentTimeMillis(),
                    1
            ));
        }
        while (history.size() > tab.messageLimit) {
            history.removeFirst();
        }
        if (!isTabActive(tab)) {
            if (UNREAD_COUNTS.getOrDefault(tab.id, 0) == 0) {
                FIRST_UNREAD_AT.put(tab.id, System.currentTimeMillis());
            }
            UNREAD_COUNTS.merge(tab.id, 1, Integer::sum);
            if (mentioned) {
                ATTENTION_UNTIL.put(tab.id, System.currentTimeMillis() + 4_000L);
            }
        }
        if (scrollOffset(tab.id) > 0) {
            NEW_WHILE_SCROLLED.put(tab.id, true);
        }
        revision++;
    }

    private static void removeEmptyWindow(String windowId) {
        if (windowId == null || PRIMARY_WINDOW_ID.equals(windowId)) {
            return;
        }
        SecondChatWindowConfig window = findWindow(windowId);
        if (window != null && tabsForWindow(windowId).isEmpty()) {
            BetterUCConfig.INSTANCE.secondChatWindows.remove(window);
        }
    }

    private static void repairActiveSelection(String windowId, String movedTabId) {
        if (windowId == null) {
            return;
        }
        List<SecondChatTabConfig> remaining = tabsForWindow(windowId);
        if (PRIMARY_WINDOW_ID.equals(windowId)) {
            if (movedTabId.equals(BetterUCConfig.INSTANCE.secondChatActiveTabId)) {
                BetterUCConfig.INSTANCE.secondChatActiveTabId = remaining.isEmpty()
                        ? MAIN_TAB_ID
                        : remaining.get(0).id;
            }
            return;
        }
        SecondChatWindowConfig window = findWindow(windowId);
        if (window != null && movedTabId.equals(window.activeTabId) && !remaining.isEmpty()) {
            window.activeTabId = remaining.get(0).id;
        }
    }

    private static String uniqueTabName(String preferred) {
        String base = preferred == null || preferred.isBlank() ? "Chat" : preferred.trim();
        String candidate = base;
        int suffix = 2;
        while (tabNameExists(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static boolean tabNameExists(String candidate) {
        for (SecondChatTabConfig tab : tabs()) {
            if (tab.name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static FilterResult evaluateFilters(
            SecondChatTabConfig tab,
            String raw,
            String normalized,
            boolean ownNameMatch,
            boolean hqMatch,
            boolean paydayMatch,
            boolean reinforcementMatch
    ) {
        boolean suppressMain = false;
        boolean playSound = false;
        SecondChatFilterConfig winner = null;
        int winnerPriority = 0;

        for (SecondChatFilterConfig filter : tab.filters) {
            if (!filter.enabled || !matchesFilter(
                    filter, raw, normalized, ownNameMatch,
                    hqMatch, paydayMatch, reinforcementMatch)) {
                continue;
            }
            playSound |= filter.playSound;
            suppressMain |= filter.hideMessage || "move".equals(filter.mode);
            if ("off".equals(filter.mode) || "ignore".equals(filter.mode)) {
                continue;
            }
            int priority = actionPriority(filter.mode);
            if (priority > winnerPriority) {
                winner = filter;
                winnerPriority = priority;
            }
        }

        if (winner == null) {
            return new FilterResult(false, suppressMain, false, 0, "", playSound);
        }
        boolean highlighted = "highlight".equals(winner.mode) || winner.customBackground;
        int color = winner.customBackground ? winner.backgroundColor : 0;
        String tooltip = winner.filterTooltip ? winner.name : "";
        return new FilterResult(true, suppressMain, highlighted, color, tooltip, playSound);
    }

    private static boolean matchesFilter(
            SecondChatFilterConfig filter,
            String raw,
            String normalized,
            boolean ownNameMatch,
            boolean hqMatch,
            boolean paydayMatch,
            boolean reinforcementMatch
    ) {
        boolean baseMatch = switch (filter.matcher) {
            case "hq" -> hqMatch;
            case "payday" -> paydayMatch;
            case "reinf" -> reinforcementMatch;
            case "private" -> isPrivateMessage(normalized);
            case "server" -> isServerInfo(normalized);
            case "betteruc" -> normalized.contains("[betteruc]");
            case "ownname" -> ownNameMatch;
            default -> matchesTerms(raw, normalized, filter.includeText, filter.caseSensitive, filter.matchType);
        };
        if (!baseMatch) {
            return false;
        }
        if (!"custom".equals(filter.matcher)
                && !filter.includeText.isBlank()
                && !matchesTerms(raw, normalized, filter.includeText, filter.caseSensitive, filter.matchType)) {
            return false;
        }
        return filter.excludeText.isBlank()
                || !matchesTerms(raw, normalized, filter.excludeText, filter.caseSensitive, "contains");
    }

    private static boolean matchesTerms(
            String raw,
            String normalized,
            String expression,
            boolean caseSensitive,
            String matchType
    ) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String haystack = caseSensitive ? raw : normalized;
        for (String part : expression.split("[;,]")) {
            String term = part.trim();
            if (term.isEmpty()) {
                continue;
            }
            String needle = caseSensitive ? term : normalize(term);
            if ("starts".equals(matchType) || needle.startsWith("^")) {
                String prefix = needle.substring(1).trim();
                if ("starts".equals(matchType)) {
                    prefix = needle;
                }
                if (!prefix.isEmpty() && haystack.startsWith(prefix)) {
                    return true;
                }
            } else if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void markRead(String tabId) {
        if (tabId == null) return;
        UNREAD_COUNTS.remove(tabId);
        ATTENTION_UNTIL.remove(tabId);
        revision++;
    }

    private static synchronized void clearRuntimeState(String tabId) {
        UNREAD_COUNTS.remove(tabId);
        FIRST_UNREAD_AT.remove(tabId);
        ATTENTION_UNTIL.remove(tabId);
        SCROLL_OFFSETS.remove(tabId);
        NEW_WHILE_SCROLLED.remove(tabId);
    }

    private static boolean isTabActive(SecondChatTabConfig tab) {
        if (PRIMARY_WINDOW_ID.equals(tab.windowId)) {
            return tab.id.equals(BetterUCConfig.INSTANCE.secondChatActiveTabId);
        }
        SecondChatWindowConfig window = findWindow(tab.windowId);
        return window != null && tab.id.equals(window.activeTabId);
    }

    private static int actionPriority(String mode) {
        return switch (mode) {
            case "move" -> 3;
            case "highlight" -> 2;
            case "copy" -> 1;
            default -> 0;
        };
    }

    private static synchronized boolean classifyHqOrWps(String text) {
        long now = System.currentTimeMillis();
        if (isWantedInfoHeader(text)) {
            wantedInfoDetailsRemaining = WANTED_INFO_ALL_DETAILS;
            wantedInfoContinuationUntilMs = now + HQ_BLOCK_WINDOW_MS;
            hqContinuationLinesRemaining = 0;
            return true;
        }

        int wantedInfoDetail = wantedInfoDetailType(text);
        if (now <= wantedInfoContinuationUntilMs
                && (wantedInfoDetailsRemaining & wantedInfoDetail) != 0) {
            wantedInfoDetailsRemaining &= ~wantedInfoDetail;
            wantedInfoContinuationUntilMs = wantedInfoDetailsRemaining == 0
                    ? 0L
                    : now + HQ_BLOCK_WINDOW_MS;
            return true;
        }
        if (now > wantedInfoContinuationUntilMs) {
            wantedInfoDetailsRemaining = 0;
        }

        int continuationLines = formattedHqContinuationLines(text);
        if (continuationLines >= 0) {
            hqContinuationLinesRemaining = continuationLines;
            hqContinuationUntilMs = now + HQ_BLOCK_WINDOW_MS;
            wantedInfoDetailsRemaining = 0;
            return true;
        }
        if (isHqOrWpsLine(text)) {
            if (hasStructuredHqPrefix(text)) {
                wantedInfoDetailsRemaining = 0;
            }
            return true;
        }
        if (now <= hqContinuationUntilMs && hqContinuationLinesRemaining > 0) {
            hqContinuationLinesRemaining--;
            hqContinuationUntilMs = now + HQ_BLOCK_WINDOW_MS;
            return true;
        }
        if (now > hqContinuationUntilMs) {
            hqContinuationLinesRemaining = 0;
        }
        return false;
    }

    private static int formattedHqContinuationLines(String text) {
        if (text.startsWith("notruf angenommen ")) {
            return 1;
        }
        if (text.startsWith("notruf ")) {
            return 2;
        }
        if (text.startsWith("gesucht ")) {
            return 2;
        }
        if (text.startsWith("getotet ")
                || text.startsWith("inhaftiert ")
                || text.startsWith("verandert ")) {
            return 2;
        }
        if (text.startsWith("geloscht ")) {
            return 1;
        }
        if (text.startsWith("ticket ausgestellt ")
                || text.startsWith("ticket bestatigt ")) {
            return 1;
        }
        if (text.startsWith("waffen abnahme ")
                || text.startsWith("drogen abnahme ")
                || text.startsWith("fuhrerschein abnahme ")
                || text.startsWith("fuhrerschein ruckgabe ")
                || text.startsWith("akten geloscht ")) {
            return 0;
        }
        return -1;
    }

    private static boolean isHqOrWpsLine(String text) {
        return WANTED_LIST_ENTRY_PATTERN.matcher(text).matches()
                || isWantedListHeader(text)
                || hasStructuredHqPrefix(text);
    }

    private static boolean isWantedListHeader(String text) {
        return text != null
                && (text.equals("online spieler mit wantedpunkten")
                || text.equals("online spieler mit wantedpunkten keine belohnung"));
    }

    private static boolean isWantedInfoHeader(String text) {
        return text != null
                && (text.startsWith("hq fahndungs informationen uber ")
                || text.startsWith("hq fahndungsinformationen uber "));
    }

    private static int wantedInfoDetailType(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        if (text.matches("^wantedpunkte \\d+$")) {
            return WANTED_INFO_POINTS;
        }
        if (text.matches("^grund .+$")) {
            return WANTED_INFO_REASON;
        }
        if (text.matches("^gefahndet seit \\d+ minuten?$")) {
            return WANTED_INFO_SINCE;
        }
        if (text.matches("^beamt(?:e r|er|in) [a-z0-9_\\[\\]^]{1,48}$")) {
            return WANTED_INFO_OFFICER;
        }
        return 0;
    }

    private static boolean hasStructuredHqPrefix(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.matches("^(?:(?:\\[?system]? \\[?chat]?)(?: \\d{1,2} \\d{2} \\d{2})? |"
                + "\\[?betteruc second chat]? )?hq(?: |$).*$");
    }

    private static synchronized boolean classifyPayday(String text) {
        long now = System.currentTimeMillis();
        if (isPaydayStart(text)) {
            paydayBlockActive = true;
            paydayContinuationUntilMs = now + PAYDAY_BLOCK_WINDOW_MS;
            return true;
        }
        if (!paydayBlockActive || now > paydayContinuationUntilMs) {
            paydayBlockActive = false;
            return false;
        }

        paydayContinuationUntilMs = now + PAYDAY_BLOCK_WINDOW_MS;
        if (isPaydayEnd(text)) {
            paydayBlockActive = false;
        }
        return true;
    }

    private static boolean isPaydayLine(String text) {
        return isPaydayStart(text)
                || isPaydayEnd(text)
                || containsAny(text,
                "alter betrag", "zinsen", "steuern", "kirchensteuer", "level bonus",
                "gehalt von jobs", "mine einnahmen", "nebenkosten fur haus", "haussteuer",
                "fraktionsgehalt", "sub fraktionsgehalt", "taschengeld", "verbeamtengeld",
                "kfz steuer", "apps", "experience");
    }

    private static boolean isPaydayStart(String text) {
        return "payday".equals(text);
    }

    private static boolean isPaydayEnd(String text) {
        return text.startsWith("neuer betrag ");
    }

    private static boolean isReinforcementLine(String text) {
        return isReinforcementAnchor(text)
                || isReinforcementRouteAction(text);
    }

    static boolean isReinforcementMessage(String raw) {
        return raw != null && isReinforcementLine(normalize(raw));
    }

    static boolean isFormattedHqMessage(String raw) {
        return formattedHqContinuationLineCount(raw) >= 0;
    }

    static boolean isHqOrWpsMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = normalize(raw);
        return isHqOrWpsLine(normalized) || formattedHqContinuationLines(normalized) >= 0;
    }

    static int formattedHqContinuationLineCount(String raw) {
        return raw == null ? -1 : formattedHqContinuationLines(normalize(raw));
    }

    static boolean isWantedInfoHeaderMessage(String raw) {
        return raw != null && isWantedInfoHeader(normalize(raw));
    }

    static boolean isWantedInfoDetailMessage(String raw) {
        return raw != null && wantedInfoDetailType(normalize(raw)) != 0;
    }

    static boolean classifyHqOrWpsBlockMessage(String raw) {
        return raw != null && !raw.isBlank() && classifyHqOrWps(normalize(raw));
    }

    private static boolean isReinforcementAnchor(String text) {
        return (text.contains(" benotigt unterstutzung in der nahe von ")
                && text.contains(" meter entfernt"))
                || (text.contains(" kommt zum verstarkungsruf von ")
                && text.contains(" meter entfernt"));
    }

    private static boolean isReinforcementRouteAction(String text) {
        return "route anzeigen unterwegs".equals(text);
    }

    private static boolean isPrivateMessage(String text) {
        return containsAny(text,
                "flustert", "privat", "private nachricht", "whisper", "schreibt dir",
                "du schreibst", "[pm]", "[pn]");
    }

    private static boolean isServerInfo(String text) {
        return containsAny(text,
                "[info]", "[event]", "[premium]", "fmotd", "werbung", "vote ",
                "server ", "unicacity");
    }

    private static boolean containsAny(String text, String... needles) {
        String padded = " " + text + " ";
        for (String needle : needles) {
            if (padded.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWord(String text, String word) {
        return (" " + text + " ").contains(" " + word + " ");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(SmallCapsText.fold(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^\\s*\\[?\\d{1,2}:\\d{2}:\\d{2}]?\\s*", "")
                .replaceAll("[^a-z0-9_\\[\\]^]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void playMentionSound(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMentionSoundAtMs < 750L) {
            return;
        }
        lastMentionSoundAtMs = now;
        client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.45F, 1.6F);
    }

    private record FilterResult(
            boolean display,
            boolean suppressMain,
            boolean highlighted,
            int highlightColor,
            String tooltip,
            boolean playSound
    ) {
    }

    public record Entry(
            Component message,
            boolean highlighted,
            int highlightColor,
            String tooltip,
            long createdAtMs,
            int repeatCount
    ) {
    }

    public record RouteResult(boolean suppressMain) {
        public static final RouteResult KEEP_MAIN = new RouteResult(false);
        public static final RouteResult HIDE_MAIN = new RouteResult(true);
    }
}
