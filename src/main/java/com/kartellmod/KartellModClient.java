package com.kartellmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.kartellmod.config.KartellConfig;
import com.kartellmod.gui.CommandGui;
import com.kartellmod.gui.KartellScreen;
import com.kartellmod.hud.AmmoHud;
import com.kartellmod.hud.BankBalanceHud;
import com.kartellmod.hud.CarMarkerHud;
import com.kartellmod.hud.CookDrugHud;
import com.kartellmod.hud.FpsHud;
import com.kartellmod.hud.HackTimerHud;
import com.kartellmod.hud.HealthHud;
import com.kartellmod.hud.PaydayHud;
import com.kartellmod.hud.ToggleSprintHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KartellModClient implements ClientModInitializer {

    private static final int MAX_KILLS = 100;
    private static final int MAX_PRICE = 10_000;
    private static final int MIN_PRICE = 100;
    private static final float ZOOM_SMOOTH_LERP = 0.30f;
    private static final float ZOOM_SNAP_EPSILON = 0.01f;
    private static final int CAR_AUTO_START_DELAY_TICKS = 8;
    private static final int MEMBERINFO_REQUEST_SPACING_MS = 450;
    private static final int EIGENBEDARF_REQUEST_SPACING_MS = 1000;
    private static final double FULLBRIGHT_GAMMA_VALUE = 1.0;
    private static final int FULLBRIGHT_REAPPLY_INTERVAL_TICKS = 20;
    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "KartellDelayScheduler");
                t.setDaemon(true);
                return t;
            });
    private static final String DOMAIN_LOCK_MESSAGE =
            "\u00A7cKartellMod funktioniert nur auf: \u00A7f" + ServerGate.allowedServersLabel();

    private boolean keyWasDown = false;
    private boolean keyWasDown2 = false;
    private int statsOnJoinDelay = -1;
    private int memberInfoDelay = -1;
    private int reloadTickCounter = -1;
    private final Map<Integer, Boolean> hotkeyPressedState = new HashMap<>();
    private boolean toggleSprintActive = false;
    private boolean toggleSprintWasActiveLastTick = false;
    private static boolean toggleSprintHudActive = false;
    private static float zoomProgress = 0.0f;
    private boolean wasRidingMinecart = false;
    private Vec3d lastMinecartPos = null;
    private int pendingCarStartTicks = -1;
    private int lastHandledCarControlSyncId = -1;
    private Double fullbrightPreviousGamma = null;
    private Double fullbrightPreviousDarknessScale = null;
    private Boolean fullbrightPreviousAo = null;
    private Boolean fullbrightPreviousEntityShadows = null;
    private boolean fullbrightApplied = false;
    private int fullbrightReapplyTicks = 0;

    private static final class MatchedReason {
        private final String key;
        private final KartellConfig.BlacklistReason reason;

        private MatchedReason(String key, KartellConfig.BlacklistReason reason) {
            this.key = key;
            this.reason = reason;
        }
    }

    private static int clampKills(int value) {
        return Math.max(0, Math.min(value, MAX_KILLS));
    }

    private static int clampPrice(int value) {
        return Math.max(MIN_PRICE, Math.min(value, MAX_PRICE));
    }

    private static int clampModBlKills(int value) {
        return Math.max(0, Math.min(value, MAX_KILLS));
    }

    private static int clampModBlPrice(int value) {
        return Math.max(MIN_PRICE, Math.min(value, MAX_PRICE));
    }

    private boolean ensureAllowedServerForManualCommand(MinecraftClient client) {
        if (ServerGate.isAllowedServer(client)) return true;
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(DOMAIN_LOCK_MESSAGE), false);
        }
        return false;
    }

    private boolean sendServerCommand(MinecraftClient client, String command) {
        return sendServerCommand(client, command, false);
    }

    private boolean sendServerCommand(MinecraftClient client, String command, boolean notifyIfBlocked) {
        if (client == null || client.player == null || command == null || command.isBlank()) return false;
        if (!ServerGate.isAllowedServer(client)) {
            if (notifyIfBlocked) {
                client.player.sendMessage(Text.literal(DOMAIN_LOCK_MESSAGE), false);
            }
            return false;
        }
        client.player.networkHandler.sendChatCommand(command);
        return true;
    }

    public static boolean isToggleSprintHudActive() {
        return toggleSprintHudActive;
    }

    public static double applyZoomFov(double baseFov) {
        if (zoomProgress <= 0.0f) return baseFov;
        float cfg = KartellConfig.INSTANCE.zoomFovMultiplier;
        float multiplier = Math.max(0.05f, Math.min(cfg, 1.0f));
        double factor = 1.0 - (1.0 - multiplier) * zoomProgress;
        return baseFov * factor;
    }

    @Override
    public void onInitializeClient() {
        KartellConfig.load();
        FactionLoader.start();
        registerHudElements();
        registerConnectionEvents();
        registerClientCommands();
        registerMessageEvents();
        registerTickEvents();
    }

    private void registerMessageEvents() {
        ClientSendMessageEvents.COMMAND.register(command -> {
            String root = getCommandRoot(command);
            if (!isBlacklistListCommand(command, root)) return;

            KartellSuppressFlags.markManualBlacklistCommand();
            KartellMod.LOGGER.info("Manual blacklist refresh requested via /{}", root);
        });
    }

    private String getCommandRoot(String command) {
        if (command == null || command.isBlank()) return "";
        String[] parts = command.trim().toLowerCase(Locale.ROOT).split("\\s+", 2);
        return parts.length == 0 ? "" : parts[0];
    }

    private boolean isBlacklistListCommand(String command, String commandRoot) {
        if ("blacklist".equals(commandRoot)) return true;
        return "bl".equals(commandRoot) && command != null && command.trim().equalsIgnoreCase("bl");
    }

    private void registerHudElements() {
        HackTimerHud.register();
        CookDrugHud.register();
        AmmoHud.register();
        BankBalanceHud.register();
        HealthHud.register();
        ToggleSprintHud.register();
        FpsHud.register();
        PaydayHud.register();
        CarMarkerHud.register();
    }

    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            KartellConfig.INSTANCE.remoteFactionPlayers.clear();
            KartellConfig.INSTANCE.remoteFactionMembersByFaction.clear();
            KartellConfig.INSTANCE.chatBlacklistPlayers.clear();
            PaydayHud.clear();
            AmmoHud.clear();
            BankBalanceHud.clear();
            CarMarkerHud.clear();
            statsOnJoinDelay = 30;
            memberInfoDelay = 40;
            scheduleReload();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetRuntimeState(client));
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            SuggestionProvider<FabricClientCommandSource> playerSuggestions =
                    (ctx, builder) -> CommandSource.suggestMatching(collectSuggestedPlayerNames(), builder);
            SuggestionProvider<FabricClientCommandSource> reasonSuggestions =
                    (ctx, builder) -> {
                        KartellConfig.INSTANCE.blReasons.keySet().forEach(builder::suggest);
                        return builder.buildFuture();
                    };
            SuggestionProvider<FabricClientCommandSource> modBlReasonSuggestions =
                    (ctx, builder) -> {
                        KartellConfig.INSTANCE.blReasons.keySet().forEach(builder::suggest);
                        builder.suggest("Vogelfrei");
                        return builder.buildFuture();
                    };

            registerEinzahlenCommand(dispatcher);
            registerSeinzahlenCommand(dispatcher);
            registerScallCommand(dispatcher, playerSuggestions);
            registerSetBlacklistCommands(dispatcher, playerSuggestions, reasonSuggestions);
            registerModBlCommand(dispatcher, playerSuggestions, modBlReasonSuggestions);
            registerSetRpCommand(dispatcher, playerSuggestions);
            registerEigenbedarfCommand(dispatcher);
        });
    }

    private LinkedHashSet<String> collectSuggestedPlayerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                String name = PlayerNameUtil.resolveProfileName(entry.getProfile());
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }

        names.addAll(KartellConfig.INSTANCE.chatBlacklistPlayers);
        names.addAll(KartellConfig.INSTANCE.remoteFactionPlayers);
        return names;
    }

    private void registerEinzahlenCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("einzahlen").executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return 0;
            if (!ensureAllowedServerForManualCommand(client)) return 0;

            sendServerCommand(client, "stats");
            runDelayedOnClient(client, 1500, () -> {
                if (client.player == null) return;
                if (KartellConfig.INSTANCE.currentMoney <= 0) {
                    client.player.sendMessage(Text.literal("\u00A7cKein Geld gefunden!"), false);
                    return;
                }
                sendServerCommand(client, "bank einzahlen " + KartellConfig.INSTANCE.currentMoney);
                client.player.sendMessage(
                        Text.literal("\u00A7a[OK] Einzahlung: \u00A7f" + KartellConfig.INSTANCE.currentMoney + "$"),
                        false
                );
            });
            return 1;
        }));
    }

    private void registerSeinzahlenCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("seinzahlen").executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return 0;
            if (!ensureAllowedServerForManualCommand(client)) return 0;

            KartellConfig.INSTANCE.currentBlackMoney = -1;
            KartellSuppressFlags.markSilentStatsRequest();
            sendServerCommand(client, "stats");
            runDelayedOnClient(client, 5000, KartellSuppressFlags::cleanupStaleSilentStatsState);

            runDelayedOnClient(client, 1500, () -> {
                if (client.player == null) return;
                int blackMoney = KartellConfig.INSTANCE.currentBlackMoney;
                if (blackMoney <= 0) {
                    client.player.sendMessage(Text.literal("\u00A7cKein Schwarzgeld gefunden!"), false);
                    return;
                }

                sendServerCommand(client, "skasse einzahlen " + blackMoney);
                client.player.sendMessage(
                        Text.literal("\u00A7a[OK] S-Kasse Einzahlung: \u00A7f" + blackMoney + "$"),
                        false
                );
            });
            return 1;
        }));
    }

    private void registerScallCommand(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            SuggestionProvider<FabricClientCommandSource> playerSuggestions
    ) {
        dispatcher.register(ClientCommandManager.literal("scall")
                .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .suggests(playerSuggestions)
                        .executes(context -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.player == null) return 0;
                            if (!ensureAllowedServerForManualCommand(client)) return 0;
                            String spieler = StringArgumentType.getString(context, "spieler");
                            sendServerCommand(client, "s " + spieler + " Auf den Boden oder du Stirbst");
                            return 1;
                        })));
    }

    private void registerSetBlacklistCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            SuggestionProvider<FabricClientCommandSource> playerSuggestions,
            SuggestionProvider<FabricClientCommandSource> reasonSuggestions
    ) {
        for (String commandName : new String[]{"blset", "setbl"}) {
            dispatcher.register(ClientCommandManager.literal(commandName)
                    .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                            .suggests(playerSuggestions)
                            .then(ClientCommandManager.argument("grund", StringArgumentType.word())
                                    .suggests(reasonSuggestions)
                                    .executes(context -> {
                                        MinecraftClient client = MinecraftClient.getInstance();
                                        if (client.player == null) return 0;
                                        if (!ensureAllowedServerForManualCommand(client)) return 0;

                                        String spieler = StringArgumentType.getString(context, "spieler");
                                        String grundInput = StringArgumentType.getString(context, "grund");
                                        MatchedReason matched = findBlacklistReason(grundInput);
                                        if (matched == null) {
                                            client.player.sendMessage(Text.literal(
                                                    "\u00A7cUnbekannter Grund: \u00A7f" + grundInput
                                                            + " \u00A77| Verfuegbar: \u00A7f"
                                                            + String.join(", ", KartellConfig.INSTANCE.blReasons.keySet())
                                            ), false);
                                            return 0;
                                        }

                                        int cappedKills = clampKills(matched.reason.kills);
                                        int cappedPrice = clampPrice(matched.reason.price);
                                        String cmd = String.format("bl add %s %d %d %s",
                                                spieler, cappedKills, cappedPrice, matched.key);

                                        sendServerCommand(client, cmd);
                                        applyLocalBlacklistUpsert(spieler, cappedKills, cappedPrice, matched.key);
                                        client.player.sendMessage(Text.literal(
                                                "\u00A7a[OK] BL: \u00A7f" + spieler
                                                        + " \u00A77| \u00A7e" + cappedKills + " Kills"
                                                        + " \u00A77| \u00A7a" + cappedPrice + "$"
                                                        + " \u00A77| \u00A7f" + matched.key
                                        ), false);
                                        return 1;
                                    }))));
        }
    }

    private void registerModBlCommand(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            SuggestionProvider<FabricClientCommandSource> playerSuggestions,
            SuggestionProvider<FabricClientCommandSource> modBlReasonSuggestions
    ) {
        dispatcher.register(ClientCommandManager.literal("modbl")
                .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .suggests(playerSuggestions)
                        .then(ClientCommandManager.argument("neuergrund", StringArgumentType.word())
                                .suggests(modBlReasonSuggestions)
                                .executes(context -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client.player == null) return 0;

                                    String spieler = StringArgumentType.getString(context, "spieler");
                                    String neuerGrund = StringArgumentType.getString(context, "neuergrund");
                                    return executeModBl(client, spieler, neuerGrund);
                                }))));
    }

    private void registerSetRpCommand(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            SuggestionProvider<FabricClientCommandSource> playerSuggestions
    ) {
        dispatcher.register(ClientCommandManager.literal("setrp")
                .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .suggests(playerSuggestions)
                        .then(ClientCommandManager.argument("stufe", IntegerArgumentType.integer(1, 3))
                                .suggests((ctx, builder) -> {
                                    builder.suggest(1);
                                    builder.suggest(2);
                                    builder.suggest(3);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    MinecraftClient client = MinecraftClient.getInstance();
                                    if (client.player == null) return 0;

                                    String spieler = StringArgumentType.getString(context, "spieler");
                                    int stufe = IntegerArgumentType.getInteger(context, "stufe");
                                    return executeSetRp(client, spieler, stufe);
                                }))));
    }

    private void registerEigenbedarfCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("eigenbedarf")
                .executes(context -> executeEigenbedarf(MinecraftClient.getInstance(), 0))
                .then(ClientCommandManager.argument("feld", IntegerArgumentType.integer(1, 2))
                        .executes(context -> executeEigenbedarf(
                                MinecraftClient.getInstance(),
                                IntegerArgumentType.getInteger(context, "feld")
                        ))));
    }

    private int executeEigenbedarf(MinecraftClient client, int field) {
        if (client == null || client.player == null) return 0;
        if (!ensureAllowedServerForManualCommand(client)) return 0;

        if (field == 1 || field == 2) {
            KartellConfig.EigenbedarfPreset preset = field == 1
                    ? KartellConfig.INSTANCE.eigenbedarfSlot1
                    : KartellConfig.INSTANCE.eigenbedarfSlot2;

            String cmd = buildEigenbedarfCommand(preset);
            if (cmd == null) {
                client.player.sendMessage(Text.literal(
                        "\u00A7cEigenbedarf Feld " + field + " ist ungueltig. "
                                + "\u00A77Bitte Droge/Menge/Reinheit im Settings-HUD setzen."
                ), false);
                return 0;
            }

            sendServerCommand(client, cmd);
            client.player.sendMessage(Text.literal(
                    "\u00A7a[OK] Eigenbedarf Feld " + field + ": \u00A7f/" + cmd
            ), false);
            return 1;
        }

        List<String> commands = new ArrayList<>();
        String cmd1 = buildEigenbedarfCommand(KartellConfig.INSTANCE.eigenbedarfSlot1);
        String cmd2 = buildEigenbedarfCommand(KartellConfig.INSTANCE.eigenbedarfSlot2);
        if (cmd1 != null) commands.add(cmd1);
        if (cmd2 != null) commands.add(cmd2);

        if (commands.isEmpty()) {
            client.player.sendMessage(Text.literal(
                    "\u00A7cKein Eigenbedarf-Preset aktiv. \u00A77Setze Menge > 0 in Feld 1 oder Feld 2."
            ), false);
            return 0;
        }

        for (int i = 0; i < commands.size(); i++) {
            String cmd = commands.get(i);
            if (i == 0) {
                sendServerCommand(client, cmd);
            } else {
                long delay = (long) EIGENBEDARF_REQUEST_SPACING_MS * i;
                runDelayedOnClient(client, delay, () -> {
                    if (client.player != null) {
                        sendServerCommand(client, cmd);
                    }
                });
            }
        }

        client.player.sendMessage(Text.literal(
                "\u00A7a[OK] Eigenbedarf: \u00A7f" + commands.size() + "x /dbank get gesendet."
        ), false);
        return 1;
    }

    private String buildEigenbedarfCommand(KartellConfig.EigenbedarfPreset preset) {
        if (preset == null) return null;

        String droge = KartellConfig.normalizeEigenbedarfDrug(preset.droge);
        int menge = KartellConfig.clampEigenbedarfAmount(preset.menge);
        int reinheit = KartellConfig.clampEigenbedarfPurity(preset.reinheit);

        if (menge <= 0) return null;
        return String.format("dbank get %s %d %d", droge, menge, reinheit);
    }

    private int executeModBl(MinecraftClient client, String spieler, String neuerGrund) {
        if (client.player == null) return 0;
        if (!ensureAllowedServerForManualCommand(client)) return 0;

        boolean isVogelfreiFlag = neuerGrund.equalsIgnoreCase("Vogelfrei");
        MatchedReason matched = null;
        if (!isVogelfreiFlag) {
            matched = findBlacklistReason(neuerGrund);
            if (matched == null) {
                client.player.sendMessage(Text.literal(
                        "\u00A7cUnbekannter Grund: \u00A7f" + neuerGrund
                                + " \u00A77| Verfuegbar: \u00A7f"
                                + String.join(", ", KartellConfig.INSTANCE.blReasons.keySet()) + ", Vogelfrei"
                ), false);
                return 0;
            }
        }

        MatchedReason finalMatched = matched;
        client.player.sendMessage(Text.literal("\u00A77Lade Blacklist fuer \u00A7f" + spieler + "\u00A77..."), false);

        KartellSuppressFlags.suppressModBlOutput = true;
        KartellSuppressFlags.modBlCallback = () -> applyModBlFromLoadedData(client, spieler, isVogelfreiFlag, finalMatched);

        sendServerCommand(client, "blacklist");
        startModBlTimeoutFallback(client);
        return 1;
    }

    private int executeSetRp(MinecraftClient client, String spieler, int stufe) {
        if (client.player == null) return 0;
        if (!ensureAllowedServerForManualCommand(client)) return 0;
        if (stufe < 1 || stufe > 3) {
            client.player.sendMessage(Text.literal("\u00A7cRP-Stufe muss zwischen 1 und 3 liegen!"), false);
            return 0;
        }

        client.player.sendMessage(Text.literal(
                "\u00A77Lade Blacklist fuer \u00A7f" + spieler + "\u00A77 (RP " + stufe + "/3)..."
        ), false);

        KartellSuppressFlags.suppressModBlOutput = true;
        KartellSuppressFlags.modBlCallback = () -> applySetRpFromLoadedData(client, spieler, stufe);

        sendServerCommand(client, "blacklist");
        startModBlTimeoutFallback(client);
        return 1;
    }

    private void applySetRpFromLoadedData(MinecraftClient client, String spieler, int stufe) {
        if (client.player == null) return;

        String altGrund = KartellConfig.INSTANCE.blacklistReasons.get(spieler);
        int[] altStats = KartellConfig.INSTANCE.blacklistStats.get(spieler);
        if (altGrund == null || altStats == null) {
            client.player.sendMessage(Text.literal("\u00A7c" + spieler + " ist nicht auf der Blacklist!"), false);
            return;
        }

        String neuerGrund = buildReasonWithRpStage(spieler, altGrund, stufe);
        String commandGrundArg = toCommandReasonArg(neuerGrund);
        int finalKills = clampModBlKills(altStats.length > 0 ? altStats[0] : 0);
        int finalPreis = clampModBlPrice(altStats.length > 1 ? altStats[1] : 0);

        KartellSuppressFlags.markPendingModBlReadd(spieler);
        sendServerCommand(client, "bl remove " + spieler);
        runDelayedOnClient(client, 500, () -> sendSetRpUpdatedEntry(
                client,
                spieler,
                altGrund,
                altStats,
                finalKills,
                finalPreis,
                neuerGrund,
                commandGrundArg
        ));
    }

    private String buildReasonWithRpStage(String spieler, String altGrund, int stufe) {
        boolean hasVogelfrei = (altGrund != null && altGrund.toLowerCase(Locale.ROOT).contains("(vogelfrei)"))
                || KartellConfig.isVogelfrei(spieler)
                || stufe == 3;
        LinkedHashSet<String> parts = extractReasonPartsWithoutRpAndVogelfrei(altGrund);
        String basis = String.join(" + ", parts);
        if (hasVogelfrei) {
            return basis.isEmpty() ? "(Vogelfrei)" : basis + " (Vogelfrei)";
        }
        String result = basis.isEmpty() ? "(" + stufe + "/3)" : basis + " (" + stufe + "/3)";
        return result;
    }

    private void applyModBlFromLoadedData(
            MinecraftClient client,
            String spieler,
            boolean isVogelfreiFlag,
            MatchedReason matched
    ) {
        if (client.player == null) return;

        String altGrund = KartellConfig.INSTANCE.blacklistReasons.get(spieler);
        int[] altStats = KartellConfig.INSTANCE.blacklistStats.get(spieler);
        if (altGrund == null || altStats == null) {
            client.player.sendMessage(Text.literal("\u00A7c" + spieler + " ist nicht auf der Blacklist!"), false);
            return;
        }

        int baseKills = altStats.length > 0 ? Math.max(0, altStats[0]) : 0;
        int basePreis = altStats.length > 1 ? Math.max(0, altStats[1]) : 0;
        int addKills = (!isVogelfreiFlag && matched != null) ? Math.max(0, matched.reason.kills) : 0;
        int addPreis = (!isVogelfreiFlag && matched != null) ? Math.max(0, matched.reason.price) : 0;

        // Always add for /modbl and enforce hard limits:
        // Kills max 100, Preis max 10000.
        int neueKillsRaw = baseKills + addKills;
        int neuerPreisRaw = basePreis + addPreis;
        int neueKills = clampModBlKills(neueKillsRaw);
        int neuerPreis = clampModBlPrice(neuerPreisRaw);

        String kombinierterGrund = buildCombinedReason(spieler, altGrund, isVogelfreiFlag, matched == null ? null : matched.key, client);
        if (kombinierterGrund == null) return;
        String commandGrundArg = toCommandReasonArg(kombinierterGrund);

        KartellSuppressFlags.markPendingModBlReadd(spieler);
        sendServerCommand(client, "bl remove " + spieler);
        runDelayedOnClient(client, 500, () -> sendUpdatedBlacklistEntry(
                client,
                spieler,
                altGrund,
                altStats,
                neueKills,
                neuerPreis,
                kombinierterGrund,
                commandGrundArg
        ));
    }

    private String buildCombinedReason(
            String spieler,
            String altGrund,
            boolean isVogelfreiFlag,
            String newReasonKey,
            MinecraftClient client
    ) {
        LinkedHashSet<String> grundTeile = extractReasonPartsWithoutRpAndVogelfrei(altGrund);

        boolean altHatVogelfrei = altGrund.toLowerCase().contains("(vogelfrei)");
        if (isVogelfreiFlag) {
            if (altHatVogelfrei) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("\u00A7e" + spieler + " hat bereits (Vogelfrei)!"), false);
                }
            }
            String basis = String.join(" + ", grundTeile);
            return basis.isEmpty() ? "(Vogelfrei)" : basis + " (Vogelfrei)";
        }

        String cleanedNewReason = sanitizeReasonToken(newReasonKey);
        // Always keep stacking reasons in the combined list.
        // Existing reasons are preserved, new reason is appended if not already present.
        boolean hatGrundBereits = !cleanedNewReason.isEmpty()
                && grundTeile.stream().anyMatch(g -> g.equalsIgnoreCase(cleanedNewReason));
        if (!hatGrundBereits && !cleanedNewReason.isEmpty()) {
            grundTeile.add(cleanedNewReason);
        }

        String basis = String.join(" + ", grundTeile);
        if (basis.isEmpty()) basis = cleanedNewReason;

        if ((KartellConfig.isVogelfrei(spieler) || altHatVogelfrei)
                && !basis.toLowerCase().contains("(vogelfrei)")) {
            basis = basis + " (Vogelfrei)";
        }
        return basis;
    }

    private String sanitizeReasonToken(String raw) {
        if (raw == null) return "";

        String cleaned = raw
                .replace("\\\"", "\"")
                .replace("\\", " ")
                .replaceAll("[\\u0022\\u201C\\u201D\\u201E\\u00AB\\u00BB']", " ")
                .trim();

        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned;
    }

    private String toCommandReasonArg(String prettyReason) {
        String cleaned = sanitizeReasonToken(prettyReason);
        if (cleaned.isEmpty()) return "Unbekannt";

        boolean hasVogelfrei = cleaned.toLowerCase(Locale.ROOT).contains("(vogelfrei)")
                || cleaned.equalsIgnoreCase("vogelfrei");

        String stripped = cleaned
                .replaceAll("(?i)\\s*\\(vogelfrei\\)\\s*", " ")
                .replaceAll("\\s*\\([123]\\s*/\\s*3\\)\\s*", " ")
                .trim();

        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String part : stripped.split("\\s*(?:,|\\+)\\s*")) {
            String token = sanitizeReasonToken(part);
            if (token.matches("(?i)\\(?\\s*vogelfrei\\s*\\)?")) {
                token = "Vogelfrei";
            }
            if (!token.isEmpty()) {
                parts.add(token);
            }
        }
        if (hasVogelfrei) {
            parts.add("Vogelfrei");
        }
        if (parts.isEmpty()) return "Unbekannt";
        return String.join("+", parts);
    }

    private void sendUpdatedBlacklistEntry(
            MinecraftClient client,
            String spieler,
            String altGrund,
            int[] altStats,
            int neueKills,
            int neuerPreis,
            String kombinierterGrund,
            String commandGrundArg
    ) {
        if (client.player == null) return;

        int finalKills = clampModBlKills(neueKills);
        int finalPreis = clampModBlPrice(neuerPreis);
        String cmd = String.format("bl add %s %d %d %s", spieler, finalKills, finalPreis, commandGrundArg);
        sendServerCommand(client, cmd);
        applyLocalBlacklistUpsert(spieler, finalKills, finalPreis, kombinierterGrund);
        KartellSuppressFlags.clearPendingModBlReadd(spieler);
        client.player.sendMessage(Text.literal(
                "\u00A7a[OK] BL-Mod: \u00A7f" + spieler
                        + "\n\u00A77Alt: \u00A7e" + altStats[0] + " Kills \u00A77| \u00A7a" + altStats[1] + "$ \u00A77| \u00A7f" + altGrund
                        + "\n\u00A77Neu: \u00A7e" + finalKills + " Kills \u00A77| \u00A7a" + finalPreis + "$ \u00A77| \u00A7f" + kombinierterGrund
        ), false);
        KartellMod.LOGGER.info("modbl: /{}", cmd);
    }

    private void sendSetRpUpdatedEntry(
            MinecraftClient client,
            String spieler,
            String altGrund,
            int[] altStats,
            int finalKills,
            int finalPreis,
            String neuerGrund,
            String commandGrundArg
    ) {
        if (client.player == null) return;

        String cmd = String.format("bl add %s %d %d %s", spieler, finalKills, finalPreis, commandGrundArg);
        sendServerCommand(client, cmd);
        applyLocalBlacklistUpsert(spieler, finalKills, finalPreis, neuerGrund);
        KartellSuppressFlags.clearPendingModBlReadd(spieler);

        client.player.sendMessage(Text.literal(
                "\u00A7a[OK] RP-Set: \u00A7f" + spieler
                        + "\n\u00A77Alt: \u00A7e" + altStats[0] + " Kills \u00A77| \u00A7a" + altStats[1] + "$ \u00A77| \u00A7f" + altGrund
                        + "\n\u00A77Neu: \u00A7e" + finalKills + " Kills \u00A77| \u00A7a" + finalPreis + "$ \u00A77| \u00A7f" + neuerGrund
        ), false);
        KartellMod.LOGGER.info("setrp: /{}", cmd);
    }

    private void startModBlTimeoutFallback(MinecraftClient client) {
        runDelayedOnClient(client, 1500, () -> {
            if (KartellSuppressFlags.modBlCallback == null) return;
            KartellMod.LOGGER.info("modbl: Timeout-Fallback ausgeloest");
            KartellSuppressFlags.suppressModBlOutput = false;
            Runnable cb = KartellSuppressFlags.modBlCallback;
            KartellSuppressFlags.modBlCallback = null;
            cb.run();
        });
    }

    private MatchedReason findBlacklistReason(String input) {
        for (Map.Entry<String, KartellConfig.BlacklistReason> entry : KartellConfig.INSTANCE.blReasons.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(input)) {
                return new MatchedReason(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    private void applyLocalBlacklistUpsert(String spieler, int kills, int preis, String rawReason) {
        if (spieler == null || spieler.isBlank()) return;
        String name = spieler.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) return;

        if (KartellConfig.INSTANCE.chatBlacklistPlayers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
            KartellConfig.INSTANCE.chatBlacklistPlayers.add(name);
        }
        KartellSuppressFlags.clearRecentBlacklistRemove(name);

        int safeKills = clampModBlKills(Math.max(0, kills));
        int safePreis = clampModBlPrice(Math.max(0, preis));
        KartellConfig.INSTANCE.blacklistStats.put(name, new int[]{safeKills, safePreis});

        String normalizedReason = normalizeReasonForStorage(rawReason);
        if (!normalizedReason.isEmpty()) {
            KartellConfig.INSTANCE.blacklistReasons.put(name, normalizedReason);
        }

        boolean hasVogelfrei = normalizedReason.toLowerCase(Locale.ROOT).contains("(vogelfrei)");
        if (hasVogelfrei) {
            if (KartellConfig.INSTANCE.vogelfreiPlayers.stream().noneMatch(s -> s.equalsIgnoreCase(name))) {
                KartellConfig.INSTANCE.vogelfreiPlayers.add(name);
            }
        } else {
            KartellConfig.INSTANCE.vogelfreiPlayers.removeIf(s -> s.equalsIgnoreCase(name));
        }
    }

    private String normalizeReasonForStorage(String raw) {
        if (raw == null) return "";

        boolean hasVogelfrei = raw.toLowerCase(Locale.ROOT).matches(".*\\b\\(?vogelfrei\\)?\\b.*");
        LinkedHashSet<String> parts = extractReasonPartsWithoutRpAndVogelfrei(raw);
        String joined = String.join(" + ", parts);
        if (hasVogelfrei) {
            return joined.isEmpty() ? "(Vogelfrei)" : joined + " (Vogelfrei)";
        }
        return joined;
    }

    private LinkedHashSet<String> extractReasonPartsWithoutRpAndVogelfrei(String rawReason) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (rawReason == null || rawReason.isBlank()) return parts;

        String stripped = rawReason
                .replaceAll("(?i)\\s*\\(?vogelfrei\\)?\\s*", " ")
                .replaceAll("\\s*\\([123]\\s*/\\s*3\\)\\s*", " ")
                .trim();

        if (stripped.isEmpty()) return parts;

        for (String part : stripped.split("\\s*(?:,|\\+)\\s*")) {
            String cleaned = sanitizeReasonToken(part);
            if (!cleaned.isEmpty()) {
                parts.add(cleaned);
            }
        }
        return parts;
    }

    private void runDelayedOnClient(MinecraftClient client, long delayMs, Runnable task) {
        if (client == null || task == null) return;
        long safeDelayMs = Math.max(0L, delayMs);
        DELAY_EXECUTOR.schedule(() -> client.execute(task), safeDelayMs, TimeUnit.MILLISECONDS);
    }

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            HackTimerHud.tick();
            CookDrugHud.tick();
            tickStatsOnJoin(client);
            tickMemberReload(client);
            tickReloadCycle(client);
            handleConfiguredHotkeys(client);
            handleToggleSprint(client);
            tickZoom(client);
            handleFullbright(client);
            handleCarAutomation(client);
            handleScreenHotkeys(client);
        });
    }

    private void scheduleReload() {
        reloadTickCounter = KartellConfig.INSTANCE.reloadIntervalMinutes * 60 * 20;
    }

    private void triggerReload(MinecraftClient client) {
        if (client.player == null) return;
        memberInfoDelay = 0;
        KartellMod.LOGGER.info("Auto-Reload ausgeloest (Intervall: {} Minuten)",
                KartellConfig.INSTANCE.reloadIntervalMinutes);
    }

    private void tickMemberReload(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (PaydayHud.isPausedByAfk()) return;

        if (memberInfoDelay == 0) {
            List<String> factionQueries = KartellConfig.getTrackedFactionQueries();
            if (factionQueries.isEmpty()) {
                KartellConfig.rebuildRemoteFactionUnion();
                memberInfoDelay = -1;
                runDelayedOnClient(client, 1000, KartellSuppressFlags::cleanupStaleSilentMemberState);
                return;
            }

            for (int i = 0; i < factionQueries.size(); i++) {
                String query = factionQueries.get(i);
                if (query == null || query.isBlank()) continue;
                String finalQuery = query;
                int delay = i * MEMBERINFO_REQUEST_SPACING_MS;
                runDelayedOnClient(client, delay, () -> {
                    if (client.player == null) return;
                    if (!ServerGate.isAllowedServer(client)) return;
                    KartellSuppressFlags.markSilentMemberRequest();
                    sendServerCommand(client, "memberinfoall " + finalQuery);
                    KartellMod.LOGGER.info("Auto-sent /memberinfoall {}", finalQuery);
                });
            }

            memberInfoDelay = -1;
            runDelayedOnClient(client, 5000L + (long) factionQueries.size() * MEMBERINFO_REQUEST_SPACING_MS,
                    KartellSuppressFlags::cleanupStaleSilentMemberState);
        } else if (memberInfoDelay > 0) {
            memberInfoDelay--;
        }
    }

    private void tickStatsOnJoin(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (statsOnJoinDelay == 0) {
            sendServerCommand(client, "stats");
            KartellMod.LOGGER.info("Auto-sent /stats for Payday initialization");
            statsOnJoinDelay = -1;
        } else if (statsOnJoinDelay > 0) {
            statsOnJoinDelay--;
        }
    }

    private void tickReloadCycle(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (PaydayHud.isPausedByAfk()) return;

        if (reloadTickCounter > 0) {
            reloadTickCounter--;
        } else if (reloadTickCounter == 0) {
            triggerReload(client);
            scheduleReload();
        }
    }

    private void handleScreenHotkeys(MinecraftClient client) {
        boolean nDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
        if (nDown && !keyWasDown && client.currentScreen == null) {
            client.setScreen(new KartellScreen());
        }
        keyWasDown = nDown;

        boolean mDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
        if (mDown && !keyWasDown2 && client.currentScreen == null) {
            client.setScreen(new CommandGui());
        }
        keyWasDown2 = mDown;
    }

    private void handleConfiguredHotkeys(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) return;

        Set<Integer> activeKeys = new HashSet<>();
        for (KartellConfig.HotkeyCommand entry : KartellConfig.INSTANCE.hotkeyCommands) {
            if (entry == null) continue;
            int keyCode = entry.keyCode;
            if (keyCode < 0 || entry.command == null) continue;

            String command = entry.command.trim();
            if (command.isEmpty()) continue;

            activeKeys.add(keyCode);
            boolean isDown = GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
            boolean wasDown = hotkeyPressedState.getOrDefault(keyCode, false);

            if (isDown && !wasDown) {
                String cmd = command.startsWith("/") ? command.substring(1).trim() : command;
                if (!cmd.isEmpty()) {
                    sendServerCommand(client, cmd);
                }
            }
            hotkeyPressedState.put(keyCode, isDown);
        }

        hotkeyPressedState.keySet().removeIf(k -> !activeKeys.contains(k));
    }

    private void handleToggleSprint(MinecraftClient client) {
        KeyBinding sprintKey = client.options.sprintKey;
        if (sprintKey == null || sprintKey.isUnbound()) {
            toggleSprintActive = false;
            toggleSprintWasActiveLastTick = false;
            toggleSprintHudActive = false;
            return;
        }

        while (sprintKey.wasPressed()) {
            if (KartellConfig.INSTANCE.toggleSprintEnabled && client.currentScreen == null) {
                toggleSprintActive = !toggleSprintActive;
            }
        }

        if (!KartellConfig.INSTANCE.toggleSprintEnabled) {
            toggleSprintActive = false;
            toggleSprintWasActiveLastTick = false;
            toggleSprintHudActive = false;
            return;
        }

        boolean shouldForceSprint = KartellConfig.INSTANCE.toggleSprintEnabled
                && toggleSprintActive
                && client.currentScreen == null
                && client.player != null
                && client.player.isAlive()
                && client.player.getHungerManager().getFoodLevel() > 6
                && !client.player.isSneaking();

        if (toggleSprintActive) {
            sprintKey.setPressed(shouldForceSprint);
        } else if (toggleSprintWasActiveLastTick) {
            sprintKey.setPressed(false);
        }

        toggleSprintWasActiveLastTick = toggleSprintActive;
        toggleSprintHudActive = toggleSprintActive;
    }

    private void tickZoom(MinecraftClient client) {
        int keyCode = KartellConfig.INSTANCE.zoomKeyCode;
        boolean zoomDown = KartellConfig.INSTANCE.zoomEnabled
                && keyCode > 0
                && client.currentScreen == null
                && GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
        float target = zoomDown ? 1.0f : 0.0f;
        if (KartellConfig.INSTANCE.zoomInstant) {
            zoomProgress = target;
            return;
        }

        zoomProgress += (target - zoomProgress) * ZOOM_SMOOTH_LERP;
        if (Math.abs(target - zoomProgress) < ZOOM_SNAP_EPSILON) {
            zoomProgress = target;
        }
    }

    private void handleFullbright(MinecraftClient client) {
        if (client.options == null) return;

        if (KartellConfig.INSTANCE.fullbrightEnabled) {
            if (!fullbrightApplied) {
                fullbrightPreviousGamma = client.options.getGamma().getValue();
                fullbrightPreviousDarknessScale = client.options.getDarknessEffectScale().getValue();
                fullbrightPreviousAo = client.options.getAo().getValue();
                fullbrightPreviousEntityShadows = client.options.getEntityShadows().getValue();
                applyFullbrightVisualOptions(client);
                fullbrightApplied = true;
                fullbrightReapplyTicks = FULLBRIGHT_REAPPLY_INTERVAL_TICKS;
                return;
            }

            if (fullbrightReapplyTicks <= 0) {
                applyFullbrightVisualOptions(client);
                fullbrightReapplyTicks = FULLBRIGHT_REAPPLY_INTERVAL_TICKS;
            } else {
                fullbrightReapplyTicks--;
            }
            return;
        }

        restoreFullbright(client);
    }

    private void restoreFullbright(MinecraftClient client) {
        if (!fullbrightApplied && fullbrightPreviousGamma == null && fullbrightPreviousDarknessScale == null
                && fullbrightPreviousAo == null && fullbrightPreviousEntityShadows == null) return;

        if (fullbrightPreviousGamma != null) client.options.getGamma().setValue(fullbrightPreviousGamma);
        if (fullbrightPreviousDarknessScale != null) client.options.getDarknessEffectScale().setValue(fullbrightPreviousDarknessScale);
        if (fullbrightPreviousAo != null) client.options.getAo().setValue(fullbrightPreviousAo);
        if (fullbrightPreviousEntityShadows != null) client.options.getEntityShadows().setValue(fullbrightPreviousEntityShadows);

        fullbrightApplied = false;
        fullbrightPreviousGamma = null;
        fullbrightPreviousDarknessScale = null;
        fullbrightPreviousAo = null;
        fullbrightPreviousEntityShadows = null;
        fullbrightReapplyTicks = 0;
    }

    private void applyFullbrightVisualOptions(MinecraftClient client) {
        client.options.getGamma().setValue(FULLBRIGHT_GAMMA_VALUE);
        client.options.getDarknessEffectScale().setValue(0.0);
        client.options.getAo().setValue(false);
        client.options.getEntityShadows().setValue(false);
    }

    private void handleCarAutomation(MinecraftClient client) {
        if (client.player == null) return;
        if (!ServerGate.isAllowedServer(client)) {
            pendingCarStartTicks = -1;
            lastHandledCarControlSyncId = -1;
            wasRidingMinecart = false;
            return;
        }

        boolean enabled = KartellConfig.INSTANCE.carAutomationEnabled;
        AbstractMinecartEntity minecart = client.player.getVehicle() instanceof AbstractMinecartEntity
                ? (AbstractMinecartEntity) client.player.getVehicle()
                : null;
        boolean ridingMinecart = minecart != null;

        if (ridingMinecart && !wasRidingMinecart) {
            CarMarkerHud.clear();
        }

        if (minecart != null) {
            lastMinecartPos = new Vec3d(minecart.getX(), minecart.getY(), minecart.getZ());
        } else if (wasRidingMinecart && lastMinecartPos != null) {
            CarMarkerHud.setMarker(client, lastMinecartPos);
            KartellMod.LOGGER.info("Auto-car: Marker gesetzt bei {}", lastMinecartPos);
        }

        if (enabled) {
            if (ridingMinecart && !wasRidingMinecart) {
                sendServerCommand(client, "car lock");
                pendingCarStartTicks = CAR_AUTO_START_DELAY_TICKS;
                lastHandledCarControlSyncId = -1;
                KartellMod.LOGGER.info("Auto-car: Minecart betreten -> /car lock");
            }
            autoClickCarControlScreen(client);
        } else {
            pendingCarStartTicks = -1;
            lastHandledCarControlSyncId = -1;
        }

        if (enabled && pendingCarStartTicks >= 0) {
            if (pendingCarStartTicks == 0) {
                if (ridingMinecart) {
                    sendServerCommand(client, "car start");
                    KartellMod.LOGGER.info("Auto-car: /car start");
                }
                pendingCarStartTicks = -1;
            } else {
                pendingCarStartTicks--;
            }
        }

        wasRidingMinecart = ridingMinecart;
    }

    private void autoClickCarControlScreen(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            lastHandledCarControlSyncId = -1;
            return;
        }
        if (client.interactionManager == null || client.player == null) return;

        String title = handledScreen.getTitle().getString();
        if (title == null || !title.toLowerCase(Locale.ROOT).contains("carcontrol")) return;

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler == client.player.playerScreenHandler) return;

        int syncId = handler.syncId;
        if (lastHandledCarControlSyncId == syncId) return;

        int slotId = findCarControlActionSlot(client, handler, Items.EMERALD);
        if (slotId < 0) {
            slotId = findCarControlActionSlot(client, handler, Items.REDSTONE);
        }
        if (slotId < 0) return;

        client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
        client.player.closeHandledScreen();
        lastHandledCarControlSyncId = syncId;
        KartellMod.LOGGER.info("Auto-car: CarControl Slot {} geklickt", slotId);
    }

    private int findCarControlActionSlot(MinecraftClient client, ScreenHandler handler, Item item) {
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            if (slot == null || slot.inventory == client.player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private void resetRuntimeState(MinecraftClient client) {
        KartellConfig.INSTANCE.remoteFactionPlayers.clear();
        KartellConfig.INSTANCE.remoteFactionMembersByFaction.clear();
        KartellConfig.INSTANCE.chatBlacklistPlayers.clear();
        statsOnJoinDelay = -1;
        memberInfoDelay = -1;
        reloadTickCounter = -1;
        hotkeyPressedState.clear();
        toggleSprintActive = false;
        toggleSprintWasActiveLastTick = false;
        toggleSprintHudActive = false;
        zoomProgress = 0.0f;
        wasRidingMinecart = false;
        lastMinecartPos = null;
        pendingCarStartTicks = -1;
        lastHandledCarControlSyncId = -1;
        CarMarkerHud.clear();
        restoreFullbright(client);
        fullbrightApplied = false;
        fullbrightPreviousGamma = null;
        fullbrightPreviousDarknessScale = null;
        fullbrightPreviousAo = null;
        fullbrightPreviousEntityShadows = null;
        fullbrightReapplyTicks = 0;
        PaydayHud.clear();
        AmmoHud.clear();
        BankBalanceHud.clear();

        if (client.options != null && client.options.sprintKey != null) {
            client.options.sprintKey.setPressed(false);
        }

        KartellSuppressFlags.suppressModBlOutput = false;
        KartellSuppressFlags.modBlCallback = null;
        KartellSuppressFlags.clearSilentBlacklistState();
        HackTimerHud.secondsRemaining = 0;
        CookDrugHud.clear();
    }
}
