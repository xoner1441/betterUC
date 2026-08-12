package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Objects;
import java.util.Optional;

public final class ReinforcementAcceptClient {

    private static final long HEADLINE_MATCH_WINDOW_MS = 5_000L;
    private static final long PENDING_EXPIRY_MS = 15_000L;
    private static final long DUPLICATE_WINDOW_MS = 10_000L;

    private static ReinforcementTypeMatcher.Type latestType;
    private static String latestHeadline = "";
    private static long latestHeadlineAtMs;
    private static PendingReinforcement pending;
    private static String lastAcceptedFingerprint = "";
    private static long lastAcceptedAtMs;
    private static boolean afk;

    private ReinforcementAcceptClient() {
    }

    public static void handleChatMessage(Minecraft client, Component message) {
        if (message == null) return;

        long now = System.currentTimeMillis();
        String raw = message.getString();
        ReinforcementTypeMatcher.Type type = ReinforcementTypeMatcher.classify(raw);
        if (type != null) {
            String playerName = client != null && client.player != null
                    ? client.player.getName().getString()
                    : "";
            if (ReinforcementTypeMatcher.isRequestedBy(raw, playerName)) {
                clearLatestHeadline();
                return;
            }
            latestType = type;
            latestHeadline = ReinforcementTypeMatcher.normalize(raw);
            latestHeadlineAtMs = now;
            return;
        }

        if (!BetterUCConfig.INSTANCE.reinfAcceptEnabled || afk) return;
        if (!ReinforcementTypeMatcher.normalize(raw).contains("unterwegs")) return;
        if (latestType == null || now - latestHeadlineAtMs > HEADLINE_MATCH_WINDOW_MS) return;
        if (!isTypeEnabled(latestType)) return;

        String command = findRunCommandForLabel(message, "unterwegs");
        if (command == null || command.isBlank()) return;

        String fingerprint = latestType.name() + '|' + latestHeadline + '|' + command;
        if (fingerprint.equals(lastAcceptedFingerprint)
                && now - lastAcceptedAtMs < DUPLICATE_WINDOW_MS) {
            return;
        }

        pending = new PendingReinforcement(latestType, command, fingerprint, now);
        if (!BetterUCConfig.INSTANCE.reinfAcceptAutomatic) {
            notify(client, "\u00A7b[betterUC] \u00A7f" + latestType.label()
                    + "-Reinf bereit. Nutze deinen Reinf-Hotkey.");
        }
    }

    public static void tick(Minecraft client) {
        if (pending == null) return;
        long now = System.currentTimeMillis();
        if (!BetterUCConfig.INSTANCE.reinfAcceptEnabled || afk || now - pending.createdAtMs > PENDING_EXPIRY_MS) {
            pending = null;
            return;
        }
        if (!BetterUCConfig.INSTANCE.reinfAcceptAutomatic) return;
        tryAccept(client, false);
    }

    public static void acceptPending(Minecraft client) {
        if (!BetterUCConfig.INSTANCE.reinfAcceptEnabled) {
            notify(client, "\u00A7c[betterUC] Reinf-Annahme ist deaktiviert.");
            return;
        }
        if (afk) {
            notify(client, "\u00A7c[betterUC] Reinf-Annahme ist im AFK-Modus gesperrt.");
            return;
        }
        if (pending == null || System.currentTimeMillis() - pending.createdAtMs > PENDING_EXPIRY_MS) {
            pending = null;
            notify(client, "\u00A77[betterUC] Kein aktueller Reinf zum Annehmen.");
            return;
        }
        tryAccept(client, true);
    }

    public static void setAfk(boolean value) {
        afk = value;
        if (value) pending = null;
    }

    public static void reset() {
        clearLatestHeadline();
        pending = null;
        lastAcceptedFingerprint = "";
        lastAcceptedAtMs = 0L;
        afk = false;
    }

    private static void clearLatestHeadline() {
        latestType = null;
        latestHeadline = "";
        latestHeadlineAtMs = 0L;
    }

    private static void tryAccept(Minecraft client, boolean notifyOnBlocked) {
        if (pending == null) return;
        long now = System.currentTimeMillis();
        long cooldownMs = BetterUCConfig.INSTANCE.reinfAcceptCooldownSeconds * 1_000L;
        if (now - lastAcceptedAtMs < cooldownMs) {
            if (notifyOnBlocked) {
                notify(client, "\u00A77[betterUC] Reinf-Cooldown ist noch aktiv.");
            }
            return;
        }

        String command = stripLeadingSlash(pending.command);
        if (!ServerCommandUtil.sendAutomatic(client, command)) {
            if (notifyOnBlocked) {
                notify(client, "\u00A77[betterUC] Annahme kurz blockiert. Versuche es gleich erneut.");
            }
            return;
        }

        lastAcceptedAtMs = now;
        lastAcceptedFingerprint = pending.fingerprint;
        ReinforcementTypeMatcher.Type acceptedType = pending.type;
        pending = null;
        notify(client, "\u00A7a[betterUC] " + acceptedType.label() + "-Reinf angenommen.");
    }

    static String findRunCommandForLabel(Component component, String label) {
        if (component == null || label == null || label.isBlank()) return null;

        String normalizedLabel = ReinforcementTypeMatcher.normalize(label);
        return component.visit((style, text) -> {
            if (!ReinforcementTypeMatcher.normalize(text).contains(normalizedLabel)) {
                return Optional.empty();
            }
            ClickEvent clickEvent = style.getClickEvent();
            if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
                return Optional.of(runCommand.command());
            }
            return Optional.empty();
        }, Style.EMPTY).orElse(null);
    }

    private static boolean isTypeEnabled(ReinforcementTypeMatcher.Type type) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        return switch (Objects.requireNonNull(type)) {
            case NORMAL -> config.reinfAcceptNormal;
            case URGENT -> config.reinfAcceptUrgent;
            case MEDIC -> config.reinfAcceptMedic;
            case HOSTAGE -> config.reinfAcceptHostage;
            case CONTRACT -> config.reinfAcceptContract;
            case TRAINING -> config.reinfAcceptTraining;
            case DRUGS -> config.reinfAcceptDrugs;
            case BODY_GUARD -> config.reinfAcceptBodyGuard;
            case BOMB -> config.reinfAcceptBomb;
            case PLANTAGE -> config.reinfAcceptPlantage;
        };
    }

    private static String stripLeadingSlash(String command) {
        String trimmed = command == null ? "" : command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static void notify(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private record PendingReinforcement(
            ReinforcementTypeMatcher.Type type,
            String command,
            String fingerprint,
            long createdAtMs
    ) {
    }
}
