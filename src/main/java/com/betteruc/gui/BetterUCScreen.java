package com.betteruc.gui;

import com.betteruc.BetterUCMod;
import com.betteruc.client.BetterUCFontManager;
import com.betteruc.client.AutomationController;
import com.betteruc.client.ClientCompat;
import com.betteruc.client.CloudSettingsClient;
import com.betteruc.client.CommunicationDeviceTracker;
import com.betteruc.client.ChatCustomizationFormatter;
import com.betteruc.client.PingRelayClient;
import com.betteruc.client.RemoteFeatureFlagsClient;
import com.betteruc.client.SyncRefreshActions;
import com.betteruc.client.UserStatsClient;
import com.betteruc.client.VersionChecker;
import com.betteruc.client.ZoomController;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.ArmorHud;
import com.betteruc.hud.CashHud;
import com.betteruc.hud.DateTimeHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.HealthHud;
import com.betteruc.hud.ModernHudRenderer;
import com.betteruc.hud.PotionEffectsHud;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class BetterUCScreen extends Screen {

    private static final int PANEL_BG = 0xDD0D1117;
    private static final int PANEL_INNER = 0xCC141A22;
    private static final int PANEL_ALT = 0xB81B222D;
    private static final int PANEL_BORDER = 0x80333C49;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int TEXT_SOFT = 0xFFCBD5E1;
    private static final int BUTTON_H = 20;
    private static final int MODULE_H = 16;
    private static final int MODULE_GAP = 3;
    private static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer(BetterUCMod.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");
    private Category selectedCategory = Category.HUD;
    private ModuleOption selectedModule = ModuleOption.FPS;
    private final EnumMap<Category, Integer> moduleScrollIndices = new EnumMap<>(Category.class);
    private final EnumMap<Category, ModuleOption> selectedModulesByCategory = new EnumMap<>(Category.class);
    private final List<ScrollableControl> detailControls = new ArrayList<>();
    private final List<DetailSectionHeader> detailSectionHeaders = new ArrayList<>();
    private final List<ControlTooltip> controlTooltips = new ArrayList<>();
    private final List<Runnable> textFieldFlushers = new ArrayList<>();
    private String activeDetailSection = "";
    private boolean rebuildingWidgets = false;
    private int moduleScrollIndex = 0;
    private int detailScrollOffset = 0;
    private int detailContentHeight = 0;
    private int updatesScrollOffset = 0;
    private int updatesContentHeight = 0;
    private String hudProfileNameDraft;
    private boolean hudProfileDropdownOpen = false;
    private boolean hudProfileDeleteConfirmation = false;
    private Button bugReportButton;

    public BetterUCScreen() {
        super(Component.literal("betterUC"));
        selectedCategory = parseCategory(BetterUCConfig.INSTANCE.clickGuiLastCategory);
        selectedModule = parseModule(BetterUCConfig.INSTANCE.clickGuiLastModule, selectedCategory);
        selectedCategory = selectedModule.category;
        for (Category category : Category.values()) {
            int storedIndex = storedModuleScroll(category);
            moduleScrollIndices.put(category, storedIndex);
            selectedModulesByCategory.put(category, moduleAtCategoryIndex(category, storedIndex));
        }
        selectedModulesByCategory.put(selectedCategory, selectedModule);
        moduleScrollIndex = moduleScrollIndices.getOrDefault(selectedCategory, 0);
        detailScrollOffset = storedDetailScroll(selectedModule);
        updatesScrollOffset = Math.max(0, BetterUCConfig.INSTANCE.clickGuiUpdatesScrollOffset);
        hudProfileNameDraft = BetterUCConfig.activeHudProfileName();
    }

    @Override
    protected void init() {
        if (!rebuildingWidgets) {
            flushTextFields();
        }
        textFieldFlushers.clear();
        if (selectedModule.category != selectedCategory) {
            selectedModule = firstModuleFor(selectedCategory);
            detailScrollOffset = storedDetailScroll(selectedModule);
        }
        selectedModulesByCategory.put(selectedCategory, selectedModule);
        ensureSelectedModuleVisible();

        detailControls.clear();
        detailSectionHeaders.clear();
        controlTooltips.clear();
        detailContentHeight = 0;
        addDetailControls();
        Button hudPreviewButton = Button.builder(Component.literal("HUD Vorschau"), b -> openScreen(new HudLayoutScreen(this)))
                .bounds(mainX() + 12, mainY() + mainH() - 28, 118, BUTTON_H)
                .build();
        addRenderableWidget(hudPreviewButton);
        registerTooltip(hudPreviewButton,
                "Öffnet den HUD-Editor. Dort kannst du aktive HUDs verschieben, skalieren und einrasten lassen.");

        Button saveButton = Button.builder(Component.literal("Speichern & Schließen"), b -> {
            saveConfig();
            onClose();
        }).bounds(mainX() + mainW() - 150, mainY() + mainH() - 28, 138, BUTTON_H).build();
        addRenderableWidget(saveButton);
        registerTooltip(saveButton, "Speichert deine Änderungen und schließt das ClickGUI.");

        bugReportButton = Button.builder(Component.literal("Bug melden"), b -> openScreen(new BugReportScreen(this)))
                .bounds(saveButton.getX() - 114, saveButton.getY(), 104, BUTTON_H)
                .build();
        addRenderableWidget(bugReportButton);
        registerTooltip(bugReportButton,
                "Öffnet das Ingame-Formular und erstellt nach deiner Bestätigung einen öffentlichen Discord-Forumbeitrag.");
    }

    private void addDetailControls() {
        activeDetailSection = "";
        int x = detailX() + 14;
        int startY = detailControlsTop();
        int y = startY;
        int controlW = Math.max(120, Math.min(194, detailW() - 28));

        if (selectedModule.hasHudStyle()) {
            y = addSectionHeader(x, y, controlW, "Darstellung", 0xFF38BDF8);
            y = addHudStyleButton(x, y, controlW, selectedModule);
            if (BetterUCConfig.isCustomHudStyle(getHudStyle(selectedModule))) {
                y = addCustomFontControls(x, y, controlW, selectedModule);
            }
        }
        if (selectedModule.category == Category.HUD && selectedModule != ModuleOption.HUD_PROFILES) {
            y = addHudGradientControls(x, y, controlW, selectedModule);
        }
        if (hasHudPrefix(selectedModule)) {
            y = addSectionHeader(x, y, controlW, "Beschriftung", 0xFFFACC15);
            y = addHudPrefixControls(x, y, controlW, selectedModule);
        }

        switch (selectedModule) {
            case HUD_PROFILES -> y = addHudProfileControls(x, y, controlW);
            case HEALTH -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFFFF5555);
                y = addToggle(x, y, controlW, "Health HUD", BetterUCConfig.INSTANCE.showHealthHud,
                        () -> BetterUCConfig.INSTANCE.showHealthHud = !BetterUCConfig.INSTANCE.showHealthHud);
                y = addToggle(x, y, controlW, "Absorptionsherzen", BetterUCConfig.INSTANCE.showHealthAbsorption,
                        () -> BetterUCConfig.INSTANCE.showHealthAbsorption = !BetterUCConfig.INSTANCE.showHealthAbsorption);
                y = addColorButton(x, y, controlW, "Herz Farbe", BetterUCConfig.INSTANCE.healthHudHeartColor,
                        color -> BetterUCConfig.INSTANCE.healthHudHeartColor = color);
                y = addColorButton(x, y, controlW, "Zahl Farbe", BetterUCConfig.INSTANCE.healthHudTextColor,
                        color -> BetterUCConfig.INSTANCE.healthHudTextColor = color);
                if (BetterUCConfig.INSTANCE.showHealthAbsorption) {
                    y = addColorButton(x, y, controlW, "Absorption Farbe",
                            BetterUCConfig.INSTANCE.healthHudAbsorptionColor,
                            color -> BetterUCConfig.INSTANCE.healthHudAbsorptionColor = color);
                }
            }
            case FPS -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF22D3EE);
                y = addToggle(x, y, controlW, "FPS HUD", BetterUCConfig.INSTANCE.showFpsHud,
                        () -> BetterUCConfig.INSTANCE.showFpsHud = !BetterUCConfig.INSTANCE.showFpsHud);
                y = addColorButton(x, y, controlW, "FPS Farbe", BetterUCConfig.INSTANCE.fpsHudColor,
                        color -> BetterUCConfig.INSTANCE.fpsHudColor = color);
            }
            case DATE_TIME -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF38BDF8);
                y = addToggle(x, y, controlW, "Datum & Uhrzeit HUD", BetterUCConfig.INSTANCE.showDateTimeHud,
                        () -> BetterUCConfig.INSTANCE.showDateTimeHud = !BetterUCConfig.INSTANCE.showDateTimeHud);
                y = addToggle(x, y, controlW, "Datum anzeigen", BetterUCConfig.INSTANCE.dateTimeHudShowDate,
                        () -> BetterUCConfig.INSTANCE.dateTimeHudShowDate = !BetterUCConfig.INSTANCE.dateTimeHudShowDate);
                y = addToggle(x, y, controlW, "Uhrzeit anzeigen", BetterUCConfig.INSTANCE.dateTimeHudShowTime,
                        () -> BetterUCConfig.INSTANCE.dateTimeHudShowTime = !BetterUCConfig.INSTANCE.dateTimeHudShowTime);
                if (BetterUCConfig.INSTANCE.dateTimeHudShowTime) {
                    y = addToggle(x, y, controlW, "Sekunden anzeigen", BetterUCConfig.INSTANCE.dateTimeHudShowSeconds,
                            () -> BetterUCConfig.INSTANCE.dateTimeHudShowSeconds = !BetterUCConfig.INSTANCE.dateTimeHudShowSeconds);
                }
                if (BetterUCConfig.INSTANCE.dateTimeHudShowDate && BetterUCConfig.INSTANCE.dateTimeHudShowTime) {
                    y = addToggle(x, y, controlW, "Getrennt positionieren", BetterUCConfig.INSTANCE.dateTimeHudSeparate,
                            () -> BetterUCConfig.INSTANCE.dateTimeHudSeparate = !BetterUCConfig.INSTANCE.dateTimeHudSeparate);
                }
                y = addColorButton(x, y, controlW, "Datum & Uhrzeit Farbe", BetterUCConfig.INSTANCE.dateTimeHudColor,
                        color -> BetterUCConfig.INSTANCE.dateTimeHudColor = color);
            }
            case PAYDAY -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFFFACC15);
                y = addToggle(x, y, controlW, "Payday HUD", BetterUCConfig.INSTANCE.showPaydayHud,
                        () -> BetterUCConfig.INSTANCE.showPaydayHud = !BetterUCConfig.INSTANCE.showPaydayHud);
                y = addColorButton(x, y, controlW, "Payday Farbe", BetterUCConfig.INSTANCE.paydayHudColor,
                        color -> BetterUCConfig.INSTANCE.paydayHudColor = color);
            }
            case AMMO -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFFF59E0B);
                y = addToggle(x, y, controlW, "Ammo HUD", BetterUCConfig.INSTANCE.showAmmoHud,
                        () -> BetterUCConfig.INSTANCE.showAmmoHud = !BetterUCConfig.INSTANCE.showAmmoHud);
                y = addToggle(x, y, controlW, "Magazinbalken", BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled,
                        () -> BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled = !BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled);

                y = addSectionHeader(x, y, controlW, "Warnung", 0xFFFF5555);
                y = addToggle(x, y, controlW, "Niedrige Munition", BetterUCConfig.INSTANCE.ammoHudLowAmmoWarningEnabled,
                        () -> BetterUCConfig.INSTANCE.ammoHudLowAmmoWarningEnabled = !BetterUCConfig.INSTANCE.ammoHudLowAmmoWarningEnabled);
                if (BetterUCConfig.INSTANCE.ammoHudLowAmmoWarningEnabled) {
                    y = addToggle(x, y, controlW, "Warnsignal", BetterUCConfig.INSTANCE.ammoHudLowAmmoSoundEnabled,
                            () -> BetterUCConfig.INSTANCE.ammoHudLowAmmoSoundEnabled = !BetterUCConfig.INSTANCE.ammoHudLowAmmoSoundEnabled);
                    y = addRangeIntSlider(x, y, controlW, "Warnschwelle %",
                            BetterUCConfig.INSTANCE.ammoHudLowAmmoThresholdPercent, 5, 50,
                            value -> BetterUCConfig.INSTANCE.ammoHudLowAmmoThresholdPercent = value);
                }

                y = addSectionHeader(x, y, controlW, "Waffendaten", 0xFF94A3B8);
                String kr47Magazine = BetterUCConfig.INSTANCE.ammoHudKr47MagazineSize > 0
                        ? BetterUCConfig.INSTANCE.ammoHudKr47MagazineSize + " Schuss"
                        : "wird automatisch gelernt";
                y = addInfo(x, y, controlW, "KR47 Magazin", kr47Magazine);
            }
            case ARMOR -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", BetterUCConfig.DEFAULT_ARMOR_HUD_COLOR);
                y = addToggle(x, y, controlW, "Armor HUD", BetterUCConfig.INSTANCE.showArmorHud,
                        () -> BetterUCConfig.INSTANCE.showArmorHud = !BetterUCConfig.INSTANCE.showArmorHud);
                y = addToggle(x, y, controlW, "Haltbarkeit anzeigen", BetterUCConfig.INSTANCE.armorHudDurabilityEnabled,
                        () -> BetterUCConfig.INSTANCE.armorHudDurabilityEnabled = !BetterUCConfig.INSTANCE.armorHudDurabilityEnabled);
                y = addColorButton(x, y, controlW, "Armor Farbe", BetterUCConfig.INSTANCE.armorHudColor,
                        color -> BetterUCConfig.INSTANCE.armorHudColor = color);
            }
            case BANK -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF22D3EE);
                y = addToggle(x, y, controlW, "Bank HUD", BetterUCConfig.INSTANCE.showBankHud,
                        () -> BetterUCConfig.INSTANCE.showBankHud = !BetterUCConfig.INSTANCE.showBankHud);
                y = addColorButton(x, y, controlW, "Bank Farbe", BetterUCConfig.INSTANCE.bankHudColor,
                        color -> BetterUCConfig.INSTANCE.bankHudColor = color);

                y = addSectionHeader(x, y, controlW, "Automatik", 0xFF4ADE80);
                y = addToggle(x, y, controlW, "Auto-/fbank", BetterUCConfig.INSTANCE.autoFactionBankOnBalanceEnabled,
                        () -> BetterUCConfig.INSTANCE.autoFactionBankOnBalanceEnabled = !BetterUCConfig.INSTANCE.autoFactionBankOnBalanceEnabled);
                y = addToggle(x, y, controlW, "Auto-/atminfo", BetterUCConfig.INSTANCE.autoAtmInfoOnBalanceEnabled,
                        () -> BetterUCConfig.INSTANCE.autoAtmInfoOnBalanceEnabled = !BetterUCConfig.INSTANCE.autoAtmInfoOnBalanceEnabled);

                y = addSectionHeader(x, y, controlW, "Bankautomat", 0xFFFACC15);
                y = addToggle(x, y, controlW, "Automatisch trotzdem einzahlen",
                        BetterUCConfig.INSTANCE.autoForceDepositEnabled,
                        () -> BetterUCConfig.INSTANCE.autoForceDepositEnabled =
                                !BetterUCConfig.INSTANCE.autoForceDepositEnabled);

                y = addSectionHeader(x, y, controlW, "Reichensteuer", 0xFFFF5555);
                y = addToggle(x, y, controlW, "Reichensteuer-Alert", BetterUCConfig.INSTANCE.richTaxAlertEnabled,
                        () -> BetterUCConfig.INSTANCE.richTaxAlertEnabled = !BetterUCConfig.INSTANCE.richTaxAlertEnabled);
                if (BetterUCConfig.INSTANCE.richTaxAlertEnabled) {
                    y = addToggle(x, y, controlW, "Alert-Ton", BetterUCConfig.INSTANCE.richTaxAlertSoundEnabled,
                            () -> BetterUCConfig.INSTANCE.richTaxAlertSoundEnabled = !BetterUCConfig.INSTANCE.richTaxAlertSoundEnabled);
                    y = addInfo(x, y, controlW, "Grenze", "mehr als 100.000$");
                }
            }
            case CASH -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF4ADE80);
                y = addToggle(x, y, controlW, "Bargeld HUD", BetterUCConfig.INSTANCE.showCashHud,
                        () -> BetterUCConfig.INSTANCE.showCashHud = !BetterUCConfig.INSTANCE.showCashHud);
                y = addColorButton(x, y, controlW, "Bargeld Farbe", BetterUCConfig.INSTANCE.cashHudColor,
                        color -> BetterUCConfig.INSTANCE.cashHudColor = color);
            }
            case POTION -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFFA855F7);
                y = addToggle(x, y, controlW, "Potion HUD", BetterUCConfig.INSTANCE.showPotionEffectsHud,
                        () -> BetterUCConfig.INSTANCE.showPotionEffectsHud = !BetterUCConfig.INSTANCE.showPotionEffectsHud);
                y = addColorButton(x, y, controlW, "Potion Farbe", BetterUCConfig.INSTANCE.potionHudColor,
                        color -> BetterUCConfig.INSTANCE.potionHudColor = color);
            }
            case SPRINT -> {
                y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF4ADE80);
                y = addToggle(x, y, controlW, "ToggleSprint", BetterUCConfig.INSTANCE.toggleSprintEnabled,
                        () -> BetterUCConfig.INSTANCE.toggleSprintEnabled = !BetterUCConfig.INSTANCE.toggleSprintEnabled);
                y = addColorButton(x, y, controlW, "Sprint Farbe", BetterUCConfig.INSTANCE.toggleSprintHudColor,
                        color -> BetterUCConfig.INSTANCE.toggleSprintHudColor = color);
            }
            case HACK_TIMER, PLANT_TIMER, DEALER_TIMER, MASK_TIMER, PRODUCTION_TIMER -> {
                if (selectedModule == ModuleOption.PLANT_TIMER) {
                    y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF4ADE80);
                    y = addToggle(x, y, controlW, "Plant HUD", BetterUCConfig.INSTANCE.showPlantTimerHud,
                            () -> BetterUCConfig.INSTANCE.showPlantTimerHud = !BetterUCConfig.INSTANCE.showPlantTimerHud);
                }
                if (selectedModule == ModuleOption.DEALER_TIMER) {
                    y = addSectionHeader(x, y, controlW, "Anzeige", 0xFFF59E0B);
                    y = addToggle(x, y, controlW, "Dealer Timer", BetterUCConfig.INSTANCE.showDealerTimerHud,
                            () -> BetterUCConfig.INSTANCE.showDealerTimerHud = !BetterUCConfig.INSTANCE.showDealerTimerHud);
                    y = addColorButton(x, y, controlW, "Dealer Farbe", BetterUCConfig.INSTANCE.dealerTimerHudColor,
                            color -> BetterUCConfig.INSTANCE.dealerTimerHudColor = color);
                }
                if (selectedModule == ModuleOption.MASK_TIMER) {
                    y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF22D3EE);
                    y = addToggle(x, y, controlW, "Masken Timer", BetterUCConfig.INSTANCE.showMaskTimerHud,
                            () -> BetterUCConfig.INSTANCE.showMaskTimerHud = !BetterUCConfig.INSTANCE.showMaskTimerHud);
                    y = addColorButton(x, y, controlW, "Masken Farbe", BetterUCConfig.INSTANCE.maskTimerHudColor,
                            color -> BetterUCConfig.INSTANCE.maskTimerHudColor = color);
                }
                if (selectedModule == ModuleOption.PRODUCTION_TIMER) {
                    y = addSectionHeader(x, y, controlW, "Anzeige", 0xFF22D3EE);
                    y = addToggle(x, y, controlW, "Produktion Timer", BetterUCConfig.INSTANCE.showProductionTimerHud,
                            () -> BetterUCConfig.INSTANCE.showProductionTimerHud = !BetterUCConfig.INSTANCE.showProductionTimerHud);
                    y = addColorButton(x, y, controlW, "Produktion Farbe", BetterUCConfig.INSTANCE.productionTimerHudColor,
                            color -> BetterUCConfig.INSTANCE.productionTimerHudColor = color);
                }
            }
            case ZOOM -> {
                y = addSectionHeader(x, y, controlW, "Funktion", 0xFF60A5FA);
                y = addToggle(x, y, controlW, "Zoom", BetterUCConfig.INSTANCE.zoomEnabled,
                        () -> BetterUCConfig.INSTANCE.zoomEnabled = !BetterUCConfig.INSTANCE.zoomEnabled);
                y = addInfo(x, y, controlW, "Hotkey", "Minecraft-Steuerung > betterUC > Zoom");
                if (ZoomController.isZoomifyLoaded()) {
                    y = addInfo(x, y, controlW, "Kompatibilität", "Zoomify erkannt – Keybinds trennen");
                }

                if (BetterUCConfig.INSTANCE.zoomEnabled) {
                    y = addToggle(x, y, controlW, "Umschaltmodus", BetterUCConfig.INSTANCE.zoomToggleMode,
                            () -> BetterUCConfig.INSTANCE.zoomToggleMode = !BetterUCConfig.INSTANCE.zoomToggleMode);
                    y = addZoomFactorSlider(x, y, controlW, "Vergrößerung",
                            BetterUCConfig.INSTANCE.zoomFactor,
                            value -> BetterUCConfig.INSTANCE.zoomFactor = value);

                    y = addSectionHeader(x, y, controlW, "Bedienung", 0xFF38BDF8);
                    y = addToggle(x, y, controlW, "Mausrad-Zoomstufe",
                            BetterUCConfig.INSTANCE.zoomScrollAdjustEnabled,
                            () -> BetterUCConfig.INSTANCE.zoomScrollAdjustEnabled =
                                    !BetterUCConfig.INSTANCE.zoomScrollAdjustEnabled);
                    if (BetterUCConfig.INSTANCE.zoomScrollAdjustEnabled) {
                        y = addToggle(x, y, controlW, "Zoomstufe merken",
                                BetterUCConfig.INSTANCE.zoomRememberLevel,
                                () -> BetterUCConfig.INSTANCE.zoomRememberLevel =
                                        !BetterUCConfig.INSTANCE.zoomRememberLevel);
                    }
                    y = addToggle(x, y, controlW, "Maus-Sensitivität anpassen",
                            BetterUCConfig.INSTANCE.zoomSensitivityScalingEnabled,
                            () -> BetterUCConfig.INSTANCE.zoomSensitivityScalingEnabled =
                                    !BetterUCConfig.INSTANCE.zoomSensitivityScalingEnabled);

                    y = addSectionHeader(x, y, controlW, "Animation", 0xFFA78BFA);
                    y = addToggle(x, y, controlW, "Weicher Übergang",
                            BetterUCConfig.INSTANCE.zoomSmoothEnabled,
                            () -> BetterUCConfig.INSTANCE.zoomSmoothEnabled =
                                    !BetterUCConfig.INSTANCE.zoomSmoothEnabled);
                    if (BetterUCConfig.INSTANCE.zoomSmoothEnabled) {
                        y = addRangeIntSlider(x, y, controlW, "Übergang (ms)",
                                BetterUCConfig.INSTANCE.zoomAnimationDurationMs, 50, 600,
                                value -> BetterUCConfig.INSTANCE.zoomAnimationDurationMs = value);
                    }
                }
            }
            case AUTO_STATS -> {
                y = addSectionHeader(x, y, controlW, "Automatik", 0xFF4ADE80);
                y = addToggle(x, y, controlW, "Auto-Stats Join", BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled,
                        () -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled = !BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled);
                y = addSectionHeader(x, y, controlW, "Aktionen", 0xFF38BDF8);
                y = addButton(x, y, controlW, "Stats neu laden", b -> SyncRefreshActions.requestStatsRefresh(minecraft, true));
            }
            case AUTOMATIONS -> {
                y = addSectionHeader(x, y, controlW, "Job-Automationen", 0xFF4ADE80);
                y = addToggle(x, y, controlW, "Lieferant /adropdrink", BetterUCConfig.INSTANCE.autoDropDrinkEnabled,
                        () -> BetterUCConfig.INSTANCE.autoDropDrinkEnabled = !BetterUCConfig.INSTANCE.autoDropDrinkEnabled);
                y = addToggle(x, y, controlW, "Fischer", BetterUCConfig.INSTANCE.autoFisherEnabled,
                        () -> BetterUCConfig.INSTANCE.autoFisherEnabled = !BetterUCConfig.INSTANCE.autoFisherEnabled);
                y = addToggle(x, y, controlW, "Winzer", BetterUCConfig.INSTANCE.autoWinzerEnabled,
                        () -> BetterUCConfig.INSTANCE.autoWinzerEnabled = !BetterUCConfig.INSTANCE.autoWinzerEnabled);
                y = addToggle(x, y, controlW, "G\u00E4rtner", BetterUCConfig.INSTANCE.autoGaertnerEnabled,
                        () -> BetterUCConfig.INSTANCE.autoGaertnerEnabled = !BetterUCConfig.INSTANCE.autoGaertnerEnabled);
                y = addToggle(x, y, controlW, "M\u00FCllmann", BetterUCConfig.INSTANCE.autoMuellmannEnabled,
                        () -> BetterUCConfig.INSTANCE.autoMuellmannEnabled = !BetterUCConfig.INSTANCE.autoMuellmannEnabled);
                y = addToggle(x, y, controlW, "Geldtransport /dropmoney",
                        BetterUCConfig.INSTANCE.autoMoneyTransportEnabled,
                        () -> BetterUCConfig.INSTANCE.autoMoneyTransportEnabled =
                                !BetterUCConfig.INSTANCE.autoMoneyTransportEnabled);
                y = addToggle(x, y, controlW, "Transport /droptransport",
                        BetterUCConfig.INSTANCE.autoTransportEnabled,
                        () -> BetterUCConfig.INSTANCE.autoTransportEnabled =
                                !BetterUCConfig.INSTANCE.autoTransportEnabled);

                y = addSectionHeader(x, y, controlW, "Einkaufen", 0xFF38BDF8);
                y = addToggle(x, y, controlW, "Auto-Kauf /abuy", BetterUCConfig.INSTANCE.autoBuyEnabled,
                        () -> BetterUCConfig.INSTANCE.autoBuyEnabled = !BetterUCConfig.INSTANCE.autoBuyEnabled);

                y = addSectionHeader(x, y, controlW, "Erste Hilfe", 0xFFFF6B6B);
                y = addToggle(x, y, controlW, "Folgeannahmen", BetterUCConfig.INSTANCE.autoFirstAidEnabled,
                        () -> BetterUCConfig.INSTANCE.autoFirstAidEnabled = !BetterUCConfig.INSTANCE.autoFirstAidEnabled);
            }
            case CHAT -> {
                y = addSectionHeader(x, y, controlW, "Links & Klickaktionen", 0xFF22D3EE);
                y = addToggle(x, y, controlW, "Links anklickbar",
                        BetterUCConfig.INSTANCE.chatLinksClickableEnabled,
                        () -> BetterUCConfig.INSTANCE.chatLinksClickableEnabled =
                                !BetterUCConfig.INSTANCE.chatLinksClickableEnabled);
                if (BetterUCConfig.INSTANCE.chatLinksClickableEnabled) {
                    y = addToggle(x, y, controlW, "Links hervorheben",
                            BetterUCConfig.INSTANCE.chatLinkHighlightEnabled,
                            () -> BetterUCConfig.INSTANCE.chatLinkHighlightEnabled =
                                    !BetterUCConfig.INSTANCE.chatLinkHighlightEnabled);
                }
                y = addToggle(x, y, controlW, "Command-Bestätigung",
                        BetterUCConfig.INSTANCE.chatCommandConfirmationEnabled,
                        () -> BetterUCConfig.INSTANCE.chatCommandConfirmationEnabled =
                                !BetterUCConfig.INSTANCE.chatCommandConfirmationEnabled);

                y = addSectionHeader(x, y, controlW, "Second Chat", 0xFF38BDF8);
                y = addToggle(x, y, controlW, "Second Chat", BetterUCConfig.INSTANCE.secondChatEnabled,
                        () -> BetterUCConfig.INSTANCE.secondChatEnabled = !BetterUCConfig.INSTANCE.secondChatEnabled);

                y = addSectionHeader(x, y, controlW, "Hotkeys", 0xFFFBBF24);
                y = addButton(x, y, controlW, "Hotkey Commands", b -> openScreen(new HotkeyCommandsScreen(this)));

                y = addSectionHeader(x, y, controlW, "WPS & HQ", 0xFFFF6B6B);
                y = addToggle(x, y, controlW, "Formatierung", BetterUCConfig.INSTANCE.chatCustomizationEnabled,
                        () -> BetterUCConfig.INSTANCE.chatCustomizationEnabled = !BetterUCConfig.INSTANCE.chatCustomizationEnabled);
                if (BetterUCConfig.INSTANCE.chatCustomizationEnabled) {
                    y = addButton(x, y, controlW,
                            "Aktionsschrift: " + BetterUCConfig.chatActionTextStyleLabel(
                                    BetterUCConfig.INSTANCE.chatActionTextStyle),
                            b -> {
                                BetterUCConfig.INSTANCE.chatActionTextStyle = BetterUCConfig.toggleChatActionTextStyle(
                                        BetterUCConfig.INSTANCE.chatActionTextStyle);
                                saveConfig();
                                refreshWidgets();
                            });
                    y = addButton(x, y, controlW,
                            "Trennstil: " + BetterUCConfig.chatHeadlineSeparatorStyleLabel(
                                    BetterUCConfig.INSTANCE.chatHeadlineSeparatorStyle),
                            b -> {
                                BetterUCConfig.INSTANCE.chatHeadlineSeparatorStyle =
                                        BetterUCConfig.toggleChatHeadlineSeparatorStyle(
                                                BetterUCConfig.INSTANCE.chatHeadlineSeparatorStyle);
                                saveConfig();
                                refreshWidgets();
                            });
                    y = addToggle(x, y, controlW, "Chat-Farbverläufe",
                            BetterUCConfig.INSTANCE.chatCustomizationGradientEnabled,
                            () -> BetterUCConfig.INSTANCE.chatCustomizationGradientEnabled =
                                    !BetterUCConfig.INSTANCE.chatCustomizationGradientEnabled);
                    if (BetterUCConfig.INSTANCE.chatCustomizationGradientEnabled) {
                        y = addButton(x, y, controlW, "Farbverläufe anpassen",
                                b -> openScreen(new ChatGradientConfigScreen(this)));
                    }
                }

                y = addSectionHeader(x, y, controlW, "Reinf", BetterUCConfig.INSTANCE.reinfLabelColor);
                y = addToggle(x, y, controlW, "Formatierung", BetterUCConfig.INSTANCE.reinfCustomizationEnabled,
                        () -> BetterUCConfig.INSTANCE.reinfCustomizationEnabled = !BetterUCConfig.INSTANCE.reinfCustomizationEnabled);
                if (BetterUCConfig.INSTANCE.reinfCustomizationEnabled) {
                    y = addToggle(x, y, controlW, "Einheitliche Reinf-Farbe",
                            BetterUCConfig.INSTANCE.reinfUniformColorEnabled,
                            () -> BetterUCConfig.INSTANCE.reinfUniformColorEnabled =
                                    !BetterUCConfig.INSTANCE.reinfUniformColorEnabled);
                    if (BetterUCConfig.INSTANCE.reinfUniformColorEnabled) {
                        y = addColorButton(x, y, controlW, "Reinf Farbe",
                                BetterUCConfig.INSTANCE.reinfUniformColor,
                                color -> BetterUCConfig.INSTANCE.reinfUniformColor = color);
                    } else {
                        y = addColorButton(x, y, controlW, "Reinf Labelfarbe",
                                BetterUCConfig.INSTANCE.reinfLabelColor,
                                color -> BetterUCConfig.INSTANCE.reinfLabelColor = color);
                        y = addColorButton(x, y, controlW, "Reinf Textfarbe",
                                BetterUCConfig.INSTANCE.reinfTextColor,
                                color -> BetterUCConfig.INSTANCE.reinfTextColor = color);
                        y = addColorButton(x, y, controlW, "Reinf Distanzfarbe",
                                BetterUCConfig.INSTANCE.reinfDistanceColor,
                                color -> BetterUCConfig.INSTANCE.reinfDistanceColor = color);
                    }
                    y = addButton(x, y, controlW, "Reinf Farben zur\u00FCcksetzen", b -> resetReinfColors());
                }

                y = addSectionHeader(x, y, controlW, "Reinf-Annahme", 0xFFFBBF24);
                y = addToggle(x, y, controlW, "Reinfs per Hotkey", BetterUCConfig.INSTANCE.reinfAcceptEnabled,
                        () -> BetterUCConfig.INSTANCE.reinfAcceptEnabled = !BetterUCConfig.INSTANCE.reinfAcceptEnabled);
                if (BetterUCConfig.INSTANCE.reinfAcceptEnabled) {
                    y = addToggle(x, y, controlW, "Nur im Survival-Modus",
                            BetterUCConfig.INSTANCE.reinfAcceptSurvivalOnly,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptSurvivalOnly =
                                    !BetterUCConfig.INSTANCE.reinfAcceptSurvivalOnly);
                    y = addRangeIntSlider(x, y, controlW, "Cooldown (Sek.)",
                            BetterUCConfig.INSTANCE.reinfAcceptCooldownSeconds, 1, 10,
                            value -> BetterUCConfig.INSTANCE.reinfAcceptCooldownSeconds = value);

                    y = addSectionHeader(x, y, controlW, "Reinf-Typen", 0xFF38BDF8);
                    y = addToggle(x, y, controlW, "Normal", BetterUCConfig.INSTANCE.reinfAcceptNormal,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptNormal = !BetterUCConfig.INSTANCE.reinfAcceptNormal);
                    y = addToggle(x, y, controlW, "Dringend", BetterUCConfig.INSTANCE.reinfAcceptUrgent,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptUrgent = !BetterUCConfig.INSTANCE.reinfAcceptUrgent);
                    y = addToggle(x, y, controlW, "Medic", BetterUCConfig.INSTANCE.reinfAcceptMedic,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptMedic = !BetterUCConfig.INSTANCE.reinfAcceptMedic);
                    y = addToggle(x, y, controlW, "Geiselnahme", BetterUCConfig.INSTANCE.reinfAcceptHostage,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptHostage = !BetterUCConfig.INSTANCE.reinfAcceptHostage);
                    y = addToggle(x, y, controlW, "Contract", BetterUCConfig.INSTANCE.reinfAcceptContract,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptContract = !BetterUCConfig.INSTANCE.reinfAcceptContract);
                    y = addToggle(x, y, controlW, "Training", BetterUCConfig.INSTANCE.reinfAcceptTraining,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptTraining = !BetterUCConfig.INSTANCE.reinfAcceptTraining);
                    y = addToggle(x, y, controlW, "Drogenabnahme", BetterUCConfig.INSTANCE.reinfAcceptDrugs,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptDrugs = !BetterUCConfig.INSTANCE.reinfAcceptDrugs);
                    y = addToggle(x, y, controlW, "Leichenbewachung", BetterUCConfig.INSTANCE.reinfAcceptBodyGuard,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptBodyGuard = !BetterUCConfig.INSTANCE.reinfAcceptBodyGuard);
                    y = addToggle(x, y, controlW, "Bombe", BetterUCConfig.INSTANCE.reinfAcceptBomb,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptBomb = !BetterUCConfig.INSTANCE.reinfAcceptBomb);
                    y = addToggle(x, y, controlW, "Plantage", BetterUCConfig.INSTANCE.reinfAcceptPlantage,
                            () -> BetterUCConfig.INSTANCE.reinfAcceptPlantage = !BetterUCConfig.INSTANCE.reinfAcceptPlantage);
                }

                y = addSectionHeader(x, y, controlW, "Chat-Zeit", 0xFF94A3B8);
                y = addToggle(x, y, controlW, "Zeitstempel", BetterUCConfig.INSTANCE.chatTimestampsEnabled,
                        () -> BetterUCConfig.INSTANCE.chatTimestampsEnabled = !BetterUCConfig.INSTANCE.chatTimestampsEnabled);
                y = addTimestampField(x, y, controlW);
            }
            case CONNECTION -> y = addConnectionControls(x, y, controlW);
            case SCREENSHOTS -> {
                y = addSectionHeader(x, y, controlW, "Screenshot-Aktionen", 0xFF38BDF8);
                y = addToggle(x, y, controlW, "Aktionen nach F2", BetterUCConfig.INSTANCE.screenshotActionsEnabled,
                        () -> BetterUCConfig.INSTANCE.screenshotActionsEnabled = !BetterUCConfig.INSTANCE.screenshotActionsEnabled);
                y = addToggle(x, y, controlW, "Direkt kopieren", BetterUCConfig.INSTANCE.screenshotAutoCopyEnabled,
                        () -> BetterUCConfig.INSTANCE.screenshotAutoCopyEnabled = !BetterUCConfig.INSTANCE.screenshotAutoCopyEnabled);

                y = addSectionHeader(x, y, controlW, "Freigabe", 0xFF4ADE80);
                y = addInfo(x, y, controlW, "Upload", "Nur auf Klick");
                y = addInfo(x, y, controlW, "Link-Ablauf", "7 Tage");
            }
            case CLOUD_SYNC -> y = addCloudSyncControls(x, y, controlW);
            case BLACKLIST -> {
                y = addSectionHeader(x, y, controlW, "Verwaltung", 0xFFF59E0B);
                y = addButton(x, y, controlW, "Blacklist Gründe", b -> openScreen(new BlacklistConfigScreen(this)));
                y = addSectionHeader(x, y, controlW, "Synchronisierung", 0xFF38BDF8);
                y = addButton(x, y, controlW, "Stats neu laden", b -> SyncRefreshActions.requestStatsRefresh(minecraft, true));
            }
            case PING -> {
                y = addPingControls(x, y, controlW);
            }
            case COMMANDS -> y = addButton(x, y, controlW, "Command Menu", b -> openScreen(new CommandGui()));
            case TRASH_FILTER -> {
                y = addSectionHeader(x, y, controlW, "Filter", 0xFF4ADE80);
                y = addToggle(x, y, controlW, "Mülleimer Filter", BetterUCConfig.INSTANCE.trashFilterEnabled,
                        () -> BetterUCConfig.INSTANCE.trashFilterEnabled = !BetterUCConfig.INSTANCE.trashFilterEnabled);

                y = addSectionHeader(x, y, controlW, "Verhalten", 0xFFFACC15);
                y = addToggle(x, y, controlW, "5s Schließsperre", BetterUCConfig.INSTANCE.trashFilterCloseLockEnabled,
                        () -> BetterUCConfig.INSTANCE.trashFilterCloseLockEnabled = !BetterUCConfig.INSTANCE.trashFilterCloseLockEnabled);

                y = addSectionHeader(x, y, controlW, "Fundstücke", 0xFF38BDF8);
                y = addToggle(x, y, controlW, "Verrottetes Fleisch", BetterUCConfig.INSTANCE.trashFilterRottenFlesh,
                        () -> BetterUCConfig.INSTANCE.trashFilterRottenFlesh = !BetterUCConfig.INSTANCE.trashFilterRottenFlesh);
                y = addToggle(x, y, controlW, "Papier", BetterUCConfig.INSTANCE.trashFilterPaper,
                        () -> BetterUCConfig.INSTANCE.trashFilterPaper = !BetterUCConfig.INSTANCE.trashFilterPaper);
                y = addToggle(x, y, controlW, "Kartoffel", BetterUCConfig.INSTANCE.trashFilterPotato,
                        () -> BetterUCConfig.INSTANCE.trashFilterPotato = !BetterUCConfig.INSTANCE.trashFilterPotato);
                y = addToggle(x, y, controlW, "Karotte", BetterUCConfig.INSTANCE.trashFilterCarrot,
                        () -> BetterUCConfig.INSTANCE.trashFilterCarrot = !BetterUCConfig.INSTANCE.trashFilterCarrot);
                y = addToggle(x, y, controlW, "Apfel", BetterUCConfig.INSTANCE.trashFilterApple,
                        () -> BetterUCConfig.INSTANCE.trashFilterApple = !BetterUCConfig.INSTANCE.trashFilterApple);
                y = addToggle(x, y, controlW, "Truhe", BetterUCConfig.INSTANCE.trashFilterChest,
                        () -> BetterUCConfig.INSTANCE.trashFilterChest = !BetterUCConfig.INSTANCE.trashFilterChest);
                y = addToggle(x, y, controlW, "Redstone-Truhe", BetterUCConfig.INSTANCE.trashFilterTrappedChest,
                        () -> BetterUCConfig.INSTANCE.trashFilterTrappedChest = !BetterUCConfig.INSTANCE.trashFilterTrappedChest);
                y = addToggle(x, y, controlW, "Endertruhe", BetterUCConfig.INSTANCE.trashFilterEnderChest,
                        () -> BetterUCConfig.INSTANCE.trashFilterEnderChest = !BetterUCConfig.INSTANCE.trashFilterEnderChest);
            }
            case DISCORD -> y = addDiscordControls(x, y, controlW);
            case UPDATES -> {
                y = addSectionHeader(x, y, controlW, "Updater", 0xFF38BDF8);
                y = addInfo(x, y, controlW, "Status", VersionChecker.statusLabel());
                y = addToggle(x, y, controlW, "Auto-Updater", BetterUCConfig.INSTANCE.autoUpdateEnabled,
                        () -> BetterUCConfig.INSTANCE.autoUpdateEnabled = !BetterUCConfig.INSTANCE.autoUpdateEnabled);
                y = addButton(x, y, controlW, "Update installieren", b -> VersionChecker.installLatestUpdate(minecraft, true));
                y = addSectionHeader(x, y, controlW, "Changelog", 0xFF4ADE80);
                y = addButton(x, y, controlW, "Changelog öffnen", b -> openScreen(new ChangelogScreen(this)));
            }
        }

        detailContentHeight = Math.max(0, y - startY);
        applyDetailScrollPositions();
    }

    private int addToggle(int x, int y, int width, String label, boolean active, Runnable toggleAction) {
        return addButton(x, y, width, label + ": " + (active ? "AN" : "AUS"), b -> {
            toggleAction.run();
            saveConfig();
            refreshWidgets();
        });
    }

    private int addHudStyleButton(int x, int y, int width, ModuleOption module) {
        return addButton(x, y, width, "Stil: " + BetterUCConfig.hudStyleLabel(getHudStyle(module)), b -> {
            setHudStyle(module, BetterUCConfig.toggleHudStyle(getHudStyle(module)));
            saveConfig();
            refreshWidgets();
        });
    }

    private int addCustomFontControls(int x, int y, int width, ModuleOption module) {
        y = addButton(x, y, width, "Font: " + BetterUCFontManager.selectedFontLabel(getHudFont(module)), b -> {
            setHudFont(module, BetterUCFontManager.nextCustomFontId(getHudFont(module)));
            saveConfig();
            BetterUCFontManager.rebuildAndReload(minecraft);
            refreshWidgets();
        });
        y = addButton(x, y, width, "Fonts neu laden", b -> {
            BetterUCFontManager.rebuildAndReload(minecraft);
            refreshWidgets();
        });
        return addButton(x, y, width, "Font Ordner", b -> BetterUCFontManager.openFontsFolder(minecraft));
    }

    private int addPingControls(int x, int y, int width) {
        y = addSectionHeader(x, y, width, "System", 0xFF38BDF8);
        y = addToggle(x, y, width, "Ping System", BetterUCConfig.INSTANCE.pingRelayEnabled,
                () -> BetterUCConfig.INSTANCE.pingRelayEnabled = !BetterUCConfig.INSTANCE.pingRelayEnabled);
        y = addToggle(x, y, width, "Ping Anzeige", BetterUCConfig.INSTANCE.showPingHud,
                () -> BetterUCConfig.INSTANCE.showPingHud = !BetterUCConfig.INSTANCE.showPingHud);
        y = addButton(x, y, width, pingScopeLabel(), b -> {
            String currentScope = BetterUCConfig.INSTANCE.pingRelayScope == null
                    ? "global"
                    : BetterUCConfig.INSTANCE.pingRelayScope;
            BetterUCConfig.INSTANCE.pingRelayScope = switch (currentScope) {
                case "global" -> "faction";
                case "faction" -> "state";
                default -> "global";
            };
            saveConfig();
            refreshWidgets();
        });

        y = addSectionHeader(x, y, width, "Anzeige", 0xFF22D3EE);
        y = addDoubleSlider(x, y, width, "Ping Größe", BetterUCConfig.INSTANCE.pingHudScale,
                BetterUCConfig.MIN_HUD_SCALE, BetterUCConfig.MAX_HUD_SCALE,
                value -> BetterUCConfig.INSTANCE.pingHudScale = (float) value);
        y = addRangeIntSlider(x, y, width, "Sichtweite", BetterUCConfig.INSTANCE.pingRelayMaxDistance, 0, 128,
                value -> BetterUCConfig.INSTANCE.pingRelayMaxDistance = Math.max(0, value));

        y = addSectionHeader(x, y, width, "Audio & Cooldown", 0xFFFACC15);
        y = addToggle(x, y, width, "Ping Ton", BetterUCConfig.INSTANCE.pingSoundEnabled,
                () -> BetterUCConfig.INSTANCE.pingSoundEnabled = !BetterUCConfig.INSTANCE.pingSoundEnabled);
        y = addButton(x, y, width, "Sound: " + PingRelayClient.pingSoundLabel(BetterUCConfig.INSTANCE.pingSoundId), b -> {
            BetterUCConfig.INSTANCE.pingSoundId = PingRelayClient.nextPingSoundId(BetterUCConfig.INSTANCE.pingSoundId);
            saveConfig();
            refreshWidgets();
        });
        y = addRangeIntSlider(x, y, width, "Cooldown ms", BetterUCConfig.INSTANCE.pingCooldownMs, 500, 10000,
                value -> BetterUCConfig.INSTANCE.pingCooldownMs = Math.max(500, value));

        y = addSectionHeader(x, y, width, "Ping-Farben", 0xFFA855F7);
        y = addColorButton(x, y, width, "Normal Farbe", parseHexColor(BetterUCConfig.INSTANCE.pingNormalColor, 0xFF38BDF8),
                color -> {
                    BetterUCConfig.INSTANCE.pingNormalColor = "#" + hex(color);
                    BetterUCConfig.INSTANCE.pingRelayColor = BetterUCConfig.INSTANCE.pingNormalColor;
                });
        y = addColorButton(x, y, width, "Gefahr Farbe", parseHexColor(BetterUCConfig.INSTANCE.pingDangerColor, 0xFFFF5555),
                color -> BetterUCConfig.INSTANCE.pingDangerColor = "#" + hex(color));
        y = addColorButton(x, y, width, "Sammeln Farbe", parseHexColor(BetterUCConfig.INSTANCE.pingGatherColor, 0xFF22C55E),
                color -> BetterUCConfig.INSTANCE.pingGatherColor = "#" + hex(color));

        y = addSectionHeader(x, y, width, "Test", 0xFF94A3B8);
        return addButton(x, y, width, "Ping testen", b -> PingRelayClient.sendPingAtCrosshair(minecraft, PingRelayClient.PingType.NORMAL));
    }

    private int addConnectionControls(int x, int y, int width) {
        y = addSectionHeader(x, y, width, "Relay", 0xFF38BDF8);
        y = addToggle(x, y, width, "Relay-Verbindung", BetterUCConfig.INSTANCE.pingRelayEnabled, () -> {
            BetterUCConfig.INSTANCE.pingRelayEnabled = !BetterUCConfig.INSTANCE.pingRelayEnabled;
            PingRelayClient.onDisconnect();
            PingRelayClient.tick(minecraft);
        });

        y = addSectionHeader(x, y, width, "Accountstatus", 0xFF4ADE80);
        y = addInfo(x, y, width, "Status", PingRelayClient.statusLabel());
        y = addInfo(x, y, width, "Spieler", currentPlayerName());
        y = addInfo(x, y, width, "Rolle", PingRelayClient.roleLabel());
        y = addInfo(x, y, width, "Fraktion", currentFactionLabel());
        y = addInfo(x, y, width, "Kommunikation", CommunicationDeviceTracker.statusLabel());
        y = addInfo(x, y, width, "Server", currentServerLabel());
        y = addInfo(x, y, width, "Version", MOD_VERSION);

        y = addSectionHeader(x, y, width, "Darstellung", 0xFF38BDF8);
        y = addToggle(x, y, width, "Hologramme", BetterUCConfig.INSTANCE.showRoleHolograms,
                () -> BetterUCConfig.INSTANCE.showRoleHolograms = !BetterUCConfig.INSTANCE.showRoleHolograms);

        y = addSectionHeader(x, y, width, "Zugang", 0xFFFACC15);
        y = addTextField(x, y, width, "Access Code", BetterUCConfig.INSTANCE.pingRelayToken, 160,
                value -> {
                    BetterUCConfig.INSTANCE.pingRelayToken = value.trim();
                    if (!BetterUCConfig.INSTANCE.pingRelayToken.isEmpty()) {
                        BetterUCConfig.INSTANCE.pingRelayEnabled = true;
                    }
                });
        y = addButton(x, y, width, "Access Code holen", b -> Util.getPlatform().openUri(URI.create("https://betteruc.de/access")));
        y = addButton(x, y, width, "Stats neu senden", b -> {
            UserStatsClient.uploadCurrentStats(minecraft);
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("[betterUC] Stats werden ans Userpanel gesendet."));
            }
        });

        y = addSectionHeader(x, y, width, "Erweitert", 0xFF94A3B8);
        y = addTextField(x, y, width, "Ping Gruppe", BetterUCConfig.INSTANCE.pingRelayChannel, 32,
                value -> BetterUCConfig.INSTANCE.pingRelayChannel = value);
        y = addTextField(x, y, width, "Relay Server", BetterUCConfig.INSTANCE.pingRelayUrl, 160,
                value -> BetterUCConfig.INSTANCE.pingRelayUrl = value);
        y = addButton(x, y, width, "Standardserver nutzen", b -> {
            flushTextFields();
            BetterUCConfig.INSTANCE.pingRelayUrl = BetterUCConfig.DEFAULT_PING_RELAY_URL;
            BetterUCConfig.save();
            refreshWidgetsWithoutFlushingTextFields();
        });
        return addButton(x, y, width, "Neu verbinden", b -> {
            flushTextFields();
            BetterUCConfig.INSTANCE.pingRelayEnabled = true;
            saveConfig();
            PingRelayClient.onDisconnect();
            PingRelayClient.tick(minecraft);
            refreshWidgets();
        });
    }

    private int addDiscordControls(int x, int y, int width) {
        y = addSectionHeader(x, y, width, "Community", 0xFF5865F2);
        y = addInfo(x, y, width, "Discord", "betterUC Community");
        y = addButton(x, y, width, "Discord öffnen", b -> openDiscordInvite());
        return addButton(x, y, width, "Invite kopieren", b -> copyDiscordInvite());
    }

    private int addHudProfileControls(int x, int y, int width) {
        String activeProfile = BetterUCConfig.activeHudProfileName();
        if (hudProfileNameDraft == null || hudProfileNameDraft.isBlank()) {
            hudProfileNameDraft = activeProfile;
        }

        y = addSectionHeader(x, y, width, "Aktives Profil", 0xFF38BDF8);
        y = addButton(
                x,
                y,
                width,
                "Profil: " + activeProfile + (hudProfileDropdownOpen ? " ^" : " v"),
                b -> {
                    hudProfileDropdownOpen = !hudProfileDropdownOpen;
                    refreshWidgets();
                }
        );
        if (hudProfileDropdownOpen) {
            for (String profileName : BetterUCConfig.hudProfileNames()) {
                boolean active = profileName.equals(activeProfile);
                y = addButton(
                        x + 8,
                        y,
                        Math.max(40, width - 8),
                        (active ? "[Aktiv] " : "") + profileName,
                        b -> {
                            hudProfileDropdownOpen = false;
                            hudProfileDeleteConfirmation = false;
                            if (BetterUCConfig.switchHudProfile(profileName)) {
                                hudProfileNameDraft = BetterUCConfig.activeHudProfileName();
                                BetterUCFontManager.rebuildAndReload(minecraft);
                            }
                            refreshWidgets();
                        }
                );
            }
        }
        y = addInfo(x, y, width, "Gespeichert", BetterUCConfig.hudProfileNames().size() + " Profile");

        y = addSectionHeader(x, y, width, "Profil verwalten", 0xFFFACC15);
        y = addTextField(x, y, width, "Profilname", hudProfileNameDraft, 24,
                value -> hudProfileNameDraft = value);
        y = addButton(x, y, width, "Neues Profil erstellen", b -> {
            hudProfileDeleteConfirmation = false;
            if (BetterUCConfig.createHudProfile(hudProfileNameDraft)) {
                hudProfileNameDraft = BetterUCConfig.activeHudProfileName();
            }
            refreshWidgets();
        });
        y = addButton(x, y, width, "Aktives Profil duplizieren", b -> {
            hudProfileDeleteConfirmation = false;
            if (BetterUCConfig.duplicateActiveHudProfile(hudProfileNameDraft)) {
                hudProfileNameDraft = BetterUCConfig.activeHudProfileName();
            }
            refreshWidgets();
        });
        y = addButton(x, y, width, "Aktives Profil umbenennen", b -> {
            hudProfileDeleteConfirmation = false;
            if (BetterUCConfig.renameActiveHudProfile(hudProfileNameDraft)) {
                hudProfileNameDraft = BetterUCConfig.activeHudProfileName();
            }
            refreshWidgets();
        });
        y = addButton(x, y, width, "Aktives Profil zur\u00FCcksetzen", b -> {
            hudProfileDeleteConfirmation = false;
            BetterUCConfig.resetActiveHudProfile();
            BetterUCFontManager.rebuildAndReload(minecraft);
            notifyHudProfile("[betterUC] HUD-Profil auf Standardwerte zur\u00FCckgesetzt.");
            refreshWidgets();
        });
        y = addButton(
                x,
                y,
                width,
                hudProfileDeleteConfirmation
                        ? "L\u00F6schen best\u00E4tigen"
                        : "Aktives Profil l\u00F6schen",
                b -> {
                    if (!hudProfileDeleteConfirmation) {
                        hudProfileDeleteConfirmation = true;
                        refreshWidgets();
                        return;
                    }
                    hudProfileDeleteConfirmation = false;
                    if (BetterUCConfig.deleteActiveHudProfile()) {
                        hudProfileNameDraft = BetterUCConfig.activeHudProfileName();
                        BetterUCFontManager.rebuildAndReload(minecraft);
                    } else {
                        notifyHudProfile("[betterUC] Das letzte HUD-Profil kann nicht gel\u00F6scht werden.");
                    }
                    refreshWidgets();
                }
        );

        y = addSectionHeader(x, y, width, "Import & Export", 0xFF38BDF8);
        y = addButton(x, y, width, "Aktives Profil exportieren", b -> {
            var exported = BetterUCConfig.exportActiveHudProfile();
            if (exported == null) {
                notifyHudProfile("[betterUC] HUD-Profil konnte nicht exportiert werden.");
            } else {
                notifyHudProfile("[betterUC] HUD-Profil exportiert: " + exported.getFileName());
            }
            refreshWidgets();
        });
        y = addButton(x, y, width, "JSON-Profile importieren", b -> {
            BetterUCConfig.HudProfileImportResult result = BetterUCConfig.importHudProfiles();
            if (!result.directoryReadable()) {
                notifyHudProfile("[betterUC] Profilordner konnte nicht gelesen werden.");
            } else {
                notifyHudProfile(
                        "[betterUC] " + result.imported() + " Profil(e) importiert"
                                + (result.skipped() > 0 ? ", " + result.skipped() + " \u00FCbersprungen." : ".")
                );
            }
            refreshWidgets();
        });
        y = addButton(x, y, width, "Profilordner \u00F6ffnen", b ->
                Util.getPlatform().openPath(BetterUCConfig.hudProfileDirectory()));

        y = addSectionHeader(x, y, width, "Inhalt", 0xFF4ADE80);
        y = addInfo(x, y, width, "Enthalten", "Position, Stil, Farbe, Gr\u00F6\u00DFe");
        return addInfo(x, y, width, "Cloud Sync", "Profile werden synchronisiert");
    }

    private void notifyHudProfile(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }

    private int addButton(int x, int y, int width, String label, Button.OnPress action) {
        return addButton(
                x,
                y,
                width,
                Component.literal(label),
                tooltipForControl(label, activeDetailSection),
                action
        );
    }

    private int addButton(
            int x,
            int y,
            int width,
            Component label,
            String tooltip,
            Button.OnPress action
    ) {
        Button button = Button.builder(label, action)
                .bounds(x, y, width, BUTTON_H)
                .build();
        addScrollableControl(button);
        registerTooltip(button, tooltip);
        return y + 24;
    }

    private void registerTooltip(AbstractWidget widget, String text) {
        if (widget == null || text == null || text.isBlank()) {
            return;
        }
        controlTooltips.add(new ControlTooltip(widget, text));
    }

    private String tooltipForControl(String label, String section) {
        String raw = label == null ? "" : label.trim();
        String key = raw.replaceFirst(": (?:AN|AUS)$", "").trim();
        String lower = key.toLowerCase(Locale.ROOT);

        if (lower.startsWith("stil:")) {
            return "Wechselt den Darstellungsstil dieses HUDs.";
        }
        if (lower.startsWith("font:")) {
            return "Wählt die Custom-Schrift aus, die dieses HUD verwendet.";
        }
        if (lower.startsWith("profil:")) {
            return "Öffnet die Profilauswahl und aktiviert das gewählte HUD-Layout.";
        }
        if (lower.startsWith("[aktiv] ")) {
            return "Dieses HUD-Profil ist derzeit aktiv.";
        }
        if (raw.matches(".* #[0-9A-Fa-f]{6}$")) {
            return "Öffnet den Farbwähler für diese Darstellung.";
        }
        return switch (key) {
            case "Health HUD" -> "Zeigt deine aktuellen Herzen als frei positionierbares HUD.";
            case "Absorptionsherzen" -> "Zeigt zusätzliche Absorptionsherzen neben deinen normalen Herzen.";
            case "FPS HUD" -> "Zeigt deine aktuellen Bilder pro Sekunde.";
            case "Datum & Uhrzeit HUD" -> "Zeigt dein lokales Datum und deine lokale Uhrzeit als frei positionierbares HUD.";
            case "Datum anzeigen" -> "Blendet das lokale Systemdatum im Widget ein.";
            case "Uhrzeit anzeigen" -> "Blendet die lokale Systemzeit im Widget ein.";
            case "Sekunden anzeigen" -> "Erweitert die Uhrzeit um eine sekundengenaue Anzeige.";
            case "Getrennt positionieren" ->
                    "Macht Datum und Uhrzeit im HUD-Editor zu zwei unabhängig verschiebbaren Elementen.";
            case "Payday HUD" -> "Zeigt den Fortschritt bis zu deinem nächsten PayDay.";
            case "Ammo HUD" -> "Zeigt Munition, Magazinstand und die erkannte Waffe.";
            case "Magazinbalken" -> "Ergänzt das moderne Ammo-HUD um einen Magazin-Fortschrittsbalken.";
            case "Niedrige Munition" -> "Warnt dich, sobald das Magazin die eingestellte Schwelle erreicht.";
            case "Warnsignal" -> "Spielt zusätzlich einen Ton bei niedriger Munition ab.";
            case "Bank HUD" -> "Zeigt und aktualisiert dein erkanntes Bankguthaben.";
            case "Auto-/fbank" -> "Sendet nach einer Kontostand-Abfrage automatisch /fbank.";
            case "Auto-/atminfo" -> "Sendet nach einer Kontostand-Abfrage automatisch /atminfo.";
            case "Automatisch trotzdem einzahlen" ->
                    "Sendet bei einem vollen oder nicht ausreichend freien Bankautomaten nach 1 Sekunde automatisch /einzahlen force.";
            case "Reichensteuer-Alert" ->
                    "Warnt dich 5 Minuten vor dem PayDay, wenn mehr als 100.000$ auf deinem Konto liegen.";
            case "Alert-Ton" -> "Spielt zusätzlich einen kurzen Ton für den Reichensteuer-Alert ab.";
            case "Bargeld HUD" -> "Zeigt und aktualisiert dein erkanntes Bargeld.";
            case "Potion HUD" -> "Zeigt aktive Trankeffekte und ihre verbleibende Dauer.";
            case "ToggleSprint" -> "Hält Sprint nach einmaligem Drücken aktiv und zeigt den Status im HUD.";
            case "Plant HUD" -> "Zeigt erkannte Plantagenzeiten und Reife-Fortschritte.";
            case "Dealer Timer" -> "Zeigt nach einem Drogenverkauf den 15-Sekunden-Cooldown.";
            case "Masken Timer" -> "Zeigt die verbleibende Maskenzeit und warnt kurz vor dem Ablauf.";
            case "Produktion Timer" -> "Zeigt die Produktionszeit und startet danach die gespeicherte Navigation.";
            case "Auto-Stats Join" -> "Ruft beim Beitritt und nach AFK automatisch und unsichtbar /stats ab.";
            case "Stats neu laden" -> "Fordert deine aktuellen Statistiken erneut vom Server an.";
            case "Lieferant /adropdrink" -> "Automatisiert die Getränkeabgabe des Lieferanten-Jobs.";
            case "Fischer" -> "Automatisiert Fischschwarm-Suche, Fangen und Abgabe.";
            case "Winzer" -> "Sammelt in den Winzer-Inventaren automatisch alle Trauben ein.";
            case "Gärtner" ->
                    "Automatisiert Blumenabgabe und Unkrautentfernung und markiert erledigte Töpfe grün.";
            case "Müllmann" -> "Automatisiert die Müllabgabe in den konfigurierten Müllhalden-Bereichen.";
            case "Geldtransport /dropmoney" ->
                    "Führt am erkannten Einzahlungsziel automatisch einmalig /dropmoney aus.";
            case "Transport /droptransport" ->
                    "Liefert am Ziel automatisch die im Transport-Scoreboard erkannten Kisten ab.";
            case "Auto-Kauf /abuy" -> "Erlaubt automatische Mengenkäufe über /abuy <Menge>.";
            case "Folgeannahmen" ->
                    "Nimmt nach deiner ersten manuellen Bestätigung weitere Erste-Hilfe-Angebote automatisch an.";
            case "Formatierung" -> "Reinf".equals(section)
                    ? "Formatiert Verstärkungsrufe kompakter und mit deinen gewählten Farben."
                    : "Formatiert WPS- und HQ-Servermeldungen kompakter und übersichtlicher.";
            case "Einheitliche Reinf-Farbe" -> "Verwendet für den gesamten Verstärkungsruf nur eine Farbe.";
            case "Reinf Farben zurücksetzen" -> "Setzt alle Farben der Verstärkungsrufe auf die Standardwerte zurück.";
            case "Reinfs per Hotkey" ->
                    "Erkennt passende Verstärkungsrufe und nimmt sie ausschließlich über deinen Reinf-Hotkey an.";
            case "Zeitstempel" -> "Blendet vor Chatnachrichten die aktuelle Uhrzeit ein.";
            case "Blacklist Gründe" -> "Öffnet die Verwaltung der gespeicherten Blacklist-Gründe.";
            case "Second Chat" ->
                    "Aktiviert zusätzliche Chat-Tabs und frei platzierbare Chatfenster mit eigenen Filtern.";
            case "Hotkey Commands" -> "Öffnet die Verwaltung eigener Nachrichten- und Command-Hotkeys.";
            case "Links anklickbar" ->
                    "Erkennt HTTP-, HTTPS- und www-Adressen im normalen Chat und Second Chat als echte Links.";
            case "Links hervorheben" ->
                    "Kennzeichnet automatisch erkannte Links in Aqua und unterstreicht sie.";
            case "Command-Bestätigung" ->
                    "Ist diese Option AUS, werden vorhandene anklickbare Serverbefehle direkt ausgeführt. Normaler Text wird niemals automatisch gestartet.";
            case "Command Menu" -> "Öffnet das Schnellmenü für betterUC-Commands.";
            case "Mülleimer Filter" -> "Hebt ausgewählte Fundstücke im oberen Mülleimer-Inventar grün hervor.";
            case "5s Schließsperre" ->
                    "Verhindert 5 Sekunden lang das Schließen, wenn ein ausgewähltes Fundstück gefunden wurde.";
            case "Verrottetes Fleisch", "Papier", "Kartoffel", "Karotte", "Apfel", "Truhe",
                    "Redstone-Truhe", "Endertruhe" ->
                    "Legt fest, ob dieses Fundstück im Mülleimer grün hervorgehoben wird.";
            case "Auto-Updater" -> "Lädt neue betterUC-Versionen automatisch für deine Minecraft-Version vor.";
            case "Update installieren" -> "Lädt das verfügbare Update und bereitet den Neustart vor.";
            case "Changelog öffnen" -> "Öffnet die Neuerungen der aktuellen betterUC-Version.";
            case "Ping System" -> "Aktiviert das private Ping-System zwischen verifizierten Mod-Nutzern.";
            case "Ping Anzeige" -> "Legt fest, ob empfangene Pings in der Spielwelt angezeigt werden.";
            case "Ping Ton" -> "Spielt beim Empfang eines sichtbaren Pings einen Ton ab.";
            case "Ping testen" -> "Setzt einen normalen Test-Ping an deiner angesehenen Position.";
            case "Relay-Verbindung" -> "Aktiviert oder deaktiviert die Verbindung zum betterUC-Relay.";
            case "Hologramme" -> "Blendet betterUC-Rollen über den Köpfen anderer Mod-Nutzer ein oder aus.";
            case "Access Code holen" -> "Öffnet die Website zum Erstellen deines persönlichen Access Codes.";
            case "Stats neu senden" -> "Überträgt die zuletzt erkannten Spielerdaten erneut an dein Userpanel.";
            case "Standardserver nutzen" -> "Setzt die Relay-Adresse auf den offiziellen betterUC-Server zurück.";
            case "Neu verbinden" -> "Trennt die aktuelle Relay-Verbindung und baut sie neu auf.";
            case "Discord öffnen" -> "Öffnet den permanenten Einladungslink zur betterUC Community.";
            case "Invite kopieren" -> "Kopiert den Discord-Einladungslink in deine Zwischenablage.";
            case "Neues Profil erstellen" -> "Erstellt ein neues HUD-Profil mit dem eingegebenen Namen.";
            case "Aktives Profil duplizieren" -> "Erstellt eine Kopie des aktuellen HUD-Profils.";
            case "Aktives Profil umbenennen" -> "Benennt das aktuelle HUD-Profil in den eingegebenen Namen um.";
            case "Aktives Profil zurücksetzen" -> "Setzt das aktive HUD-Profil auf die Standardwerte zurück.";
            case "Aktives Profil löschen", "Löschen bestätigen" -> "Löscht das aktive HUD-Profil nach Bestätigung.";
            case "Aktives Profil exportieren" -> "Speichert das aktive HUD-Profil als JSON-Datei.";
            case "JSON-Profile importieren" -> "Importiert alle gültigen JSON-Profile aus dem Profilordner.";
            case "Profilordner öffnen" -> "Öffnet den Ordner für exportierte und importierbare HUD-Profile.";
            case "Cloud Sync" -> "Synchronisiert deine unterstützten Mod-Einstellungen über dein betterUC-Konto.";
            case "Cloud-Einstellungen laden" -> "Lädt deine zuletzt gespeicherten Einstellungen aus der Cloud.";
            case "Aktuelle Einstellungen hochladen" -> "Speichert deine aktuellen Einstellungen in der Cloud.";
            case "Farbverlauf" -> "Aktiviert für dieses HUD einen Farbverlauf von links nach rechts.";
            case "Prefix anzeigen" -> "Blendet die Beschriftung vor dem HUD-Wert ein oder aus.";
            case "Fonts neu laden" -> "Liest den Custom-Font-Ordner neu ein.";
            case "Font Ordner" -> "Öffnet den Ordner, in den du eigene Schriftarten legen kannst.";
            case "Chat-Farbverläufe" -> "Aktiviert die farbigen Verläufe für formatierte HQ- und PAY-Nachrichten.";
            case "Farbverläufe anpassen" -> "Öffnet getrennte Farbprofile für HQ- und PAY-Nachrichten.";
            default -> {
                if (lower.startsWith("ping ziel:")) {
                    yield "Bestimmt, ob deine Pings global, nur für deine Fraktion oder für Staatsfraktionen sichtbar sind.";
                }
                if (lower.startsWith("sound:")) {
                    yield "Wechselt den Sound, der bei sichtbaren Pings abgespielt wird.";
                }
                if (lower.startsWith("gradient farbe")) {
                    yield "Legt die rechte Zielfarbe des HUD-Farbverlaufs fest.";
                }
                yield "Klicke, um diese Einstellung zu ändern.";
            }
        };
    }

    private int addInfo(int x, int y, int width, String label, String value) {
        Button widget = Button.builder(Component.literal(label + ": " + value), b -> {
        }).bounds(x, y, width, BUTTON_H).build();
        widget.active = false;
        addScrollableControl(widget);
        return y + 24;
    }

    private int addSectionHeader(int x, int y, int width, String label, int color) {
        activeDetailSection = label;
        detailSectionHeaders.add(new DetailSectionHeader(label, x, y, width, color));
        return y + 18;
    }

    private int addColorButton(
            int x,
            int y,
            int width,
            String label,
            int color,
            ColorPickerScreen.ColorApplyTarget target
    ) {
        MutableComponent buttonLabel = Component.literal(label + "  ");
        buttonLabel.append(Component.literal("\u2588")
                .setStyle(Style.EMPTY.withColor(color & 0xFFFFFF)));

        return addButton(
                x,
                y,
                width,
                buttonLabel,
                "Öffnet den Farbwähler für diese Darstellung.",
                b -> openScreen(new ColorPickerScreen(
                this,
                label,
                label + " wählen",
                color,
                target
                ))
        );
    }

    private void resetReinfColors() {
        BetterUCConfig.INSTANCE.reinfUniformColorEnabled = false;
        BetterUCConfig.INSTANCE.reinfLabelColor = BetterUCConfig.DEFAULT_REINF_LABEL_COLOR;
        BetterUCConfig.INSTANCE.reinfTextColor = BetterUCConfig.DEFAULT_REINF_TEXT_COLOR;
        BetterUCConfig.INSTANCE.reinfDistanceColor = BetterUCConfig.DEFAULT_REINF_DISTANCE_COLOR;
        BetterUCConfig.INSTANCE.reinfUniformColor = BetterUCConfig.DEFAULT_REINF_UNIFORM_COLOR;
        saveConfig();
        refreshWidgets();
    }

    private int addCloudSyncControls(int x, int y, int width) {
        y = addSectionHeader(x, y, width, "Cloud Sync", 0xFF38BDF8);
        y = addToggle(x, y, width, "Cloud Sync", BetterUCConfig.INSTANCE.cloudSettingsEnabled,
                () -> BetterUCConfig.INSTANCE.cloudSettingsEnabled = !BetterUCConfig.INSTANCE.cloudSettingsEnabled);
        y = addInfo(x, y, width, "Server-Freigabe", RemoteFeatureFlagsClient.statusLabel());
        y = addInfo(x, y, width, "Status", CloudSettingsClient.statusLabel());
        y = addInfo(x, y, width, "Letzter Sync", CloudSettingsClient.lastSyncLabel());

        y = addSectionHeader(x, y, width, "Aktionen", 0xFF4ADE80);
        y = addButton(x, y, width, "Cloud-Einstellungen laden",
                b -> CloudSettingsClient.downloadNow(minecraft));
        return addButton(x, y, width, "Aktuelle Einstellungen hochladen",
                b -> CloudSettingsClient.uploadNow(minecraft));
    }

    private int addHudGradientControls(int x, int y, int width, ModuleOption module) {
        y = addToggle(x, y, width, "Farbverlauf", getHudGradientEnabled(module),
                () -> setHudGradientEnabled(module, !getHudGradientEnabled(module)));
        if (getHudGradientEnabled(module)) {
            y = addColorButton(x, y, width, "Gradient Farbe", getHudGradientColor(module),
                    color -> setHudGradientColor(module, color));
        }
        return y;
    }

    private int addHudPrefixControls(int x, int y, int width, ModuleOption module) {
        y = addToggle(x, y, width, "Prefix anzeigen", getHudPrefixEnabled(module),
                () -> setHudPrefixEnabled(module, !getHudPrefixEnabled(module)));
        return addTextField(x, y, width, "Prefix Text", getHudPrefix(module), 24,
                value -> setHudPrefix(module, value));
    }

    private int addIntSlider(int x, int y, int width, String label, int current, int max, IntConsumer setter) {
        int safeMax = Math.max(1, max);
        int safeCurrent = clamp(current, 0, safeMax);
        AbstractSliderButton slider = new AbstractSliderButton(
                x,
                y,
                width,
                BUTTON_H,
                Component.literal(label + ": " + safeCurrent),
                safeCurrent / (double) safeMax
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(label + ": " + sliderIntValue(value, safeMax)));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderIntValue(value, safeMax));
            }
        };
        addScrollableControl(slider);
        registerTooltip(slider, tooltipForField(label));
        return y + 24;
    }

    private int addRangeIntSlider(
            int x,
            int y,
            int width,
            String label,
            int current,
            int min,
            int max,
            IntConsumer setter
    ) {
        int safeMin = Math.max(0, min);
        int safeMax = Math.max(safeMin + 1, max);
        int safeCurrent = clamp(current, safeMin, safeMax);
        AbstractSliderButton slider = new AbstractSliderButton(
                x,
                y,
                width,
                BUTTON_H,
                Component.literal(label + ": " + safeCurrent),
                (safeCurrent - safeMin) / (double) (safeMax - safeMin)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(label + ": " + sliderRangeIntValue(value, safeMin, safeMax)));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderRangeIntValue(value, safeMin, safeMax));
            }
        };
        addScrollableControl(slider);
        registerTooltip(slider, tooltipForField(label));
        return y + 24;
    }

    private int addDoubleSlider(
            int x,
            int y,
            int width,
            String label,
            double current,
            double min,
            double max,
            DoubleConsumer setter
    ) {
        double normalized = clamp01((current - min) / Math.max(0.0001, max - min));
        AbstractSliderButton slider = new AbstractSliderButton(
                x,
                y,
                width,
                BUTTON_H,
                Component.literal(label + ": " + percent(current)),
                normalized
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(label + ": " + percent(sliderDoubleValue(value, min, max))));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderDoubleValue(value, min, max));
            }
        };
        addScrollableControl(slider);
        registerTooltip(slider, tooltipForField(label));
        return y + 24;
    }

    private int addZoomFactorSlider(
            int x,
            int y,
            int width,
            String label,
            double current,
            DoubleConsumer setter
    ) {
        double min = ZoomController.MIN_ZOOM_FACTOR;
        double max = ZoomController.MAX_ZOOM_FACTOR;
        double safeCurrent = Math.max(min, Math.min(max, current));
        AbstractSliderButton slider = new AbstractSliderButton(
                x,
                y,
                width,
                BUTTON_H,
                Component.literal(label + ": " + zoomFactorLabel(safeCurrent)),
                (safeCurrent - min) / (max - min)
        ) {
            private double factor() {
                double raw = min + value * (max - min);
                return Math.round(raw * 10.0D) / 10.0D;
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.literal(label + ": " + zoomFactorLabel(factor())));
            }

            @Override
            protected void applyValue() {
                setter.accept(factor());
            }
        };
        addScrollableControl(slider);
        registerTooltip(slider, tooltipForField(label));
        return y + 24;
    }

    private static String zoomFactorLabel(double factor) {
        return String.format(Locale.GERMANY, "%.1fx", factor);
    }

    private int addTimestampField(int x, int y, int width) {
        EditBox timestampField = new EditBox(
                font,
                x,
                y,
                width,
                BUTTON_H,
                Component.literal("Timestamp Format")
        );
        timestampField.setMaxLength(32);
        timestampField.setValue(BetterUCConfig.INSTANCE.chatTimestampFormat);
        timestampField.setResponder(text -> BetterUCConfig.INSTANCE.chatTimestampFormat = text);
        textFieldFlushers.add(() -> BetterUCConfig.INSTANCE.chatTimestampFormat = timestampField.getValue());
        addScrollableControl(timestampField);
        registerTooltip(timestampField, "Legt das Format des Chat-Zeitstempels fest, zum Beispiel [HH:mm:ss].");
        return y + 24;
    }

    private int addTextField(int x, int y, int width, String label, String current, int maxLength, Consumer<String> setter) {
        EditBox field = new EditBox(
                font,
                x,
                y,
                width,
                BUTTON_H,
                Component.literal(label)
        );
        field.setMaxLength(maxLength);
        field.setHint(Component.literal(label));
        field.setValue(current == null ? "" : current);
        field.setResponder(setter::accept);
        textFieldFlushers.add(() -> setter.accept(field.getValue()));
        addScrollableControl(field);
        registerTooltip(field, tooltipForField(label));
        return y + 24;
    }

    private String tooltipForField(String label) {
        return switch (label) {
            case "Warnschwelle %" -> "Legt fest, ab welchem Magazinstand die Munitionswarnung erscheint.";
            case "Ping Größe" -> "Skaliert die Darstellung aller Pings in der Spielwelt.";
            case "Sichtweite" -> "Pings außerhalb dieser Entfernung werden weder angezeigt noch abgespielt.";
            case "Cooldown ms" -> "Bestimmt die Mindestpause zwischen zwei eigenen Pings.";
            case "Vergrößerung" -> "Legt die anfängliche Vergrößerung des Zooms fest.";
            case "Übergang (ms)" -> "Bestimmt, wie schnell der Zoom weich ein- und ausgeblendet wird.";
            case "Access Code" -> "Dein persönlicher Code verbindet diese Mod-Installation mit deinem Account.";
            case "Ping Gruppe" -> "Erweiterte Relay-Gruppe. Normalerweise sollte hier global stehen.";
            case "Relay Server" -> "Erweiterte WebSocket-Adresse des betterUC-Relay-Servers.";
            case "Profilname" -> "Name für ein neues, dupliziertes oder umbenanntes HUD-Profil.";
            case "Prefix Text" -> "Ändert die Beschriftung, die vor dem Wert dieses HUDs steht.";
            case "Filter 1", "Filter 2", "Filter 3" ->
                    "Eigener Textfilter. Mit ^ am Anfang wird nur der Nachrichtenanfang geprüft.";
            default -> "Ziehe oder bearbeite den Wert, um diese Einstellung anzupassen.";
        };
    }

    private <T extends AbstractWidget> T addScrollableControl(T widget) {
        addRenderableWidget(widget);
        detailControls.add(new ScrollableControl(widget, widget.getY()));
        return widget;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);
        renderFrame(context, mouseX, mouseY);
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (bugReportButton != null && bugReportButton.visible) {
            drawBorder(context, bugReportButton.getX() - 1, bugReportButton.getY() - 1,
                    bugReportButton.getWidth() + 2, bugReportButton.getHeight() + 2, 0xFFF59E0B);
        }
        renderHoveredTooltip(context, mouseX, mouseY);
    }

    private void renderHoveredTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        String tooltip = null;

        for (ControlTooltip entry : controlTooltips) {
            AbstractWidget widget = entry.widget();
            if (widget.visible && inBounds(mouseX, mouseY, widget.getX(), widget.getY(),
                    widget.getWidth(), widget.getHeight())) {
                tooltip = entry.text();
                break;
            }
        }

        if (tooltip == null) {
            tooltip = hoveredModuleDescription(mouseX, mouseY);
        }
        if (tooltip == null || tooltip.isBlank()) {
            return;
        }

        int maxTextWidth = Math.max(100, Math.min(230, width - 24));
        List<String> lines = wrapTooltipText(tooltip, maxTextWidth);
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int padding = 6;
        int tooltipW = textWidth + padding * 2;
        int tooltipH = lines.size() * 10 + padding * 2 - 1;
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY + 12;
        if (tooltipX + tooltipW > width - 4) {
            tooltipX = mouseX - tooltipW - 8;
        }
        if (tooltipY + tooltipH > height - 4) {
            tooltipY = mouseY - tooltipH - 8;
        }
        tooltipX = clamp(tooltipX, 4, Math.max(4, width - tooltipW - 4));
        tooltipY = clamp(tooltipY, 4, Math.max(4, height - tooltipH - 4));

        drawSoftRect(context, tooltipX, tooltipY, tooltipW, tooltipH, 0xF0151B24);
        drawBorder(context, tooltipX, tooltipY, tooltipW, tooltipH, withAlpha(selectedModule.accent, 0xEE));
        for (int i = 0; i < lines.size(); i++) {
            context.text(font, Component.literal(lines.get(i)),
                    tooltipX + padding, tooltipY + padding + i * 10, TEXT_SOFT);
        }
    }

    private String hoveredModuleDescription(int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = moduleListY();
        int w = sidebarW() - 20;
        int categoryIndex = 0;
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) {
                continue;
            }
            if (categoryIndex++ < moduleScrollIndex) {
                continue;
            }
            if (y + MODULE_H > moduleListBottom()) {
                break;
            }
            if (inBounds(mouseX, mouseY, x, y, w, MODULE_H)) {
                return module.description;
            }
            y += MODULE_H + MODULE_GAP;
        }
        return null;
    }

    private List<String> wrapTooltipText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void renderFrame(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = mainX();
        int y = mainY();
        int w = mainW();
        int h = mainH();

        drawSoftRect(context, x, y, w, h, PANEL_BG);
        drawSoftRect(context, x + 1, y + 1, w - 2, h - 2, PANEL_INNER);
        drawBorder(context, x, y, w, h, PANEL_BORDER);
        context.fill(x + 2, y + 2, x + w - 2, y + 3, 0x24FFFFFF);

        String title = "betterUC";
        int titleX = x + 14;
        int titleY = y + 11;
        context.text(font, Component.literal(title), titleX, titleY, TEXT_PRIMARY);
        context.text(font, Component.literal("v" + MOD_VERSION),
                titleX + font.width(title) + 7, titleY, TEXT_MUTED);

        renderCategoryTabs(context, mouseX, mouseY);
        renderModuleList(context, mouseX, mouseY);
        renderDetailPanel(context, mouseX, mouseY);
    }

    private void renderCategoryTabs(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = mainY() + 42;
        int tabW = Math.max(38, (sidebarW() - 20) / Category.values().length);

        for (Category category : Category.values()) {
            boolean selected = category == selectedCategory;
            boolean hovered = inBounds(mouseX, mouseY, x, y, tabW, 18);
            int color = selected ? withAlpha(category.accent, 0xCC) : hovered ? 0x80404A59 : 0x50333C49;
            drawSoftRect(context, x, y, tabW - 3, 18, color);
            drawBorder(context, x, y, tabW - 3, 18, selected ? withAlpha(category.accent, 0xFF) : 0x50333C49);
            context.centeredText(font, Component.literal(category.label), x + (tabW - 3) / 2, y + 5,
                    selected ? TEXT_PRIMARY : TEXT_SOFT);
            x += tabW;
        }
    }

    private void renderModuleList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = moduleListY();
        int w = sidebarW() - 20;
        int categoryIndex = 0;

        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) continue;
            if (categoryIndex++ < moduleScrollIndex) continue;
            if (y + MODULE_H > moduleListBottom()) break;
            boolean selected = module == selectedModule;
            boolean hovered = inBounds(mouseX, mouseY, x, y, w, MODULE_H);
            int bg = selected ? 0xB82A3442 : hovered ? 0x80313A47 : 0x4D1F2631;
            drawSoftRect(context, x, y, w, MODULE_H, bg);
            context.fill(x + 2, y + 2, x + 4, y + MODULE_H - 2,
                    selected ? withAlpha(module.accent, 0xFF) : withAlpha(module.accent, 0x99));
            context.text(font, Component.literal(module.label), x + 8, y + 4,
                    selected ? TEXT_PRIMARY : TEXT_SOFT);

            String state = module.hasToggle() ? (isEnabled(module) ? "ON" : "OFF") : "SET";
            int stateColor = module.hasToggle() && !isEnabled(module) ? TEXT_MUTED : withAlpha(module.accent, 0xFF);
            context.text(font, Component.literal(state), x + w - font.width(state) - 6, y + 4, stateColor);
            y += MODULE_H + MODULE_GAP;
        }

        renderModuleScrollbar(context);
    }

    private void renderModuleScrollbar(GuiGraphicsExtractor context) {
        int maximum = maxModuleScrollIndex();
        if (maximum <= 0) {
            return;
        }
        int top = moduleListY();
        int bottom = moduleListBottom();
        int trackHeight = Math.max(1, bottom - top);
        int visibleRows = visibleModuleRows();
        int totalRows = moduleCount(selectedCategory);
        int thumbHeight = Math.max(18, trackHeight * visibleRows / Math.max(1, totalRows));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int thumbY = top + (int) Math.round(
                (trackHeight - thumbHeight) * (moduleScrollIndex / (double) maximum)
        );
        int x = moduleScrollbarX();
        context.fill(x, top, x + 3, bottom, 0x55334155);
        context.fill(x, thumbY, x + 3, thumbY + thumbHeight, withAlpha(selectedCategory.accent, 0xDD));
    }

    private void renderDetailPanel(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = detailX();
        int y = detailY();
        int w = detailW();
        int h = detailH();
        drawSoftRect(context, x, y, w, h, PANEL_ALT);
        drawBorder(context, x, y, w, h, 0x70333C49);
        context.fill(x + 2, y + 2, x + 4, y + h - 2, withAlpha(selectedModule.accent, 0xEE));

        context.text(font, Component.literal(selectedModule.label), x + 14, y + 12, TEXT_PRIMARY);
        context.text(font, Component.literal(selectedModule.description), x + 14, y + 25, TEXT_MUTED);
        renderSelectedStatus(context, x + 14, y + 39);
        if (selectedModule == ModuleOption.UPDATES) {
            boolean splitLayout = w >= 470;
            int updatesX = splitLayout ? x + 224 : x + 14;
            int updatesY = splitLayout ? y + 58 : y + 138;
            renderUpdates(context, updatesX, updatesY, x + w - 14 - updatesX, y + h - 14 - updatesY);
            return;
        }
        renderPreview(context, x + Math.max(224, w - 172), y + 58);
        renderDetailSectionHeaders(context);
        renderDetailScrollbar(context);
    }

    private void renderDetailSectionHeaders(GuiGraphicsExtractor context) {
        int top = detailControlsTop();
        int bottom = detailControlsBottom();

        for (DetailSectionHeader header : detailSectionHeaders) {
            int y = header.baseY() - detailScrollOffset;
            if (y < top || y + 11 > bottom) continue;

            int color = withAlpha(header.color(), 0xFF);
            context.text(font, Component.literal(header.label()), header.x(), y + 1, color);
            int lineStart = header.x() + font.width(header.label()) + 8;
            int lineEnd = header.x() + header.sectionWidth();
            if (lineStart < lineEnd) {
                context.fill(lineStart, y + 7, lineEnd, y + 8, withAlpha(header.color(), 0x66));
            }
        }
    }

    private void renderDetailScrollbar(GuiGraphicsExtractor context) {
        int maxScroll = maxDetailScroll();
        if (maxScroll <= 0) return;

        int top = detailControlsTop();
        int height = detailControlsHeight();
        int trackX = detailX() + detailW() - 8;
        int trackY = top;
        int trackH = Math.max(1, height);
        int thumbH = Math.max(22, (int) (trackH * (trackH / (double) Math.max(trackH, detailContentHeight))));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = trackY + (int) Math.round(travel * (detailScrollOffset / (double) maxScroll));

        context.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x55333C49);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, withAlpha(selectedModule.accent, 0xDD));
    }

    private void renderSelectedStatus(GuiGraphicsExtractor context, int x, int y) {
        if (selectedModule == ModuleOption.UPDATES) {
            context.text(font, Component.literal("Version " + MOD_VERSION), x, y, 0xFF86EFAC);
            return;
        }

        if (selectedModule == ModuleOption.BLACKLIST) {
            context.text(
                    font,
                    Component.literal("Einträge: " + BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
                            + " | Sync " + syncAge(BetterUCConfig.INSTANCE.lastBlacklistSyncMs)),
                    x,
                    y,
                    TEXT_SOFT
            );
            return;
        }

        if (selectedModule == ModuleOption.CONNECTION) {
            context.text(
                    font,
                    Component.literal("Verbindung: " + PingRelayClient.statusLabel()),
                    x,
                    y,
                    PingRelayClient.isConnected() ? 0xFF86EFAC : TEXT_MUTED
            );
            return;
        }

        if (selectedModule == ModuleOption.CLOUD_SYNC) {
            context.text(
                    font,
                    Component.literal("Cloud: " + CloudSettingsClient.statusLabel()),
                    x,
                    y,
                    CloudSettingsClient.isReady() ? 0xFF86EFAC : TEXT_MUTED
            );
            return;
        }

        if (selectedModule.hasToggle()) {
            context.text(
                    font,
                    Component.literal(isEnabled(selectedModule) ? "Status: aktiv" : "Status: aus"),
                    x,
                    y,
                    isEnabled(selectedModule) ? 0xFF86EFAC : TEXT_MUTED
            );
        } else {
            context.text(font, Component.literal("Modul-Einstellungen"), x, y, TEXT_SOFT);
        }
    }

    private void renderPreview(GuiGraphicsExtractor context, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        int previewX = Math.min(x, detailX() + detailW() - 150);
        int previewY = y;

        context.text(font, Component.literal("Preview"), previewX, previewY - 14, TEXT_MUTED);
        String style = getHudStyle(selectedModule);
        String fontId = getHudFont(selectedModule);
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);
        ModernHudRenderer.withHudGradient(getHudGradientEnabled(selectedModule), getHudGradientColor(selectedModule), () -> {
        switch (selectedModule) {
            case HUD_PROFILES -> drawMiniInfo(
                    context,
                    previewX,
                    previewY,
                    "HUD-Profil",
                    BetterUCConfig.activeHudProfileName(),
                    true
            );
            case HEALTH -> {
                HealthHud.drawPreview(
                        context,
                        minecraft,
                        previewX,
                        previewY,
                        style,
                        fontId,
                        BetterUCConfig.INSTANCE.healthHudHeartColor,
                        BetterUCConfig.INSTANCE.healthHudTextColor
                );
            }
            case FPS -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.FPS), "144",
                            BetterUCConfig.INSTANCE.fpsHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.FPS, "144"), previewX, previewY,
                            BetterUCConfig.INSTANCE.fpsHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.FPS, "144"), previewX, previewY,
                            BetterUCConfig.INSTANCE.fpsHudColor);
                }
            }
            case DATE_TIME -> DateTimeHud.drawPreview(context, minecraft, previewX, previewY);
            case PAYDAY -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.PAYDAY),
                            "25/60 min", 25.0F / 60.0F, BetterUCConfig.INSTANCE.paydayHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.PAYDAY, "25/60 Minuten"), previewX, previewY,
                            BetterUCConfig.INSTANCE.paydayHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.PAYDAY, "25/60 Minuten"), previewX, previewY,
                            BetterUCConfig.INSTANCE.paydayHudColor);
                }
            }
            case AMMO -> {
                if (modernStyle) {
                    if (BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled) {
                        ModernHudRenderer.drawTwoLineProgressModule(context, minecraft, previewX, previewY,
                                hudPreviewLabel(ModuleOption.AMMO), "20/96", "TS19",
                                20.0F / 21.0F, 0xFFFFAA33, 0xFF7CFF8A);
                    } else {
                        ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY,
                                hudPreviewLabel(ModuleOption.AMMO), "20/96", "TS19",
                                0xFFFFAA33, 0xFF7CFF8A);
                    }
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.AMMO, "20/96"), previewX, previewY, 0xFFFFAA33);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "TS19", previewX, previewY + 11, 0xFF55FF55);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.AMMO, "20/96"), previewX, previewY, 0xFFFFAA33);
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, "TS19", previewX, previewY + 10, 0xFF55FF55);
                }
            }
            case ARMOR -> ArmorHud.drawPreview(
                    context,
                    minecraft,
                    previewX,
                    previewY,
                    style,
                    fontId,
                    BetterUCConfig.INSTANCE.armorHudColor,
                    BetterUCConfig.INSTANCE.armorHudDurabilityEnabled
            );
            case BANK -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.BANK),
                            previewBankValue(), BetterUCConfig.INSTANCE.bankHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.BANK, previewBankValue()), previewX, previewY,
                            BetterUCConfig.INSTANCE.bankHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.BANK, previewBankValue()), previewX, previewY,
                            BetterUCConfig.INSTANCE.bankHudColor);
                }
            }
            case CASH -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.CASH),
                            previewCashValue(), BetterUCConfig.INSTANCE.cashHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.CASH, previewCashValue()), previewX, previewY,
                            BetterUCConfig.INSTANCE.cashHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.CASH, previewCashValue()), previewX, previewY,
                            BetterUCConfig.INSTANCE.cashHudColor);
                }
            }
            case POTION -> {
                int potionColor = BetterUCConfig.INSTANCE.potionHudColor;
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "EFFECT", "Stärke II",
                            "1:26", potionColor);
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY + 33, "EFFECT", "Speed",
                            "0:49", potionColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "Stärke II", previewX, previewY, potionColor);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "1:26", previewX, previewY + 11, TEXT_MUTED);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "Speed", previewX, previewY + 25, potionColor);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "0:49", previewX, previewY + 36, TEXT_MUTED);
                } else {
                    PotionEffectsHud.drawTransparentPreview(context, minecraft, previewX, previewY, potionColor);
                }
            }
            case SPRINT -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.SPRINT), "ON",
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.SPRINT, "ON"), previewX, previewY,
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.SPRINT, "ON"), previewX, previewY,
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                }
            }
            case HACK_TIMER -> {
                String time = HackTimerHud.secondsRemaining > 0
                        ? String.format(Locale.ROOT, "%02d:%02d", HackTimerHud.secondsRemaining / 60, HackTimerHud.secondsRemaining % 60)
                        : "02:39";
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.HACK_TIMER), time, 0xFF60A5FA);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.HACK_TIMER, time), previewX, previewY, 0xFF60A5FA);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.HACK_TIMER, time), previewX, previewY, 0xFF60A5FA);
                }
            }
            case DEALER_TIMER -> {
                String time = "00:15";
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.DEALER_TIMER), time,
                            BetterUCConfig.INSTANCE.dealerTimerHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.DEALER_TIMER, time), previewX, previewY,
                            BetterUCConfig.INSTANCE.dealerTimerHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.DEALER_TIMER, time), previewX, previewY,
                            BetterUCConfig.INSTANCE.dealerTimerHudColor);
                }
            }
            case MASK_TIMER -> {
                String time = "18:42";
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, previewX, previewY,
                            hudPreviewLabel(ModuleOption.MASK_TIMER), time, 0.07F,
                            BetterUCConfig.INSTANCE.maskTimerHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId,
                            hudPreviewText(ModuleOption.MASK_TIMER, time), previewX, previewY,
                            BetterUCConfig.INSTANCE.maskTimerHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font,
                            hudPreviewText(ModuleOption.MASK_TIMER, time), previewX, previewY,
                            BetterUCConfig.INSTANCE.maskTimerHudColor);
                }
            }
            case PRODUCTION_TIMER -> {
                String time = "19:59";
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.PRODUCTION_TIMER), time, 0.35F,
                            BetterUCConfig.INSTANCE.productionTimerHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.PRODUCTION_TIMER, time), previewX, previewY,
                            BetterUCConfig.INSTANCE.productionTimerHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.PRODUCTION_TIMER, time), previewX, previewY,
                            BetterUCConfig.INSTANCE.productionTimerHudColor);
                }
            }
            case PLANT_TIMER -> {
                String plantValue = "Pulver 7/10";
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, hudPreviewLabel(ModuleOption.PLANT_TIMER),
                            plantValue, "Reif: 1:30:00 | Wasser: 20:00", 0xFF6CF27D, 0xFFFFD866);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, hudPreviewText(ModuleOption.PLANT_TIMER, plantValue), previewX, previewY, 0xFF6CF27D);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, fontId, "Reif: 1:30:00 | Wasser: 20:00", previewX, previewY + 11, 0xFFFFD866);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, hudPreviewText(ModuleOption.PLANT_TIMER, plantValue), previewX, previewY, 0xFF6CF27D);
                    ModernHudRenderer.drawHudTextWithShadow(context, this.font, "Reif: 1:30:00 | Wasser: 20:00", previewX, previewY + 10, 0xFFFFD866);
                }
            }
            case ZOOM -> drawMiniInfo(
                    context,
                    previewX,
                    previewY,
                    "Zoom",
                    ZoomController.configuredFactorLabel(),
                    BetterUCConfig.INSTANCE.zoomEnabled
            );
            case AUTO_STATS -> drawMiniInfo(context, previewX, previewY, "Auto-Stats", "Join /stats", BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled);
            case AUTOMATIONS -> {
                int enabled = AutomationController.localEnabledCount();
                drawMiniInfo(context, previewX, previewY, "Automationen", enabled + "/9 aktiv", enabled > 0);
            }
            case CHAT -> drawReinfPreview(context, previewX, previewY);
            case CONNECTION -> drawMiniInfo(context, previewX, previewY, "Verbindung", PingRelayClient.statusLabel(),
                    PingRelayClient.isConnected());
            case SCREENSHOTS -> drawMiniInfo(context, previewX, previewY, "Screenshots",
                    BetterUCConfig.INSTANCE.screenshotActionsEnabled ? "Aktionen im Chat" : "Vanilla",
                    BetterUCConfig.INSTANCE.screenshotActionsEnabled);
            case CLOUD_SYNC -> drawMiniInfo(context, previewX, previewY, "Cloud Sync",
                    CloudSettingsClient.statusLabel(), CloudSettingsClient.isReady());
            case BLACKLIST -> drawMiniInfo(context, previewX, previewY, "Blacklist",
                    BetterUCConfig.INSTANCE.chatBlacklistPlayers.size() + " Spieler", true);
            case PING -> drawMiniInfo(context, previewX, previewY, "Ping System",
                    BetterUCConfig.INSTANCE.pingRelayEnabled ? "Aktiv" : "Aus",
                    BetterUCConfig.INSTANCE.pingRelayEnabled);
            case COMMANDS -> drawMiniInfo(context, previewX, previewY, "Tools", "Command Menu", true);
            case TRASH_FILTER -> drawMiniInfo(context, previewX, previewY, "Mülleimer Filter",
                    BetterUCConfig.INSTANCE.trashFilterEnabled ? "Markierung aktiv" : "Aus",
                    BetterUCConfig.INSTANCE.trashFilterEnabled);
            case DISCORD -> drawMiniInfo(context, previewX, previewY, "Discord", "Invite öffnen", true);
            case UPDATES -> {
            }
        }
        });
    }

    private void renderUpdates(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        int bottom = y + Math.max(0, height);
        int safeWidth = Math.max(120, width - 7);
        int cursor = 0;

        cursor += drawUpdateIntro(context, x, y + cursor - updatesScrollOffset, safeWidth, y, bottom);
        for (ChangelogContent.Page section : ChangelogContent.clickGuiSections()) {
            int sectionHeight = updateSectionHeight(section, safeWidth);
            int sectionY = y + cursor - updatesScrollOffset;
            drawUpdateSection(context, section, x, sectionY, safeWidth, sectionHeight, y, bottom);
            cursor += sectionHeight + 7;
        }

        updatesContentHeight = cursor;
        updatesScrollOffset = clamp(updatesScrollOffset, 0, maxUpdatesScroll(height));
        renderUpdatesScrollbar(context, x + width - 3, y, height);
    }

    private int drawUpdateIntro(
            GuiGraphicsExtractor context,
            int x,
            int drawY,
            int width,
            int clipTop,
            int clipBottom
    ) {
        if (drawY >= clipTop && drawY + 9 <= clipBottom) {
            context.text(font, Component.literal("AKTUELLE VERSION"), x, drawY, withAlpha(selectedModule.accent, 0xFF));
        }
        if (drawY + 15 >= clipTop && drawY + 24 <= clipBottom) {
            context.text(font, Component.literal("betterUC " + MOD_VERSION), x, drawY + 15, TEXT_PRIMARY);
        }
        int descriptionHeight = wrappedUpdateHeight(
                "Die wichtigsten Änderungen dieses Updates. Für alle Features den vollständigen Changelog öffnen.",
                width,
                11
        );
        drawClippedWrappedUpdateLine(
                context,
                "Die wichtigsten Änderungen dieses Updates. Für alle Features den vollständigen Changelog öffnen.",
                x,
                drawY + 29,
                width,
                TEXT_MUTED,
                clipTop,
                clipBottom,
                11,
                ""
        );
        return 29 + descriptionHeight + 10;
    }

    private int updateSectionHeight(ChangelogContent.Page section, int width) {
        int innerWidth = Math.max(40, width - 28);
        int height = 12;
        height += wrappedUpdateHeight(section.title(), innerWidth, 11);
        height += 3 + wrappedUpdateHeight(section.description(), innerWidth, 11) + 8;
        for (String line : section.lines()) {
            height += wrappedUpdateHeight(line, innerWidth - font.width("• "), 11) + 4;
        }
        return height + 8;
    }

    private void drawUpdateSection(
            GuiGraphicsExtractor context,
            ChangelogContent.Page section,
            int x,
            int drawY,
            int width,
            int sectionHeight,
            int clipTop,
            int clipBottom
    ) {
        if (drawY + sectionHeight < clipTop || drawY > clipBottom) return;

        int visibleTop = Math.max(clipTop, drawY);
        int visibleBottom = Math.min(clipBottom, drawY + sectionHeight);
        context.fill(x, visibleTop, x + width, visibleBottom, 0x681B2430);
        context.fill(x, visibleTop, x + 3, visibleBottom, withAlpha(selectedModule.accent, 0xE6));

        int innerX = x + 14;
        int innerWidth = Math.max(40, width - 28);
        int lineY = drawY + 9;
        int used = drawClippedWrappedUpdateLine(context, section.title(), innerX, lineY, innerWidth,
                TEXT_PRIMARY, clipTop, clipBottom, 11, "");
        lineY += used + 3;
        used = drawClippedWrappedUpdateLine(context, section.description(), innerX, lineY, innerWidth,
                TEXT_MUTED, clipTop, clipBottom, 11, "");
        lineY += used + 8;

        for (String line : section.lines()) {
            used = drawClippedWrappedUpdateLine(context, line, innerX, lineY, innerWidth,
                    TEXT_SOFT, clipTop, clipBottom, 11, "• ");
            lineY += used + 4;
        }
    }

    private int drawClippedWrappedUpdateLine(
            GuiGraphicsExtractor context,
            String line,
            int x,
            int y,
            int maxWidth,
            int color,
            int clipTop,
            int clipBottom,
            int lineHeight,
            String prefix
    ) {
        String remaining = line;
        boolean firstLine = true;
        int currentY = y;
        while (!remaining.isEmpty()) {
            String usedPrefix = firstLine ? prefix : "  ";
            int availableWidth = Math.max(20, maxWidth - font.width(usedPrefix));
            String part = takeFittingText(remaining, availableWidth);
            if (currentY >= clipTop && currentY + 9 <= clipBottom) {
                context.text(font, Component.literal(usedPrefix + part), x, currentY, color);
            }
            remaining = remaining.substring(part.length()).trim();
            currentY += lineHeight;
            firstLine = false;
        }
        return Math.max(lineHeight, currentY - y);
    }

    private int wrappedUpdateHeight(String line, int maxWidth, int lineHeight) {
        String remaining = line;
        int lines = 0;
        while (!remaining.isEmpty()) {
            String part = takeFittingText(remaining, Math.max(20, maxWidth));
            remaining = remaining.substring(part.length()).trim();
            lines++;
        }
        return Math.max(lineHeight, lines * lineHeight);
    }

    private void renderUpdatesScrollbar(GuiGraphicsExtractor context, int x, int y, int height) {
        int maxScroll = maxUpdatesScroll(height);
        if (maxScroll <= 0 || height <= 0) return;
        int thumbH = Math.max(22, (int) (height * (height / (double) Math.max(height, updatesContentHeight))));
        int travel = Math.max(1, height - thumbH);
        int thumbY = y + (int) Math.round(travel * (updatesScrollOffset / (double) maxScroll));
        context.fill(x, y, x + 3, y + height, 0x55333C49);
        context.fill(x, thumbY, x + 3, thumbY + thumbH, withAlpha(selectedModule.accent, 0xDD));
    }

    private int maxUpdatesScroll(int viewportHeight) {
        return Math.max(0, updatesContentHeight - Math.max(0, viewportHeight));
    }

    private void drawMiniInfo(GuiGraphicsExtractor context, int x, int y, String label, String value, boolean active) {
        int w = Math.max(110, Math.max(font.width(label), font.width(value)) + 22);
        ModernHudRenderer.drawPanel(context, x, y, w, 38, active ? selectedModule.accent : 0xFF64748B);
        context.text(font, Component.literal(label), x + 10, y + 8, withAlpha(selectedModule.accent, 0xFF));
        context.text(font, Component.literal(value), x + 10, y + 20, TEXT_PRIMARY);
    }

    private void drawReinfPreview(GuiGraphicsExtractor context, int x, int y) {
        if (!BetterUCConfig.INSTANCE.reinfCustomizationEnabled) {
            drawMiniInfo(context, x, y, "Reinf", "Anpassung aus", false);
            return;
        }

        List<Component> lines = ChatCustomizationFormatter.reinforcementPreview();
        int width = 110;
        for (Component line : lines) {
            width = Math.max(width, font.width(line) + 20);
        }
        int accent = BetterUCConfig.INSTANCE.reinfUniformColorEnabled
                ? BetterUCConfig.INSTANCE.reinfUniformColor
                : BetterUCConfig.INSTANCE.reinfLabelColor;
        ModernHudRenderer.drawPanel(context, x, y, width, 38, accent);
        context.text(font, lines.get(0), x + 10, y + 8, TEXT_PRIMARY);
        context.text(font, lines.get(1), x + 10, y + 20, TEXT_PRIMARY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxModuleScrollIndex() > 0
                && verticalAmount != 0.0D
                && inBounds(mouseX, mouseY, mainX() + 8, moduleListY(), sidebarW() - 12,
                moduleListBottom() - moduleListY())) {
            int rows = Math.max(1, (int) Math.round(Math.abs(verticalAmount)));
            setModuleScrollIndex(moduleScrollIndex - (verticalAmount > 0.0D ? rows : -rows));
            return true;
        }
        if (selectedModule == ModuleOption.UPDATES) {
            int x = detailX();
            int y = detailY();
            int w = detailW();
            int h = detailH();
            boolean splitLayout = w >= 470;
            int updatesX = splitLayout ? x + 224 : x + 14;
            int updatesY = splitLayout ? y + 58 : y + 138;
            int updatesW = x + w - 14 - updatesX;
            int updatesH = y + h - 14 - updatesY;
            if (inBounds(mouseX, mouseY, updatesX, updatesY, updatesW, updatesH)) {
                updatesScrollOffset = clamp(
                        updatesScrollOffset - (int) Math.round(verticalAmount * 30.0D),
                        0,
                        maxUpdatesScroll(updatesH)
                );
                BetterUCConfig.INSTANCE.clickGuiUpdatesScrollOffset = updatesScrollOffset;
                return true;
            }
        }
        if (selectedModule != ModuleOption.UPDATES
                && maxDetailScroll() > 0
                && inBounds(mouseX, mouseY, detailX(), detailControlsTop(), detailW(), detailControlsHeight())) {
            int nextOffset = detailScrollOffset - (int) Math.round(verticalAmount * 28.0D);
            setDetailScrollOffset(nextOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        double mouseX = event.x();
        double mouseY = event.y();

        if (maxModuleScrollIndex() > 0
                && inBounds(mouseX, mouseY, moduleScrollbarX() - 2, moduleListY(), 7,
                moduleListBottom() - moduleListY())) {
            double ratio = clamp01((mouseY - moduleListY())
                    / Math.max(1.0D, moduleListBottom() - moduleListY()));
            setModuleScrollIndex((int) Math.round(ratio * maxModuleScrollIndex()));
            return true;
        }

        if (selectedModule != ModuleOption.UPDATES && maxDetailScroll() > 0) {
            int trackX = detailX() + detailW() - 10;
            int trackY = detailControlsTop();
            int trackHeight = detailControlsHeight();
            if (inBounds(mouseX, mouseY, trackX, trackY, 8, trackHeight)) {
                double ratio = clamp01((mouseY - trackY) / Math.max(1.0D, trackHeight));
                setDetailScrollOffset((int) Math.round(ratio * maxDetailScroll()));
                return true;
            }
        }

        Category category = categoryAt(mouseX, mouseY);
        if (category != null) {
            rememberCurrentUiState();
            moduleScrollIndices.put(selectedCategory, moduleScrollIndex);
            selectedModulesByCategory.put(selectedCategory, selectedModule);
            selectedCategory = category;
            moduleScrollIndex = moduleScrollIndices.getOrDefault(category, 0);
            selectedModule = selectedModulesByCategory.getOrDefault(category, firstModuleFor(category));
            ensureSelectedModuleVisible();
            restoreSelectedModuleScroll();
            refreshWidgets();
            return true;
        }

        ModuleOption module = moduleAt(mouseX, mouseY);
        if (module != null) {
            rememberCurrentUiState();
            selectedCategory = module.category;
            selectedModule = module;
            selectedModulesByCategory.put(selectedCategory, selectedModule);
            restoreSelectedModuleScroll();
            refreshWidgets();
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        saveConfig();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Category categoryAt(double mouseX, double mouseY) {
        int x = mainX() + 10;
        int y = mainY() + 42;
        int tabW = Math.max(38, (sidebarW() - 20) / Category.values().length);
        for (Category category : Category.values()) {
            if (inBounds(mouseX, mouseY, x, y, tabW - 3, 18)) return category;
            x += tabW;
        }
        return null;
    }

    private ModuleOption moduleAt(double mouseX, double mouseY) {
        int x = mainX() + 10;
        int y = moduleListY();
        int w = sidebarW() - 20;
        int categoryIndex = 0;
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) continue;
            if (categoryIndex++ < moduleScrollIndex) continue;
            if (y + MODULE_H > moduleListBottom()) break;
            if (inBounds(mouseX, mouseY, x, y, w, MODULE_H)) return module;
            y += MODULE_H + MODULE_GAP;
        }
        return null;
    }

    private ModuleOption firstModuleFor(Category category) {
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category == category) return module;
        }
        return ModuleOption.FPS;
    }

    private ModuleOption moduleAtCategoryIndex(Category category, int targetIndex) {
        int index = 0;
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != category) continue;
            if (index++ == Math.max(0, targetIndex)) return module;
        }
        return firstModuleFor(category);
    }

    private int moduleIndex(ModuleOption target) {
        if (target == null) return 0;
        int index = 0;
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != target.category) continue;
            if (module == target) return index;
            index++;
        }
        return 0;
    }

    private int moduleCount(Category category) {
        int count = 0;
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category == category) count++;
        }
        return count;
    }

    private Category parseCategory(String value) {
        try {
            return Category.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException ignored) {
            return Category.HUD;
        }
    }

    private ModuleOption parseModule(String value, Category fallbackCategory) {
        try {
            ModuleOption module = ModuleOption.valueOf(value == null ? "" : value);
            return module.category == fallbackCategory ? module : firstModuleFor(fallbackCategory);
        } catch (IllegalArgumentException ignored) {
            return firstModuleFor(fallbackCategory);
        }
    }

    private int storedDetailScroll(ModuleOption module) {
        if (module == null || BetterUCConfig.INSTANCE.clickGuiScrollOffsets == null) return 0;
        return Math.max(0, BetterUCConfig.INSTANCE.clickGuiScrollOffsets.getOrDefault(module.name(), 0));
    }

    private int storedModuleScroll(Category category) {
        if (category == null || BetterUCConfig.INSTANCE.clickGuiScrollOffsets == null) return 0;
        return Math.max(0, BetterUCConfig.INSTANCE.clickGuiScrollOffsets.getOrDefault(moduleScrollKey(category), 0));
    }

    private String moduleScrollKey(Category category) {
        return "sidebar:" + category.name();
    }

    private void setModuleScrollIndex(int index) {
        moduleScrollIndex = clamp(index, 0, maxModuleScrollIndex());
        moduleScrollIndices.put(selectedCategory, moduleScrollIndex);
        BetterUCConfig.INSTANCE.clickGuiScrollOffsets.put(moduleScrollKey(selectedCategory), moduleScrollIndex);
    }

    private void ensureSelectedModuleVisible() {
        moduleScrollIndex = clamp(moduleScrollIndex, 0, maxModuleScrollIndex());
        int selectedIndex = moduleIndex(selectedModule);
        int visibleRows = visibleModuleRows();
        if (selectedIndex < moduleScrollIndex) {
            moduleScrollIndex = selectedIndex;
        } else if (selectedIndex >= moduleScrollIndex + visibleRows) {
            moduleScrollIndex = selectedIndex - visibleRows + 1;
        }
        moduleScrollIndex = clamp(moduleScrollIndex, 0, maxModuleScrollIndex());
        moduleScrollIndices.put(selectedCategory, moduleScrollIndex);
    }

    private int maxModuleScrollIndex() {
        return Math.max(0, moduleCount(selectedCategory) - visibleModuleRows());
    }

    private int visibleModuleRows() {
        int height = Math.max(1, moduleListBottom() - moduleListY());
        return Math.max(1, (height + MODULE_GAP) / (MODULE_H + MODULE_GAP));
    }

    private void restoreSelectedModuleScroll() {
        detailScrollOffset = storedDetailScroll(selectedModule);
        updatesScrollOffset = selectedModule == ModuleOption.UPDATES
                ? Math.max(0, BetterUCConfig.INSTANCE.clickGuiUpdatesScrollOffset)
                : updatesScrollOffset;
    }

    private void rememberCurrentUiState() {
        BetterUCConfig.INSTANCE.clickGuiLastCategory = selectedCategory.name();
        BetterUCConfig.INSTANCE.clickGuiLastModule = selectedModule.name();
        BetterUCConfig.INSTANCE.clickGuiScrollOffsets.put(moduleScrollKey(selectedCategory),
                Math.max(0, moduleScrollIndex));
        BetterUCConfig.INSTANCE.clickGuiScrollOffsets.put(selectedModule.name(), Math.max(0, detailScrollOffset));
        BetterUCConfig.INSTANCE.clickGuiUpdatesScrollOffset = Math.max(0, updatesScrollOffset);
    }

    private boolean isEnabled(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.showHealthHud;
            case FPS -> BetterUCConfig.INSTANCE.showFpsHud;
            case DATE_TIME -> BetterUCConfig.INSTANCE.showDateTimeHud;
            case PAYDAY -> BetterUCConfig.INSTANCE.showPaydayHud;
            case AMMO -> BetterUCConfig.INSTANCE.showAmmoHud;
            case ARMOR -> BetterUCConfig.INSTANCE.showArmorHud;
            case BANK -> BetterUCConfig.INSTANCE.showBankHud;
            case CASH -> BetterUCConfig.INSTANCE.showCashHud;
            case POTION -> BetterUCConfig.INSTANCE.showPotionEffectsHud;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.showPlantTimerHud;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.showDealerTimerHud;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.showMaskTimerHud;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.showProductionTimerHud;
            case AUTO_STATS -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled;
            case ZOOM -> BetterUCConfig.INSTANCE.zoomEnabled;
            case CLOUD_SYNC -> BetterUCConfig.INSTANCE.cloudSettingsEnabled;
            case SCREENSHOTS -> BetterUCConfig.INSTANCE.screenshotActionsEnabled;
            case PING -> BetterUCConfig.INSTANCE.pingRelayEnabled;
            case TRASH_FILTER -> BetterUCConfig.INSTANCE.trashFilterEnabled;
            default -> true;
        };
    }

    private String getHudStyle(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudStyle;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudStyle;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle;
            case CASH -> BetterUCConfig.INSTANCE.cashHudStyle;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudStyle;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudStyle;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudStyle;
            case PING -> BetterUCConfig.INSTANCE.pingHudStyle;
            default -> BetterUCConfig.HUD_STYLE_MODERN;
        };
    }

    private void setHudStyle(ModuleOption module, String style) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle = style;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle = style;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudStyle = style;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle = style;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle = style;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudStyle = style;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle = style;
            case CASH -> BetterUCConfig.INSTANCE.cashHudStyle = style;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle = style;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle = style;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle = style;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle = style;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudStyle = style;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudStyle = style;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudStyle = style;
            case PING -> BetterUCConfig.INSTANCE.pingHudStyle = style;
            default -> {
            }
        }
    }

    private String getHudFont(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudCustomFont;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudCustomFont;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont;
            case CASH -> BetterUCConfig.INSTANCE.cashHudCustomFont;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudCustomFont;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudCustomFont;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudCustomFont;
            case PING -> BetterUCConfig.INSTANCE.pingHudCustomFont;
            default -> BetterUCConfig.INSTANCE.customHudFont;
        };
    }

    private void setHudFont(ModuleOption module, String fontId) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont = fontId;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont = fontId;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudCustomFont = fontId;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont = fontId;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont = fontId;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudCustomFont = fontId;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont = fontId;
            case CASH -> BetterUCConfig.INSTANCE.cashHudCustomFont = fontId;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont = fontId;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont = fontId;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont = fontId;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont = fontId;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudCustomFont = fontId;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudCustomFont = fontId;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudCustomFont = fontId;
            case PING -> BetterUCConfig.INSTANCE.pingHudCustomFont = fontId;
            default -> BetterUCConfig.INSTANCE.customHudFont = fontId;
        }
    }

    private boolean getHudGradientEnabled(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientEnabled;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientEnabled;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudGradientEnabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientEnabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientEnabled;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudGradientEnabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientEnabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientEnabled;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientEnabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientEnabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientEnabled;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudGradientEnabled;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudGradientEnabled;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudGradientEnabled;
            default -> false;
        };
    }

    private void setHudGradientEnabled(ModuleOption module, boolean enabled) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientEnabled = enabled;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientEnabled = enabled;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudGradientEnabled = enabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientEnabled = enabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientEnabled = enabled;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudGradientEnabled = enabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientEnabled = enabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientEnabled = enabled;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientEnabled = enabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientEnabled = enabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientEnabled = enabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientEnabled = enabled;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudGradientEnabled = enabled;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudGradientEnabled = enabled;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudGradientEnabled = enabled;
            default -> {
            }
        }
    }

    private int getHudGradientColor(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientColor;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientColor;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudGradientColor;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientColor;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientColor;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudGradientColor;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientColor;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientColor;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientColor;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientColor;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientColor;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientColor;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudGradientColor;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudGradientColor;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudGradientColor;
            default -> BetterUCConfig.DEFAULT_HUD_GRADIENT_COLOR;
        };
    }

    private void setHudGradientColor(ModuleOption module, int color) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientColor = color;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientColor = color;
            case DATE_TIME -> BetterUCConfig.INSTANCE.dateTimeHudGradientColor = color;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientColor = color;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientColor = color;
            case ARMOR -> BetterUCConfig.INSTANCE.armorHudGradientColor = color;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientColor = color;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientColor = color;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientColor = color;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientColor = color;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientColor = color;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientColor = color;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudGradientColor = color;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudGradientColor = color;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudGradientColor = color;
            default -> {
            }
        }
    }

    private boolean hasHudPrefix(ModuleOption module) {
        return switch (module) {
            case FPS, PAYDAY, AMMO, BANK, CASH, SPRINT, HACK_TIMER, PLANT_TIMER, DEALER_TIMER, MASK_TIMER,
                    PRODUCTION_TIMER -> true;
            default -> false;
        };
    }

    private boolean getHudPrefixEnabled(ModuleOption module) {
        return switch (module) {
            case FPS -> BetterUCConfig.INSTANCE.fpsHudPrefixEnabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudPrefixEnabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudPrefixEnabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudPrefixEnabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudPrefixEnabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudPrefixEnabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudPrefixEnabled;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudPrefixEnabled;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudPrefixEnabled;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudPrefixEnabled;
            default -> false;
        };
    }

    private void setHudPrefixEnabled(ModuleOption module, boolean enabled) {
        switch (module) {
            case FPS -> BetterUCConfig.INSTANCE.fpsHudPrefixEnabled = enabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudPrefixEnabled = enabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudPrefixEnabled = enabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudPrefixEnabled = enabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudPrefixEnabled = enabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudPrefixEnabled = enabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudPrefixEnabled = enabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudPrefixEnabled = enabled;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudPrefixEnabled = enabled;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudPrefixEnabled = enabled;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudPrefixEnabled = enabled;
            default -> {
            }
        }
    }

    private String getHudPrefix(ModuleOption module) {
        return switch (module) {
            case FPS -> BetterUCConfig.INSTANCE.fpsHudPrefix;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudPrefix;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudPrefix;
            case BANK -> BetterUCConfig.INSTANCE.bankHudPrefix;
            case CASH -> BetterUCConfig.INSTANCE.cashHudPrefix;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudPrefix;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudPrefix;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudPrefix;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudPrefix;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudPrefix;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudPrefix;
            default -> "";
        };
    }

    private void setHudPrefix(ModuleOption module, String prefix) {
        switch (module) {
            case FPS -> BetterUCConfig.INSTANCE.fpsHudPrefix = prefix;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudPrefix = prefix;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudPrefix = prefix;
            case BANK -> BetterUCConfig.INSTANCE.bankHudPrefix = prefix;
            case CASH -> BetterUCConfig.INSTANCE.cashHudPrefix = prefix;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudPrefix = prefix;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudPrefix = prefix;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudPrefix = prefix;
            case DEALER_TIMER -> BetterUCConfig.INSTANCE.dealerTimerHudPrefix = prefix;
            case MASK_TIMER -> BetterUCConfig.INSTANCE.maskTimerHudPrefix = prefix;
            case PRODUCTION_TIMER -> BetterUCConfig.INSTANCE.productionTimerHudPrefix = prefix;
            default -> {
            }
        }
    }

    private String hudPreviewLabel(ModuleOption module) {
        return BetterUCConfig.hudModuleLabel(getHudPrefixEnabled(module), getHudPrefix(module));
    }

    private String hudPreviewText(ModuleOption module, String value) {
        return BetterUCConfig.prefixedHudText(getHudPrefixEnabled(module), getHudPrefix(module), value);
    }

    private String previewBankValue() {
        int live = BankBalanceHud.getCurrentBankBalance();
        return live >= 0 ? BankBalanceHud.formatMoney(live) + "$" : BankBalanceHud.formatMoney(88375) + "$";
    }

    private String previewCashValue() {
        int live = CashHud.getCurrentCash();
        return live >= 0 ? CashHud.formatMoney(live) + "$" : CashHud.formatMoney(1278) + "$";
    }

    private String pingScopeLabel() {
        String currentScope = BetterUCConfig.INSTANCE.pingRelayScope == null
                ? "global"
                : BetterUCConfig.INSTANCE.pingRelayScope;
        String label = switch (currentScope) {
            case "faction" -> "Fraktion";
            case "state" -> "Staat";
            default -> "Global";
        };
        return "Ping Ziel: " + label;
    }

    private String currentFactionLabel() {
        String raw = BetterUCConfig.INSTANCE.currentPlayerFactionLabel == null
                ? ""
                : BetterUCConfig.INSTANCE.currentPlayerFactionLabel.trim();
        if (raw.isBlank()) {
            raw = BetterUCConfig.INSTANCE.currentPlayerFaction == null
                    ? ""
                    : BetterUCConfig.INSTANCE.currentPlayerFaction.trim();
        }
        return raw.isBlank() ? "nicht erkannt" : raw;
    }

    private String currentPlayerName() {
        if (minecraft == null || minecraft.player == null) return "nicht erkannt";
        String name = minecraft.player.getName().getString();
        return name == null || name.isBlank() ? "nicht erkannt" : name;
    }

    private String currentServerLabel() {
        String server = PingRelayClient.currentServerId(minecraft);
        return server == null || server.isBlank() ? "nicht erkannt" : server;
    }

    private void openDiscordInvite() {
        String invite = safeDiscordInvite();
        try {
            Util.getPlatform().openUri(URI.create(invite));
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not open betterUC Discord invite {}", invite, e);
            copyDiscordInvite();
        }
    }

    private void copyDiscordInvite() {
        String invite = safeDiscordInvite();
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(invite);
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("[betterUC] Discord-Invite kopiert: " + invite));
            }
        }
    }

    private String safeDiscordInvite() {
        return BetterUCConfig.DEFAULT_DISCORD_INVITE_URL;
    }

    private String takeFittingText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;

        int lastSpace = -1;
        for (int i = 1; i <= text.length(); i++) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                lastSpace = i - 1;
            }
            String candidate = text.substring(0, i).trim();
            if (font.width(candidate) > maxWidth) {
                if (lastSpace > 0) {
                    return text.substring(0, lastSpace).trim();
                }
                return text.substring(0, Math.max(1, i - 1)).trim();
            }
        }
        return text;
    }

    private String syncAge(long timestampMs) {
        if (timestampMs <= 0L) return "nie";
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
        if (ageSeconds < 60L) return "vor " + ageSeconds + "s";
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) return "vor " + ageMinutes + "m";
        return "vor " + (ageMinutes / 60L) + "h";
    }

    private void openScreen(Screen screen) {
        if (minecraft != null) {
            ClientCompat.setScreen(minecraft, screen);
        }
    }

    private void setDetailScrollOffset(int scrollOffset) {
        detailScrollOffset = clamp(scrollOffset, 0, maxDetailScroll());
        BetterUCConfig.INSTANCE.clickGuiScrollOffsets.put(selectedModule.name(), detailScrollOffset);
        applyDetailScrollPositions();
    }

    private void applyDetailScrollPositions() {
        detailScrollOffset = clamp(detailScrollOffset, 0, maxDetailScroll());
        int top = detailControlsTop();
        int bottom = detailControlsBottom();

        for (ScrollableControl control : detailControls) {
            AbstractWidget widget = control.widget();
            int y = control.baseY() - detailScrollOffset;
            widget.setY(y);

            boolean visible = y >= top && y + widget.getHeight() <= bottom;
            widget.visible = visible;
            if (!visible && widget.isFocused()) {
                widget.setFocused(false);
            }
        }
    }

    private int maxDetailScroll() {
        return Math.max(0, detailContentHeight - detailControlsHeight());
    }

    private void saveConfig() {
        flushTextFields();
        rememberCurrentUiState();
        BetterUCConfig.save();
    }

    private void flushTextFields() {
        for (Runnable flusher : textFieldFlushers) {
            flusher.run();
        }
    }

    private void refreshWidgets() {
        rebuildWidgets(true);
    }

    private void refreshWidgetsWithoutFlushingTextFields() {
        rebuildWidgets(false);
    }

    private void rebuildWidgets(boolean flushTextFields) {
        if (flushTextFields) {
            flushTextFields();
        }
        clearWidgets();
        rebuildingWidgets = true;
        try {
            init();
        } finally {
            rebuildingWidgets = false;
        }
    }

    private int mainW() {
        return Math.max(360, Math.min(width - 20, 760));
    }

    private int mainH() {
        return Math.max(220, Math.min(height - 20, 430));
    }

    private int mainX() {
        return width / 2 - mainW() / 2;
    }

    private int mainY() {
        return height / 2 - mainH() / 2;
    }

    private int sidebarW() {
        return Math.max(128, Math.min(150, mainW() / 4));
    }

    private int detailX() {
        return mainX() + sidebarW() + 10;
    }

    private int detailY() {
        return mainY() + 42;
    }

    private int detailW() {
        return mainW() - sidebarW() - 22;
    }

    private int detailH() {
        return mainH() - 78;
    }

    private int detailControlsTop() {
        return detailY() + 58;
    }

    private int detailControlsBottom() {
        return detailY() + detailH() - 10;
    }

    private int detailControlsHeight() {
        return Math.max(1, detailControlsBottom() - detailControlsTop());
    }

    private int moduleListY() {
        return mainY() + 66;
    }

    private int moduleListBottom() {
        return mainY() + mainH() - 34;
    }

    private int moduleScrollbarX() {
        return mainX() + sidebarW() - 7;
    }

    private void drawSoftRect(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private boolean inBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private int sliderIntValue(double value, int max) {
        return clamp((int) (value * max), 0, max);
    }

    private int sliderRangeIntValue(double value, int min, int max) {
        return clamp(min + (int) Math.round(clamp01(value) * (max - min)), min, max);
    }

    private double sliderDoubleValue(double value, double min, double max) {
        return min + clamp01(value) * (max - min);
    }

    private String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private int parseHexColor(String value, int fallback) {
        String raw = value == null ? "" : value.trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        }
        try {
            return 0xFF000000 | Integer.parseInt(raw, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0x00FFFFFF);
    }

    private enum Category {
        HUD("HUD", 0xFF38BDF8),
        GAMEPLAY("Client", 0xFFA78BFA),
        TOOLS("Tools", 0xFFFBBF24);

        private final String label;
        private final int accent;

        Category(String label, int accent) {
            this.label = label;
            this.accent = accent;
        }
    }

    private enum ModuleOption {
        HUD_PROFILES(Category.HUD, "HUD-Profile", "Eigene Layouts speichern", 0xFF38BDF8, false),
        HEALTH(Category.HUD, "Health", "Transparentes Herz-HUD", 0xFFFF5555, true),
        FPS(Category.HUD, "FPS", "Performance-Modul", BetterUCConfig.DEFAULT_FPS_HUD_COLOR, true),
        DATE_TIME(Category.HUD, "Datum & Uhrzeit", "Lokale Zeit im HUD", BetterUCConfig.DEFAULT_DATE_TIME_HUD_COLOR, true),
        PAYDAY(Category.HUD, "Payday", "Payday-Fortschritt", BetterUCConfig.DEFAULT_PAYDAY_HUD_COLOR, true),
        AMMO(Category.HUD, "Ammo", "Munition und Waffe", 0xFFFFAA33, true),
        ARMOR(Category.HUD, "Armor", "Rüstung und Haltbarkeit", BetterUCConfig.DEFAULT_ARMOR_HUD_COLOR, true),
        BANK(Category.HUD, "Bank", "Kontostand im HUD", BetterUCConfig.DEFAULT_BANK_HUD_COLOR, true),
        CASH(Category.HUD, "Bargeld", "Geld & Bargeldbestand", BetterUCConfig.DEFAULT_CASH_HUD_COLOR, true),
        POTION(Category.HUD, "Potion", "Aktive Effekte", 0xFF9328FF, true),
        SPRINT(Category.HUD, "Sprint", "ToggleSprint Anzeige", BetterUCConfig.DEFAULT_TOGGLE_SPRINT_HUD_COLOR, true),
        HACK_TIMER(Category.HUD, "Hack Timer", "Timer-Position", 0xFF60A5FA, false),
        DEALER_TIMER(Category.HUD, "Dealer Timer", "Drogenverkauf Cooldown", 0xFFD946EF, true),
        MASK_TIMER(Category.HUD, "Masken Timer", "Maskierung & Ablaufwarnung", 0xFF22D3EE, true),
        PRODUCTION_TIMER(Category.HUD, "Produktion", "Fabrik-Produktion & Navi", 0xFFFBBF24, true),
        PLANT_TIMER(Category.HUD, "Plant Timer", "Plantage-Timer", 0xFF6CF27D, true),

        ZOOM(Category.GAMEPLAY, "Zoom", "Weicher, frei einstellbarer Kamera-Zoom", 0xFF60A5FA, true),
        AUTO_STATS(Category.GAMEPLAY, "Auto Stats", "Automatisches /stats", 0xFF34D399, true),
        AUTOMATIONS(Category.GAMEPLAY, "Automationen", "Job-Helfer einzeln steuern", 0xFFFBBF24, false),
        CHAT(Category.GAMEPLAY, "Chat", "Zeitstempel & Customization", 0xFF38BDF8, false),
        SCREENSHOTS(Category.GAMEPLAY, "Screenshots", "Kopieren, teilen & verwalten", 0xFF38BDF8, true),
        CONNECTION(Category.GAMEPLAY, "Verbindung", "Account & Relay", 0xFF38BDF8, false),
        CLOUD_SYNC(Category.GAMEPLAY, "Cloud Sync", "Synchronisierte Einstellungen", 0xFF22D3EE, true),

        BLACKLIST(Category.TOOLS, "Blacklist", "Gründe und Sync", 0xFFF97316, false),
        PING(Category.TOOLS, "Ping", "Private Mod-Pings", 0xFF38BDF8, true),
        COMMANDS(Category.TOOLS, "Commands", "Command Menu", 0xFF22C55E, false),
        TRASH_FILTER(Category.TOOLS, "Mülleimer Filter", "Fundstücke hervorheben", 0xFF4ADE80, true),
        DISCORD(Category.TOOLS, "Discord", "Community Invite", 0xFF5865F2, false),
        UPDATES(Category.TOOLS, "Updates", "Changelog und neue Features", 0xFF38BDF8, false);

        private final Category category;
        private final String label;
        private final String description;
        private final int accent;
        private final boolean toggle;

        ModuleOption(Category category, String label, String description, int accent, boolean toggle) {
            this.category = category;
            this.label = label;
            this.description = description;
            this.accent = accent;
            this.toggle = toggle;
        }

        private boolean hasToggle() {
            return toggle;
        }

        private boolean hasHudStyle() {
            return (category == Category.HUD && this != HUD_PROFILES) || this == PING;
        }
    }

    private record ScrollableControl(AbstractWidget widget, int baseY) {
    }

    private record ControlTooltip(AbstractWidget widget, String text) {
    }

    private record DetailSectionHeader(String label, int x, int baseY, int sectionWidth, int color) {
    }

}
