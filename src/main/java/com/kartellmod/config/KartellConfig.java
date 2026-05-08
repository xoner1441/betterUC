package com.kartellmod.config;

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

public class KartellConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("kartellmod.json").toFile();
    private static final File LEGACY_CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("nametagmod.json").toFile();

    public List<String> manualFactionPlayers = new ArrayList<>();
    public List<String> manualBlacklistPlayers = new ArrayList<>();

    public transient List<String> remoteFactionPlayers = new ArrayList<>();
    public transient Map<String, List<String>> remoteFactionMembersByFaction = new LinkedHashMap<>();
    public transient List<String> chatBlacklistPlayers = new ArrayList<>();
    public transient List<String> vogelfreiPlayers = new ArrayList<>();

    // Name -> Grund-String (z.B. "Gangzone + Leadermord")
    public transient Map<String, String> blacklistReasons = new LinkedHashMap<>();
    // Name -> [kills, price]
    public transient Map<String, int[]> blacklistStats = new LinkedHashMap<>();

    public transient int currentMoney = 0;
    public transient int currentBlackMoney = 0;
    public List<String> trackedFactionQueries = defaultTrackedFactionQueries();
    public static final String[] EIGENBEDARF_DRUG_OPTIONS = new String[]{
            "Pulver",
            "Kr\u00E4uter",
            "Kristalle",
            "Wundert\u00FCte"
    };
    public static final int DEFAULT_TOGGLE_SPRINT_HUD_COLOR = 0xFF55FF55;
    public static final int DEFAULT_FPS_HUD_COLOR = 0xFF55FFFF;
    public static final int DEFAULT_PAYDAY_HUD_COLOR = 0xFFFFD866;
    public static final int DEFAULT_BANK_HUD_COLOR = 0xFF55FFFF;
    public static KartellConfig INSTANCE = new KartellConfig();
    private static final List<TrackableFaction> TRACKABLE_FACTIONS = List.of(
            new TrackableFaction("Polizei", "polizei"),
            new TrackableFaction("FBI", "fbi"),
            new TrackableFaction("Rettungsdienst", "medic"),
            new TrackableFaction("La Cosa Nostra", "lcn"),
            new TrackableFaction("Westside Ballas", "ballas"),
            new TrackableFaction("Calderon Kartell", "kartell"),
            new TrackableFaction("Yakuza", "yakuza"),
            new TrackableFaction("S\u00F6ldner", "soeldner"),
            new TrackableFaction("KF", "kerzakov")
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

    public static class EigenbedarfPreset {
        public String droge = EIGENBEDARF_DRUG_OPTIONS[0];
        public int menge = 0;
        public int reinheit = 0;

        public EigenbedarfPreset() {}

        public EigenbedarfPreset(String droge, int menge, int reinheit) {
            this.droge = droge;
            this.menge = menge;
            this.reinheit = reinheit;
        }
    }

    public List<HotkeyCommand> hotkeyCommands = new ArrayList<>();
    public EigenbedarfPreset eigenbedarfSlot1 = new EigenbedarfPreset(EIGENBEDARF_DRUG_OPTIONS[0], 0, 0);
    public EigenbedarfPreset eigenbedarfSlot2 = new EigenbedarfPreset(EIGENBEDARF_DRUG_OPTIONS[1], 0, 0);

    public int factionColor = 0x00FF00;
    public int blacklistColor = 0xFF0000;
    public int timerX = 10;
    public int timerY = 10;
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
    public int lastKnownBankBalance = -1;
    public int toggleSprintHudColor = DEFAULT_TOGGLE_SPRINT_HUD_COLOR;
    public int fpsHudColor = DEFAULT_FPS_HUD_COLOR;
    public int paydayHudColor = DEFAULT_PAYDAY_HUD_COLOR;
    public int bankHudColor = DEFAULT_BANK_HUD_COLOR;
    public boolean showHealthHud = true;
    public boolean showFpsHud = true;
    public boolean showPaydayHud = true;
    public boolean showAmmoHud = true;
    public boolean showBankHud = true;
    public boolean toggleSprintEnabled = false;
    public boolean zoomEnabled = true;
    public int zoomKeyCode = 67; // GLFW_KEY_C
    public float zoomFovMultiplier = 0.25f;
    public boolean zoomInstant = true;
    public boolean fullbrightEnabled = false;
    public boolean carAutomationEnabled = true;

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

    public static int clampEigenbedarfPurity(int value) {
        return Math.max(0, Math.min(value, 3));
    }

    public static int clampEigenbedarfAmount(int value) {
        return Math.max(0, value);
    }

    public static String normalizeEigenbedarfDrug(String raw) {
        if (raw == null) return EIGENBEDARF_DRUG_OPTIONS[0];
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return EIGENBEDARF_DRUG_OPTIONS[0];

        if (trimmed.equalsIgnoreCase("Kraeuter")) return EIGENBEDARF_DRUG_OPTIONS[1];
        if (trimmed.equalsIgnoreCase("Wundertuete")) return EIGENBEDARF_DRUG_OPTIONS[3];

        for (String option : EIGENBEDARF_DRUG_OPTIONS) {
            if (option.equalsIgnoreCase(trimmed)) return option;
        }
        return EIGENBEDARF_DRUG_OPTIONS[0];
    }

    private static EigenbedarfPreset sanitizeEigenbedarfPreset(EigenbedarfPreset preset, String fallbackDrug) {
        EigenbedarfPreset safe = preset == null ? new EigenbedarfPreset() : preset;
        safe.droge = normalizeEigenbedarfDrug(safe.droge == null || safe.droge.isBlank() ? fallbackDrug : safe.droge);
        safe.menge = clampEigenbedarfAmount(safe.menge);
        safe.reinheit = clampEigenbedarfPurity(safe.reinheit);
        return safe;
    }

    private static int sanitizeHudColor(int color, int fallback) {
        if (color == 0) return fallback;
        if ((color & 0xFF000000) == 0) return 0xFF000000 | color;
        return color;
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
        if (folded.equals("rettungsdienst") || folded.equals("retungsdienst")) return "retungsdienst";
        if (folded.equals("soldner") || folded.equals("soeldner")) return "soeldner";
        if (folded.equals("f b i")) return "fbi";
        return folded;
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
    }

    public static void rebuildRemoteFactionUnion() {
        if (INSTANCE.remoteFactionPlayers == null) {
            INSTANCE.remoteFactionPlayers = new ArrayList<>();
        } else {
            INSTANCE.remoteFactionPlayers.clear();
        }
        if (INSTANCE.remoteFactionMembersByFaction == null) {
            INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
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

    public static void load() {
        File source = CONFIG_FILE.exists() ? CONFIG_FILE : LEGACY_CONFIG_FILE;
        if (source.exists()) {
            loadFromFile(source);
        }

        if (!CONFIG_FILE.exists() && LEGACY_CONFIG_FILE.exists()) {
            com.kartellmod.KartellMod.LOGGER.info(
                    "Migrated legacy config from {} to {}",
                    LEGACY_CONFIG_FILE.getName(),
                    CONFIG_FILE.getName()
            );
        }
        save();
    }

    private static void loadFromFile(File file) {
        try (Reader r = new FileReader(file)) {
            KartellConfig loaded = GSON.fromJson(r, KartellConfig.class);
            if (loaded == null) return;

            INSTANCE = loaded;
            if (INSTANCE.remoteFactionPlayers == null)   INSTANCE.remoteFactionPlayers = new ArrayList<>();
            if (INSTANCE.remoteFactionMembersByFaction == null) INSTANCE.remoteFactionMembersByFaction = new LinkedHashMap<>();
            if (INSTANCE.chatBlacklistPlayers == null)   INSTANCE.chatBlacklistPlayers = new ArrayList<>();
            if (INSTANCE.vogelfreiPlayers == null)       INSTANCE.vogelfreiPlayers = new ArrayList<>();
            if (INSTANCE.blacklistReasons == null)       INSTANCE.blacklistReasons = new LinkedHashMap<>();
            if (INSTANCE.blacklistStats == null)         INSTANCE.blacklistStats = new LinkedHashMap<>();
            if (INSTANCE.manualFactionPlayers == null)   INSTANCE.manualFactionPlayers = new ArrayList<>();
            if (INSTANCE.manualBlacklistPlayers == null) INSTANCE.manualBlacklistPlayers = new ArrayList<>();
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
            if (INSTANCE.lastKnownBankBalance < -1) {
                INSTANCE.lastKnownBankBalance = -1;
            }
            INSTANCE.toggleSprintHudColor = sanitizeHudColor(INSTANCE.toggleSprintHudColor, DEFAULT_TOGGLE_SPRINT_HUD_COLOR);
            INSTANCE.fpsHudColor = sanitizeHudColor(INSTANCE.fpsHudColor, DEFAULT_FPS_HUD_COLOR);
            INSTANCE.paydayHudColor = sanitizeHudColor(INSTANCE.paydayHudColor, DEFAULT_PAYDAY_HUD_COLOR);
            INSTANCE.bankHudColor = sanitizeHudColor(INSTANCE.bankHudColor, DEFAULT_BANK_HUD_COLOR);
            INSTANCE.eigenbedarfSlot1 = sanitizeEigenbedarfPreset(
                    INSTANCE.eigenbedarfSlot1,
                    EIGENBEDARF_DRUG_OPTIONS[0]
            );
            INSTANCE.eigenbedarfSlot2 = sanitizeEigenbedarfPreset(
                    INSTANCE.eigenbedarfSlot2,
                    EIGENBEDARF_DRUG_OPTIONS[1]
            );
            if (INSTANCE.blReasons == null || INSTANCE.blReasons.isEmpty()) {
                INSTANCE.blReasons = defaultBlacklistReasons();
            }
            sanitizeTrackedFactions();
            rebuildRemoteFactionUnion();
        } catch (Exception e) {
            com.kartellmod.KartellMod.LOGGER.error("Failed to load config", e);
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            com.kartellmod.KartellMod.LOGGER.error("Failed to save config", e);
        }
    }

    public static boolean isFaction(String name) {
        return INSTANCE.manualFactionPlayers.stream().anyMatch(s -> s.equalsIgnoreCase(name))
                || INSTANCE.remoteFactionPlayers.stream().anyMatch(s -> s.equalsIgnoreCase(name));
    }

    public static boolean isBlacklist(String name) {
        return INSTANCE.manualBlacklistPlayers.stream().anyMatch(s -> s.equalsIgnoreCase(name))
                || INSTANCE.chatBlacklistPlayers.stream().anyMatch(s -> s.equalsIgnoreCase(name));
    }

    public static boolean isVogelfrei(String name) {
        return INSTANCE.vogelfreiPlayers.stream().anyMatch(s -> s.equalsIgnoreCase(name));
    }
}
