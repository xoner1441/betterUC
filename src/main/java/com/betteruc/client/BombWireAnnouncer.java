package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.mixin.BossHealthOverlayAccessor;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Announces newly collected bomb wires in alliance chat during an active bomb event. */
public final class BombWireAnnouncer {
    private static final String ALLIANCE_COMMAND_PREFIX = "d Draht gefunden: ";

    private static final int[] lastCounts = new int[WireType.values().length];
    private static final EnumSet<WireType> announcedTypes = EnumSet.noneOf(WireType.class);
    private static final Queue<String> pendingAnnouncements = new ArrayDeque<>();
    private static boolean active;

    private BombWireAnnouncer() {
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (!BetterUCConfig.INSTANCE.bombWireAnnouncerEnabled) {
            reset();
            return;
        }

        if (isBombEnd(raw)) {
            reset();
            return;
        }

        if (isBombStart(raw) && !active) {
            activate(client);
        }
    }

    public static void tick(Minecraft client) {
        if (!BetterUCConfig.INSTANCE.bombWireAnnouncerEnabled) {
            reset();
            return;
        }
        if (client == null || client.player == null || !ServerGate.isAllowedServer(client)) {
            reset();
            return;
        }
        if (!active && hasBombBossBar(client)) activate(client);
        if (!active) return;

        int[] currentCounts = new int[WireType.values().length];
        snapshotInventory(client, currentCounts);
        for (WireType type : WireType.values()) {
            int index = type.ordinal();
            if (currentCounts[index] > lastCounts[index] && announcedTypes.add(type)) {
                pendingAnnouncements.add(ALLIANCE_COMMAND_PREFIX + type.displayName);
            }
        }
        System.arraycopy(currentCounts, 0, lastCounts, 0, lastCounts.length);

        String command = pendingAnnouncements.peek();
        if (command != null && ServerCommandUtil.sendAutomatic(client, command)) {
            pendingAnnouncements.remove();
        }
    }

    public static void reset() {
        active = false;
        Arrays.fill(lastCounts, 0);
        announcedTypes.clear();
        pendingAnnouncements.clear();
    }

    static boolean isBombStart(String raw) {
        String line = normalize(raw);
        return line.contains("news")
                && line.contains("achtung")
                && line.contains("es wurde eine bombe")
                && (line.contains("in der nahe von") || line.contains("in der naehe von"))
                && line.contains("gefunden");
    }

    static boolean isBombEnd(String raw) {
        String line = normalize(raw);
        return line.contains("news")
                && (line.contains("die bombe konnte erfolgreich entscharft werden")
                || line.contains("die bombe konnte erfolgreich entschaerft werden")
                || line.contains("die bombe konnte nicht erfolgreich entscharft werden")
                || line.contains("die bombe konnte nicht erfolgreich entschaerft werden"));
    }

    static boolean isBombBossBar(String raw) {
        String line = normalize(raw);
        return line.matches("^bombe \\d+ minuten? \\d+ sekunden? ort(?: .+)?$");
    }

    static WireType wireType(String itemId, String hoverName) {
        if (!"minecraft:paper".equals(itemId)) return null;
        String name = normalize(hoverName);
        return switch (name) {
            case "grun draht", "gruner draht", "gruen draht", "gruener draht" -> WireType.GREEN;
            case "rot draht", "roter draht" -> WireType.RED;
            case "blau draht", "blauer draht" -> WireType.BLUE;
            case "lila draht", "violett draht", "violetter draht" -> WireType.PURPLE;
            default -> null;
        };
    }

    private static void snapshotInventory(Minecraft client, int[] target) {
        Arrays.fill(target, 0);
        if (client == null || client.player == null) return;

        var inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            WireType type = wireType(id == null ? "" : id.toString(), stack.getHoverName().getString());
            if (type != null) target[type.ordinal()] += stack.getCount();
        }
    }

    private static void activate(Minecraft client) {
        active = true;
        pendingAnnouncements.clear();
        announcedTypes.clear();
        snapshotInventory(client, lastCounts);
        for (WireType type : WireType.values()) {
            if (lastCounts[type.ordinal()] > 0) announcedTypes.add(type);
        }
    }

    private static boolean hasBombBossBar(Minecraft client) {
        if (client.gui == null || client.gui.hud == null) return false;
        var overlay = client.gui.hud.getBossOverlay();
        if (!(overlay instanceof BossHealthOverlayAccessor accessor)) return false;
        return accessor.betteruc$getEvents().values().stream()
                .anyMatch(event -> isBombBossBar(event.getName().getString()));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
                .toLowerCase(Locale.ROOT)
                .replace("\u00DF", "ss")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    enum WireType {
        GREEN("Gr\u00FCner Draht"),
        RED("Roter Draht"),
        BLUE("Blauer Draht"),
        PURPLE("Lila Draht");

        private final String displayName;

        WireType(String displayName) {
            this.displayName = displayName;
        }
    }
}
