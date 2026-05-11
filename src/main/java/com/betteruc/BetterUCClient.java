package com.betteruc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.betteruc.client.AutoCarController;
import com.betteruc.client.ClientScheduler;
import com.betteruc.client.MemberSyncController;
import com.betteruc.client.MovementController;
import com.betteruc.client.ServerCommandUtil;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.gui.CommandGui;
import com.betteruc.gui.BetterUCScreen;
import com.betteruc.hud.AmmoHud;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.CarMarkerHud;
import com.betteruc.hud.CookDrugHud;
import com.betteruc.hud.FpsHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.HealthHud;
import com.betteruc.hud.PaydayHud;
import com.betteruc.hud.PlantageHud;
import com.betteruc.hud.ToggleSprintHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BetterUCClient implements ClientModInitializer {

    private static final int MAX_KILLS = 100;
    private static final int MAX_PRICE = 10_000;
    private static final int MIN_PRICE = 100;
    private static final int EIGENBEDARF_REQUEST_SPACING_MS = 1000;
    private boolean keyWasDown = false;
    private boolean keyWasDown2 = false;
    private int statsOnJoinDelay = -1;
    private final MemberSyncController memberSyncController = new MemberSyncController();
    private final AutoCarController autoCarController = new AutoCarController();
    private final Map<Integer, Boolean> hotkeyPressedState = new HashMap<>();

    private static final class MatchedReason {
        private final String key;
        private final BetterUCConfig.BlacklistReason reason;

        private MatchedReason(String key, BetterUCConfig.BlacklistReason reason) {
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
        return ServerCommandUtil.ensureAllowedServerForManualCommand(client);
    }

    private boolean sendServerCommand(MinecraftClient client, String command) {
        return sendServerCommand(client, command, false);
    }

    private boolean sendServerCommand(MinecraftClient client, String command, boolean notifyIfBlocked) {
        return ServerCommandUtil.send(client, command, notifyIfBlocked);
    }

    public static boolean isToggleSprintHudActive() {
        return MovementController.isToggleSprintHudActive();
    }

    public static double applyZoomFov(double baseFov) {
        return MovementController.applyZoomFov(baseFov);
    }

    @Override
    public void onInitializeClient() {
        BetterUCConfig.load();
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

            BetterUCSuppressFlags.markManualBlacklistCommand();
            BetterUCMod.LOGGER.info("Manual blacklist refresh requested via /{}", root);
        });

        ClientSendMessageEvents.COMMAND.register(command -> {
            String root = getCommandRoot(command);
            if (!isCarFindCommand(command, root)) return;

            AutoCarController.markCarFindCommand();
            BetterUCMod.LOGGER.info("Auto-car: /car find erkannt, warte auf Koordinaten-Nachricht");
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

    private boolean isCarFindCommand(String command, String commandRoot) {
        if (!"car".equals(commandRoot) || command == null) return false;
        String[] parts = command.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return parts.length >= 2 && "find".equals(parts[1]);
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
        PlantageHud.register();
        CarMarkerHud.register();
    }

    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            BetterUCConfig.clearRemoteFactionRuntime();
            BetterUCConfig.clearChatBlacklistRuntime();
            PaydayHud.clear();
            AmmoHud.clear();
            BankBalanceHud.clear();
            PlantageHud.clear();
            CarMarkerHud.clear();
            statsOnJoinDelay = 30;
            memberSyncController.onJoin();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetRuntimeState(client));
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            SuggestionProvider<FabricClientCommandSource> playerSuggestions =
                    (ctx, builder) -> CommandSource.suggestMatching(collectSuggestedPlayerNames(), builder);
            SuggestionProvider<FabricClientCommandSource> reasonSuggestions =
                    (ctx, builder) -> {
                        BetterUCConfig.INSTANCE.blReasons.keySet().forEach(builder::suggest);
                        return builder.buildFuture();
                    };
            SuggestionProvider<FabricClientCommandSource> modBlReasonSuggestions =
                    (ctx, builder) -> {
                        BetterUCConfig.INSTANCE.blReasons.keySet().forEach(builder::suggest);
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

        names.addAll(BetterUCConfig.INSTANCE.chatBlacklistPlayers);
        names.addAll(BetterUCConfig.INSTANCE.remoteFactionPlayers);
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
                if (BetterUCConfig.INSTANCE.currentMoney <= 0) {
                    client.player.sendMessage(Text.literal("\u00A7cKein Geld gefunden!"), false);
                    return;
                }
                sendServerCommand(client, "bank einzahlen " + BetterUCConfig.INSTANCE.currentMoney);
                client.player.sendMessage(
                        Text.literal("\u00A7a[OK] Einzahlung: \u00A7f" + BetterUCConfig.INSTANCE.currentMoney + "$"),
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

            BetterUCConfig.INSTANCE.currentBlackMoney = -1;
            BetterUCSuppressFlags.markSilentStatsRequest();
            sendServerCommand(client, "stats");
            runDelayedOnClient(client, 5000, BetterUCSuppressFlags::cleanupStaleSilentStatsState);

            runDelayedOnClient(client, 1500, () -> {
                if (client.player == null) return;
                int blackMoney = BetterUCConfig.INSTANCE.currentBlackMoney;
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
                                                            + String.join(", ", BetterUCConfig.INSTANCE.blReasons.keySet())
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
            BetterUCConfig.EigenbedarfPreset preset = field == 1
                    ? BetterUCConfig.INSTANCE.eigenbedarfSlot1
                    : BetterUCConfig.INSTANCE.eigenbedarfSlot2;

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
        String cmd1 = buildEigenbedarfCommand(BetterUCConfig.INSTANCE.eigenbedarfSlot1);
        String cmd2 = buildEigenbedarfCommand(BetterUCConfig.INSTANCE.eigenbedarfSlot2);
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

    private String buildEigenbedarfCommand(BetterUCConfig.EigenbedarfPreset preset) {
        if (preset == null) return null;

        String droge = BetterUCConfig.normalizeEigenbedarfDrug(preset.droge);
        int menge = BetterUCConfig.clampEigenbedarfAmount(preset.menge);
        int reinheit = BetterUCConfig.clampEigenbedarfPurity(preset.reinheit);

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
                                + String.join(", ", BetterUCConfig.INSTANCE.blReasons.keySet()) + ", Vogelfrei"
                ), false);
                return 0;
            }
        }

        MatchedReason finalMatched = matched;
        client.player.sendMessage(Text.literal("\u00A77Lade Blacklist fuer \u00A7f" + spieler + "\u00A77..."), false);

        BetterUCSuppressFlags.suppressModBlOutput = true;
        BetterUCSuppressFlags.modBlCallback = () -> applyModBlFromLoadedData(client, spieler, isVogelfreiFlag, finalMatched);

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

        BetterUCSuppressFlags.suppressModBlOutput = true;
        BetterUCSuppressFlags.modBlCallback = () -> applySetRpFromLoadedData(client, spieler, stufe);

        sendServerCommand(client, "blacklist");
        startModBlTimeoutFallback(client);
        return 1;
    }

    private void applySetRpFromLoadedData(MinecraftClient client, String spieler, int stufe) {
        if (client.player == null) return;

        String altGrund = BetterUCConfig.INSTANCE.blacklistReasons.get(spieler);
        int[] altStats = BetterUCConfig.INSTANCE.blacklistStats.get(spieler);
        if (altGrund == null || altStats == null) {
            client.player.sendMessage(Text.literal("\u00A7c" + spieler + " ist nicht auf der Blacklist!"), false);
            return;
        }

        String neuerGrund = buildReasonWithRpStage(spieler, altGrund, stufe);
        String commandGrundArg = toCommandReasonArg(neuerGrund, true);
        int finalKills = clampModBlKills(altStats.length > 0 ? altStats[0] : 0);
        int finalPreis = clampModBlPrice(altStats.length > 1 ? altStats[1] : 0);

        BetterUCSuppressFlags.markPendingModBlReadd(spieler);
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
                || BetterUCConfig.isVogelfrei(spieler)
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

        String altGrund = BetterUCConfig.INSTANCE.blacklistReasons.get(spieler);
        int[] altStats = BetterUCConfig.INSTANCE.blacklistStats.get(spieler);
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
        String commandGrundArg = toCommandReasonArg(kombinierterGrund, false);

        BetterUCSuppressFlags.markPendingModBlReadd(spieler);
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

        if ((BetterUCConfig.isVogelfrei(spieler) || altHatVogelfrei)
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

    private String toCommandReasonArg(String prettyReason, boolean keepRpStage) {
        String cleaned = sanitizeReasonToken(prettyReason);
        if (cleaned.isEmpty()) return "Unbekannt";

        boolean hasVogelfrei = cleaned.toLowerCase(Locale.ROOT).contains("(vogelfrei)")
                || cleaned.equalsIgnoreCase("vogelfrei");

        Matcher rpStageMatcher = Pattern.compile("\\(\\s*([123])\\s*/\\s*3\\s*\\)").matcher(cleaned);
        String rpStageToken = null;
        if (rpStageMatcher.find()) {
            rpStageToken = "(" + rpStageMatcher.group(1) + "/3)";
        }

        LinkedHashSet<String> parts = extractReasonPartsWithoutRpAndVogelfrei(cleaned);
        if (hasVogelfrei) {
            parts.add("Vogelfrei");
        } else if (keepRpStage && rpStageToken != null) {
            parts.add(rpStageToken);
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
        BetterUCSuppressFlags.clearPendingModBlReadd(spieler);
        client.player.sendMessage(Text.literal(
                "\u00A7a[OK] BL-Mod: \u00A7f" + spieler
                        + "\n\u00A77Alt: \u00A7e" + altStats[0] + " Kills \u00A77| \u00A7a" + altStats[1] + "$ \u00A77| \u00A7f" + altGrund
                        + "\n\u00A77Neu: \u00A7e" + finalKills + " Kills \u00A77| \u00A7a" + finalPreis + "$ \u00A77| \u00A7f" + kombinierterGrund
        ), false);
        BetterUCMod.LOGGER.info("modbl: /{}", cmd);
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
        BetterUCSuppressFlags.clearPendingModBlReadd(spieler);

        client.player.sendMessage(Text.literal(
                "\u00A7a[OK] RP-Set: \u00A7f" + spieler
                        + "\n\u00A77Alt: \u00A7e" + altStats[0] + " Kills \u00A77| \u00A7a" + altStats[1] + "$ \u00A77| \u00A7f" + altGrund
                        + "\n\u00A77Neu: \u00A7e" + finalKills + " Kills \u00A77| \u00A7a" + finalPreis + "$ \u00A77| \u00A7f" + neuerGrund
        ), false);
        BetterUCMod.LOGGER.info("setrp: /{}", cmd);
    }

    private void startModBlTimeoutFallback(MinecraftClient client) {
        runDelayedOnClient(client, 1500, () -> {
            if (BetterUCSuppressFlags.modBlCallback == null) return;
            BetterUCMod.LOGGER.info("modbl: Timeout-Fallback ausgeloest");
            BetterUCSuppressFlags.suppressModBlOutput = false;
            Runnable cb = BetterUCSuppressFlags.modBlCallback;
            BetterUCSuppressFlags.modBlCallback = null;
            cb.run();
        });
    }

    private MatchedReason findBlacklistReason(String input) {
        for (Map.Entry<String, BetterUCConfig.BlacklistReason> entry : BetterUCConfig.INSTANCE.blReasons.entrySet()) {
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

        BetterUCConfig.addChatBlacklistPlayer(name);
        BetterUCSuppressFlags.clearRecentBlacklistRemove(name);

        int safeKills = clampModBlKills(Math.max(0, kills));
        int safePreis = clampModBlPrice(Math.max(0, preis));
        BetterUCConfig.INSTANCE.blacklistStats.put(name, new int[]{safeKills, safePreis});

        String normalizedReason = normalizeReasonForStorage(rawReason);
        if (!normalizedReason.isEmpty()) {
            BetterUCConfig.INSTANCE.blacklistReasons.put(name, normalizedReason);
        }

        boolean hasVogelfrei = normalizedReason.toLowerCase(Locale.ROOT).contains("(vogelfrei)");
        if (hasVogelfrei) {
            BetterUCConfig.addVogelfreiPlayer(name);
        } else {
            BetterUCConfig.removeVogelfreiPlayer(name);
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
        ClientScheduler.runDelayedOnClient(client, delayMs, task);
    }

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            HackTimerHud.tick();
            CookDrugHud.tick();
            PlantageHud.tick();
            tickStatsOnJoin(client);
            memberSyncController.tick(client);
            handleConfiguredHotkeys(client);
            MovementController.tick(client);
            autoCarController.tick(client);
            handleScreenHotkeys(client);
        });
    }

    private void tickStatsOnJoin(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (statsOnJoinDelay == 0) {
            sendServerCommand(client, "stats");
            BetterUCMod.LOGGER.info("Auto-sent /stats for Payday initialization");
            statsOnJoinDelay = -1;
        } else if (statsOnJoinDelay > 0) {
            statsOnJoinDelay--;
        }
    }

    private void handleScreenHotkeys(MinecraftClient client) {
        boolean nDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
        if (nDown && !keyWasDown && client.currentScreen == null) {
            client.setScreen(new BetterUCScreen());
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
        for (BetterUCConfig.HotkeyCommand entry : BetterUCConfig.INSTANCE.hotkeyCommands) {
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

    private void resetRuntimeState(MinecraftClient client) {
        BetterUCConfig.clearRemoteFactionRuntime();
        BetterUCConfig.clearChatBlacklistRuntime();
        statsOnJoinDelay = -1;
        memberSyncController.reset();
        hotkeyPressedState.clear();
        MovementController.reset(client);
        autoCarController.reset();
        PaydayHud.clear();
        AmmoHud.clear();
        BankBalanceHud.clear();
        PlantageHud.clear();

        BetterUCSuppressFlags.suppressModBlOutput = false;
        BetterUCSuppressFlags.modBlCallback = null;
        BetterUCSuppressFlags.clearSilentBlacklistState();
        HackTimerHud.secondsRemaining = 0;
        CookDrugHud.clear();
    }
}
