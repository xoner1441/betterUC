package com.betteruc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.betteruc.parser.FactionStatsParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final Path HUD_PROFILE_DIRECTORY = FabricLoader.getInstance()
            .getConfigDir().resolve("betteruc").resolve("hud-profiles");
    private static final String HUD_PROFILE_FILE_FORMAT = "betteruc-hud-profile";
    private static final int HUD_PROFILE_FILE_SCHEMA = 1;
    private static final int MAX_HUD_PROFILES = 64;
    private static final long MAX_HUD_PROFILE_FILE_BYTES = 256L * 1024L;
    private static final Set<String> CLOUD_SETTING_FIELDS = Set.of(
            "manualFactionPlayers", "manualBlacklistPlayers", "trackedFactionQueries", "hotkeyCommands",
            "timerX", "timerY", "hackTimerX", "hackTimerY", "plantTimerX", "plantTimerY",
            "dealerTimerX", "dealerTimerY", "maskTimerX", "maskTimerY", "productionTimerX", "productionTimerY",
            "healthHudX", "healthHudY", "toggleSprintHudX", "toggleSprintHudY", "fpsHudX", "fpsHudY",
            "paydayHudX", "paydayHudY", "ammoHudX", "ammoHudY", "bankHudX", "bankHudY",
            "cashHudX", "cashHudY", "potionHudX", "potionHudY",
            "healthHudScale", "toggleSprintHudScale", "fpsHudScale", "paydayHudScale", "ammoHudScale",
            "bankHudScale", "cashHudScale", "potionHudScale", "hackTimerHudScale", "plantTimerHudScale",
            "dealerTimerHudScale", "maskTimerHudScale", "productionTimerHudScale",
            "toggleSprintHudColor", "fpsHudColor", "paydayHudColor", "bankHudColor", "cashHudColor",
            "potionHudColor",
            "dealerTimerHudColor", "maskTimerHudColor", "productionTimerHudColor", "healthHudHeartColor", "healthHudTextColor",
            "healthHudAbsorptionColor", "showHealthAbsorption",
            "healthHudColor", "hudColorGradientEnabled", "hudGradientColor", "hudGradientConfigVersion",
            "healthHudGradientEnabled", "toggleSprintHudGradientEnabled", "fpsHudGradientEnabled",
            "paydayHudGradientEnabled", "ammoHudGradientEnabled", "bankHudGradientEnabled",
            "cashHudGradientEnabled", "potionHudGradientEnabled", "hackTimerHudGradientEnabled",
            "plantTimerHudGradientEnabled", "dealerTimerHudGradientEnabled", "maskTimerHudGradientEnabled",
            "productionTimerHudGradientEnabled",
            "healthHudGradientColor", "toggleSprintHudGradientColor", "fpsHudGradientColor",
            "paydayHudGradientColor", "ammoHudGradientColor", "bankHudGradientColor", "cashHudGradientColor",
            "potionHudGradientColor", "hackTimerHudGradientColor", "plantTimerHudGradientColor",
            "dealerTimerHudGradientColor", "maskTimerHudGradientColor", "productionTimerHudGradientColor",
            "healthHudStyle", "toggleSprintHudStyle", "fpsHudStyle", "paydayHudStyle", "ammoHudStyle",
            "bankHudStyle", "cashHudStyle", "potionHudStyle", "hackTimerHudStyle", "plantTimerHudStyle",
            "dealerTimerHudStyle", "maskTimerHudStyle", "productionTimerHudStyle", "healthHudCustomFont",
            "toggleSprintHudCustomFont", "fpsHudCustomFont", "paydayHudCustomFont", "ammoHudCustomFont",
            "bankHudCustomFont", "cashHudCustomFont", "potionHudCustomFont", "hackTimerHudCustomFont",
            "plantTimerHudCustomFont", "dealerTimerHudCustomFont", "maskTimerHudCustomFont",
            "productionTimerHudCustomFont",
            "customHudFont", "cartoonHudFont",
            "toggleSprintHudPrefixEnabled", "fpsHudPrefixEnabled", "paydayHudPrefixEnabled",
            "ammoHudPrefixEnabled", "bankHudPrefixEnabled", "cashHudPrefixEnabled",
            "hackTimerHudPrefixEnabled", "plantTimerHudPrefixEnabled", "dealerTimerHudPrefixEnabled",
            "maskTimerHudPrefixEnabled", "productionTimerHudPrefixEnabled", "toggleSprintHudPrefix", "fpsHudPrefix", "paydayHudPrefix",
            "ammoHudPrefix", "bankHudPrefix", "cashHudPrefix", "hackTimerHudPrefix", "plantTimerHudPrefix",
            "dealerTimerHudPrefix", "maskTimerHudPrefix", "productionTimerHudPrefix",
            "ammoHudMagazineBarEnabled", "ammoHudLowAmmoWarningEnabled", "ammoHudLowAmmoSoundEnabled",
            "ammoHudLowAmmoThresholdPercent", "ammoHudKr47MagazineSize",
            "showHealthHud", "showFpsHud", "showPaydayHud", "showAmmoHud", "showBankHud", "showCashHud",
            "showPotionEffectsHud", "showPlantTimerHud", "showDealerTimerHud", "showMaskTimerHud",
            "showProductionTimerHud",
            "toggleSprintEnabled", "autoStatsOnJoinEnabled", "autoFactionBankOnBalanceEnabled",
            "autoAtmInfoOnBalanceEnabled", "autoForceDepositEnabled", "richTaxAlertEnabled",
            "richTaxAlertSoundEnabled",
            "chatTimestampsEnabled", "chatCustomizationEnabled",
            "secondChatEnabled", "secondChatLocked", "secondChatBackgroundEnabled",
            "secondChatX", "secondChatY", "secondChatWidth", "secondChatHeight",
            "secondChatPrimaryCustomSize",
            "secondChatBackgroundOpacity", "secondChatAccentColor", "secondChatHighlightColor",
            "secondChatMentionSoundEnabled", "secondChatHqMode", "secondChatReinfMode",
            "secondChatPrivateMode", "secondChatServerInfoMode", "secondChatBetterUcMode",
            "secondChatOwnNameMode", "secondChatCustom1Mode", "secondChatCustom2Mode",
            "secondChatCustom3Mode", "secondChatCustom1Text", "secondChatCustom2Text",
            "secondChatCustom3Text", "secondChatTabs", "secondChatWindows", "secondChatActiveTabId",
            "autoDropDrinkEnabled", "autoFisherEnabled", "autoWinzerEnabled", "autoGaertnerEnabled",
            "autoMuellmannEnabled", "autoFirstAidEnabled", "autoBuyEnabled",
            "reinfCustomizationEnabled", "reinfUniformColorEnabled", "reinfLabelColor", "reinfTextColor",
            "reinfDistanceColor", "reinfUniformColor", "chatTimestampFormat", "maxChatHistory",
            "pingRelayEnabled", "showPingHud", "showRoleHolograms", "pingHudScale", "pingHudStyle",
            "pingHudCustomFont", "pingRelayScope", "pingRelayTtlSeconds", "pingRelayMaxDistance",
            "pingRelayColor", "pingNormalColor", "pingDangerColor", "pingGatherColor", "pingCooldownMs",
            "pingSoundEnabled", "pingSoundId", "autoUpdateEnabled", "blReasons",
            "trashFilterEnabled", "trashFilterCloseLockEnabled", "trashFilterRottenFlesh",
            "trashFilterPaper", "trashFilterPotato", "trashFilterCarrot", "trashFilterApple",
            "trashFilterChest", "trashFilterTrappedChest", "trashFilterEnderChest",
            "hudProfiles", "activeHudProfile"
    );
    private static final Set<String> HUD_PROFILE_FIELDS = Set.of(
            "healthHudX", "healthHudY", "toggleSprintHudX", "toggleSprintHudY", "fpsHudX", "fpsHudY",
            "paydayHudX", "paydayHudY", "ammoHudX", "ammoHudY", "bankHudX", "bankHudY",
            "cashHudX", "cashHudY", "potionHudX", "potionHudY", "hackTimerX", "hackTimerY",
            "plantTimerX", "plantTimerY", "dealerTimerX", "dealerTimerY", "maskTimerX", "maskTimerY",
            "productionTimerX", "productionTimerY",
            "healthHudScale", "toggleSprintHudScale", "fpsHudScale", "paydayHudScale", "ammoHudScale",
            "bankHudScale", "cashHudScale", "potionHudScale", "hackTimerHudScale", "plantTimerHudScale",
            "dealerTimerHudScale", "maskTimerHudScale", "productionTimerHudScale",
            "toggleSprintHudColor", "fpsHudColor", "paydayHudColor", "bankHudColor", "cashHudColor",
            "potionHudColor", "dealerTimerHudColor", "maskTimerHudColor", "productionTimerHudColor",
            "healthHudHeartColor", "healthHudTextColor", "healthHudAbsorptionColor",
            "healthHudGradientEnabled", "toggleSprintHudGradientEnabled", "fpsHudGradientEnabled",
            "paydayHudGradientEnabled", "ammoHudGradientEnabled", "bankHudGradientEnabled",
            "cashHudGradientEnabled", "potionHudGradientEnabled", "hackTimerHudGradientEnabled",
            "plantTimerHudGradientEnabled", "dealerTimerHudGradientEnabled", "maskTimerHudGradientEnabled",
            "productionTimerHudGradientEnabled",
            "healthHudGradientColor", "toggleSprintHudGradientColor", "fpsHudGradientColor",
            "paydayHudGradientColor", "ammoHudGradientColor", "bankHudGradientColor", "cashHudGradientColor",
            "potionHudGradientColor", "hackTimerHudGradientColor", "plantTimerHudGradientColor",
            "dealerTimerHudGradientColor", "maskTimerHudGradientColor", "productionTimerHudGradientColor",
            "healthHudStyle", "toggleSprintHudStyle", "fpsHudStyle", "paydayHudStyle", "ammoHudStyle",
            "bankHudStyle", "cashHudStyle", "potionHudStyle", "hackTimerHudStyle", "plantTimerHudStyle",
            "dealerTimerHudStyle", "maskTimerHudStyle", "productionTimerHudStyle",
            "healthHudCustomFont", "toggleSprintHudCustomFont", "fpsHudCustomFont", "paydayHudCustomFont",
            "ammoHudCustomFont", "bankHudCustomFont", "cashHudCustomFont", "potionHudCustomFont",
            "hackTimerHudCustomFont", "plantTimerHudCustomFont", "dealerTimerHudCustomFont",
            "maskTimerHudCustomFont", "productionTimerHudCustomFont",
            "toggleSprintHudPrefixEnabled", "fpsHudPrefixEnabled", "paydayHudPrefixEnabled",
            "ammoHudPrefixEnabled", "bankHudPrefixEnabled", "cashHudPrefixEnabled",
            "hackTimerHudPrefixEnabled", "plantTimerHudPrefixEnabled", "dealerTimerHudPrefixEnabled",
            "maskTimerHudPrefixEnabled", "productionTimerHudPrefixEnabled", "toggleSprintHudPrefix", "fpsHudPrefix", "paydayHudPrefix",
            "ammoHudPrefix", "bankHudPrefix", "cashHudPrefix", "hackTimerHudPrefix", "plantTimerHudPrefix",
            "dealerTimerHudPrefix", "maskTimerHudPrefix", "productionTimerHudPrefix",
            "showHealthHud", "showHealthAbsorption", "showFpsHud", "showPaydayHud", "showAmmoHud",
            "showBankHud", "showCashHud", "showPotionEffectsHud", "showPlantTimerHud",
            "showDealerTimerHud", "showMaskTimerHud", "showProductionTimerHud", "toggleSprintEnabled",
            "ammoHudMagazineBarEnabled", "ammoHudLowAmmoWarningEnabled", "ammoHudLowAmmoSoundEnabled",
            "ammoHudLowAmmoThresholdPercent", "ammoHudKr47MagazineSize"
    );
    private static Runnable saveListener = () -> { };

    public List<String> manualFactionPlayers = new ArrayList<>();
    public List<String> manualBlacklistPlayers = new ArrayList<>();
    public transient Map<String, WasteDropArea> wasteDropAreas = new LinkedHashMap<>();

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
    public static final int DEFAULT_CASH_HUD_COLOR = 0xFF86EFAC;
    public static final int DEFAULT_POTION_HUD_COLOR = 0xFF9328FF;
    public static final int DEFAULT_HEALTH_HUD_COLOR = 0xFFFF5555;
    public static final int DEFAULT_HEALTH_HUD_HEART_COLOR = DEFAULT_HEALTH_HUD_COLOR;
    public static final int DEFAULT_HEALTH_HUD_TEXT_COLOR = DEFAULT_HEALTH_HUD_COLOR;
    public static final int DEFAULT_HEALTH_HUD_ABSORPTION_COLOR = 0xFFFFC83D;
    public static final int DEFAULT_HUD_GRADIENT_COLOR = 0xFFA855F7;
    public static final int DEFAULT_REINF_LABEL_COLOR = 0xFFFF5555;
    public static final int DEFAULT_REINF_TEXT_COLOR = 0xFF55FFFF;
    public static final int DEFAULT_REINF_DISTANCE_COLOR = 0xFFFFFF55;
    public static final int DEFAULT_REINF_UNIFORM_COLOR = 0xFF55FFFF;
    public static final float MIN_HUD_SCALE = 0.5F;
    public static final float MAX_HUD_SCALE = 3.0F;
    public static final float DEFAULT_HUD_SCALE = 1.0F;
    public static final String HUD_STYLE_MODERN = "modern";
    public static final String HUD_STYLE_TRANSPARENT = "transparent";
    public static final String HUD_STYLE_CARTOON = "cartoon";
    public static final String HUD_STYLE_CUSTOM = "custom";
    public static final String DEFAULT_PING_RELAY_URL = "wss://ping.betteruc.de/ws";
    public static final String DEFAULT_DISCORD_INVITE_URL = "https://discord.gg/UQQQw8hVsn";
    private static final String LEGACY_PING_RELAY_URL = "ws://65.109.175.203:3000/ws";
    public static BetterUCConfig INSTANCE = new BetterUCConfig();
    private static final List<TrackableFaction> TRACKABLE_FACTIONS = List.of(
            new TrackableFaction("Zivilist", "zivilist"),
            new TrackableFaction("Polizei", "polizei"),
            new TrackableFaction("FBI", "fbi"),
            new TrackableFaction("Rettungsdienst", "medic"),
            new TrackableFaction("La Cosa Nostra", "lcn"),
            new TrackableFaction("Westside Ballas", "ballas"),
            new TrackableFaction("Calderon Kartell", "kartell"),
            new TrackableFaction("Kerzakov Familie", "kerzakov"),
            new TrackableFaction("Yakuza", "yakuza"),
            new TrackableFaction("S\u00F6ldner", "soeldner"),
            new TrackableFaction("News", "news"),
            new TrackableFaction("Ordo Absolutus", "ordo")
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
        public boolean sendImmediately = true;
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
    public int dealerTimerX = 10;
    public int dealerTimerY = 202;
    public int maskTimerX = 10;
    public int maskTimerY = 238;
    public int productionTimerX = 10;
    public int productionTimerY = 220;
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
    public int cashHudX = 10;
    public int cashHudY = 184;
    public int potionHudX = 10;
    public int potionHudY = 118;
    public float healthHudScale = DEFAULT_HUD_SCALE;
    public float toggleSprintHudScale = DEFAULT_HUD_SCALE;
    public float fpsHudScale = DEFAULT_HUD_SCALE;
    public float paydayHudScale = DEFAULT_HUD_SCALE;
    public float ammoHudScale = DEFAULT_HUD_SCALE;
    public float bankHudScale = DEFAULT_HUD_SCALE;
    public float cashHudScale = DEFAULT_HUD_SCALE;
    public float potionHudScale = DEFAULT_HUD_SCALE;
    public float hackTimerHudScale = DEFAULT_HUD_SCALE;
    public float plantTimerHudScale = DEFAULT_HUD_SCALE;
    public float dealerTimerHudScale = DEFAULT_HUD_SCALE;
    public float maskTimerHudScale = DEFAULT_HUD_SCALE;
    public float productionTimerHudScale = DEFAULT_HUD_SCALE;
    public int lastKnownBankBalance = -1;
    public int toggleSprintHudColor = DEFAULT_TOGGLE_SPRINT_HUD_COLOR;
    public int fpsHudColor = DEFAULT_FPS_HUD_COLOR;
    public int paydayHudColor = DEFAULT_PAYDAY_HUD_COLOR;
    public int bankHudColor = DEFAULT_BANK_HUD_COLOR;
    public int cashHudColor = DEFAULT_CASH_HUD_COLOR;
    public int potionHudColor = DEFAULT_POTION_HUD_COLOR;
    public int dealerTimerHudColor = 0xFFD946EF;
    public int maskTimerHudColor = 0xFF22D3EE;
    public int productionTimerHudColor = 0xFFFBBF24;
    public int healthHudHeartColor = 0;
    public int healthHudTextColor = 0;
    public int healthHudAbsorptionColor = DEFAULT_HEALTH_HUD_ABSORPTION_COLOR;
    public int healthHudColor = DEFAULT_HEALTH_HUD_COLOR;
    public boolean hudColorGradientEnabled = false;
    public int hudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int hudGradientConfigVersion = 0;
    public boolean healthHudGradientEnabled = false;
    public boolean toggleSprintHudGradientEnabled = false;
    public boolean fpsHudGradientEnabled = false;
    public boolean paydayHudGradientEnabled = false;
    public boolean ammoHudGradientEnabled = false;
    public boolean bankHudGradientEnabled = false;
    public boolean cashHudGradientEnabled = false;
    public boolean potionHudGradientEnabled = false;
    public boolean hackTimerHudGradientEnabled = false;
    public boolean plantTimerHudGradientEnabled = false;
    public boolean dealerTimerHudGradientEnabled = false;
    public boolean maskTimerHudGradientEnabled = false;
    public boolean productionTimerHudGradientEnabled = false;
    public int healthHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int toggleSprintHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int fpsHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int paydayHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int ammoHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int bankHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int cashHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int potionHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int hackTimerHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int plantTimerHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int dealerTimerHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int maskTimerHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public int productionTimerHudGradientColor = DEFAULT_HUD_GRADIENT_COLOR;
    public String healthHudStyle = HUD_STYLE_TRANSPARENT;
    public String toggleSprintHudStyle = HUD_STYLE_MODERN;
    public String fpsHudStyle = HUD_STYLE_MODERN;
    public String paydayHudStyle = HUD_STYLE_MODERN;
    public String ammoHudStyle = HUD_STYLE_MODERN;
    public String bankHudStyle = HUD_STYLE_MODERN;
    public String cashHudStyle = HUD_STYLE_MODERN;
    public String potionHudStyle = HUD_STYLE_MODERN;
    public String hackTimerHudStyle = HUD_STYLE_MODERN;
    public String plantTimerHudStyle = HUD_STYLE_MODERN;
    public String dealerTimerHudStyle = HUD_STYLE_MODERN;
    public String maskTimerHudStyle = HUD_STYLE_MODERN;
    public String productionTimerHudStyle = HUD_STYLE_MODERN;
    public String healthHudCustomFont = "";
    public String toggleSprintHudCustomFont = "";
    public String fpsHudCustomFont = "";
    public String paydayHudCustomFont = "";
    public String ammoHudCustomFont = "";
    public String bankHudCustomFont = "";
    public String cashHudCustomFont = "";
    public String potionHudCustomFont = "";
    public String hackTimerHudCustomFont = "";
    public String plantTimerHudCustomFont = "";
    public String dealerTimerHudCustomFont = "";
    public String maskTimerHudCustomFont = "";
    public String productionTimerHudCustomFont = "";
    public String customHudFont = "";
    public String cartoonHudFont = "";
    public boolean toggleSprintHudPrefixEnabled = true;
    public boolean fpsHudPrefixEnabled = true;
    public boolean paydayHudPrefixEnabled = true;
    public boolean ammoHudPrefixEnabled = true;
    public boolean bankHudPrefixEnabled = true;
    public boolean cashHudPrefixEnabled = true;
    public boolean hackTimerHudPrefixEnabled = true;
    public boolean plantTimerHudPrefixEnabled = true;
    public boolean dealerTimerHudPrefixEnabled = true;
    public boolean maskTimerHudPrefixEnabled = true;
    public boolean productionTimerHudPrefixEnabled = true;
    public String toggleSprintHudPrefix = "ToggleSprint";
    public String fpsHudPrefix = "FPS";
    public String paydayHudPrefix = "Payday";
    public String ammoHudPrefix = "Ammo";
    public String bankHudPrefix = "Bank";
    public String cashHudPrefix = "Bargeld";
    public String hackTimerHudPrefix = "Hack";
    public String plantTimerHudPrefix = "Plant";
    public String dealerTimerHudPrefix = "Dealer";
    public String maskTimerHudPrefix = "Maske";
    public String productionTimerHudPrefix = "Produktion";
    public boolean ammoHudMagazineBarEnabled = true;
    public boolean ammoHudLowAmmoWarningEnabled = true;
    public boolean ammoHudLowAmmoSoundEnabled = true;
    public int ammoHudLowAmmoThresholdPercent = 25;
    public int ammoHudKr47MagazineSize = 0;
    public boolean showHealthHud = true;
    public boolean showHealthAbsorption = true;
    public boolean showFpsHud = true;
    public boolean showPaydayHud = true;
    public boolean showAmmoHud = true;
    public boolean showBankHud = true;
    public boolean showCashHud = true;
    public boolean showPotionEffectsHud = true;
    public boolean showPlantTimerHud = true;
    public boolean showDealerTimerHud = true;
    public boolean showMaskTimerHud = true;
    public boolean showProductionTimerHud = true;
    public boolean toggleSprintEnabled = false;
    public boolean autoStatsOnJoinEnabled = true;
    public boolean autoFactionBankOnBalanceEnabled = false;
    public boolean autoAtmInfoOnBalanceEnabled = false;
    public boolean autoForceDepositEnabled = false;
    public boolean richTaxAlertEnabled = true;
    public boolean richTaxAlertSoundEnabled = true;
    public boolean autoDropDrinkEnabled = true;
    public boolean autoFisherEnabled = true;
    public boolean autoWinzerEnabled = true;
    public boolean autoGaertnerEnabled = true;
    public boolean autoMuellmannEnabled = true;
    public boolean autoFirstAidEnabled = false;
    public boolean autoBuyEnabled = true;
    public Map<String, PlantTimerState> plantTimerStates = new LinkedHashMap<>();
    public Map<String, JsonObject> hudProfiles = new LinkedHashMap<>();
    public String activeHudProfile = "Standard";
    public String clickGuiLastCategory = "HUD";
    public String clickGuiLastModule = "FPS";
    public Map<String, Integer> clickGuiScrollOffsets = new LinkedHashMap<>();
    public int clickGuiUpdatesScrollOffset = 0;

    public String factionUrl = "https://example.com/faction.json";
    public int reloadIntervalMinutes = 5;
    public boolean chatTimestampsEnabled = true;
    public boolean chatCustomizationEnabled = true;
    public boolean reinfCustomizationEnabled = true;
    public boolean reinfUniformColorEnabled = false;
    public int reinfLabelColor = DEFAULT_REINF_LABEL_COLOR;
    public int reinfTextColor = DEFAULT_REINF_TEXT_COLOR;
    public int reinfDistanceColor = DEFAULT_REINF_DISTANCE_COLOR;
    public int reinfUniformColor = DEFAULT_REINF_UNIFORM_COLOR;
    public String chatTimestampFormat = "[HH:mm:ss]";
    public int maxChatHistory = 2000;
    public boolean secondChatEnabled = false;
    public boolean secondChatLocked = true;
    public boolean secondChatBackgroundEnabled = true;
    public int secondChatX = 8;
    public int secondChatY = 62;
    public int secondChatWidth = 330;
    public int secondChatHeight = 120;
    public boolean secondChatPrimaryCustomSize = false;
    public int secondChatBackgroundOpacity = 150;
    public int secondChatAccentColor = 0xFF38BDF8;
    public int secondChatHighlightColor = 0xFFFFD54A;
    public boolean secondChatMentionSoundEnabled = true;
    public String secondChatHqMode = "copy";
    public String secondChatReinfMode = "copy";
    public String secondChatPrivateMode = "copy";
    public String secondChatServerInfoMode = "off";
    public String secondChatBetterUcMode = "copy";
    public String secondChatOwnNameMode = "highlight";
    public String secondChatCustom1Mode = "off";
    public String secondChatCustom2Mode = "off";
    public String secondChatCustom3Mode = "off";
    public String secondChatCustom1Text = "";
    public String secondChatCustom2Text = "";
    public String secondChatCustom3Text = "";
    public List<SecondChatTabConfig> secondChatTabs = new ArrayList<>();
    public List<SecondChatWindowConfig> secondChatWindows = new ArrayList<>();
    public String secondChatActiveTabId = "main";
    public boolean secondChatTabsMigrated = false;
    public int lastKnownCash = -1;
    public String currentPlayerFaction = "";
    public String currentPlayerFactionLabel = "";
    public boolean pingRelayEnabled = true;
    public boolean showPingHud = true;
    public boolean showRoleHolograms = true;
    public float pingHudScale = DEFAULT_HUD_SCALE;
    public String pingHudStyle = HUD_STYLE_MODERN;
    public String pingHudCustomFont = "";
    public String pingRelayUrl = DEFAULT_PING_RELAY_URL;
    public String pingRelayToken = "";
    public String pingRelayChannel = "global";
    public String pingRelayScope = "global";
    public int pingRelayTtlSeconds = 15;
    public int pingRelayMaxDistance = 128;
    public String pingRelayColor = "#38BDF8";
    public String pingNormalColor = "#38BDF8";
    public String pingDangerColor = "#FF5555";
    public String pingGatherColor = "#22C55E";
    public int pingCooldownMs = 2000;
    public boolean pingSoundEnabled = true;
    public String pingSoundId = "pling";
    public String lastSeenWelcomeVersion = "";
    public String discordInviteUrl = DEFAULT_DISCORD_INVITE_URL;
    public boolean autoUpdateEnabled = true;
    public boolean cloudSettingsEnabled = true;
    public boolean trashFilterEnabled = false;
    public boolean trashFilterCloseLockEnabled = false;
    public boolean trashFilterRottenFlesh = true;
    public boolean trashFilterPaper = true;
    public boolean trashFilterPotato = true;
    public boolean trashFilterCarrot = true;
    public boolean trashFilterApple = true;
    public boolean trashFilterChest = true;
    public boolean trashFilterTrappedChest = true;
    public boolean trashFilterEnderChest = true;

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

    public static class WasteDropArea {
        public int x1;
        public int z1;
        public int x2;
        public int z2;
        public boolean pos1Set;
        public boolean pos2Set;
        public String dimension = "";

        public boolean isComplete() {
            return pos1Set && pos2Set && dimension != null && !dimension.isBlank();
        }

        public boolean contains(int x, int z, String currentDimension) {
            if (!isComplete() || currentDimension == null || !dimension.equals(currentDimension)) return false;
            return x >= Math.min(x1, x2)
                    && x <= Math.max(x1, x2)
                    && z >= Math.min(z1, z2)
                    && z <= Math.max(z1, z2);
        }
    }

    public static String hudPrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        return prefix.trim().replaceAll("\\s+", " ");
    }

    public static String hudModuleLabel(boolean enabled, String prefix) {
        if (!enabled) {
            return "";
        }
        return hudPrefix(prefix).toUpperCase(Locale.ROOT);
    }

    public static String prefixedHudText(boolean enabled, String prefix, String value) {
        String safeValue = value == null ? "" : value;
        if (!enabled) {
            return safeValue;
        }

        String safePrefix = hudPrefix(prefix);
        if (safePrefix.isEmpty()) {
            return safeValue;
        }
        return safePrefix + ": " + safeValue;
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
        INSTANCE.cashHudStyle = normalizeHudStyle(INSTANCE.cashHudStyle, HUD_STYLE_MODERN);
        INSTANCE.potionHudStyle = normalizeHudStyle(INSTANCE.potionHudStyle, HUD_STYLE_MODERN);
        INSTANCE.hackTimerHudStyle = normalizeHudStyle(INSTANCE.hackTimerHudStyle, HUD_STYLE_MODERN);
        INSTANCE.plantTimerHudStyle = normalizeHudStyle(INSTANCE.plantTimerHudStyle, HUD_STYLE_MODERN);
        INSTANCE.dealerTimerHudStyle = normalizeHudStyle(INSTANCE.dealerTimerHudStyle, HUD_STYLE_MODERN);
        INSTANCE.maskTimerHudStyle = normalizeHudStyle(INSTANCE.maskTimerHudStyle, HUD_STYLE_MODERN);
        INSTANCE.productionTimerHudStyle = normalizeHudStyle(INSTANCE.productionTimerHudStyle, HUD_STYLE_MODERN);
        INSTANCE.pingHudStyle = normalizeHudStyle(INSTANCE.pingHudStyle, HUD_STYLE_MODERN);
    }

    private static void sanitizeHudScales() {
        INSTANCE.healthHudScale = normalizeHudScale(INSTANCE.healthHudScale);
        INSTANCE.toggleSprintHudScale = normalizeHudScale(INSTANCE.toggleSprintHudScale);
        INSTANCE.fpsHudScale = normalizeHudScale(INSTANCE.fpsHudScale);
        INSTANCE.paydayHudScale = normalizeHudScale(INSTANCE.paydayHudScale);
        INSTANCE.ammoHudScale = normalizeHudScale(INSTANCE.ammoHudScale);
        INSTANCE.bankHudScale = normalizeHudScale(INSTANCE.bankHudScale);
        INSTANCE.cashHudScale = normalizeHudScale(INSTANCE.cashHudScale);
        INSTANCE.potionHudScale = normalizeHudScale(INSTANCE.potionHudScale);
        INSTANCE.hackTimerHudScale = normalizeHudScale(INSTANCE.hackTimerHudScale);
        INSTANCE.plantTimerHudScale = normalizeHudScale(INSTANCE.plantTimerHudScale);
        INSTANCE.dealerTimerHudScale = normalizeHudScale(INSTANCE.dealerTimerHudScale);
        INSTANCE.maskTimerHudScale = normalizeHudScale(INSTANCE.maskTimerHudScale);
        INSTANCE.productionTimerHudScale = normalizeHudScale(INSTANCE.productionTimerHudScale);
        INSTANCE.pingHudScale = normalizeHudScale(INSTANCE.pingHudScale);
    }

    private static void sanitizeHudPrefixes() {
        INSTANCE.toggleSprintHudPrefix = sanitizeHudPrefix(INSTANCE.toggleSprintHudPrefix, "ToggleSprint");
        INSTANCE.fpsHudPrefix = sanitizeHudPrefix(INSTANCE.fpsHudPrefix, "FPS");
        INSTANCE.paydayHudPrefix = sanitizeHudPrefix(INSTANCE.paydayHudPrefix, "Payday");
        INSTANCE.ammoHudPrefix = sanitizeHudPrefix(INSTANCE.ammoHudPrefix, "Ammo");
        INSTANCE.bankHudPrefix = sanitizeHudPrefix(INSTANCE.bankHudPrefix, "Bank");
        INSTANCE.cashHudPrefix = sanitizeHudPrefix(INSTANCE.cashHudPrefix, "Bargeld");
        INSTANCE.hackTimerHudPrefix = sanitizeHudPrefix(INSTANCE.hackTimerHudPrefix, "Hack");
        INSTANCE.plantTimerHudPrefix = sanitizeHudPrefix(INSTANCE.plantTimerHudPrefix, "Plant");
        INSTANCE.dealerTimerHudPrefix = sanitizeHudPrefix(INSTANCE.dealerTimerHudPrefix, "Dealer");
        INSTANCE.maskTimerHudPrefix = sanitizeHudPrefix(INSTANCE.maskTimerHudPrefix, "Maske");
        INSTANCE.productionTimerHudPrefix = sanitizeHudPrefix(INSTANCE.productionTimerHudPrefix, "Produktion");
    }

    private static void sanitizeAmmoHud() {
        INSTANCE.ammoHudLowAmmoThresholdPercent = Math.max(5, Math.min(50, INSTANCE.ammoHudLowAmmoThresholdPercent));
        if (INSTANCE.ammoHudKr47MagazineSize != 25 && INSTANCE.ammoHudKr47MagazineSize != 30) {
            INSTANCE.ammoHudKr47MagazineSize = 0;
        }
    }

    private static void sanitizeReinfColors() {
        INSTANCE.reinfLabelColor = sanitizeHudColor(INSTANCE.reinfLabelColor, DEFAULT_REINF_LABEL_COLOR);
        INSTANCE.reinfTextColor = sanitizeHudColor(INSTANCE.reinfTextColor, DEFAULT_REINF_TEXT_COLOR);
        INSTANCE.reinfDistanceColor = sanitizeHudColor(INSTANCE.reinfDistanceColor, DEFAULT_REINF_DISTANCE_COLOR);
        INSTANCE.reinfUniformColor = sanitizeHudColor(INSTANCE.reinfUniformColor, DEFAULT_REINF_UNIFORM_COLOR);
    }

    public static void sanitizeSecondChat() {
        INSTANCE.secondChatX = Math.max(0, INSTANCE.secondChatX);
        INSTANCE.secondChatY = Math.max(0, INSTANCE.secondChatY);
        INSTANCE.secondChatWidth = Math.max(180, Math.min(600, INSTANCE.secondChatWidth));
        INSTANCE.secondChatHeight = Math.max(60, Math.min(320, INSTANCE.secondChatHeight));
        INSTANCE.secondChatBackgroundOpacity = Math.max(0, Math.min(255, INSTANCE.secondChatBackgroundOpacity));
        INSTANCE.secondChatAccentColor = sanitizeHudColor(INSTANCE.secondChatAccentColor, 0xFF38BDF8);
        INSTANCE.secondChatHighlightColor = sanitizeHudColor(INSTANCE.secondChatHighlightColor, 0xFFFFD54A);
        INSTANCE.secondChatHqMode = sanitizeSecondChatMode(INSTANCE.secondChatHqMode);
        INSTANCE.secondChatReinfMode = sanitizeSecondChatMode(INSTANCE.secondChatReinfMode);
        INSTANCE.secondChatPrivateMode = sanitizeSecondChatMode(INSTANCE.secondChatPrivateMode);
        INSTANCE.secondChatServerInfoMode = sanitizeSecondChatMode(INSTANCE.secondChatServerInfoMode);
        INSTANCE.secondChatBetterUcMode = sanitizeSecondChatMode(INSTANCE.secondChatBetterUcMode);
        INSTANCE.secondChatOwnNameMode = sanitizeSecondChatMode(INSTANCE.secondChatOwnNameMode);
        INSTANCE.secondChatCustom1Mode = sanitizeSecondChatMode(INSTANCE.secondChatCustom1Mode);
        INSTANCE.secondChatCustom2Mode = sanitizeSecondChatMode(INSTANCE.secondChatCustom2Mode);
        INSTANCE.secondChatCustom3Mode = sanitizeSecondChatMode(INSTANCE.secondChatCustom3Mode);
        INSTANCE.secondChatCustom1Text = sanitizeSecondChatFilter(INSTANCE.secondChatCustom1Text);
        INSTANCE.secondChatCustom2Text = sanitizeSecondChatFilter(INSTANCE.secondChatCustom2Text);
        INSTANCE.secondChatCustom3Text = sanitizeSecondChatFilter(INSTANCE.secondChatCustom3Text);
        ensureSecondChatTabs();
        ensureSecondChatWindows();
    }

    private static void ensureSecondChatTabs() {
        if (INSTANCE.secondChatTabs == null) {
            INSTANCE.secondChatTabs = new ArrayList<>();
        }
        INSTANCE.secondChatTabs.removeIf(tab -> tab == null);
        if (!INSTANCE.secondChatTabsMigrated) {
            if (INSTANCE.secondChatTabs.isEmpty()) {
                SecondChatTabConfig migrated = new SecondChatTabConfig("Chat 2");
                migrated.hqMode = INSTANCE.secondChatHqMode;
                migrated.reinfMode = INSTANCE.secondChatReinfMode;
                migrated.privateMode = INSTANCE.secondChatPrivateMode;
                migrated.serverInfoMode = INSTANCE.secondChatServerInfoMode;
                migrated.betterUcMode = INSTANCE.secondChatBetterUcMode;
                migrated.ownNameMode = INSTANCE.secondChatOwnNameMode;
                migrated.custom1Mode = INSTANCE.secondChatCustom1Mode;
                migrated.custom2Mode = INSTANCE.secondChatCustom2Mode;
                migrated.custom3Mode = INSTANCE.secondChatCustom3Mode;
                migrated.custom1Text = INSTANCE.secondChatCustom1Text;
                migrated.custom2Text = INSTANCE.secondChatCustom2Text;
                migrated.custom3Text = INSTANCE.secondChatCustom3Text;
                INSTANCE.secondChatTabs.add(migrated);
            }
            INSTANCE.secondChatTabsMigrated = true;
        }
        while (INSTANCE.secondChatTabs.size() > 8) {
            INSTANCE.secondChatTabs.remove(INSTANCE.secondChatTabs.size() - 1);
        }
        for (int i = 0; i < INSTANCE.secondChatTabs.size(); i++) {
            INSTANCE.secondChatTabs.get(i).sanitize(i);
        }
        if (INSTANCE.secondChatActiveTabId == null || INSTANCE.secondChatActiveTabId.isBlank()) {
            INSTANCE.secondChatActiveTabId = "main";
        }
        boolean known = "main".equals(INSTANCE.secondChatActiveTabId);
        if (!known) {
            for (SecondChatTabConfig tab : INSTANCE.secondChatTabs) {
                if (tab.id.equals(INSTANCE.secondChatActiveTabId)) {
                    known = true;
                    break;
                }
            }
        }
        if (!known) {
            INSTANCE.secondChatActiveTabId = "main";
        }
    }

    private static void ensureSecondChatWindows() {
        if (INSTANCE.secondChatWindows == null) {
            INSTANCE.secondChatWindows = new ArrayList<>();
        }
        INSTANCE.secondChatWindows.removeIf(window -> window == null);
        while (INSTANCE.secondChatWindows.size() > 4) {
            INSTANCE.secondChatWindows.remove(INSTANCE.secondChatWindows.size() - 1);
        }
        for (int i = 0; i < INSTANCE.secondChatWindows.size(); i++) {
            INSTANCE.secondChatWindows.get(i).sanitize(i);
        }

        Set<String> windowIds = new LinkedHashSet<>();
        windowIds.add("primary");
        for (SecondChatWindowConfig window : INSTANCE.secondChatWindows) {
            windowIds.add(window.id);
        }
        for (SecondChatTabConfig tab : INSTANCE.secondChatTabs) {
            if (!windowIds.contains(tab.windowId)) {
                tab.windowId = "primary";
            }
        }
        INSTANCE.secondChatWindows.removeIf(window -> INSTANCE.secondChatTabs.stream()
                .noneMatch(tab -> window.id.equals(tab.windowId)));
        for (SecondChatWindowConfig window : INSTANCE.secondChatWindows) {
            boolean activeKnown = INSTANCE.secondChatTabs.stream()
                    .anyMatch(tab -> window.id.equals(tab.windowId) && tab.id.equals(window.activeTabId));
            if (!activeKnown) {
                window.activeTabId = INSTANCE.secondChatTabs.stream()
                        .filter(tab -> window.id.equals(tab.windowId))
                        .map(tab -> tab.id)
                        .findFirst()
                        .orElse("");
            }
        }
    }

    private static String sanitizeSecondChatMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "copy", "move", "highlight", "ignore" -> normalized;
            default -> "off";
        };
    }

    private static String sanitizeSecondChatFilter(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96).trim();
    }

    private static String sanitizeHudPrefix(String value, String fallback) {
        String normalized = hudPrefix(value);
        if (normalized.isEmpty()) {
            return fallback;
        }
        if (normalized.length() > 24) {
            return normalized.substring(0, 24).trim();
        }
        return normalized;
    }

    private static void sanitizeHudGradients() {
        INSTANCE.hudGradientColor = sanitizeHudColor(INSTANCE.hudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);

        if (INSTANCE.hudGradientConfigVersion < 1) {
            INSTANCE.healthHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.toggleSprintHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.fpsHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.paydayHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.ammoHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.bankHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.cashHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.potionHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.hackTimerHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.plantTimerHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.dealerTimerHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.maskTimerHudGradientEnabled = INSTANCE.hudColorGradientEnabled;
            INSTANCE.productionTimerHudGradientEnabled = INSTANCE.hudColorGradientEnabled;

            INSTANCE.healthHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.toggleSprintHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.fpsHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.paydayHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.ammoHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.bankHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.cashHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.potionHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.hackTimerHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.plantTimerHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.dealerTimerHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.maskTimerHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.productionTimerHudGradientColor = INSTANCE.hudGradientColor;
            INSTANCE.hudGradientConfigVersion = 1;
        }

        INSTANCE.healthHudGradientColor = sanitizeHudColor(INSTANCE.healthHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.toggleSprintHudGradientColor = sanitizeHudColor(INSTANCE.toggleSprintHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.fpsHudGradientColor = sanitizeHudColor(INSTANCE.fpsHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.paydayHudGradientColor = sanitizeHudColor(INSTANCE.paydayHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.ammoHudGradientColor = sanitizeHudColor(INSTANCE.ammoHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.bankHudGradientColor = sanitizeHudColor(INSTANCE.bankHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.cashHudGradientColor = sanitizeHudColor(INSTANCE.cashHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.potionHudGradientColor = sanitizeHudColor(INSTANCE.potionHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.hackTimerHudGradientColor = sanitizeHudColor(INSTANCE.hackTimerHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.plantTimerHudGradientColor = sanitizeHudColor(INSTANCE.plantTimerHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.dealerTimerHudGradientColor = sanitizeHudColor(INSTANCE.dealerTimerHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.maskTimerHudGradientColor = sanitizeHudColor(INSTANCE.maskTimerHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
        INSTANCE.productionTimerHudGradientColor = sanitizeHudColor(INSTANCE.productionTimerHudGradientColor, DEFAULT_HUD_GRADIENT_COLOR);
    }

    private static void sanitizePingRelay() {
        if (INSTANCE.pingRelayUrl == null || INSTANCE.pingRelayUrl.isBlank()) {
            INSTANCE.pingRelayUrl = DEFAULT_PING_RELAY_URL;
        } else if (isLegacyPingRelayUrl(INSTANCE.pingRelayUrl)) {
            INSTANCE.pingRelayUrl = DEFAULT_PING_RELAY_URL;
        }
        if (INSTANCE.pingRelayToken == null) {
            INSTANCE.pingRelayToken = "";
        }
        if (INSTANCE.pingRelayChannel == null || INSTANCE.pingRelayChannel.isBlank()) {
            INSTANCE.pingRelayChannel = "global";
        } else {
            String cleaned = INSTANCE.pingRelayChannel
                    .trim()
                    .replaceAll("[^A-Za-z0-9_-]", "")
                    .toLowerCase(Locale.ROOT);
            INSTANCE.pingRelayChannel = cleaned.isEmpty() ? "global" : cleaned;
        }
        String pingScope = INSTANCE.pingRelayScope == null
                ? ""
                : INSTANCE.pingRelayScope.trim().toLowerCase(Locale.ROOT);
        INSTANCE.pingRelayScope = switch (pingScope) {
            case "faction" -> "faction";
            case "state" -> "state";
            default -> "global";
        };
        INSTANCE.pingRelayTtlSeconds = Math.max(5, Math.min(60, INSTANCE.pingRelayTtlSeconds));
        INSTANCE.pingRelayMaxDistance = Math.max(0, Math.min(128, INSTANCE.pingRelayMaxDistance));
        INSTANCE.pingRelayColor = sanitizeHexColor(INSTANCE.pingRelayColor, "#38BDF8");
        INSTANCE.pingNormalColor = sanitizeHexColor(INSTANCE.pingNormalColor, INSTANCE.pingRelayColor);
        INSTANCE.pingDangerColor = sanitizeHexColor(INSTANCE.pingDangerColor, "#FF5555");
        INSTANCE.pingGatherColor = sanitizeHexColor(INSTANCE.pingGatherColor, "#22C55E");
        INSTANCE.pingCooldownMs = Math.max(500, Math.min(10000, INSTANCE.pingCooldownMs));
        INSTANCE.pingSoundId = sanitizePingSound(INSTANCE.pingSoundId);
    }

    private static String sanitizeHexColor(String value, String fallback) {
        String raw = value == null ? "" : value.trim();
        if (raw.matches("#?[0-9A-Fa-f]{6}")) {
            return raw.startsWith("#") ? raw : "#" + raw;
        }
        String cleanFallback = fallback == null ? "#38BDF8" : fallback.trim();
        if (cleanFallback.matches("#?[0-9A-Fa-f]{6}")) {
            return cleanFallback.startsWith("#") ? cleanFallback : "#" + cleanFallback;
        }
        return "#38BDF8";
    }

    private static String sanitizePingSound(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "bell", "chime", "bit", "banjo", "cowbell" -> raw;
            default -> "pling";
        };
    }

    private static boolean isLegacyPingRelayUrl(String url) {
        String normalized = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(LEGACY_PING_RELAY_URL)
                || normalized.equals("65.109.175.203:3000")
                || normalized.equals("65.109.175.203:3000/ws")
                || normalized.equals("http://65.109.175.203:3000")
                || normalized.equals("http://65.109.175.203:3000/ws")
                || normalized.equals("ws://65.109.175.203:3000");
    }

    private static void sanitizeDiscordInvite() {
        String raw = INSTANCE.discordInviteUrl == null ? "" : INSTANCE.discordInviteUrl.trim();
        if (!raw.startsWith("https://") && !raw.startsWith("http://")) {
            raw = DEFAULT_DISCORD_INVITE_URL;
        }
        INSTANCE.discordInviteUrl = raw.isBlank() ? DEFAULT_DISCORD_INVITE_URL : raw;
    }

    public static boolean hasSeenWelcomeChangelog(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isEmpty()) return true;
        String seen = INSTANCE.lastSeenWelcomeVersion == null ? "" : INSTANCE.lastSeenWelcomeVersion.trim();
        return normalized.equals(seen);
    }

    public static void markWelcomeChangelogSeen(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isEmpty()) return;
        INSTANCE.lastSeenWelcomeVersion = normalized;
        save();
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
        if (folded.equals("zivilist") || folded.equals("zivi") || folded.equals("ziv")) return "zivilist";
        if (folded.equals("rettungsdienst") || folded.equals("retungsdienst") || folded.equals("medic")) return "medic";
        if (folded.equals("la cosa nostra") || folded.equals("lcn")) return "lcn";
        if (folded.equals("westside ballas") || folded.equals("ballas")) return "ballas";
        if (folded.equals("soldner") || folded.equals("soeldner")) return "soeldner";
        if (folded.equals("ordo absolutus") || folded.equals("ordo")) return "ordo";
        if (folded.equals("kf") || folded.equals("k f")
                || folded.equals("kerzakov") || folded.equals("kerzakov familie")
                || folded.equals("kerzakov family")) return "kerzakov";
        if (folded.equals("f b i")) return "fbi";
        return folded;
    }

    public static String factionQueryFromStatsValue(String raw) {
        return FactionStatsParser.queryFromStatsValue(raw);
    }

    public static boolean updateCurrentPlayerFactionFromStats(String rawFactionValue) {
        String query = factionQueryFromStatsValue(rawFactionValue);
        if (query.isEmpty()) return false;

        String label = factionLabelForQuery(query);
        boolean changed = !query.equals(INSTANCE.currentPlayerFaction)
                || !label.equals(INSTANCE.currentPlayerFactionLabel);
        INSTANCE.currentPlayerFaction = query;
        INSTANCE.currentPlayerFactionLabel = label;
        if (changed) {
            save();
        }
        return changed;
    }

    public static void clearCurrentPlayerFaction() {
        if ((INSTANCE.currentPlayerFaction == null || INSTANCE.currentPlayerFaction.isBlank())
                && (INSTANCE.currentPlayerFactionLabel == null || INSTANCE.currentPlayerFactionLabel.isBlank())) {
            return;
        }
        INSTANCE.currentPlayerFaction = "";
        INSTANCE.currentPlayerFactionLabel = "";
        save();
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
        if (INSTANCE.wasteDropAreas == null) INSTANCE.wasteDropAreas = new LinkedHashMap<>();
        if (INSTANCE.manualFactionPlayerKeys == null) INSTANCE.manualFactionPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.remoteFactionPlayerKeys == null) INSTANCE.remoteFactionPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.manualBlacklistPlayerKeys == null) INSTANCE.manualBlacklistPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.chatBlacklistPlayerKeys == null) INSTANCE.chatBlacklistPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.vogelfreiPlayerKeys == null) INSTANCE.vogelfreiPlayerKeys = new LinkedHashSet<>();
        if (INSTANCE.hudProfiles == null) INSTANCE.hudProfiles = new LinkedHashMap<>();
        if (INSTANCE.clickGuiScrollOffsets == null) INSTANCE.clickGuiScrollOffsets = new LinkedHashMap<>();
        if (INSTANCE.secondChatTabs == null) INSTANCE.secondChatTabs = new ArrayList<>();
        if (INSTANCE.secondChatWindows == null) INSTANCE.secondChatWindows = new ArrayList<>();
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
        sanitizeHudPrefixes();
        sanitizeHudGradients();
        sanitizeReinfColors();
        sanitizeSecondChat();
        sanitizePingRelay();
        sanitizeDiscordInvite();
        sanitizeTrackedFactions();
        rebuildRemoteFactionUnion();
        refreshRuntimeNameCaches();
        save();
    }

    private static File firstExistingLegacyConfig() {
        return LEGACY_NAMETAG_CONFIG_FILE;
    }

    private static void loadFromFile(File file) {
        try {
            String rawJson = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            boolean hasChatCustomizationSetting = rawJson.contains("\"chatCustomizationEnabled\"");
            boolean hasReinfCustomizationSetting = rawJson.contains("\"reinfCustomizationEnabled\"");
            boolean hasCloudSettingsSetting = rawJson.contains("\"cloudSettingsEnabled\"");
            boolean hasRichTaxAlertSetting = rawJson.contains("\"richTaxAlertEnabled\"");
            boolean hasRichTaxAlertSoundSetting = rawJson.contains("\"richTaxAlertSoundEnabled\"");
            BetterUCConfig loaded = GSON.fromJson(rawJson, BetterUCConfig.class);
            if (loaded == null) return;

            INSTANCE = loaded;
            if (!hasChatCustomizationSetting) {
                INSTANCE.chatCustomizationEnabled = true;
            }
            if (!hasReinfCustomizationSetting) {
                INSTANCE.reinfCustomizationEnabled = true;
            }
            if (!hasCloudSettingsSetting) {
                INSTANCE.cloudSettingsEnabled = true;
            }
            if (!hasRichTaxAlertSetting) {
                INSTANCE.richTaxAlertEnabled = true;
            }
            if (!hasRichTaxAlertSoundSetting) {
                INSTANCE.richTaxAlertSoundEnabled = true;
            }
            ensureRuntimeCollections();
            if (INSTANCE.hotkeyCommands == null)         INSTANCE.hotkeyCommands = new ArrayList<>();
            if (INSTANCE.trackedFactionQueries == null)  INSTANCE.trackedFactionQueries = defaultTrackedFactionQueries();
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
            if (INSTANCE.cashHudX == 0 && INSTANCE.cashHudY == 0) {
                INSTANCE.cashHudX = 10;
                INSTANCE.cashHudY = 184;
                INSTANCE.showCashHud = true;
            }
            if (INSTANCE.potionHudX == 0 && INSTANCE.potionHudY == 0) {
                INSTANCE.potionHudX = 10;
                INSTANCE.potionHudY = 118;
                INSTANCE.showPotionEffectsHud = true;
            }
            if (INSTANCE.lastKnownBankBalance < -1) {
                INSTANCE.lastKnownBankBalance = -1;
            }
            if (INSTANCE.lastKnownCash < -1) {
                INSTANCE.lastKnownCash = -1;
            }
            migrateSplitTimerPositions();
            INSTANCE.toggleSprintHudColor = sanitizeHudColor(INSTANCE.toggleSprintHudColor, DEFAULT_TOGGLE_SPRINT_HUD_COLOR);
            INSTANCE.fpsHudColor = sanitizeHudColor(INSTANCE.fpsHudColor, DEFAULT_FPS_HUD_COLOR);
            INSTANCE.paydayHudColor = sanitizeHudColor(INSTANCE.paydayHudColor, DEFAULT_PAYDAY_HUD_COLOR);
            INSTANCE.bankHudColor = sanitizeHudColor(INSTANCE.bankHudColor, DEFAULT_BANK_HUD_COLOR);
            INSTANCE.cashHudColor = sanitizeHudColor(INSTANCE.cashHudColor, DEFAULT_CASH_HUD_COLOR);
            INSTANCE.potionHudColor = sanitizeHudColor(INSTANCE.potionHudColor, DEFAULT_POTION_HUD_COLOR);
            INSTANCE.dealerTimerHudColor = sanitizeHudColor(INSTANCE.dealerTimerHudColor, 0xFFD946EF);
            INSTANCE.maskTimerHudColor = sanitizeHudColor(INSTANCE.maskTimerHudColor, 0xFF22D3EE);
            INSTANCE.productionTimerHudColor = sanitizeHudColor(INSTANCE.productionTimerHudColor, 0xFFFBBF24);
            INSTANCE.healthHudColor = sanitizeHudColor(INSTANCE.healthHudColor, DEFAULT_HEALTH_HUD_COLOR);
            INSTANCE.healthHudHeartColor = sanitizeHudColor(INSTANCE.healthHudHeartColor, INSTANCE.healthHudColor);
            INSTANCE.healthHudTextColor = sanitizeHudColor(INSTANCE.healthHudTextColor, INSTANCE.healthHudColor);
            INSTANCE.healthHudAbsorptionColor = sanitizeHudColor(INSTANCE.healthHudAbsorptionColor, DEFAULT_HEALTH_HUD_ABSORPTION_COLOR);
            sanitizeReinfColors();
            sanitizeSecondChat();
            sanitizeHudGradients();
            sanitizeHudStyles();
            sanitizeHudScales();
            sanitizeHudPrefixes();
            sanitizeAmmoHud();
            sanitizePingRelay();
            sanitizeDiscordInvite();
            if (INSTANCE.blReasons == null || INSTANCE.blReasons.isEmpty()) {
                INSTANCE.blReasons = defaultBlacklistReasons();
            }
            if (INSTANCE.plantTimerStates == null) {
                INSTANCE.plantTimerStates = new LinkedHashMap<>();
            }
            sanitizeTrackedFactions();
            ensureHudProfiles();
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
        boolean saved = false;
        ensureRuntimeCollections();
        saveActiveHudProfile();
        try (Writer w = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, w);
            saved = true;
        } catch (Exception e) {
            com.betteruc.BetterUCMod.LOGGER.error("Failed to save config", e);
        }
        if (saved) {
            try {
                saveListener.run();
            } catch (Exception e) {
                com.betteruc.BetterUCMod.LOGGER.warn("Config save listener failed", e);
            }
        }
    }

    public static void setSaveListener(Runnable listener) {
        saveListener = listener == null ? () -> { } : listener;
    }

    public static JsonObject cloudSettingsSnapshot() {
        ensureHudProfiles();
        saveActiveHudProfile();
        JsonObject result = new JsonObject();
        JsonObject serialized = GSON.toJsonTree(INSTANCE).getAsJsonObject();
        for (String name : CLOUD_SETTING_FIELDS) {
            if (serialized.has(name)) {
                result.add(name, serialized.get(name).deepCopy());
            }
        }
        return result;
    }

    public static void applyCloudSettings(JsonObject settings) {
        if (settings == null) return;
        for (String name : CLOUD_SETTING_FIELDS) {
            JsonElement value = settings.get(name);
            if (value == null || value.isJsonNull()) continue;
            try {
                Field field = BetterUCConfig.class.getField(name);
                Object parsed = GSON.fromJson(value, field.getGenericType());
                field.set(INSTANCE, parsed);
            } catch (Exception e) {
                com.betteruc.BetterUCMod.LOGGER.warn("Ignored invalid cloud setting {}", name, e);
            }
        }
        sanitizeAfterCloudApply();
    }

    private static void sanitizeAfterCloudApply() {
        ensureRuntimeCollections();
        sanitizeHudStyles();
        sanitizeHudScales();
        sanitizeHudPrefixes();
        sanitizeHudGradients();
        sanitizeReinfColors();
        sanitizeSecondChat();
        sanitizeAmmoHud();
        sanitizePingRelay();
        sanitizeDiscordInvite();
        sanitizeTrackedFactions();
        ensureHudProfiles();
        rebuildRemoteFactionUnion();
        refreshRuntimeNameCaches();
    }

    public static List<String> hudProfileNames() {
        ensureHudProfiles();
        return new ArrayList<>(INSTANCE.hudProfiles.keySet());
    }

    public static String activeHudProfileName() {
        ensureHudProfiles();
        return INSTANCE.activeHudProfile;
    }

    public static boolean createHudProfile(String requestedName) {
        ensureHudProfiles();
        String name = uniqueHudProfileName(requestedName, null);
        if (name.isBlank()) return false;
        saveActiveHudProfile();
        INSTANCE.hudProfiles.put(name, hudProfileSnapshot());
        INSTANCE.activeHudProfile = name;
        save();
        return true;
    }

    public static boolean duplicateActiveHudProfile(String requestedName) {
        ensureHudProfiles();
        String fallback = INSTANCE.activeHudProfile + " Kopie";
        String name = uniqueHudProfileName(
                requestedName == null || requestedName.isBlank() ? fallback : requestedName,
                null
        );
        if (name.isBlank()) return false;
        saveActiveHudProfile();
        INSTANCE.hudProfiles.put(name, INSTANCE.hudProfiles.get(INSTANCE.activeHudProfile).deepCopy());
        INSTANCE.activeHudProfile = name;
        save();
        return true;
    }

    public static boolean renameActiveHudProfile(String requestedName) {
        ensureHudProfiles();
        String oldName = INSTANCE.activeHudProfile;
        String name = uniqueHudProfileName(requestedName, oldName);
        if (name.isBlank() || oldName.equals(name)) return false;

        saveActiveHudProfile();
        LinkedHashMap<String, JsonObject> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : INSTANCE.hudProfiles.entrySet()) {
            renamed.put(entry.getKey().equals(oldName) ? name : entry.getKey(), entry.getValue());
        }
        INSTANCE.hudProfiles = renamed;
        INSTANCE.activeHudProfile = name;
        save();
        return true;
    }

    public static boolean deleteActiveHudProfile() {
        ensureHudProfiles();
        if (INSTANCE.hudProfiles.size() <= 1) return false;
        INSTANCE.hudProfiles.remove(INSTANCE.activeHudProfile);
        INSTANCE.activeHudProfile = INSTANCE.hudProfiles.keySet().iterator().next();
        applyHudProfile(INSTANCE.hudProfiles.get(INSTANCE.activeHudProfile));
        save();
        return true;
    }

    public static boolean switchHudProfile(String profileName) {
        ensureHudProfiles();
        if (profileName == null || profileName.equals(INSTANCE.activeHudProfile)) return false;
        JsonObject target = INSTANCE.hudProfiles.get(profileName);
        if (target == null) return false;
        saveActiveHudProfile();
        INSTANCE.activeHudProfile = profileName;
        applyHudProfile(target);
        save();
        return true;
    }

    public static boolean resetActiveHudProfile() {
        ensureHudProfiles();
        BetterUCConfig defaults = new BetterUCConfig();
        applyHudProfile(hudProfileSnapshot(defaults));
        save();
        return true;
    }

    public static Path hudProfileDirectory() {
        try {
            Files.createDirectories(HUD_PROFILE_DIRECTORY);
        } catch (Exception e) {
            com.betteruc.BetterUCMod.LOGGER.warn(
                    "Could not create HUD profile directory {}",
                    HUD_PROFILE_DIRECTORY,
                    e
            );
        }
        return HUD_PROFILE_DIRECTORY;
    }

    public static Path exportActiveHudProfile() {
        ensureHudProfiles();
        saveActiveHudProfile();
        try {
            Files.createDirectories(HUD_PROFILE_DIRECTORY);
            JsonObject document = new JsonObject();
            document.addProperty("format", HUD_PROFILE_FILE_FORMAT);
            document.addProperty("schema", HUD_PROFILE_FILE_SCHEMA);
            document.addProperty("name", INSTANCE.activeHudProfile);
            document.add("settings", INSTANCE.hudProfiles.get(INSTANCE.activeHudProfile).deepCopy());

            Path target = HUD_PROFILE_DIRECTORY.resolve(
                    sanitizeHudProfileFileName(INSTANCE.activeHudProfile) + ".json"
            );
            Files.writeString(
                    target,
                    GSON.toJson(document),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            return target;
        } catch (Exception e) {
            com.betteruc.BetterUCMod.LOGGER.warn("Could not export HUD profile", e);
            return null;
        }
    }

    public static HudProfileImportResult importHudProfiles() {
        ensureHudProfiles();
        int imported = 0;
        int skipped = 0;
        try {
            Files.createDirectories(HUD_PROFILE_DIRECTORY);
            List<Path> files;
            try (var stream = Files.list(HUD_PROFILE_DIRECTORY)) {
                files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            }

            for (Path file : files) {
                if (INSTANCE.hudProfiles.size() >= MAX_HUD_PROFILES) {
                    skipped += files.size() - imported - skipped;
                    break;
                }
                try {
                    if (Files.size(file) > MAX_HUD_PROFILE_FILE_BYTES) {
                        skipped++;
                        continue;
                    }
                    JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (!parsed.isJsonObject()) {
                        skipped++;
                        continue;
                    }
                    JsonObject document = parsed.getAsJsonObject();
                    if (!HUD_PROFILE_FILE_FORMAT.equals(stringValue(document, "format"))
                            || intValue(document, "schema", -1) != HUD_PROFILE_FILE_SCHEMA
                            || !document.has("settings")
                            || !document.get("settings").isJsonObject()) {
                        skipped++;
                        continue;
                    }

                    JsonObject settings = sanitizeImportedHudProfile(document.getAsJsonObject("settings"));
                    if (settings.size() == 0) {
                        skipped++;
                        continue;
                    }
                    String requestedName = stringValue(document, "name");
                    if (requestedName.isBlank()) {
                        requestedName = stripJsonExtension(file.getFileName().toString());
                    }
                    String name = uniqueHudProfileName(requestedName, null);
                    if (name.isBlank()) {
                        skipped++;
                        continue;
                    }
                    INSTANCE.hudProfiles.put(name, settings);
                    imported++;
                } catch (Exception e) {
                    skipped++;
                    com.betteruc.BetterUCMod.LOGGER.warn("Ignored invalid HUD profile file {}", file, e);
                }
            }

            if (imported > 0) save();
        } catch (Exception e) {
            com.betteruc.BetterUCMod.LOGGER.warn("Could not import HUD profiles", e);
            return new HudProfileImportResult(imported, skipped, false);
        }
        return new HudProfileImportResult(imported, skipped, true);
    }

    public record HudProfileImportResult(int imported, int skipped, boolean directoryReadable) {
    }

    private static void ensureHudProfiles() {
        ensureRuntimeCollections();
        INSTANCE.activeHudProfile = sanitizeHudProfileName(INSTANCE.activeHudProfile);
        if (INSTANCE.activeHudProfile.isBlank()) INSTANCE.activeHudProfile = "Standard";

        INSTANCE.hudProfiles.entrySet().removeIf(entry ->
                sanitizeHudProfileName(entry.getKey()).isBlank() || entry.getValue() == null);
        if (INSTANCE.hudProfiles.isEmpty()) {
            INSTANCE.hudProfiles.put(INSTANCE.activeHudProfile, hudProfileSnapshot());
        } else if (!INSTANCE.hudProfiles.containsKey(INSTANCE.activeHudProfile)) {
            INSTANCE.activeHudProfile = INSTANCE.hudProfiles.keySet().iterator().next();
        }
    }

    private static void saveActiveHudProfile() {
        ensureRuntimeCollections();
        if (INSTANCE.activeHudProfile == null || INSTANCE.activeHudProfile.isBlank()) return;
        INSTANCE.hudProfiles.put(INSTANCE.activeHudProfile, hudProfileSnapshot());
    }

    private static JsonObject hudProfileSnapshot() {
        return hudProfileSnapshot(INSTANCE);
    }

    private static JsonObject hudProfileSnapshot(BetterUCConfig source) {
        JsonObject result = new JsonObject();
        JsonObject serialized = GSON.toJsonTree(source).getAsJsonObject();
        for (String name : HUD_PROFILE_FIELDS) {
            if (serialized.has(name)) result.add(name, serialized.get(name).deepCopy());
        }
        return result;
    }

    private static JsonObject sanitizeImportedHudProfile(JsonObject source) {
        JsonObject result = hudProfileSnapshot(new BetterUCConfig());
        int importedFields = 0;
        for (String name : HUD_PROFILE_FIELDS) {
            JsonElement value = source.get(name);
            if (value == null || value.isJsonNull()) continue;
            try {
                Field field = BetterUCConfig.class.getField(name);
                Object parsed = GSON.fromJson(value, field.getGenericType());
                if (parsed == null) continue;
                result.add(name, GSON.toJsonTree(parsed, field.getGenericType()));
                importedFields++;
            } catch (Exception e) {
                com.betteruc.BetterUCMod.LOGGER.debug("Ignored invalid imported HUD field {}", name);
            }
        }
        return importedFields == 0 ? new JsonObject() : result;
    }

    private static void applyHudProfile(JsonObject profile) {
        if (profile == null) return;
        for (String name : HUD_PROFILE_FIELDS) {
            JsonElement value = profile.get(name);
            if (value == null || value.isJsonNull()) continue;
            try {
                Field field = BetterUCConfig.class.getField(name);
                field.set(INSTANCE, GSON.fromJson(value, field.getGenericType()));
            } catch (Exception e) {
                com.betteruc.BetterUCMod.LOGGER.warn("Ignored invalid HUD profile setting {}", name, e);
            }
        }
        sanitizeHudStyles();
        sanitizeHudScales();
        sanitizeHudPrefixes();
        sanitizeHudGradients();
        sanitizeAmmoHud();
        INSTANCE.healthHudAbsorptionColor = sanitizeHudColor(
                INSTANCE.healthHudAbsorptionColor,
                DEFAULT_HEALTH_HUD_ABSORPTION_COLOR
        );
    }

    private static String uniqueHudProfileName(String requestedName, String allowedExistingName) {
        String base = sanitizeHudProfileName(requestedName);
        if (base.isBlank()) return "";
        String candidate = base;
        int suffix = 2;
        while (hudProfileNameExists(candidate, allowedExistingName)) {
            String suffixText = " " + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 24 - suffixText.length())) + suffixText;
        }
        return candidate;
    }

    private static boolean hudProfileNameExists(String name, String allowedExistingName) {
        for (String existing : INSTANCE.hudProfiles.keySet()) {
            if (allowedExistingName != null && existing.equals(allowedExistingName)) continue;
            if (existing.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static String stringValue(JsonObject object, String name) {
        try {
            JsonElement value = object.get(name);
            return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int intValue(JsonObject object, String name, int fallback) {
        try {
            JsonElement value = object.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String sanitizeHudProfileFileName(String value) {
        String clean = sanitizeHudProfileName(value)
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[. ]+$", "");
        return clean.isBlank() ? "hud-profile" : clean;
    }

    private static String stripJsonExtension(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).endsWith(".json")
                ? value.substring(0, value.length() - 5)
                : value;
    }

    private static String sanitizeHudProfileName(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("\\p{Cntrl}", "").trim().replaceAll("\\s+", " ");
        return clean.substring(0, Math.min(clean.length(), 24));
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
