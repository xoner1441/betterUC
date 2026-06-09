package com.betteruc.gui;

import com.betteruc.BetterUCMod;
import com.betteruc.client.BetterUCFontManager;
import com.betteruc.client.CommunicationDeviceTracker;
import com.betteruc.client.PingRelayClient;
import com.betteruc.client.SyncRefreshActions;
import com.betteruc.client.UserStatsClient;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.CashHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.ModernHudRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

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
    private static final UpdateSection[] UPDATE_SECTIONS = new UpdateSection[]{
            new UpdateSection("Neu in 1.1.9", new String[]{
                    "bUC-Tablist-Badge wird jetzt als eigenes Overlay gerendert",
                    "Andere Client-Icons wie Unique, LabyMod oder Badlion bleiben in der Tablist sichtbar",
                    "HUD-Farbverlauf ist jetzt pro HUD einzeln einstellbar",
                    "Bargeld-HUD erkennt Einzahlungen und Auszahlungen an der Fraktionsbank",
                    "Access-Code und Relay-Felder speichern zuverlaessiger beim Wechseln im ClickGUI",
                    "Stats-Filter unterdrueckt Detailzeilen wie Immobilien sauberer",
                    "Normale User bekommen nur noch das bUC-Tablist-Badge, Rollen behalten ihr Hologramm"
            }),
            new UpdateSection("Kurzstart", new String[]{
                    "Standard: N öffnet das betterUC ClickGUI",
                    "Standard: M öffnet das Command Menu",
                    "Keybinds sind in den Minecraft-Steuerungen änderbar",
                    "Ping-Key tippen: Normal-Ping setzen",
                    "Ping-Key halten: Pingrad öffnen und Typ wählen"
            }),
            new UpdateSection("HUD & Design", new String[]{
                    "Health, FPS, Payday, Ammo, Bank, Bargeld, Potion, Sprint, Hack und Plant einzeln einstellbar",
                    "HUD Vorschau verschiebt und skaliert aktive HUDs per Maus",
                    "Stile: Modern, Transparent, Cartoon und Custom",
                    "Custom Fonts pro HUD-Modul auswählbar"
            }),
            new UpdateSection("Ping-System", new String[]{
                    "Global: alle verbundenen Mod-User sehen den Ping",
                    "Fraktion: nur Spieler deiner getrackten Fraktion sehen den Ping",
                    "Normale, Gefahr- und Sammel-Pings haben eigene Farben",
                    "Ping-Ton und Ping-Anzeige können ein- und ausgeschaltet werden"
            }),
            new UpdateSection("Accounts & Website", new String[]{
                    "Access Code verbindet die Mod mit dem betterUC Relay",
                    "/register <passwort> legt dein Userpanel-Passwort fest",
                    "Userpanel zeigt Bank, Bargeld, Häuser, Spielzeit, Warns und Fraktion",
                    "Adminpanel verwaltet Codes, Rollen und Spielerdaten",
                    "Admin bleibt die einzige Rolle mit Adminpanel-Rechten",
                    "Discord-Bereich öffnet oder kopiert den Community-Invite"
            }),
            new UpdateSection("Commandliste", new String[]{
                    "/blset <spieler> <grund> oder /setbl setzt einen Blacklist-Eintrag",
                    "/modbl <spieler> <grund> erweitert einen bestehenden Blacklist-Eintrag",
                    "/blinfo <spieler> zeigt gespeicherte Blacklist-Infos",
                    "/setrp <spieler> <1-3> setzt die RP-Stufe"
            }),
            new UpdateSection("Komfort", new String[]{
                    "Auto-Stats aktualisiert HUD- und Userpanel-Daten nach Join und AFK",
                    "Stats-Ausgaben werden im Chat möglichst sauber unterdrückt",
                    "Update Notify prüft GitHub und meldet neue Versionen direkt im Chat",
                    "Hotkey Commands können im Tools-Bereich frei angelegt werden"
            })
    };

    private Category selectedCategory = Category.HUD;
    private ModuleOption selectedModule = ModuleOption.FPS;
    private final List<ScrollableControl> detailControls = new ArrayList<>();
    private final List<Runnable> textFieldFlushers = new ArrayList<>();
    private boolean capturingZoomKey = false;
    private boolean rebuildingWidgets = false;
    private int detailScrollOffset = 0;
    private int detailContentHeight = 0;

    public BetterUCScreen() {
        super(Text.literal("betterUC"));
    }

    @Override
    protected void init() {
        if (!rebuildingWidgets) {
            flushTextFields();
        }
        textFieldFlushers.clear();
        if (selectedModule.category != selectedCategory) {
            selectedModule = firstModuleFor(selectedCategory);
            detailScrollOffset = 0;
        }

        detailControls.clear();
        detailContentHeight = 0;
        addDetailControls();
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD Vorschau"), b -> openScreen(new HudLayoutScreen(this)))
                .dimensions(mainX() + 12, mainY() + mainH() - 28, 118, BUTTON_H)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Speichern & Schließen"), b -> {
            saveConfig();
            close();
        }).dimensions(mainX() + mainW() - 150, mainY() + mainH() - 28, 138, BUTTON_H).build());
    }

    private void addDetailControls() {
        int x = detailX() + 14;
        int startY = detailControlsTop();
        int y = startY;
        int controlW = Math.max(120, Math.min(194, detailW() - 28));

        if (selectedModule.hasHudStyle()) {
            y = addHudStyleButton(x, y, controlW, selectedModule);
            if (BetterUCConfig.isCustomHudStyle(getHudStyle(selectedModule))) {
                y = addCustomFontControls(x, y, controlW, selectedModule);
            }
        }
        if (selectedModule.category == Category.HUD) {
            y = addHudGradientControls(x, y, controlW, selectedModule);
        }

        switch (selectedModule) {
            case HEALTH -> {
                y = addToggle(x, y, controlW, "Health HUD", BetterUCConfig.INSTANCE.showHealthHud,
                        () -> BetterUCConfig.INSTANCE.showHealthHud = !BetterUCConfig.INSTANCE.showHealthHud);
                y = addColorButton(x, y, controlW, "Herz Farbe", BetterUCConfig.INSTANCE.healthHudHeartColor,
                        color -> BetterUCConfig.INSTANCE.healthHudHeartColor = color);
                addColorButton(x, y, controlW, "Zahl Farbe", BetterUCConfig.INSTANCE.healthHudTextColor,
                        color -> BetterUCConfig.INSTANCE.healthHudTextColor = color);
            }
            case FPS -> {
                y = addToggle(x, y, controlW, "FPS HUD", BetterUCConfig.INSTANCE.showFpsHud,
                        () -> BetterUCConfig.INSTANCE.showFpsHud = !BetterUCConfig.INSTANCE.showFpsHud);
                addColorButton(x, y, controlW, "FPS Farbe", BetterUCConfig.INSTANCE.fpsHudColor,
                        color -> BetterUCConfig.INSTANCE.fpsHudColor = color);
            }
            case PAYDAY -> {
                y = addToggle(x, y, controlW, "Payday HUD", BetterUCConfig.INSTANCE.showPaydayHud,
                        () -> BetterUCConfig.INSTANCE.showPaydayHud = !BetterUCConfig.INSTANCE.showPaydayHud);
                addColorButton(x, y, controlW, "Payday Farbe", BetterUCConfig.INSTANCE.paydayHudColor,
                        color -> BetterUCConfig.INSTANCE.paydayHudColor = color);
            }
            case AMMO -> {
                y = addToggle(x, y, controlW, "Ammo HUD", BetterUCConfig.INSTANCE.showAmmoHud,
                        () -> BetterUCConfig.INSTANCE.showAmmoHud = !BetterUCConfig.INSTANCE.showAmmoHud);
            }
            case BANK -> {
                y = addToggle(x, y, controlW, "Bank HUD", BetterUCConfig.INSTANCE.showBankHud,
                        () -> BetterUCConfig.INSTANCE.showBankHud = !BetterUCConfig.INSTANCE.showBankHud);
                addColorButton(x, y, controlW, "Bank Farbe", BetterUCConfig.INSTANCE.bankHudColor,
                        color -> BetterUCConfig.INSTANCE.bankHudColor = color);
            }
            case CASH -> {
                y = addToggle(x, y, controlW, "Bargeld HUD", BetterUCConfig.INSTANCE.showCashHud,
                        () -> BetterUCConfig.INSTANCE.showCashHud = !BetterUCConfig.INSTANCE.showCashHud);
                addColorButton(x, y, controlW, "Bargeld Farbe", BetterUCConfig.INSTANCE.cashHudColor,
                        color -> BetterUCConfig.INSTANCE.cashHudColor = color);
            }
            case POTION -> {
                y = addToggle(x, y, controlW, "Potion HUD", BetterUCConfig.INSTANCE.showPotionEffectsHud,
                        () -> BetterUCConfig.INSTANCE.showPotionEffectsHud = !BetterUCConfig.INSTANCE.showPotionEffectsHud);
            }
            case SPRINT -> {
                y = addToggle(x, y, controlW, "ToggleSprint", BetterUCConfig.INSTANCE.toggleSprintEnabled,
                        () -> BetterUCConfig.INSTANCE.toggleSprintEnabled = !BetterUCConfig.INSTANCE.toggleSprintEnabled);
                addColorButton(x, y, controlW, "Sprint Farbe", BetterUCConfig.INSTANCE.toggleSprintHudColor,
                        color -> BetterUCConfig.INSTANCE.toggleSprintHudColor = color);
            }
            case HACK_TIMER, PLANT_TIMER -> {
                if (selectedModule == ModuleOption.PLANT_TIMER) {
                    y = addToggle(x, y, controlW, "Plant HUD", BetterUCConfig.INSTANCE.showPlantTimerHud,
                            () -> BetterUCConfig.INSTANCE.showPlantTimerHud = !BetterUCConfig.INSTANCE.showPlantTimerHud);
                }
            }
            case ZOOM -> {
                y = addToggle(x, y, controlW, "Zoom", BetterUCConfig.INSTANCE.zoomEnabled,
                        () -> BetterUCConfig.INSTANCE.zoomEnabled = !BetterUCConfig.INSTANCE.zoomEnabled);
                y = addButton(x, y, controlW, zoomKeyLabel(), b -> {
                    capturingZoomKey = true;
                    refreshWidgets();
                });
                y = addDoubleSlider(x, y, controlW, "Zoom FOV", BetterUCConfig.INSTANCE.zoomFovMultiplier, 0.05, 0.80,
                        value -> BetterUCConfig.INSTANCE.zoomFovMultiplier = (float) value);
            }
            case AUTO_STATS -> {
                y = addToggle(x, y, controlW, "Auto-Stats Join", BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled,
                        () -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled = !BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled);
                y = addButton(x, y, controlW, "Stats neu laden", b -> SyncRefreshActions.requestStatsRefresh(client, true));
            }
            case CHAT -> {
                y = addToggle(x, y, controlW, "Chat-Zeit", BetterUCConfig.INSTANCE.chatTimestampsEnabled,
                        () -> BetterUCConfig.INSTANCE.chatTimestampsEnabled = !BetterUCConfig.INSTANCE.chatTimestampsEnabled);
                y = addTimestampField(x, y, controlW);
            }
            case CONNECTION -> y = addConnectionControls(x, y, controlW);
            case BLACKLIST -> {
                y = addButton(x, y, controlW, "Blacklist Gründe", b -> openScreen(new BlacklistConfigScreen(this)));
                y = addButton(x, y, controlW, "Stats neu laden", b -> SyncRefreshActions.requestStatsRefresh(client, true));
            }
            case PING -> {
                y = addPingControls(x, y, controlW);
            }
            case HOTKEYS -> y = addButton(x, y, controlW, "Hotkey Commands", b -> openScreen(new HotkeyCommandsScreen(this)));
            case COMMANDS -> y = addButton(x, y, controlW, "Command Menu", b -> openScreen(new CommandGui()));
            case DISCORD -> y = addDiscordControls(x, y, controlW);
            case UPDATES -> {
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
            BetterUCFontManager.rebuildAndReload(client);
            refreshWidgets();
        });
        y = addButton(x, y, width, "Fonts neu laden", b -> {
            BetterUCFontManager.rebuildAndReload(client);
            refreshWidgets();
        });
        return addButton(x, y, width, "Font Ordner", b -> BetterUCFontManager.openFontsFolder(client));
    }

    private int addPingControls(int x, int y, int width) {
        y = addToggle(x, y, width, "Ping System", BetterUCConfig.INSTANCE.pingRelayEnabled,
                () -> BetterUCConfig.INSTANCE.pingRelayEnabled = !BetterUCConfig.INSTANCE.pingRelayEnabled);
        y = addToggle(x, y, width, "Ping Anzeige", BetterUCConfig.INSTANCE.showPingHud,
                () -> BetterUCConfig.INSTANCE.showPingHud = !BetterUCConfig.INSTANCE.showPingHud);
        y = addButton(x, y, width, pingScopeLabel(), b -> {
            BetterUCConfig.INSTANCE.pingRelayScope = "faction".equals(BetterUCConfig.INSTANCE.pingRelayScope)
                    ? "global"
                    : "faction";
            saveConfig();
            refreshWidgets();
        });
        y = addDoubleSlider(x, y, width, "Ping Größe", BetterUCConfig.INSTANCE.pingHudScale,
                BetterUCConfig.MIN_HUD_SCALE, BetterUCConfig.MAX_HUD_SCALE,
                value -> BetterUCConfig.INSTANCE.pingHudScale = (float) value);
        y = addRangeIntSlider(x, y, width, "Sichtweite", BetterUCConfig.INSTANCE.pingRelayMaxDistance, 0, 128,
                value -> BetterUCConfig.INSTANCE.pingRelayMaxDistance = Math.max(0, value));
        y = addToggle(x, y, width, "Ping Ton", BetterUCConfig.INSTANCE.pingSoundEnabled,
                () -> BetterUCConfig.INSTANCE.pingSoundEnabled = !BetterUCConfig.INSTANCE.pingSoundEnabled);
        y = addButton(x, y, width, "Sound: " + PingRelayClient.pingSoundLabel(BetterUCConfig.INSTANCE.pingSoundId), b -> {
            BetterUCConfig.INSTANCE.pingSoundId = PingRelayClient.nextPingSoundId(BetterUCConfig.INSTANCE.pingSoundId);
            saveConfig();
            refreshWidgets();
        });
        y = addRangeIntSlider(x, y, width, "Cooldown ms", BetterUCConfig.INSTANCE.pingCooldownMs, 500, 10000,
                value -> BetterUCConfig.INSTANCE.pingCooldownMs = Math.max(500, value));
        y = addColorButton(x, y, width, "Normal Farbe", parseHexColor(BetterUCConfig.INSTANCE.pingNormalColor, 0xFF38BDF8),
                color -> {
                    BetterUCConfig.INSTANCE.pingNormalColor = "#" + hex(color);
                    BetterUCConfig.INSTANCE.pingRelayColor = BetterUCConfig.INSTANCE.pingNormalColor;
                });
        y = addColorButton(x, y, width, "Gefahr Farbe", parseHexColor(BetterUCConfig.INSTANCE.pingDangerColor, 0xFFFF5555),
                color -> BetterUCConfig.INSTANCE.pingDangerColor = "#" + hex(color));
        y = addColorButton(x, y, width, "Sammeln Farbe", parseHexColor(BetterUCConfig.INSTANCE.pingGatherColor, 0xFF22C55E),
                color -> BetterUCConfig.INSTANCE.pingGatherColor = "#" + hex(color));
        return addButton(x, y, width, "Ping testen", b -> PingRelayClient.sendPingAtCrosshair(client, PingRelayClient.PingType.NORMAL));
    }

    private int addConnectionControls(int x, int y, int width) {
        y = addInfo(x, y, width, "Status", PingRelayClient.statusLabel());
        y = addInfo(x, y, width, "Spieler", currentPlayerName());
        y = addInfo(x, y, width, "Rolle", PingRelayClient.roleLabel());
        y = addInfo(x, y, width, "Fraktion", currentFactionLabel());
        y = addInfo(x, y, width, "Kommunikation", CommunicationDeviceTracker.statusLabel());
        y = addInfo(x, y, width, "Server", currentServerLabel());
        y = addInfo(x, y, width, "Version", MOD_VERSION);
        y = addTextField(x, y, width, "Access Code", BetterUCConfig.INSTANCE.pingRelayToken, 160,
                value -> BetterUCConfig.INSTANCE.pingRelayToken = value.trim());
        y = addButton(x, y, width, "Access Code holen", b -> Util.getOperatingSystem().open(URI.create("https://betteruc.de/access")));
        y = addButton(x, y, width, "Stats neu senden", b -> {
            UserStatsClient.uploadCurrentStats(client);
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("[betterUC] Stats werden ans Userpanel gesendet."), false);
            }
        });
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
            saveConfig();
            PingRelayClient.onDisconnect();
            PingRelayClient.tick(client);
            refreshWidgets();
        });
    }

    private int addDiscordControls(int x, int y, int width) {
        y = addInfo(x, y, width, "Discord", "betterUC Community");
        y = addButton(x, y, width, "Discord öffnen", b -> openDiscordInvite());
        return addButton(x, y, width, "Invite kopieren", b -> copyDiscordInvite());
    }

    private int addButton(int x, int y, int width, String label, ButtonWidget.PressAction action) {
        addScrollableControl(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, width, BUTTON_H)
                .build());
        return y + 24;
    }

    private int addInfo(int x, int y, int width, String label, String value) {
        ButtonWidget widget = ButtonWidget.builder(Text.literal(label + ": " + value), b -> {
        }).dimensions(x, y, width, BUTTON_H).build();
        widget.active = false;
        addScrollableControl(widget);
        return y + 24;
    }

    private int addColorButton(
            int x,
            int y,
            int width,
            String label,
            int color,
            ColorPickerScreen.ColorApplyTarget target
    ) {
        return addButton(x, y, width, label + " #" + hex(color), b -> openScreen(new ColorPickerScreen(
                this,
                label,
                label + " wählen",
                color,
                target
        )));
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

    private int addIntSlider(int x, int y, int width, String label, int current, int max, IntConsumer setter) {
        int safeMax = Math.max(1, max);
        int safeCurrent = clamp(current, 0, safeMax);
        addScrollableControl(new SliderWidget(
                x,
                y,
                width,
                BUTTON_H,
                Text.literal(label + ": " + safeCurrent),
                safeCurrent / (double) safeMax
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(label + ": " + sliderIntValue(value, safeMax)));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderIntValue(value, safeMax));
            }
        });
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
        addScrollableControl(new SliderWidget(
                x,
                y,
                width,
                BUTTON_H,
                Text.literal(label + ": " + safeCurrent),
                (safeCurrent - safeMin) / (double) (safeMax - safeMin)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(label + ": " + sliderRangeIntValue(value, safeMin, safeMax)));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderRangeIntValue(value, safeMin, safeMax));
            }
        });
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
        addScrollableControl(new SliderWidget(
                x,
                y,
                width,
                BUTTON_H,
                Text.literal(label + ": " + percent(current)),
                normalized
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.literal(label + ": " + percent(sliderDoubleValue(value, min, max))));
            }

            @Override
            protected void applyValue() {
                setter.accept(sliderDoubleValue(value, min, max));
            }
        });
        return y + 24;
    }

    private int addTimestampField(int x, int y, int width) {
        TextFieldWidget timestampField = new TextFieldWidget(
                textRenderer,
                x,
                y,
                width,
                BUTTON_H,
                Text.literal("Timestamp Format")
        );
        timestampField.setMaxLength(32);
        timestampField.setText(BetterUCConfig.INSTANCE.chatTimestampFormat);
        timestampField.setChangedListener(text -> BetterUCConfig.INSTANCE.chatTimestampFormat = text);
        textFieldFlushers.add(() -> BetterUCConfig.INSTANCE.chatTimestampFormat = timestampField.getText());
        addScrollableControl(timestampField);
        return y + 24;
    }

    private int addTextField(int x, int y, int width, String label, String current, int maxLength, Consumer<String> setter) {
        TextFieldWidget field = new TextFieldWidget(
                textRenderer,
                x,
                y,
                width,
                BUTTON_H,
                Text.literal(label)
        );
        field.setMaxLength(maxLength);
        field.setPlaceholder(Text.literal(label));
        field.setText(current == null ? "" : current);
        field.setChangedListener(setter::accept);
        textFieldFlushers.add(() -> setter.accept(field.getText()));
        addScrollableControl(field);
        return y + 24;
    }

    private <T extends ClickableWidget> T addScrollableControl(T widget) {
        addDrawableChild(widget);
        detailControls.add(new ScrollableControl(widget, widget.getY()));
        return widget;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x66000000);
        renderFrame(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        renderZoomCaptureHint(context);
    }

    private void renderFrame(DrawContext context, int mouseX, int mouseY) {
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
        context.drawTextWithShadow(textRenderer, Text.literal(title), titleX, titleY, TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("v" + MOD_VERSION),
                titleX + textRenderer.getWidth(title) + 7, titleY, TEXT_MUTED);

        renderCategoryTabs(context, mouseX, mouseY);
        renderModuleList(context, mouseX, mouseY);
        renderDetailPanel(context, mouseX, mouseY);
    }

    private void renderCategoryTabs(DrawContext context, int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = mainY() + 42;
        int tabW = Math.max(38, (sidebarW() - 20) / Category.values().length);

        for (Category category : Category.values()) {
            boolean selected = category == selectedCategory;
            boolean hovered = inBounds(mouseX, mouseY, x, y, tabW, 18);
            int color = selected ? withAlpha(category.accent, 0xCC) : hovered ? 0x80404A59 : 0x50333C49;
            drawSoftRect(context, x, y, tabW - 3, 18, color);
            drawBorder(context, x, y, tabW - 3, 18, selected ? withAlpha(category.accent, 0xFF) : 0x50333C49);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(category.label), x + (tabW - 3) / 2, y + 5,
                    selected ? TEXT_PRIMARY : TEXT_SOFT);
            x += tabW;
        }
    }

    private void renderModuleList(DrawContext context, int mouseX, int mouseY) {
        int x = mainX() + 10;
        int y = moduleListY();
        int w = sidebarW() - 20;

        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) continue;
            boolean selected = module == selectedModule;
            boolean hovered = inBounds(mouseX, mouseY, x, y, w, MODULE_H);
            int bg = selected ? 0xB82A3442 : hovered ? 0x80313A47 : 0x4D1F2631;
            drawSoftRect(context, x, y, w, MODULE_H, bg);
            context.fill(x + 2, y + 2, x + 4, y + MODULE_H - 2,
                    selected ? withAlpha(module.accent, 0xFF) : withAlpha(module.accent, 0x99));
            context.drawTextWithShadow(textRenderer, Text.literal(module.label), x + 8, y + 4,
                    selected ? TEXT_PRIMARY : TEXT_SOFT);

            String state = module.hasToggle() ? (isEnabled(module) ? "ON" : "OFF") : "SET";
            int stateColor = module.hasToggle() && !isEnabled(module) ? TEXT_MUTED : withAlpha(module.accent, 0xFF);
            context.drawTextWithShadow(textRenderer, Text.literal(state), x + w - textRenderer.getWidth(state) - 6, y + 4, stateColor);
            y += MODULE_H + MODULE_GAP;
        }
    }

    private void renderDetailPanel(DrawContext context, int mouseX, int mouseY) {
        int x = detailX();
        int y = detailY();
        int w = detailW();
        int h = detailH();
        drawSoftRect(context, x, y, w, h, PANEL_ALT);
        drawBorder(context, x, y, w, h, 0x70333C49);
        context.fill(x + 2, y + 2, x + 4, y + h - 2, withAlpha(selectedModule.accent, 0xEE));

        context.drawTextWithShadow(textRenderer, Text.literal(selectedModule.label), x + 14, y + 12, TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal(selectedModule.description), x + 14, y + 25, TEXT_MUTED);
        renderSelectedStatus(context, x + 14, y + 39);
        if (selectedModule == ModuleOption.UPDATES) {
            renderUpdates(context, x + 14, y + 58, w - 28, h - 72);
            return;
        }
        renderPreview(context, x + Math.max(224, w - 172), y + 58);
        renderDetailScrollbar(context);
    }

    private void renderDetailScrollbar(DrawContext context) {
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

    private void renderSelectedStatus(DrawContext context, int x, int y) {
        if (selectedModule == ModuleOption.UPDATES) {
            context.drawTextWithShadow(textRenderer, Text.literal("Version " + MOD_VERSION), x, y, 0xFF86EFAC);
            return;
        }

        if (selectedModule == ModuleOption.BLACKLIST) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Einträge: " + BetterUCConfig.INSTANCE.chatBlacklistPlayers.size()
                            + " | Sync " + syncAge(BetterUCConfig.INSTANCE.lastBlacklistSyncMs)),
                    x,
                    y,
                    TEXT_SOFT
            );
            return;
        }

        if (selectedModule == ModuleOption.CONNECTION) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Verbindung: " + PingRelayClient.statusLabel()),
                    x,
                    y,
                    PingRelayClient.isConnected() ? 0xFF86EFAC : TEXT_MUTED
            );
            return;
        }

        if (selectedModule.hasToggle()) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(isEnabled(selectedModule) ? "Status: aktiv" : "Status: aus"),
                    x,
                    y,
                    isEnabled(selectedModule) ? 0xFF86EFAC : TEXT_MUTED
            );
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Modul-Einstellungen"), x, y, TEXT_SOFT);
        }
    }

    private void renderPreview(DrawContext context, int x, int y) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        int previewX = Math.min(x, detailX() + detailW() - 150);
        int previewY = y;

        context.drawTextWithShadow(textRenderer, Text.literal("Preview"), previewX, previewY - 14, TEXT_MUTED);
        String style = getHudStyle(selectedModule);
        String font = getHudFont(selectedModule);
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        boolean stylizedStyle = BetterUCConfig.isStylizedHudStyle(style);
        ModernHudRenderer.withHudGradient(getHudGradientEnabled(selectedModule), getHudGradientColor(selectedModule), () -> {
        switch (selectedModule) {
            case HEALTH -> {
                if (modernStyle) {
                    ModernHudRenderer.drawPanel(context, previewX, previewY, 38, 17, BetterUCConfig.INSTANCE.healthHudHeartColor);
                    context.drawGuiTexture(
                            net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                            net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                            previewX + 7,
                            previewY + 4,
                            9,
                            9,
                            BetterUCConfig.INSTANCE.healthHudHeartColor
                    );
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "10", previewX + 19, previewY + 4,
                            BetterUCConfig.INSTANCE.healthHudTextColor);
                    return;
                }
                context.drawGuiTexture(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.util.Identifier.ofVanilla("hud/heart/full"),
                        previewX,
                        previewY,
                        9,
                        9,
                        BetterUCConfig.INSTANCE.healthHudHeartColor
                );
                if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, textRenderer, style, font, Text.literal("10"), previewX + 12, previewY,
                            BetterUCConfig.INSTANCE.healthHudTextColor);
                    return;
                }
                ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "10", previewX + 11, previewY,
                        BetterUCConfig.INSTANCE.healthHudTextColor);
            }
            case FPS -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "FPS", "144",
                            BetterUCConfig.INSTANCE.fpsHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "FPS: 144", previewX, previewY,
                            BetterUCConfig.INSTANCE.fpsHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "FPS: 144", previewX, previewY,
                            BetterUCConfig.INSTANCE.fpsHudColor);
                }
            }
            case PAYDAY -> {
                if (modernStyle) {
                    ModernHudRenderer.drawProgressModule(context, minecraft, previewX, previewY, "PAYDAY",
                            "25/60 min", 25.0F / 60.0F, BetterUCConfig.INSTANCE.paydayHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Payday: 25/60 Minuten", previewX, previewY,
                            BetterUCConfig.INSTANCE.paydayHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Payday: 25/60 Minuten", previewX, previewY,
                            BetterUCConfig.INSTANCE.paydayHudColor);
                }
            }
            case AMMO -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "AMMO", "20/96",
                            "TS19", 0xFFFFAA33, 0xFF7CFF8A);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "20/96", previewX, previewY, 0xFFFFAA33);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "TS19", previewX, previewY + 11, 0xFF55FF55);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "20/96", previewX, previewY, 0xFFFFAA33);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "TS19", previewX, previewY + 10, 0xFF55FF55);
                }
            }
            case BANK -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "BANK",
                            previewBankValue(), BetterUCConfig.INSTANCE.bankHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Bank: " + previewBankValue(), previewX, previewY,
                            BetterUCConfig.INSTANCE.bankHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Bank: " + previewBankValue(), previewX, previewY,
                            BetterUCConfig.INSTANCE.bankHudColor);
                }
            }
            case CASH -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "BARGELD",
                            previewCashValue(), BetterUCConfig.INSTANCE.cashHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Bargeld: " + previewCashValue(), previewX, previewY,
                            BetterUCConfig.INSTANCE.cashHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Bargeld: " + previewCashValue(), previewX, previewY,
                            BetterUCConfig.INSTANCE.cashHudColor);
                }
            }
            case POTION -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "EFFECT", "Stärke II",
                            "1:26", 0xFF9328FF);
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY + 33, "EFFECT", "Speed",
                            "0:49", 0xFF7CAFC6);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Stärke II", previewX, previewY, 0xFF9328FF);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "1:26", previewX, previewY + 11, TEXT_MUTED);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Speed", previewX, previewY + 25, 0xFF7CAFC6);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "0:49", previewX, previewY + 36, TEXT_MUTED);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Stärke II", previewX, previewY, 0xFF9328FF);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "1:26", previewX, previewY + 10, TEXT_MUTED);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Speed", previewX, previewY + 24, 0xFF7CAFC6);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "0:49", previewX, previewY + 34, TEXT_MUTED);
                }
            }
            case SPRINT -> {
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "SPRINT", "ON",
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "ToggleSprint: ON", previewX, previewY,
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "ToggleSprint: ON", previewX, previewY,
                            BetterUCConfig.INSTANCE.toggleSprintHudColor);
                }
            }
            case HACK_TIMER -> {
                String time = HackTimerHud.secondsRemaining > 0
                        ? String.format(Locale.ROOT, "%02d:%02d", HackTimerHud.secondsRemaining / 60, HackTimerHud.secondsRemaining % 60)
                        : "02:39";
                if (modernStyle) {
                    ModernHudRenderer.drawModule(context, minecraft, previewX, previewY, "HACK", time, 0xFF60A5FA);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Hack: " + time, previewX, previewY, 0xFF60A5FA);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Hack: " + time, previewX, previewY, 0xFF60A5FA);
                }
            }
            case PLANT_TIMER -> {
                if (modernStyle) {
                    ModernHudRenderer.drawTwoLineModule(context, minecraft, previewX, previewY, "PLANT",
                            "Plantage Pulver 7/10", "Reif: 1:30:00 | Wasser: 20:00", 0xFF6CF27D, 0xFFFFD866);
                } else if (stylizedStyle) {
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Plantage Pulver 7/10", previewX, previewY, 0xFF6CF27D);
                    ModernHudRenderer.drawStyledText(context, minecraft, style, font, "Reif: 1:30:00 | Wasser: 20:00", previewX, previewY + 11, 0xFFFFD866);
                } else {
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Plantage Pulver 7/10", previewX, previewY, 0xFF6CF27D);
                    ModernHudRenderer.drawHudTextWithShadow(context, textRenderer, "Reif: 1:30:00 | Wasser: 20:00", previewX, previewY + 10, 0xFFFFD866);
                }
            }
            case ZOOM -> drawMiniInfo(context, previewX, previewY, "Zoom", zoomKeyLabel(), BetterUCConfig.INSTANCE.zoomEnabled);
            case AUTO_STATS -> drawMiniInfo(context, previewX, previewY, "Auto-Stats", "Join /stats", BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled);
            case CHAT -> drawMiniInfo(context, previewX, previewY, "Chat",
                    BetterUCConfig.INSTANCE.chatTimestampsEnabled ? BetterUCConfig.INSTANCE.chatTimestampFormat : "AUS",
                    BetterUCConfig.INSTANCE.chatTimestampsEnabled);
            case CONNECTION -> drawMiniInfo(context, previewX, previewY, "Verbindung", PingRelayClient.statusLabel(),
                    PingRelayClient.isConnected());
            case BLACKLIST -> drawMiniInfo(context, previewX, previewY, "Blacklist",
                    BetterUCConfig.INSTANCE.chatBlacklistPlayers.size() + " Spieler", true);
            case PING -> drawMiniInfo(context, previewX, previewY, "Ping System",
                    BetterUCConfig.INSTANCE.pingRelayEnabled ? "Aktiv" : "Aus",
                    BetterUCConfig.INSTANCE.pingRelayEnabled);
            case HOTKEYS -> drawMiniInfo(context, previewX, previewY, "Hotkeys",
                    BetterUCConfig.INSTANCE.hotkeyCommands.size() + " Commands", true);
            case COMMANDS -> drawMiniInfo(context, previewX, previewY, "Tools", "Command Menu", true);
            case DISCORD -> drawMiniInfo(context, previewX, previewY, "Discord", "Invite öffnen", true);
            case UPDATES -> {
            }
        }
        });
    }

    private void renderUpdates(DrawContext context, int x, int y, int width, int height) {
        int maxY = y + height;
        if (width < 420) {
            int currentY = y;
            for (UpdateSection section : UPDATE_SECTIONS) {
                if (currentY + 16 > maxY) break;
                currentY = drawUpdateSection(context, section, x, currentY, width, maxY);
            }
            return;
        }

        int columnGap = 20;
        int columnWidth = Math.max(160, (width - columnGap) / 2);
        int[] columnY = {y, y};

        for (int i = 0; i < UPDATE_SECTIONS.length; i++) {
            int column = i % 2;
            int sectionX = x + column * (columnWidth + columnGap);
            int sectionY = columnY[column];
            if (sectionY + 16 > maxY) break;

            columnY[column] = drawUpdateSection(context, UPDATE_SECTIONS[i], sectionX, sectionY, columnWidth, maxY);
        }
    }

    private int drawUpdateSection(DrawContext context, UpdateSection section, int x, int y, int width, int maxY) {
        context.drawTextWithShadow(textRenderer, Text.literal(section.title), x, y, withAlpha(selectedModule.accent, 0xFF));

        int lineY = y + 16;
        for (String line : section.lines) {
            if (lineY + 10 > maxY) break;
            lineY = drawWrappedUpdateLine(context, line, x, lineY, width);
        }
        return lineY + 10;
    }

    private int drawWrappedUpdateLine(DrawContext context, String line, int x, int y, int maxWidth) {
        String prefix = "- ";
        String remaining = line;
        boolean firstLine = true;
        int currentY = y;
        while (!remaining.isEmpty()) {
            String usedPrefix = firstLine ? prefix : "  ";
            int availableWidth = Math.max(20, maxWidth - textRenderer.getWidth(usedPrefix));
            String part = takeFittingText(remaining, availableWidth);
            context.drawTextWithShadow(textRenderer, Text.literal(usedPrefix + part), x, currentY, firstLine ? TEXT_SOFT : TEXT_MUTED);
            remaining = remaining.substring(part.length()).trim();
            currentY += 11;
            firstLine = false;
        }
        return currentY + 1;
    }

    private void drawMiniInfo(DrawContext context, int x, int y, String label, String value, boolean active) {
        int w = Math.max(110, Math.max(textRenderer.getWidth(label), textRenderer.getWidth(value)) + 22);
        ModernHudRenderer.drawPanel(context, x, y, w, 38, active ? selectedModule.accent : 0xFF64748B);
        context.drawTextWithShadow(textRenderer, Text.literal(label), x + 10, y + 8, withAlpha(selectedModule.accent, 0xFF));
        context.drawTextWithShadow(textRenderer, Text.literal(value), x + 10, y + 20, TEXT_PRIMARY);
    }

    private void renderZoomCaptureHint(DrawContext context) {
        if (!capturingZoomKey) return;
        int w = Math.min(320, width - 28);
        int h = 58;
        int x = width / 2 - w / 2;
        int y = height / 2 - h / 2;
        drawSoftRect(context, x, y, w, h, 0xF0101318);
        drawBorder(context, x, y, w, h, withAlpha(ModuleOption.ZOOM.accent, 0xFF));
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Drücke eine Taste für Zoom"), width / 2, y + 17, TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("ESC = Abbrechen"), width / 2, y + 33, TEXT_MUTED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;

        double mouseX = event.x();
        double mouseY = event.y();

        Category category = categoryAt(mouseX, mouseY);
        if (category != null) {
            selectedCategory = category;
            selectedModule = firstModuleFor(category);
            detailScrollOffset = 0;
            refreshWidgets();
            return true;
        }

        ModuleOption module = moduleAt(mouseX, mouseY);
        if (module != null) {
            selectedCategory = module.category;
            selectedModule = module;
            detailScrollOffset = 0;
            refreshWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.getKeycode();
        if (!capturingZoomKey) {
            return super.keyPressed(input);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            capturingZoomKey = false;
            refreshWidgets();
            return true;
        }

        BetterUCConfig.INSTANCE.zoomKeyCode = keyCode;
        saveConfig();
        capturingZoomKey = false;
        refreshWidgets();
        return true;
    }

    @Override
    public void removed() {
        saveConfig();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
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
        for (ModuleOption module : ModuleOption.values()) {
            if (module.category != selectedCategory) continue;
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

    private boolean isEnabled(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.showHealthHud;
            case FPS -> BetterUCConfig.INSTANCE.showFpsHud;
            case PAYDAY -> BetterUCConfig.INSTANCE.showPaydayHud;
            case AMMO -> BetterUCConfig.INSTANCE.showAmmoHud;
            case BANK -> BetterUCConfig.INSTANCE.showBankHud;
            case CASH -> BetterUCConfig.INSTANCE.showCashHud;
            case POTION -> BetterUCConfig.INSTANCE.showPotionEffectsHud;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.showPlantTimerHud;
            case ZOOM -> BetterUCConfig.INSTANCE.zoomEnabled;
            case AUTO_STATS -> BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled;
            case PING -> BetterUCConfig.INSTANCE.pingRelayEnabled;
            default -> true;
        };
    }

    private String getHudStyle(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle;
            case CASH -> BetterUCConfig.INSTANCE.cashHudStyle;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle;
            case PING -> BetterUCConfig.INSTANCE.pingHudStyle;
            default -> BetterUCConfig.HUD_STYLE_MODERN;
        };
    }

    private void setHudStyle(ModuleOption module, String style) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudStyle = style;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudStyle = style;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudStyle = style;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudStyle = style;
            case BANK -> BetterUCConfig.INSTANCE.bankHudStyle = style;
            case CASH -> BetterUCConfig.INSTANCE.cashHudStyle = style;
            case POTION -> BetterUCConfig.INSTANCE.potionHudStyle = style;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudStyle = style;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudStyle = style;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudStyle = style;
            case PING -> BetterUCConfig.INSTANCE.pingHudStyle = style;
            default -> {
            }
        }
    }

    private String getHudFont(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont;
            case CASH -> BetterUCConfig.INSTANCE.cashHudCustomFont;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont;
            case PING -> BetterUCConfig.INSTANCE.pingHudCustomFont;
            default -> BetterUCConfig.INSTANCE.customHudFont;
        };
    }

    private void setHudFont(ModuleOption module, String fontId) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudCustomFont = fontId;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudCustomFont = fontId;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudCustomFont = fontId;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudCustomFont = fontId;
            case BANK -> BetterUCConfig.INSTANCE.bankHudCustomFont = fontId;
            case CASH -> BetterUCConfig.INSTANCE.cashHudCustomFont = fontId;
            case POTION -> BetterUCConfig.INSTANCE.potionHudCustomFont = fontId;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudCustomFont = fontId;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudCustomFont = fontId;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudCustomFont = fontId;
            case PING -> BetterUCConfig.INSTANCE.pingHudCustomFont = fontId;
            default -> BetterUCConfig.INSTANCE.customHudFont = fontId;
        }
    }

    private boolean getHudGradientEnabled(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientEnabled;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientEnabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientEnabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientEnabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientEnabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientEnabled;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientEnabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientEnabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientEnabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientEnabled;
            default -> false;
        };
    }

    private void setHudGradientEnabled(ModuleOption module, boolean enabled) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientEnabled = enabled;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientEnabled = enabled;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientEnabled = enabled;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientEnabled = enabled;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientEnabled = enabled;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientEnabled = enabled;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientEnabled = enabled;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientEnabled = enabled;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientEnabled = enabled;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientEnabled = enabled;
            default -> {
            }
        }
    }

    private int getHudGradientColor(ModuleOption module) {
        return switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientColor;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientColor;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientColor;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientColor;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientColor;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientColor;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientColor;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientColor;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientColor;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientColor;
            default -> BetterUCConfig.DEFAULT_HUD_GRADIENT_COLOR;
        };
    }

    private void setHudGradientColor(ModuleOption module, int color) {
        switch (module) {
            case HEALTH -> BetterUCConfig.INSTANCE.healthHudGradientColor = color;
            case FPS -> BetterUCConfig.INSTANCE.fpsHudGradientColor = color;
            case PAYDAY -> BetterUCConfig.INSTANCE.paydayHudGradientColor = color;
            case AMMO -> BetterUCConfig.INSTANCE.ammoHudGradientColor = color;
            case BANK -> BetterUCConfig.INSTANCE.bankHudGradientColor = color;
            case CASH -> BetterUCConfig.INSTANCE.cashHudGradientColor = color;
            case POTION -> BetterUCConfig.INSTANCE.potionHudGradientColor = color;
            case SPRINT -> BetterUCConfig.INSTANCE.toggleSprintHudGradientColor = color;
            case HACK_TIMER -> BetterUCConfig.INSTANCE.hackTimerHudGradientColor = color;
            case PLANT_TIMER -> BetterUCConfig.INSTANCE.plantTimerHudGradientColor = color;
            default -> {
            }
        }
    }

    private String previewBankValue() {
        int live = BankBalanceHud.getCurrentBankBalance();
        return live >= 0 ? BankBalanceHud.formatMoney(live) + "$" : BankBalanceHud.formatMoney(88375) + "$";
    }

    private String previewCashValue() {
        int live = CashHud.getCurrentCash();
        return live >= 0 ? CashHud.formatMoney(live) + "$" : CashHud.formatMoney(1278) + "$";
    }

    private String zoomKeyLabel() {
        if (capturingZoomKey) return "Zoom Taste: ...";
        int code = BetterUCConfig.INSTANCE.zoomKeyCode;
        if (code <= 0) return "Zoom Taste: none";
        try {
            return "Zoom Taste: " + InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
        } catch (Exception ignored) {
            return "Zoom Taste: " + code;
        }
    }

    private String pingScopeLabel() {
        return "Ping Ziel: " + ("faction".equals(BetterUCConfig.INSTANCE.pingRelayScope) ? "Fraktion" : "Global");
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
        if (client == null || client.player == null) return "nicht erkannt";
        String name = client.player.getName().getString();
        return name == null || name.isBlank() ? "nicht erkannt" : name;
    }

    private String currentServerLabel() {
        String server = PingRelayClient.currentServerId(client);
        return server == null || server.isBlank() ? "nicht erkannt" : server;
    }

    private void openDiscordInvite() {
        String invite = safeDiscordInvite();
        try {
            Util.getOperatingSystem().open(URI.create(invite));
        } catch (Exception e) {
            BetterUCMod.LOGGER.warn("Could not open betterUC Discord invite {}", invite, e);
            copyDiscordInvite();
        }
    }

    private void copyDiscordInvite() {
        String invite = safeDiscordInvite();
        if (client != null) {
            client.keyboard.setClipboard(invite);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[betterUC] Discord-Invite kopiert: " + invite), false);
            }
        }
    }

    private String safeDiscordInvite() {
        return BetterUCConfig.DEFAULT_DISCORD_INVITE_URL;
    }

    private String takeFittingText(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;

        int lastSpace = -1;
        for (int i = 1; i <= text.length(); i++) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) {
                lastSpace = i - 1;
            }
            String candidate = text.substring(0, i).trim();
            if (textRenderer.getWidth(candidate) > maxWidth) {
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
        if (client != null) {
            client.setScreen(screen);
        }
    }

    private void setDetailScrollOffset(int scrollOffset) {
        detailScrollOffset = clamp(scrollOffset, 0, maxDetailScroll());
        applyDetailScrollPositions();
    }

    private void applyDetailScrollPositions() {
        detailScrollOffset = clamp(detailScrollOffset, 0, maxDetailScroll());
        int top = detailControlsTop();
        int bottom = detailControlsBottom();

        for (ScrollableControl control : detailControls) {
            ClickableWidget widget = control.widget();
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
        clearChildren();
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

    private void drawSoftRect(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
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
        HEALTH(Category.HUD, "Health", "Transparentes Herz-HUD", 0xFFFF5555, true),
        FPS(Category.HUD, "FPS", "Performance-Modul", BetterUCConfig.DEFAULT_FPS_HUD_COLOR, true),
        PAYDAY(Category.HUD, "Payday", "Payday-Fortschritt", BetterUCConfig.DEFAULT_PAYDAY_HUD_COLOR, true),
        AMMO(Category.HUD, "Ammo", "Munition und Waffe", 0xFFFFAA33, true),
        BANK(Category.HUD, "Bank", "Kontostand im HUD", BetterUCConfig.DEFAULT_BANK_HUD_COLOR, true),
        CASH(Category.HUD, "Bargeld", "Geld & Bargeldbestand", BetterUCConfig.DEFAULT_CASH_HUD_COLOR, true),
        POTION(Category.HUD, "Potion", "Aktive Effekte", 0xFF9328FF, true),
        SPRINT(Category.HUD, "Sprint", "ToggleSprint Anzeige", BetterUCConfig.DEFAULT_TOGGLE_SPRINT_HUD_COLOR, true),
        HACK_TIMER(Category.HUD, "Hack Timer", "Timer-Position", 0xFF60A5FA, false),
        PLANT_TIMER(Category.HUD, "Plant Timer", "Plantage-Timer", 0xFF6CF27D, true),

        ZOOM(Category.GAMEPLAY, "Zoom", "Taste und FOV", 0xFFA78BFA, true),
        AUTO_STATS(Category.GAMEPLAY, "Auto Stats", "Automatisches /stats", 0xFF34D399, true),
        CHAT(Category.GAMEPLAY, "Chat", "Zeitstempel", 0xFF38BDF8, false),
        CONNECTION(Category.GAMEPLAY, "Verbindung", "Account & Relay", 0xFF38BDF8, false),

        BLACKLIST(Category.TOOLS, "Blacklist", "Gründe und Sync", 0xFFF97316, false),
        PING(Category.TOOLS, "Ping", "Private Mod-Pings", 0xFF38BDF8, true),
        HOTKEYS(Category.TOOLS, "Hotkeys", "Commands auf Tasten", 0xFFFBBF24, false),
        COMMANDS(Category.TOOLS, "Commands", "Command Menu", 0xFF22C55E, false),
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
            return category == Category.HUD || this == PING;
        }
    }

    private record ScrollableControl(ClickableWidget widget, int baseY) {
    }

    private record UpdateSection(String title, String[] lines) {
    }
}
