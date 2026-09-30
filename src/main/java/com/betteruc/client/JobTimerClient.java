package com.betteruc.client;

import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import com.betteruc.parser.JobCooldownParser;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Silently reads all side-job cooldowns from the server's own Job-Center. */
public final class JobTimerClient {
    private static final long JOIN_DELAY_MS = 15_000L;
    private static final long REFRESH_INTERVAL_MS = 10L * 60L * 1_000L;
    private static final long STEP_TIMEOUT_MS = 8_000L;
    private static final long CLICK_DELAY_MS = 180L;
    private static final long MANUAL_MENU_GRACE_MS = 15_000L;
    private static final String JOB_MENU_TITLE = "job center";
    private static final String SIDE_JOBS_NAME = "nebenjob";

    private enum Phase { IDLE, WAITING_FOR_ROOT, WAITING_FOR_LIST }

    private static final Map<String, JobState> JOBS = new LinkedHashMap<>();
    private static Phase phase = Phase.IDLE;
    private static long nextRefreshAtMs;
    private static long deadlineMs;
    private static long nextActionAtMs;
    private static long manualMenuUntilMs;
    private static boolean refreshRequested;
    private static long lastSuccessfulSyncMs;

    private JobTimerClient() {
    }

    public static void onJoin() {
        phase = Phase.IDLE;
        refreshRequested = false;
        deadlineMs = 0L;
        nextActionAtMs = 0L;
        manualMenuUntilMs = 0L;
        nextRefreshAtMs = BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled
                ? System.currentTimeMillis() + JOIN_DELAY_MS : 0L;
    }

    public static void onDisconnect() {
        phase = Phase.IDLE;
        refreshRequested = false;
        deadlineMs = 0L;
        nextActionAtMs = 0L;
        manualMenuUntilMs = 0L;
        nextRefreshAtMs = 0L;
        JOBS.clear();
        lastSuccessfulSyncMs = 0L;
    }

    public static void handleOutgoingCommand(String command) {
        String clean = command == null ? "" : command.strip().toLowerCase(Locale.ROOT);
        if (!clean.equals("job") && !clean.startsWith("job ")) return;
        manualMenuUntilMs = System.currentTimeMillis() + MANUAL_MENU_GRACE_MS;
        if (phase != Phase.IDLE) {
            phase = Phase.IDLE;
            refreshRequested = false;
            deadlineMs = 0L;
        }
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || !ServerGate.isAllowedServer(client)) return;
        long now = System.currentTimeMillis();

        if (phase != Phase.IDLE) {
            if (now > deadlineMs) {
                abort(client);
                return;
            }
            processOpenMenu(client, now);
            return;
        }

        if (!BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled && !refreshRequested) {
            nextRefreshAtMs = 0L;
            return;
        }
        if (ClientCompat.hasScreen(client) || now < manualMenuUntilMs) return;
        if (!refreshRequested && nextRefreshAtMs > 0L && now < nextRefreshAtMs) return;
        if (!ServerCommandUtil.sendAutomatic(client, "job")) return;

        phase = Phase.WAITING_FOR_ROOT;
        refreshRequested = false;
        deadlineMs = now + STEP_TIMEOUT_MS;
        nextActionAtMs = now + CLICK_DELAY_MS;
    }

    public static void requestRefresh(Minecraft client) {
        if (client == null || client.player == null) return;
        if (!ServerCommandUtil.ensureAllowedServerForManualCommand(client)) return;
        refreshRequested = true;
        nextRefreshAtMs = 0L;
        message(client, "§7[betterUC] Job-Timer werden im Hintergrund aktualisiert …");
    }

    public static void setAutoRefresh(Minecraft client, boolean enabled) {
        BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled = enabled;
        BetterUCConfig.save();
        if (enabled) {
            nextRefreshAtMs = System.currentTimeMillis() + 1_000L;
        } else {
            refreshRequested = false;
            nextRefreshAtMs = 0L;
            if (phase != Phase.IDLE) abort(client);
        }
        message(client, "§b[betterUC] §fAutomatischer Job-Abruf: " + (enabled ? "§aAn" : "§cAus"));
    }

    public static void showStatus(Minecraft client) {
        if (client == null || client.player == null) return;
        long now = System.currentTimeMillis();
        message(client, "§b[betterUC] §fJob-Timer" + (lastSuccessfulSyncMs > 0L
                ? " §7(zuletzt geprüft vor " + formatAge(now - lastSuccessfulSyncMs) + ")" : ""));
        if (JOBS.isEmpty()) {
            message(client, "§7Noch keine Jobdaten vorhanden. Nutze §f/jobtimer refresh§7.");
            return;
        }
        for (JobState job : JOBS.values()) {
            long remaining = remainingSeconds(job, now);
            message(client, "§8• §f" + job.name() + ": "
                    + (remaining <= 0L ? "§aBereit" : "§c" + formatDuration(remaining)));
        }
    }

    public static List<JobState> jobs() {
        return List.copyOf(JOBS.values());
    }

    public static boolean hasData() {
        return lastSuccessfulSyncMs > 0L;
    }

    public static int activeCooldownCount(long nowMs) {
        int count = 0;
        for (JobState job : JOBS.values()) {
            if (remainingSeconds(job, nowMs) > 0L) count++;
        }
        return count;
    }

    public static JobState nextCooldown(long nowMs) {
        return JOBS.values().stream()
                .filter(job -> remainingSeconds(job, nowMs) > 0L)
                .min(Comparator.comparingLong(JobState::readyAtMs))
                .orElse(null);
    }

    public static long remainingSeconds(JobState job, long nowMs) {
        if (job == null || job.readyAtMs() <= 0L) return 0L;
        return Math.max(0L, (long) Math.ceil((job.readyAtMs() - nowMs) / 1000.0D));
    }

    public static String formatDuration(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3_600L;
        long minutes = (safe % 3_600L) / 60L;
        long rest = safe % 60L;
        if (hours > 0L) return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, rest);
        return String.format(Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    public static boolean shouldSuppress(Screen screen) {
        return phase != Phase.IDLE && isJobMenu(screen);
    }

    private static void processOpenMenu(Minecraft client, long now) {
        Screen screen = ClientCompat.currentScreen(client);
        AbstractContainerMenu menu = menu(screen);
        if (menu == null || !isJobMenu(screen) || now < nextActionAtMs) return;

        if (phase == Phase.WAITING_FOR_ROOT) {
            Slot sideJobs = findSideJobsSlot(client, menu);
            if (sideJobs == null) return;
            client.gameMode.handleContainerInput(
                    menu.containerId, sideJobs.index, 0, ContainerInput.PICKUP, client.player
            );
            phase = Phase.WAITING_FOR_LIST;
            deadlineMs = now + STEP_TIMEOUT_MS;
            nextActionAtMs = now + CLICK_DELAY_MS;
            return;
        }

        Map<String, JobState> scanned = scanJobs(client, menu, now);
        if (scanned.isEmpty()) return;
        JOBS.clear();
        JOBS.putAll(scanned);
        lastSuccessfulSyncMs = now;
        nextRefreshAtMs = BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled
                ? Math.min(now + REFRESH_INTERVAL_MS, nextExpiryRefresh(now)) : 0L;
        phase = Phase.IDLE;
        deadlineMs = 0L;
        client.player.closeContainer();
        ClientCompat.setScreen(client, null);
    }

    static Map<String, JobState> scanJobs(Minecraft client, AbstractContainerMenu menu, long now) {
        Map<String, JobState> result = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            Long seconds = null;
            for (Component line : lore.lines()) {
                seconds = JobCooldownParser.parseSeconds(line.getString());
                if (seconds != null) break;
            }
            if (seconds == null) continue;
            String name = cleanName(stack.getHoverName().getString());
            if (name.isBlank()) continue;
            long readyAt = seconds <= 0L ? 0L : saturatingAdd(now, seconds * 1_000L);
            result.put(name.toLowerCase(Locale.ROOT), new JobState(name, readyAt));
        }
        return result;
    }

    private static Slot findSideJobsSlot(Minecraft client, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            if (key(slot.getItem().getHoverName().getString()).equals(SIDE_JOBS_NAME)) return slot;
        }
        return null;
    }

    private static long nextExpiryRefresh(long now) {
        long earliest = JOBS.values().stream()
                .mapToLong(JobState::readyAtMs)
                .filter(value -> value > now)
                .min()
                .orElse(now + REFRESH_INTERVAL_MS);
        return earliest + 1_500L;
    }

    private static void abort(Minecraft client) {
        boolean ownedMenu = shouldSuppress(ClientCompat.currentScreen(client));
        phase = Phase.IDLE;
        deadlineMs = 0L;
        nextActionAtMs = 0L;
        nextRefreshAtMs = BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled
                ? System.currentTimeMillis() + 60_000L : 0L;
        if (ownedMenu && client != null && client.player != null) {
            client.player.closeContainer();
            ClientCompat.setScreen(client, null);
        }
    }

    private static AbstractContainerMenu menu(Screen screen) {
        if (!(screen instanceof MenuAccess<?> access)) return null;
        return access.getMenu() instanceof AbstractContainerMenu menu ? menu : null;
    }

    private static boolean isJobMenu(Screen screen) {
        return screen != null && key(screen.getTitle().getString()).contains(JOB_MENU_TITLE);
    }

    private static String key(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
                .replaceAll("[^a-zA-Z0-9]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String formatAge(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        if (seconds < 60L) return seconds + " Sek.";
        return (seconds / 60L) + " Min.";
    }

    private static void message(Minecraft client, String text) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        }
    }

    public record JobState(String name, long readyAtMs) {
    }
}
