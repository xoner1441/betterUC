package com.betteruc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BetterUCConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("betteruc.json").toFile();
    private static final File LEGACY_NAMETAG_CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("nametagmod.json").toFile();

    public List<String> manualFactionPlayers = new ArrayList<>();
    public List<String> manualBlacklistPlayers = new ArrayList<>();

    public transient List<String> remoteFactionPlayers = new ArrayList<>();
    public transient Map<String, List<String>> remoteFactionMembersByFaction = new LinkedHashMap<>();
    public transient List<String> chatBlacklistPlayers = new ArrayList<>();
    public transient List<String> vogelfreiPlayers = new ArrayList<>();
    public transient Set<String> manualFactionPlayerKeys = new LinkedHashSet<>();
    public transient Set<String> remoteFactionPlayerKeys = new LinkedHashSet<>();
    public transient Set<String> manualBlacklistPlayerKeys = new LinkedHashSet<>();
    public transient Set<String> chatBlacklistPlayerKeys = new LinkedHashSet<>();
    public transient Set<String> vogelfreiPlayerKeys = new LinkedHashSet<>();
    public transient long lastFactionSyncRequestMs = 0L;
    public transient long lastFactionSyncMs = 0L;
    public transient String lastFactionSyncQuery = "";
    public transient int lastFactionSyncMemberCount = -1;
    public transient long lastBlacklistSyncMs = 0L;
    public transient int lastBlacklistSyncPlayerCount = -1;

    // Name -> Grund-String (z.B. "Gangzone + Leadermord")
    public transient Map<String, String> blacklistReasons = new LinkedHashMap<>();
    // Name -> [kills, price]
    public transient Map<String, int[]> blacklistStats = new LinkedHashMap<>();
    // Name -> Original-Rest der Blacklist-Zeile nach dem Spielernamen.
    public transient Map<String, String> blacklistEntryRests = new LinkedHashMap<>();

    public transient int currentBlackMoney = 0;
    public List<String> trackedFactionQueries = defaultTrackedFactionQueries();
    public static final int DEFAULT_TOGGLE_SPRINT_HUD_COLOR = 0xFF55FF55;
    public static final int DEFAULT_FPS_HUD_COLOR = 0xFF55FFFF;
    public static final int DEFAULT_PAYDAY_HUD_COLOR = 0xFFFFD866;
    public static final int DEFAULT_BANK_HUD_COLOR = 0xFF55FFFF;
    public static final int DEFAULT_HEALTH_HUD_COLOR = 0xFFFF5555;
    public static final int DEFAULT_HEALTH_HUD_HEART_COLOR = DEFAULT_HEALTH_HUD_COLOR;
    public static final int DEFAULT_HEALTH_HUD_TEXT_COLOR = DEFAULT_HEALTH_HUD_COLOR;
    public static final float MIN_HUD_SCALE = 0.5F;
    public static final float MAX_HUD_SCALE = 3.0F;
    public static final float DEFAULT_HUD_SCALE = 1.0F;
    public static final String HUD_STYLE_MODERN = "modern";
    public static final String HUD_STYLE_TRANSPARENT = "transparent";
    public static final String HUD_STYLE_CARTOON = "cartoon";
    public static final String HUD_STYLE_CUSTOM = "custom";
    public static BetterUCConfig INSTANCE = new BetterUCConfig();
    private static final List<TrackableFaction> TRACKABLE_FACTIONS = List.of(
            new TrackableFaction("Polizei", "polizei"),
            new TrackableFaction("FBI", "fbi"),
            new TrackableFaction("Rettungsdienst", "medic"),
            new TrackableFaction("La Cosa Nostra", "lcn"),
            new TrackableFaction("Westside Ballas", "ballas"),
            new TrackableFaction("Calderon Kartell", "kartell"),
            new TrackableFaction("Kerzakov Familie", "kerzakov"),
            new TrackableFaction("Yakuza", "yakuza"),
            new TrackableFaction("S\u00F6ldner", "soeldner")
    );

    public static final class TrackableFaction {
        public final String label;
        public final String query;

        private TrackableFaction(String label, String query) {
            this.label = label;
            this.query = query;
        }
    }

    public static class HotkeyCommand {
        public int keyCode = -1;
        public String command = "";
        public HotkeyCommand() {}
        public HotkeyCommand(int keyCode, String command) {
            this.keyCode = keyCode;
            this.command = command;
        }
    }

    public static class PlantTimerState {
        public long plantedAtMs = 0L;
        public long nextWaterAtMs = 0L;
        public long nextFertilizeAtMs = 0L;
        public int count = 0;

        public PlantTimerState() {}

        public PlantTimerState(long plantedAtMs, long nextWaterAtMs, long nextFertilizeAtMs, int count) {
            this.plantedAtMs = plantedAtMs;
            this.nextWaterAtMs = nextWaterAtMs;
            this.nextFertilizeAtMs = nextFertilizeAtMs;
            this.count = count;
        }
    }

    public List<HotkeyCommand> hotkeyCommands = new ArrayList<>();

    public int timerX = 10;
    public int timerY = 10;
    public int hackTimerX = 10;
    public int hackTimerY = 10;
    public int plantTimerX = 10;
    public int plantTimerY = 46;
    public int healthHudX = -1;
    public int healthHudY = -1;
    public int toggleSprintHudX = 10;
    public int toggleSprintHudY = 28;
    public int fpsHudX = 10;
    public int fpsHudY = 46;
    public int paydayHudX = 10;
    public int paydayHudY = 64;
    public int ammoHudX = 10;
    public int ammoHudY = 82;
    public int bankHudX = 10;
    public int bankHudY = 100;
    public int potionHudX = 10;
    public int potionHudY = 118;
    public float healthHudScale = DEFAULT_HUD_SCALE;
    public float toggleSprintHudScale = DEFAULT_HUD_SCALE;
    public float fpsHudScale = DEFAULT_HUD_SCALE;
    public float paydayHudScale = DEFAULT_HUD_SCALE;
    public float ammoHudScale = DEFAULT_HUD_SCALE;
    public float bankHudScale = DEFAULT_HUD_SCALE;
    public float potionHudScale = DEFAULT_HUD_SCALE;
    public float hackTimerHudScale = DEFAULT_HUD_SCALE;
    public float plantTimerHudScale = DEFAULT_HUD_SCALE;
    public int lastKnownBankBalance = -1;
    public int toggleSprintHudColor = DEFAULT_TOGGLE_SPRINT_HUD_COLOR;
    public int fpsHudColor = DEFAULT_FPS_HUD_COLOR;
    public int paydayHudColor = DEFAULT_PAYDAY_HUD_COLOR;
    public int bankHudColor = DEFAULT_BANK_HUD_COLOR;
    public int healthHudHeartColor = 0;
    public int healthHudTextColor = 0;
    public int healthHudColor = DEFAULT_HEALTH_HUD_COLOR;
    public String healthHudStyle = HUD_STYLE_TRANSPARENT;
    public String toggleSprintHudStyle = HUD_STYLE_MODERN;
    public String fpsHudStyle = HUD_STYLE_MODERN;
    public String paydayHudStyle = HUD_STYLE_MODERN;
    public String ammoHudStyle = HUD_STYLE_MODERN;
    public String bankHudStyle = HUD_STYLE_MODERN;
    public String potionHudStyle = HUD_STYLE_MODERN;
    public String hackTimerHudStyle = HUD_STYLE_MODERN;
    public String plantTimerHudStyle = HUD_STYLE_MODERN;
    public String healthHudCustomFont = "";
    public String toggleSprintHudCustomFont = "";
    public String fpsHudCustomFont = "";
    public String paydayHudCustomFont = "";
    public String ammoHudCustomFont = "";
    public String bankHudCustomFont = "";
    public String potionHudCustomFont = "";
    public String hackTimerHudCustomFont = "";
    public String plantTimerHudCustomFont = "";
    public String customHudFont = "";
    public String cartoonHudFont = "";
    public boolean showHealthHud = true;
    public boolean showFpsHud = true;
    public boolean showPaydayHud = true;
    public boolean showAmmoHud = true;
    public boolean showBankHud = true;
    public boolean showPotionEffectsHud = true;
    public boolean toggleSprintEnabled = false;
    public boolean zoomEnabled = true;
    public int zoomKeyCode = 67; // GLFW_KEY_C
    public float zoomFovMultiplier = 0.25f;
    public boolean autoStatsOnJoinEnabled = true;
    public Map<String, PlantTimerState> plantTimerStates = new LinkedHashMap<>();

    public String factionUrl = "https://example.com/faction.json";
    public int reloadIntervalMinutes = 5;
    public String chatTimestampFormat = "[HH:mm:ss]";
    public int maxChatHistory = 2000;

    public static class BlacklistReason {
        public int kills;
        public int price;
        public BlacklistReason() {}
        public BlacklistReason(int kills, int price) {
            this.kills = kills;
            this.price = price;
        }
    }

    private static int sanitizeHudColor(int color, int fallback) {
        if (color == 0) return fallback;
        if ((color & 0xFF000000) == 0) return 0xFF000000 | color;
        return color;
    }

    public static boolean isModernHudStyle(String style) {
        return HUD_STYLE_MODERN.equals(normalizeHudStyle(style, HUD_STYLE_MODERN));
    }

    public static boolean isCartoonHudStyle(String style) {
        return HUD_STYLE_CARTOON.equals(normalizeHudStyle(style, HUD_STYLE_MODERN));
    }

    public static boolean isCustomHudStyle(String style) {
        return HUD_STYLE_CUSTOM.equals(normalizeHudStyle(style, HUD_STYLE_MODERN));
    }

    public static boolean isStylizedHudStyle(String style) {
        String normalized = normalizeHudStyle(style, HUD_STYLE_MODERN);
        return HUD_STYLE_CARTOON.equals(normalized) || HUD_STYLE_CUSTOM.equals(normalized);
    }

    public static String toggleHudStyle(String style) {
        String normalized = normalizeHudStyle(style, HUD_STYLE_MODERN);
        return switch (normalized) {
            case HUD_STYLE_MODERN -> HUD_STYLE_TRANSPARENT;
            case HUD_STYLE_TRANSPARENT -> HUD_STYLE_CARTOON;
            case HUD_STYLE_CARTOON -> HUD_STYLE_CUSTOM;
            default -> HUD_STYLE_MODERN;
        };
    }

    public static String hudStyleLabel(String style) {
        String normalized = normalizeHudStyle(style, HUD_STYLE_MODERN);
        return switch (normalized) {
            case HUD_STYLE_TRANSPARENT -> "Transparent";
            case HUD_STYLE_CARTOON -> "Cartoon";
            case HUD_STYLE_CUSTOM -> "Custom";
            default -> "Modern";
        };
    }

    public static float normalizeHudScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale) || scale <= 0.0F) {
            return DEFAULT_HUD_SCALE;
        }
        return Math.max(MIN_HUD_SCALE, Math.min(MAX_HUD_SCALE, scale));
    }

    private static String normalizeHudStyle(String style, String fallback) {
        String normalized = style == null ? "" : style.trim().toLowerCase(Locale.ROOT);
        if (HUD_STYLE_MODERN.equals(normalized)
                || HUD_STYLE_TRANSPARENT.equals(normalized)
                || HUD_STYLE_CARTOON.equals(normalized)
                || HUD_STYLE_CUSTOM.equals(normalized)) {
            return normalized;
        }
        return fallback;
    }

    private static void sanitizeHudStyles() {
        INSTANCE.healthHudStyle = normalizeHudStyle(INSTANCE.healthHudStyle, HUD_STYLE_TRANSPARENT);
        INSTANCE.toggleSprintHudStyle = normalizeHudStyle(INSTANCE.toggleSprintHudStyle, HUD_STYLE_MODERN);
        INSTANCE.fpsHudStyle = normalizeHudStyle(INSTANCE.fpsHudStyle, HUD_STYLE_MODERN);
        INSTANCE.paydayHudStyle = normalizeHudStyle(INSTANCE.paydayHudStyle, HUD_STYLE_MODERN);
        INSTANCE.ammoHudStyle = normalizeHudStyle(INSTANCE.ammoHudStyle, HUD_STYLE_MODERN);
        INSTANCE.bankHudStyle = normalizeHudStyle(INSTANCE.bankHudStyle, HUD_STYLE_MODERN);
        INSTANCE.potionHudStyle = normalizeHudStyle(INSTANCE.potionHudStyle, HUD_STYLE_MODERN);
        INSTANCE.hackTimerHudStyle = normalizeHudStyle(INSTANCE.hackTimerHudStyle, HUD_STYLE_MODERN);
        INSTANCE.plantTimerHudStyle = normalizeHudStyle(INSTANCE.plantTimerHudStyle, HUD_STYLE_MODERN);
    }

    private static void sanitizeHudScales() {
        INSTANCE.healthHudScale = normalizeHudScale(INSTANCE.healthHudScale);
        INSTANCE.toggleSprintHudScale = normalizeHudScale(INSTANCE.toggleSprintHudScale);
        INSTANCE.fpsHudScale = normalizeHudScale(INSTANCE.fpsHudScale);
        INSTANCE.paydayHudScale = normalizeHudScale(INSTANCE.paydayHudScale);
        INSTANCE.ammoHudScale = normalizeHudScale(INSTANCE.ammoHudScale);
        INSTANCE.bankHudScale = normalizeHudScale(INSTANCE.bankHudScale);
        INSTANCE.potionHudScale = normalizeHudScale(INSTANCE.potionHudScale);
        INSTANCE.hackTimerHudScale = normalizeHudScale(INSTANCE.hackTimerHudScale);
        INSTANCE.plantTimerHudScale = normalizeHudScale(INSTANCE.plantTimerHudScale);
    }

    private static Map<String, BlacklistReason> defaultBlacklistReasons() {
        Map<String, BlacklistReason> defaults = new LinkedHashMap<>();
        defaults.put("Gangzone", new BlacklistReason(50, 3000));
        defaults.put("Fraktionsschaedigung", new BlacklistReason(80, 6000));
        defaults.put("Leichenbewachung", new BlacklistReason(30, 2000));
        defaults.put("Leadermord", new BlacklistReason(100, 10000));
        defaults.put("Rufmord", new BlacklistReason(20, 1500));
        return defaults;
    }

    public Map<String, BlacklistReason> blReasons = defaultBlacklistReasons();

    private static List<String> defaultTrackedFactionQueries() {
        List<String> defaults = new ArrayList<>();
        defaults.add("kartell");
        return defaults;
    }

    public static List<TrackableFaction> getTrackableFactions() {
        return TRACKABLE_FACTIONS;
    }

    public static String factionLabelForQuery(String query) {
        String normalized = normalizeFactionQuery(query);
        for (TrackableFaction faction : TRACKABLE_FACTIONS) {
            if (normalizeFactionQuery(faction.query).equals(normalized)) {
                return faction.label;
            }
        }
        if (query == null || query.isBlank()) return "Unbekannt";
        return query;
    }

    public static String normalizeFactionQuery(String raw) {
        String folded = foldFactionToken(raw);
        if (folded.isEmpty()) return "";

        if (folded.equals("calderon kartell") || folded.equals("kartell")) return "kartell";
        if (folded.equals("rettungsdienst") || folded.equals("retungsdienst") || folded.equals("medic")) return "medic";
        if (folded.equals("la cosa nostra") || folded.equals("lcn")) return "lcn";
        if (folded.equals("westside ballas") || folded.equals("ballas")) return "ballas";
        if (folded.equals("soldner") || folded.equals("soeldner")) return "soeldner";
        if (folded.equals("kf") || folded.equals("k f")
                || folded.equals("kerzakov") || folded.equals("kerzakov familie")
                || folded.equals("kerzakov family")) return "kerzakov";
        if (folded.equals("f b i")) return "fbi";
        return folded;
    }

    public static String memberInfoCommandQueryFor(String raw) {
        String normalized = normalizeFactionQuery(raw);
        if (normalized.equals("soeldner")) return "s\u00F6ldner";
        return normalized;
    }

    public static void sanitizeTrackedFactions() {
        if (INSTANCE.trackedFactionQueries == null) {
            INSTANCE.trackedFactionQueries = defaultTrackedFactionQueries();
            return;
        }

        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        for (String raw : INSTANCE.trackedFactionQueries) {
            String normalized = normalizeFactionQuery(raw);
            if (!normalized.isEmpty()) {
                dedupe.add(normalized);
            }
        }

        INSTANCE.trackedFactionQueries = new ArrayList<>(dedupe);
    }

    public static List<String> getTrackedFactionQueries() {
        sanitizeTrackedFactions();
        return new ArrayList<>(INSTANCE.trackedFactionQueries);
    }

    public static String getSelectedFactionQuery() {
        List<String> tracked = getTrackedFactionQueries();
        if (!tracked.isEmpty()) {
            return normalizeFactionQuery(tracked.get(0));
        }
        return "";
    }

    public static void toggleTrackedFaction(String query) {
        String normalized = normalizeFactionQuery(query);
        if (normalized.isEmpty()) return;

        sanitizeTrackedFactions();
        List<String> list = INSTANCE.trackedFactionQueries;
        int existingIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (normalizeFactionQuery(list.get(i)).equals(normalized)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            list.remove(existingIndex);
        } else {
            list.add(normalized);
        }

        sanitizeTrackedFactions();
        save();
    }

    public static void setOnlyTrackedFaction(String query) {
        String normalized = normalizeFactionQuery(query);
        if (INSTANCE.trackedFactionQueries == null) {
            INSTANCE.trackedFactionQueries = new ArrayList<>();
        } else {
            INSTANCE.trackedFactionQueries.clear();
        }
        if (!normalized.isEmpty()) {
            INSTANCE.trackedFactionQueries.add(normalized);
        }
        sanitizeTrackedFactions();
        save();
    }

    public static boolean isFactionTracked(String query) {
        String normalized = normalizeFactionQuery(query);
        if (normalized.isEmpty()) return false;
        sanitizeTrackedFactions();
        for (String item : INSTANCE.trackedFactionQueries) {
            if (normalizeFactionQuery(item).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static void setRemoteMembersForFaction(String factionQuery, List<String> members) {
        String key = normalizeFactionQuery(factionQuery);
        if (key.isEmpty()) return;
        if (INSTANCE.remoteFactionMembersByFaction == null) {
            INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
        }

        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        if (members != null) {
            for (String member : members) {
                if (member == null) continue;
                String trimmed = member.trim();
                if (!trimmed.matches("[A-Za-z0-9_]{3,16}")) continue;
                dedupe.add(trimmed);
            }
        }

        INSTANCE.remoteFactionMembersByFaction.put(key, new ArrayList<>(dedupe));
        rebuildRemoteFactionUnion();
        markFactionSyncComplete(key);
    }

    public static void mergeRemoteMembersForFaction(String factionQuery, List<String> members) {
        String key = normalizeFactionQuery(factionQuery);
        if (key.isEmpty() || members == null || members.isEmpty()) return;
        if (INSTANCE.remoteFactionMembersByFaction == null) {
            INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
        }

        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        List<String> existing = INSTANCE.remoteFactionMembersByFaction.get(key);
        if (existing != null) {
            for (String member : existing) {
                if (member == null) continue;
                String trimmed = member.trim();
                if (trimmed.matches("[A-Za-z0-9_]{3,16}")) {
                    dedupe.add(trimmed);
                }
            }
        }

        for (String member : members) {
            if (member == null) continue;
            String trimmed = member.trim();
            if (trimmed.matches("[A-Za-z0-9_]{3,16}")) {
                dedupe.add(trimmed);
            }
        }

        INSTANCE.remoteFactionMembersByFaction.put(key, new ArrayList<>(dedupe));
        rebuildRemoteFactionUnion();
    }

    public static void rebuildRemoteFactionUnion() {
        if (INSTANCE.remoteFactionPlayers == null) {
            INSTANCE.remoteFactionPlayers = new ArrayList<>();
        } else {
            INSTANCE.remoteFactionPlayers.clear();
        }
        if (INSTANCE.remoteFactionMembersByFaction == null) {
            INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
            refreshFactionNameCaches();
            return;
        }

        Set<String> allowed = new LinkedHashSet<>(getTrackedFactionQueries());
        LinkedHashSet<String> union = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : INSTANCE.remoteFactionMembersByFaction.entrySet()) {
            String key = normalizeFactionQuery(entry.getKey());
            if (key.isEmpty() || !allowed.contains(key)) continue;
            List<String> members = entry.getValue();
            if (members == null) continue;
            for (String member : members) {
                if (member == null) continue;
                String trimmed = member.trim();
                if (!trimmed.matches("[A-Za-z0-9_]{3,16}")) continue;
                union.add(trimmed);
            }
        }

        INSTANCE.remoteFactionPlayers.addAll(union);
        refreshFactionNameCaches();
    }

    public static void clearRemoteFactionRuntime() {
        if (INSTANCE.remoteFactionPlayers == null) {
            INSTANCE.remoteFactionPlayers = new ArrayList<>();
        } else {
            INSTANCE.remoteFactionPlayers.clear();
        }
        if (INSTANCE.remoteFactionMembersByFaction == null) {
            INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
        } else {
            INSTANCE.remoteFactionMembersByFaction.clear();
        }
        INSTANCE.lastFactionSyncRequestMs = 0L;
        INSTANCE.lastFactionSyncMs = 0L;
        INSTANCE.lastFactionSyncQuery = "";
        INSTANCE.lastFactionSyncMemberCount = -1;
        refreshFactionNameCaches();
    }

    public static void clearChatBlacklistRuntime() {
        if (INSTANCE.chatBlacklistPlayers == null) {
            INSTANCE.chatBlacklistPlayers = new ArrayList<>();
        } else {
            INSTANCE.chatBlacklistPlayers.clear();
        }
        if (INSTANCE.vogelfreiPlayers == null) {
            INSTANCE.vogelfreiPlayers = new ArrayList<>();
        } else {
            INSTANCE.vogelfreiPlayers.clear();
        }
        if (INSTANCE.blacklistReasons == null) {
            INSTANCE.blacklistReasons = new LinkedHashMap<>();
        } else {
            INSTANCE.blacklistReasons.clear();
        }
        if (INSTANCE.blacklistStats == null) {
            INSTANCE.blacklistStats = new LinkedHashMap<>();
        } else {
            INSTANCE.blacklistStats.clear();
        }
        if (INSTANCE.blacklistEntryRests == null) {
            INSTANCE.blacklistEntryRests = new LinkedHashMap<>();
        } else {
            INSTANCE.blacklistEntryRests.clear();
        }
        INSTANCE.lastBlacklistSyncMs = 0L;
        INSTANCE.lastBlacklistSyncPlayerCount = -1;
        refreshBlacklistNameCaches();
    }

    public static boolean addChatBlacklistPlayer(String name) {
        String cleaned = sanitizePlayerName(name);
        if (cleaned.isEmpty()) return false;
        ensureRuntimeCollections();
        if (containsNameIgnoreCase(INSTANCE.chatBlacklistPlayers, cleaned)) return false;
        INSTANCE.chatBlacklistPlayers.add(cleaned);
        refreshBlacklistNameCaches();
        return true;
    }

    public static void removeBlacklistPlayerEverywhere(String name) {
        String key = playerKey(name);
        if (key.isEmpty()) return;
        ensureRuntimeCollections();
        INSTANCE.chatBlacklistPlayers.removeIf(s -> playerKey(s).equals(key));
        INSTANCE.manualBlacklistPlayers.removeIf(s -> playerKey(s).equals(key));
        INSTANCE.vogelfreiPlayers.removeIf(s -> playerKey(s).equals(key));
        INSTANCE.blacklistReasons.entrySet().removeIf(e -> playerKey(e.getKey()).equals(key));
        INSTANCE.blacklistStats.entrySet().removeIf(e -> playerKey(e.getKey()).equals(key));
        INSTANCE.blacklistEntryRests.entrySet().removeIf(e -> playerKey(e.getKey()).equals(key));
        refreshBlacklistNameCaches();
    }

    public static void addVogelfreiPlayer(String name) {
        String cleaned = sanitizePlayerName(name);
        if (cleaned.isEmpty()) return;
        ensureRuntimeCollections();
        if (!containsNameIgnoreCase(INSTANCE.vogelfreiPlayers, cleaned)) {
            INSTANCE.vogelfreiPlayers.add(cleaned);
        }
        refreshBlacklistNameCaches();
    }

    public static void removeVogelfreiPlayer(String name) {
        String key = playerKey(name);
        if (key.isEmpty()) return;
        ensureRuntimeCollections();
        INSTANCE.vogelfreiPlayers.removeIf(s -> playerKey(s).equals(key));
        refreshBlacklistNameCaches();
    }

    public static void refreshRuntimeNameCaches() {
        refreshFactionNameCaches();
        refreshBlacklistNameCaches();
    }

    public static void refreshFactionNameCaches() {
        ensureRuntimeCollections();
        rebuildNameKeySet(INSTANCE.manualFactionPlayers, INSTANCE.manualFactionPlayerKeys);
        rebuildNameKeySet(INSTANCE.remoteFactionPlayers, INSTANCE.remoteFactionPlayerKeys);
    }

    public static void refreshBlacklistNameCaches() {
        ensureRuntimeCollections();
        rebuildNameKeySet(INSTANCE.manualBlacklistPlayers, INSTANCE.manualBlacklistPlayerKeys);
        rebuildNameKeySet(INSTANCE.chatBlacklistPlayers, INSTANCE.chatBlacklistPlayerKeys);
        rebuildNameKeySet(INSTANCE.vogelfreiPlayers, INSTANCE.vogelfreiPlayerKeys);
    }

    public static void markFactionSyncRequested(String factionQuery) {
        INSTANCE.lastFactionSyncRequestMs = System.currentTimeMillis();
        INSTANCE.lastFactionSyncQuery = normalizeFactionQuery(factionQuery);
    }

    public static void markFactionSyncComplete(String factionQuery) {
        String key = normalizeFactionQuery(factionQuery);
        INSTANCE.lastFactionSyncMs = System.currentTimeMillis();
        INSTANCE.lastFactionSyncQuery = key;
        List<String> members = INSTANCE.remoteFactionMembersByFaction == null ? null : INSTANCE.remoteFactionMembersByFaction.get(key);
        INSTANCE.lastFactionSyncMemberCount = members == null ? 0 : members.size();
    }

    public static void markBlacklistSyncComplete() {
        INSTANCE.lastBlacklistSyncMs = System.currentTimeMillis();
        INSTANCE.lastBlacklistSyncPlayerCount = INSTANCE.chatBlacklistPlayers == null ? 0 : INSTANCE.chatBlacklistPlayers.size();
        refreshBlacklistNameCaches();
    }

    private static String foldFactionToken(String raw) {
        if (raw == null) return "";
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return folded;
    }

    private static void ensureRuntimeCollections() {
        if (INSTANCE.remoteFactionPlayers == null) INSTANCE.remoteFactionPlayers = new ArrayList<>();
        if (INSTANCE.remoteFactionMembersByFaction == null) INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
        if (INSTANCE.chatBlacklistPlayers == null) INSTANCE.chatBlacklistPlayers = new ArrayList<>();
        if (INSTANCE.vogelfreiPlayers == null) INSTANCE.vogelfreiPlayers = new ArrayList<>();
        if (INSTANCE.blacklistReasons == null) INSTANCE.blacklistReasons = new LinkedHashMap<>();
        if (INSTANCE.blacklistStats == null) INSTANCE.blacklistStats = new LinkedHashMap<>();
        if (INSTANCE.blacklistEntryRests == null) INSTANCE.blacklistEntryRests = new LinkedHashMap<>();
        if (INSTANCE.manualFactionPlayers == null) INSTANCE.manualFactionPlayers = new ArrayList<>();
        if (INSTANCE.manualBlacklistPlayers == null) INSTANCE.manualBlacklistPlayers = new ArrayList<>();
        if (INSTANCE.manualFactionPlayerKeys == null) INSTANCE.manualFactionPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.remoteFactionPlayerKeys == null) INSTANCE.remoteFactionPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.manualBlacklistPlayerKeys == null) INSTANCE.manualBlacklistPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.chatBlacklistPlayerKeys == null) INSTANCE.chatBlacklistPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.vogelfreiPlayerKeys == null) INSTANCE.vogelfreiPlayerKeys = new LinkedHashSet<>();
    }

    private static void rebuildNameKeySet(List<String> source, Set<String> target) {
        target.clear();
        if (source == null) return;
        for (String name : source) {
            String key = playerKey(name);
            if (!key.isEmpty()) {
                target.add(key);
            }
        }
    }

    private static boolean containsNameIgnoreCase(List<String> list, String name) {
        String key = playerKey(name);
        if (key.isEmpty() || list == null) return false;
        for (String value : list) {
            if (playerKey(value).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizePlayerName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        return trimmed.matches("[A-Za-z0-9_]{3,16}") ? trimmed : "";
    }

    private static String playerKey(String name) {
        String cleaned = sanitizePlayerName(name);
        return cleaned.isEmpty() ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    public static void load() {
        File source = CONFIG_FILE.exists() ? CONFIG_FILE : firstExistingLegacyConfig();
        if (source.exists()) {
            loadFromFile(source);
        }

        if (!CONFIG_FILE.exists() && source.exists() && !source.equals(CONFIG_FILE)) {
            com.betteruc.BetterUCMod.LOGGER.info(
                    "Migrated legacy config from {} to {}",
                    source.getName(),
                    CONFIG_FILE.getName()
            );
        }
        ensureRuntimeCollections();
        sanitizeHudStyles();
        sanitizeHudScales();
        sanitizeTrackedFactions();
        rebuildRemoteFactionUnion();
        refreshRuntimeNameCaches();
        save();
    }

    private static File firstExistingLegacyConfig() {
        return LEGACY_NAMETAG_CONFIG_FILE;
    }

    private static void loadFromFile(File file) {
        try (Reader r = new FileReader(file)) {
            BetterUCConfig loaded = GSON.fromJson(r, BetterUCConfig.class);
            if (loaded == null) return;

            INSTANCE = loaded;
            ensureRuntimeCollections();
            if (INSTANCE.hotkeyCommands == null)         INSTANCE.hotkeyCommands = new ArrayList<>();
            if (INSTANCE.trackedFactionQueries == null)  INSTANCE.trackedFactionQueries = defaultTrackedFactionQueries();
            if (INSTANCE.zoomKeyCode <= 0) INSTANCE.zoomKeyCode = 67;
            if (INSTANCE.zoomFovMultiplier <= 0.0f || INSTANCE.zoomFovMultiplier > 1.0f) {
                INSTANCE.zoomFovMultiplier = 0.25f;
            }
            if (INSTANCE.ammoHudX == 0 && INSTANCE.ammoHudY == 0) {
                INSTANCE.ammoHudX = 10;
                INSTANCE.ammoHudY = 82;
                INSTANCE.showAmmoHud = true;
            }
            if (INSTANCE.bankHudX == 0 && INSTANCE.bankHudY == 0) {
                INSTANCE.bankHudX = 10;
                INSTANCE.bankHudY = 100;
                INSTANCE.showBankHud = true;
            }
            if (INSTANCE.potionHudX == 0 && INSTANCE.potionHudY == 0) {
                INSTANCE.potionHudX = 10;
                INSTANCE.potionHudY = 118;
                INSTANCE.showPotionEffectsHud = true;
            }
            if (INSTANCE.lastKnownBankBalance < -1) {
                INSTANCE.lastKnownBankBalance = -1;
            }
            migrateSplitTimerPositions();
            INSTANCE.toggleSprintHudColor = sanitizeHudColor(INSTANCE.toggleSprintHudColor, DEFAULT_TOGGLE_SPRINT_HUD_COLOR);
            INSTANCE.fpsHudColor = sanitizeHudColor(INSTANCE.fpsHudColor, DEFAULT_FPS_HUD_COLOR);
            INSTANCE.paydayHudColor = sanitizeHudColor(INSTANCE.paydayHudColor, DEFAULT_PAYDAY_HUD_COLOR);
            INSTANCE.bankHudColor = sanitizeHudColor(INSTANCE.bankHudColor, DEFAULT_BANK_HUD_COLOR);
            INSTANCE.healthHudColor = sanitizeHudColor(INSTANCE.healthHudColor, DEFAULT_HEALTH_HUD_COLOR);
            INSTANCE.healthHudHeartColor = sanitizeHudColor(INSTANCE.healthHudHeartColor, INSTANCE.healthHudColor);
            INSTANCE.healthHudTextColor = sanitizeHudColor(INSTANCE.healthHudTextColor, INSTANCE.healthHudColor);
            sanitizeHudStyles();
            sanitizeHudScales();
            if (INSTANCE.blReasons == null || INSTANCE.blReasons.isEmpty()) {
                INSTANCE.blReasons = defaultBlacklistReasons();
            }
            if (INSTANCE.plantTimerStates == null) {
                INSTANCE.plantTimerStates = new LinkedHashMap<>();
            }
            sanitizeTrackedFactions();
            rebuildRemoteFactionUnion();
            refreshRuntimeNameCaches();
        } catch (Exception e) {
            com.betteruc.BetterUCMod.LOGGER.error("Failed to load config", e);
        }
    }

    private static void migrateSplitTimerPositions() {
        if (INSTANCE.timerX == 10 && INSTANCE.timerY == 10) return;

        if (INSTANCE.hackTimerX == 10 && INSTANCE.hackTimerY == 10) {
            INSTANCE.hackTimerX = INSTANCE.timerX;
            INSTANCE.hackTimerY = INSTANCE.timerY;
        }
        if (INSTANCE.plantTimerX == 10 && INSTANCE.plantTimerY == 46) {
            INSTANCE.plantTimerX = INSTANCE.timerX;
            INSTANCE.plantTimerY = INSTANCE.timerY + 36;
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            com.betteruc.BetterUCMod.LOGGER.error("Failed to save config", e);
        }
    }

    public static boolean isFaction(String name) {
        ensureRuntimeCollections();
        String key = playerKey(name);
        return !key.isEmpty()
                && (INSTANCE.manualFactionPlayerKeys.contains(key)
                || INSTANCE.remoteFactionPlayerKeys.contains(key));
    }

    public static boolean isBlacklist(String name) {
        ensureRuntimeCollections();
        String key = playerKey(name);
        return !key.isEmpty()
                && (INSTANCE.manualBlacklistPlayerKeys.contains(key)
                || INSTANCE.chatBlacklistPlayerKeys.contains(key));
    }

    public static boolean isVogelfrei(String name) {
        ensureRuntimeCollections();
        String key = playerKey(name);
        return !key.isEmpty() && INSTANCE.vogelfreiPlayerKeys.contains(key);
    }
}
