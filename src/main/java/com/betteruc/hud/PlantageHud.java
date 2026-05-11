package com.betteruc.hud;

import com.betteruc.PlayerNameUtil;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlantageHud {

    private static final long GROW_DURATION_MS = 90L * 60L * 1000L;
    private static final long WATER_INTERVAL_MS = 20L * 60L * 1000L;
    private static final long FERTILIZE_INTERVAL_MS = 25L * 60L * 1000L;
    private static final long RECENT_PLANT_WINDOW_MS = 3500L;
    private static final long EXPIRED_DISPLAY_MS = 30L * 60L * 1000L;
    private static final int LINE_HEIGHT = 10;
    private static final Pattern COUNT_PATTERN = Pattern.compile("\\[(\\d{1,2})\\s*/\\s*10\\]");

    private static final Map<PlantageType, PlantageState> STATES = new LinkedHashMap<>();
    private static PlantageType lastPlacedType = null;
    private static long lastPlacedMessageMs = 0L;

    static {
        STATES.put(PlantageType.PULVER, new PlantageState(PlantageType.PULVER));
        STATES.put(PlantageType.KRAEUTER, new PlantageState(PlantageType.KRAEUTER));
    }

    private enum PlantageType {
        PULVER("Pulver", 0xFF6CF27D),
        KRAEUTER("Kr\u00E4uter", 0xFF8FE16A);

        private final String label;
        private final int color;

        PlantageType(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final class PlantageState {
        private final PlantageType type;
        private long plantedAtMs = 0L;
        private long nextWaterAtMs = 0L;
        private long nextFertilizeAtMs = 0L;
        private int count = 0;

        private PlantageState(PlantageType type) {
            this.type = type;
        }

        private boolean isActive(long now) {
            if (plantedAtMs <= 0L) return false;
            return now <= plantedAtMs + GROW_DURATION_MS + EXPIRED_DISPLAY_MS;
        }

        private void clear() {
            plantedAtMs = 0L;
            nextWaterAtMs = 0L;
            nextFertilizeAtMs = 0L;
            count = 0;
        }
    }

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void clear() {
        for (PlantageState state : STATES.values()) {
            state.clear();
        }
        lastPlacedType = null;
        lastPlacedMessageMs = 0L;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (PlantageState state : STATES.values()) {
            if (state.plantedAtMs > 0L && !state.isActive(now)) {
                state.clear();
            }
        }
    }

    public static void handleChatMessage(MinecraftClient client, String raw) {
        if (client == null || client.player == null || raw == null || raw.isBlank()) return;

        long now = System.currentTimeMillis();
        handleCountLine(raw, now);

        String folded = fold(raw);
        if (!folded.contains("plantage")) return;

        PlantageType type = detectType(folded);
        if (type == null) return;

        String profileName = PlayerNameUtil.resolveProfileName(client.player.getGameProfile());
        String ownName = fold(profileName == null || profileName.isBlank() ? client.player.getName().getString() : profileName);
        if (!ownName.isBlank() && !isOwnPlantageMessage(folded, ownName)) return;

        PlantageState state = STATES.get(type);
        if (state == null) return;

        if (folded.contains(" gelegt")) {
            state.plantedAtMs = now;
            state.nextWaterAtMs = now + WATER_INTERVAL_MS;
            state.nextFertilizeAtMs = now + FERTILIZE_INTERVAL_MS;
            lastPlacedType = type;
            lastPlacedMessageMs = now;
            return;
        }

        if (folded.contains(" gewassert")) {
            state.nextWaterAtMs = now + WATER_INTERVAL_MS;
            return;
        }

        if (folded.contains(" gedungt")) {
            state.nextFertilizeAtMs = now + FERTILIZE_INTERVAL_MS;
            return;
        }

        if (folded.contains(" geerntet")) {
            state.clear();
        }
    }

    private static void handleCountLine(String raw, long now) {
        if (lastPlacedType == null || now - lastPlacedMessageMs > RECENT_PLANT_WINDOW_MS) return;

        Matcher matcher = COUNT_PATTERN.matcher(raw);
        if (!matcher.find()) return;

        try {
            int count = Integer.parseInt(matcher.group(1));
            PlantageState state = STATES.get(lastPlacedType);
            if (state != null) {
                state.count = Math.max(1, Math.min(10, count));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static boolean isOwnPlantageMessage(String folded, String ownName) {
        String work = " " + folded + " ";
        String own = " " + ownName + " ";
        return work.contains(own + "hat eine ")
                || work.contains(" wurde von" + own);
    }

    private static PlantageType detectType(String folded) {
        if (folded.contains("pulver")) return PlantageType.PULVER;
        if (folded.contains("krauter")) return PlantageType.KRAEUTER;
        return null;
    }

    private static String fold(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        int x = BetterUCConfig.INSTANCE.plantTimerX;
        int y = BetterUCConfig.INSTANCE.plantTimerY;

        for (PlantageState state : STATES.values()) {
            if (!state.isActive(now)) continue;

            int blockHeight = LINE_HEIGHT * 2 + 8;
            String title = "Plantage " + state.type.label + (state.count > 0 ? " " + state.count + "/10" : "");
            String timers = "Reif: " + formatRemaining(state.plantedAtMs + GROW_DURATION_MS - now)
                    + " | Wasser: " + formatCare(state.nextWaterAtMs - now)
                    + " | D\u00FCnger: " + formatCare(state.nextFertilizeAtMs - now);

            int textWidth = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(timers));
            context.fill(x - 4, y - 4, x + textWidth + 4, y + blockHeight - 4, 0xAA000000);
            context.drawTextWithShadow(client.textRenderer, Text.literal(title), x, y, state.type.color);
            context.drawTextWithShadow(client.textRenderer, Text.literal(timers), x, y + LINE_HEIGHT, 0xFFFFD866);
            y += blockHeight;
        }
    }

    private static String formatRemaining(long ms) {
        if (ms <= 0L) return "reif";
        return formatDuration(ms);
    }

    private static String formatCare(long ms) {
        if (ms <= 0L) return "f\u00E4llig";
        return formatDuration(ms);
    }

    private static String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, (ms + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
