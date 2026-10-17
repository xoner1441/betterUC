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
    private static final long STEP_TIMEOUT_MS = 8_000L;
    private static final long LIST_TIMEOUT_MS = 2_000L;
    private static final long CLICK_DELAY_MS = 75L;
    private static final long JOB_COMPLETION_REFRESH_DELAY_MS = 1_500L;
    private static final long MANUAL_MENU_GRACE_MS = 15_000L;
    private static final int SIDE_JOBS_FALLBACK_SLOT = 13;
    private static final String JOB_MENU_TITLE = "jobcenter";
    private static final String SIDE_JOBS_NAME = "nebenjob";

    private enum Phase { IDLE, WAITING_FOR_ROOT, WAITING_FOR_LIST }

    private static final Map<String, JobState> JOBS = new LinkedHashMap<>();
    private static Phase phase = Phase.IDLE;
    private static long nextRefreshAtMs;
    private static long deadlineMs;
    private static long nextActionAtMs;
    private static long manualMenuUntilMs;
    private static boolean refreshRequested;
    private static boolean sendingAutomaticJobCommand;
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
        if (sendingAutomaticJobCommand) return;
        manualMenuUntilMs = System.currentTimeMillis() + MANUAL_MENU_GRACE_MS;
        if (phase != Phase.IDLE) {
            phase = Phase.IDLE;
            refreshRequested = false;
            deadlineMs = 0L;
        }
        nextRefreshAtMs = 0L;
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
        if (!refreshRequested && nextRefreshAtMs <= 0L) return;
        if (ClientCompat.hasScreen(client) || now < manualMenuUntilMs) return;
        if (nextRefreshAtMs > 0L && now < nextRefreshAtMs) return;
        boolean commandSent;
        sendingAutomaticJobCommand = true;
        try {
            commandSent = ServerCommandUtil.sendAutomatic(client, "job");
        } finally {
            sendingAutomaticJobCommand = false;
        }
        if (!commandSent) return;

        phase = Phase.WAITING_FOR_ROOT;
        refreshRequested = false;
        nextRefreshAtMs = 0L;
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

    /** Queues a fresh scan after the server confirms a completed job through its salary message. */
    public static void onJobSalaryReceived() {
        if (!BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled) return;
        refreshRequested = true;
        nextRefreshAtMs = System.currentTimeMillis() + JOB_COMPLETION_REFRESH_DELAY_MS;
    }

    public static void setAutoRefresh(Minecraft client, boolean enabled) {
        BetterUCConfig.INSTANCE.jobTimerAutoRefreshEnabled = enabled;
        BetterUCConfig.save();
        nextRefreshAtMs = 0L;
        if (!enabled) {
            refreshRequested = false;
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

    public static List<JobState> activeCooldowns(long nowMs) {
        return JOBS.values().stream()
                .filter(job -> remainingSeconds(job, nowMs) > 0L)
                .sorted(Comparator.comparingLong(JobState::readyAtMs))
                .toList();
    }

    public static JobState nextCooldown(long nowMs) {
        List<JobState> active = activeCooldowns(nowMs);
        return active.isEmpty() ? null : active.getFirst();
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
        return phase != Phase.IDLE && menu(screen) != null;
    }

    private static void processOpenMenu(Minecraft client, long now) {
        Screen screen = ClientCompat.currentScreen(client);
        AbstractContainerMenu menu = menu(screen);
        if (menu == null || now < nextActionAtMs) return;

        if (phase == Phase.WAITING_FOR_ROOT) {
            Slot sideJobs = findSideJobsSlot(client, menu);
            if (sideJobs == null) return;
            client.gameMode.handleContainerInput(
                    menu.containerId, sideJobs.index, 0, ContainerInput.PICKUP, client.player
            );
            phase = Phase.WAITING_FOR_LIST;
            deadlineMs = now + LIST_TIMEOUT_MS;
            nextActionAtMs = now + CLICK_DELAY_MS;
            return;
        }

        Map<String, JobState> scanned = scanJobs(client, menu, now);
        if (scanned.isEmpty()) return;
        JOBS.clear();
        JOBS.putAll(scanned);
        lastSuccessfulSyncMs = now;
        nextRefreshAtMs = 0L;
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
        Slot fallback = null;
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (client.player != null && slot.container == client.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (isSideJobsItem(stack)) return slot;
            if (slot.index == SIDE_JOBS_FALLBACK_SLOT) fallback = slot;
        }
        return fallback;
    }

    private static boolean isSideJobsItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (isSideJobsLabel(stack.getHoverName().getString())) return true;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            if (isSideJobsDescription(line.getString())) return true;
        }
        return false;
    }

    private static void abort(Minecraft client) {
        boolean ownedMenu = shouldSuppress(ClientCompat.currentScreen(client));
        phase = Phase.IDLE;
        deadlineMs = 0L;
        nextActionAtMs = 0L;
        nextRefreshAtMs = 0L;
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
        return screen != null && isJobMenuTitle(screen.getTitle().getString());
    }

    static boolean isJobMenuTitle(String title) {
        return compactKey(title).contains(JOB_MENU_TITLE);
    }

    static boolean isSideJobsLabel(String label) {
        String normalized = compactKey(label);
        return normalized.equals(SIDE_JOBS_NAME) || normalized.equals(SIDE_JOBS_NAME + "s");
    }

    static boolean isSideJobsDescription(String line) {
        String normalized = compactKey(line);
        return normalized.contains("kurzerundenmitwartezeitdazwischen");
    }

    private static String compactKey(String raw) {
        return key(raw).replace(" ", "");
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
