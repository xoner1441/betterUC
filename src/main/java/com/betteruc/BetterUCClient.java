package com.betteruc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.betteruc.client.CarFindTracker;
import com.betteruc.client.ClientScheduler;
import com.betteruc.client.MovementController;
import com.betteruc.client.ServerCommandUtil;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.gui.CommandGui;
import com.betteruc.gui.BetterUCScreen;
import com.betteruc.hud.AmmoHud;
import com.betteruc.hud.BankBalanceHud;
import com.betteruc.hud.FpsHud;
import com.betteruc.hud.HackTimerHud;
import com.betteruc.hud.HealthHud;
import com.betteruc.hud.PaydayHud;
import com.betteruc.hud.PlantageHud;
import com.betteruc.hud.PotionEffectsHud;
import com.betteruc.hud.ToggleSprintHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final int AUTO_STATS_ON_JOIN_DELAY_TICKS = 240;
    private static final long BLINFO_CACHE_MAX_AGE_MS = 20_000L;
    private static final long BLINFO_TIMEOUT_MS = 5000L;
    private static final KeyBinding.Category BETTERUC_KEY_CATEGORY =
            KeyBinding.Category.create(Identifier.of("betteruc", "controls"));
    private static final KeyBinding SETTINGS_KEY = new KeyBinding(
            "key.betteruc.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            BETTERUC_KEY_CATEGORY
    );
    private static final KeyBinding COMMANDS_KEY = new KeyBinding(
            "key.betteruc.commands",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            BETTERUC_KEY_CATEGORY
    );
    private int statsOnJoinDelay = -1;
    private final Map<Integer, Boolean> hotkeyPressedState = new HashMap<>();
    private final Set<Integer> activeHotkeyKeys = new HashSet<>();

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
        registerKeyBindings();
        registerHudElements();
        registerConnectionEvents();
        registerClientCommands();
        registerMessageEvents();
        registerTickEvents();
    }

    private void registerKeyBindings() {
        KeyBindingHelper.registerKeyBinding(SETTINGS_KEY);
        KeyBindingHelper.registerKeyBinding(COMMANDS_KEY);
    }

    private void registerMessageEvents() {
        ClientSendMessageEvents.COMMAND.register(CarFindTracker::handleOutgoingCommand);
    }

    private void registerHudElements() {
        HackTimerHud.register();
        AmmoHud.register();
        BankBalanceHud.register();
        HealthHud.register();
        ToggleSprintHud.register();
        FpsHud.register();
        PaydayHud.register();
        PlantageHud.register();
        PotionEffectsHud.register();
    }

    private void registerConnectionEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerCommandUtil.markJoined(client);
            BetterUCConfig.clearChatBlacklistRuntime();
            PaydayHud.clear();
            AmmoHud.clear();
            BankBalanceHud.clear();
            statsOnJoinDelay = BetterUCConfig.INSTANCE.autoStatsOnJoinEnabled
                    ? AUTO_STATS_ON_JOIN_DELAY_TICKS
                    : -1;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ServerCommandUtil.markDisconnected();
            resetRuntimeState(client);
        });
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

            registerSeinzahlenCommand(dispatcher);
            registerScallCommand(dispatcher, playerSuggestions);
            registerSetBlacklistCommands(dispatcher, playerSuggestions, reasonSuggestions);
            registerBlacklistInfoCommand(dispatcher, playerSuggestions);
            registerModBlCommand(dispatcher, playerSuggestions, modBlReasonSuggestions);
            registerSetRpCommand(dispatcher, playerSuggestions);
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
        return names;
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

    private void registerBlacklistInfoCommand(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            SuggestionProvider<FabricClientCommandSource> playerSuggestions
    ) {
        dispatcher.register(ClientCommandManager.literal("blinfo")
                .then(ClientCommandManager.argument("spieler", StringArgumentType.word())
                        .suggests(playerSuggestions)
                        .executes(context -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.player == null) return 0;

                            String spieler = StringArgumentType.getString(context, "spieler");
                            return executeBlacklistInfo(client, spieler);
                        })));
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

    private int executeBlacklistInfo(MinecraftClient client, String spieler) {
        if (client.player == null) return 0;
        if (!ensureAllowedServerForManualCommand(client)) return 0;

        if (!isValidPlayerName(spieler)) {
            client.player.sendMessage(Text.literal("\u00A7cUngueltiger Spielername: \u00A7f" + spieler), false);
            return 0;
        }

        String requestedName = spieler.trim();
        if (isFreshBlacklistCacheAvailable() && findStoredBlacklistName(requestedName) != null) {
            showBlacklistInfoFromLoadedData(client, requestedName);
            return 1;
        }

        client.player.sendMessage(Text.literal(
                "\u00A77Suche Blacklist-Eintrag fuer \u00A7f" + requestedName + "\u00A77..."
        ), false);

        BetterUCSuppressFlags.beginBlacklistInfoLookup(requestedName);
        BetterUCSuppressFlags.suppressModBlOutput = true;
        BetterUCSuppressFlags.modBlCallback = () -> showBlacklistInfoFromLoadedData(client, requestedName);

        sendServerCommand(client, "blacklist");
        startBlacklistLoadTimeoutFallback(client, "blinfo", BLINFO_TIMEOUT_MS);
        return 1;
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
        startBlacklistLoadTimeoutFallback(client, "modbl");
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
        startBlacklistLoadTimeoutFallback(client, "setrp");
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
        String commandGrundArg = toSetRpCommandReasonArg(neuerGrund);
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
        String commandGrundArg = toCommandReasonArg(kombinierterGrund, true);

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
        String rpStageToken = extractRpStageToken(altGrund);

        boolean altHatVogelfrei = altGrund.toLowerCase().contains("(vogelfrei)");
        if (isVogelfreiFlag) {
            if (altHatVogelfrei) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("\u00A7e" + spieler + " hat bereits (Vogelfrei)!"), false);
                }
            }
            return formatBlacklistReason(grundTeile, rpStageToken, true);
        }

        String cleanedNewReason = sanitizeReasonToken(newReasonKey);
        // Always keep stacking reasons in the combined list.
        // Existing reasons are preserved, new reason is appended if not already present.
        boolean hatGrundBereits = !cleanedNewReason.isEmpty()
                && grundTeile.stream().anyMatch(g -> g.equalsIgnoreCase(cleanedNewReason));
        if (!hatGrundBereits && !cleanedNewReason.isEmpty()) {
            grundTeile.add(cleanedNewReason);
        }

        return formatBlacklistReason(
                grundTeile,
                rpStageToken,
                BetterUCConfig.isVogelfrei(spieler) || altHatVogelfrei
        );
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

        String rpStageToken = extractRpStageToken(cleaned);

        LinkedHashSet<String> parts = extractReasonPartsWithoutRpAndVogelfrei(cleaned);
        String formatted = formatBlacklistReason(parts, keepRpStage ? rpStageToken : null, hasVogelfrei);
        return formatted.isEmpty() ? "Unbekannt" : formatted;
    }

    private String toSetRpCommandReasonArg(String prettyReason) {
        String cleaned = sanitizeReasonToken(prettyReason);
        return cleaned.isEmpty() ? "Unbekannt" : cleaned;
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

    private void showBlacklistInfoFromLoadedData(MinecraftClient client, String spieler) {
        if (client.player == null) return;

        String storedName = findStoredBlacklistName(spieler);
        if (storedName == null) {
            BetterUCSuppressFlags.clearBlacklistInfoLookup();
            client.player.sendMessage(Text.literal(
                    "\u00A7cKein Blacklist-Eintrag fuer \u00A7f" + spieler + "\u00A7c gefunden."
            ), false);
            return;
        }

        String rest = getBlacklistEntryRest(storedName);
        if (rest == null || rest.isBlank()) {
            rest = buildFallbackBlacklistRest(storedName);
        }
        BetterUCSuppressFlags.clearBlacklistInfoLookup();
        BetterUCSuppressFlags.markNextBlacklistInfoLocalMessageBypass();
        client.player.sendMessage(buildBlacklistInfoText(storedName, rest), false);
    }

    private boolean isFreshBlacklistCacheAvailable() {
        long lastSync = BetterUCConfig.INSTANCE.lastBlacklistSyncMs;
        return lastSync > 0L && System.currentTimeMillis() - lastSync <= BLINFO_CACHE_MAX_AGE_MS;
    }

    private String findStoredBlacklistName(String input) {
        String key = normalizePlayerLookupKey(input);
        if (key.isEmpty()) return null;

        String fromRest = findMatchingName(
                BetterUCConfig.INSTANCE.blacklistEntryRests == null
                        ? List.of()
                        : BetterUCConfig.INSTANCE.blacklistEntryRests.keySet(),
                key
        );
        if (fromRest != null) return fromRest;

        String fromBlacklist = findMatchingName(BetterUCConfig.INSTANCE.chatBlacklistPlayers, key);
        if (fromBlacklist != null) return fromBlacklist;

        return findMatchingName(BetterUCConfig.INSTANCE.blacklistReasons.keySet(), key);
    }

    private String findMatchingName(Iterable<String> names, String key) {
        if (names == null || key == null || key.isEmpty()) return null;
        for (String name : names) {
            if (normalizePlayerLookupKey(name).equals(key)) {
                return name;
            }
        }
        return null;
    }

    private String getBlacklistEntryRest(String name) {
        if (BetterUCConfig.INSTANCE.blacklistEntryRests == null) return "";
        String direct = BetterUCConfig.INSTANCE.blacklistEntryRests.get(name);
        if (direct != null) return direct;

        String key = normalizePlayerLookupKey(name);
        for (Map.Entry<String, String> entry : BetterUCConfig.INSTANCE.blacklistEntryRests.entrySet()) {
            if (normalizePlayerLookupKey(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private Text buildBlacklistInfoText(String name, String rest) {
        MutableText line = Text.literal("\u00A77\u00BB \u00A7a" + name);
        if (rest == null || rest.isBlank()) return line;

        for (String segment : rest.split("\\|", -1)) {
            String cleaned = segment.trim();
            if (!cleaned.isEmpty()) {
                line.append(Text.literal(" \u00A77| \u00A7a" + cleaned));
            }
        }
        return line;
    }

    private String buildFallbackBlacklistRest(String name) {
        String reason = getBlacklistReason(name);
        int[] stats = getBlacklistStats(name);

        StringBuilder builder = new StringBuilder();
        if (reason != null && !reason.isBlank()) {
            builder.append(reason.trim());
        }
        if (stats != null && stats.length > 0 && stats[0] > 0) {
            if (builder.length() > 0) builder.append(" | ");
            builder.append(stats[0]).append(" Kills");
        }
        if (stats != null && stats.length > 1 && stats[1] > 0) {
            if (builder.length() > 0) builder.append(" | ");
            builder.append(stats[1]).append("$");
        }
        return builder.toString();
    }

    private String getBlacklistReason(String name) {
        String direct = BetterUCConfig.INSTANCE.blacklistReasons.get(name);
        if (direct != null) return direct;

        String key = normalizePlayerLookupKey(name);
        for (Map.Entry<String, String> entry : BetterUCConfig.INSTANCE.blacklistReasons.entrySet()) {
            if (normalizePlayerLookupKey(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private int[] getBlacklistStats(String name) {
        int[] direct = BetterUCConfig.INSTANCE.blacklistStats.get(name);
        if (direct != null) return direct;

        String key = normalizePlayerLookupKey(name);
        for (Map.Entry<String, int[]> entry : BetterUCConfig.INSTANCE.blacklistStats.entrySet()) {
            if (normalizePlayerLookupKey(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isValidPlayerName(String name) {
        return name != null && name.trim().matches("[A-Za-z0-9_]{3,16}");
    }

    private String normalizePlayerLookupKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void startBlacklistLoadTimeoutFallback(MinecraftClient client, String label) {
        startBlacklistLoadTimeoutFallback(client, label, 1500L);
    }

    private void startBlacklistLoadTimeoutFallback(MinecraftClient client, String label, long delayMs) {
        runDelayedOnClient(client, delayMs, () -> {
            if (BetterUCSuppressFlags.modBlCallback == null) return;
            BetterUCMod.LOGGER.info("{}: Blacklist-Load Timeout-Fallback ausgeloest", label);
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
        if (BetterUCConfig.INSTANCE.blacklistEntryRests == null) {
            BetterUCConfig.INSTANCE.blacklistEntryRests = new LinkedHashMap<>();
        }
        BetterUCConfig.INSTANCE.blacklistEntryRests.put(name, buildFallbackBlacklistRest(name));

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
        String rpStageToken = extractRpStageToken(raw);
        LinkedHashSet<String> parts = extractReasonPartsWithoutRpAndVogelfrei(raw);
        return formatBlacklistReason(parts, rpStageToken, hasVogelfrei);
    }

    private String extractRpStageToken(String rawReason) {
        if (rawReason == null) return null;
        Matcher rpStageMatcher = Pattern.compile("\\(\\s*([123])\\s*/\\s*3\\s*\\)").matcher(rawReason);
        return rpStageMatcher.find() ? "(" + rpStageMatcher.group(1) + "/3)" : null;
    }

    private String formatBlacklistReason(
            LinkedHashSet<String> parts,
            String rpStageToken,
            boolean hasVogelfrei
    ) {
        String basis = parts == null ? "" : String.join(" + ", parts);
        if (!basis.isEmpty() && rpStageToken != null) {
            basis = basis + " " + rpStageToken;
        } else if (basis.isEmpty() && rpStageToken != null) {
            basis = rpStageToken;
        }

        if (!basis.isEmpty() && hasVogelfrei) {
            basis = basis + " (Vogelfrei)";
        } else if (basis.isEmpty() && hasVogelfrei) {
            basis = "(Vogelfrei)";
        }
        return basis;
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
            PlantageHud.tick();
            AmmoHud.tickReloadKey(client);
            tickStatsOnJoin(client);
            handleConfiguredHotkeys(client);
            MovementController.tick(client);
            handleScreenHotkeys(client);
        });
    }

    private void tickStatsOnJoin(MinecraftClient client) {
        if (!ServerGate.isAllowedServer(client)) return;
        if (statsOnJoinDelay == 0) {
            if (!ServerCommandUtil.isAutomaticSendReady(client)) return;
            if (ServerCommandUtil.sendAutomatic(client, "stats")) {
                BetterUCSuppressFlags.markSilentStatsRequest();
                runDelayedOnClient(client, BetterUCSuppressFlags.SILENT_STATS_TIMEOUT_MS,
                        BetterUCSuppressFlags::cleanupStaleSilentStatsState);
                BetterUCMod.LOGGER.info("Auto-sent /stats for Payday initialization");
                statsOnJoinDelay = -1;
            }
        } else if (statsOnJoinDelay > 0) {
            statsOnJoinDelay--;
        }
    }

    private void handleScreenHotkeys(MinecraftClient client) {
        while (SETTINGS_KEY.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new BetterUCScreen());
            }
        }

        while (COMMANDS_KEY.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new CommandGui());
            }
        }
    }

    private void handleConfiguredHotkeys(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) return;

        List<BetterUCConfig.HotkeyCommand> hotkeys = BetterUCConfig.INSTANCE.hotkeyCommands;
        if (hotkeys == null || hotkeys.isEmpty()) {
            hotkeyPressedState.clear();
            return;
        }

        activeHotkeyKeys.clear();
        for (BetterUCConfig.HotkeyCommand entry : hotkeys) {
            if (entry == null) continue;
            int keyCode = entry.keyCode;
            if (keyCode < 0 || entry.command == null) continue;

            String command = entry.command.trim();
            if (command.isEmpty()) continue;

            activeHotkeyKeys.add(keyCode);
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

        hotkeyPressedState.keySet().removeIf(k -> !activeHotkeyKeys.contains(k));
    }

    private void resetRuntimeState(MinecraftClient client) {
        BetterUCConfig.clearChatBlacklistRuntime();
        statsOnJoinDelay = -1;
        hotkeyPressedState.clear();
        MovementController.reset(client);
        PaydayHud.clear();
        AmmoHud.clear();
        BankBalanceHud.clear();

        BetterUCSuppressFlags.suppressModBlOutput = false;
        BetterUCSuppressFlags.modBlCallback = null;
        BetterUCSuppressFlags.clearSilentBlacklistState();
        HackTimerHud.secondsRemaining = 0;
    }
}
