package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmmoHud {

    private static final Pattern AMMO_PATTERN = Pattern.compile("(\\d{1,4})\\s*/\\s*(\\d{1,4})");
    private static final long DISPLAY_TIMEOUT_MS = 12_000L;
    private static final int DEFAULT_TS19_MAGAZINE_SIZE = 21;

    private static int clipAmmo = -1;
    private static int reserveAmmo = -1;
    private static String weaponName = "";
    private static long lastUpdateMs = 0L;
    private static Text ammoText = Text.literal("");
    private static Text weaponText = Text.literal("");
    private static boolean reloadKeyWasDown = false;
    private static final Map<String, Integer> observedMagazineSizes = new HashMap<>();

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void updateFromOverlay(Text overlayMessage) {
        if (overlayMessage == null) return;

        String raw = overlayMessage.getString();
        if (raw == null || raw.isBlank()) return;

        Matcher matcher = AMMO_PATTERN.matcher(raw);
        if (!matcher.find()) return;

        try {
            clipAmmo = Integer.parseInt(matcher.group(1));
            reserveAmmo = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return;
        }

        weaponName = extractWeaponName(raw);
        rememberMagazineSize();
        refreshDisplayText();
        lastUpdateMs = System.currentTimeMillis();
    }

    public static void tickReloadKey(MinecraftClient client) {
        if (client == null || client.player == null) {
            reloadKeyWasDown = false;
            return;
        }
        if (client.currentScreen != null) {
            reloadKeyWasDown = false;
            return;
        }

        boolean qDown = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS;
        if (qDown && !reloadKeyWasDown) {
            applyOptimisticReload();
        }
        reloadKeyWasDown = qDown;
    }

    public static void clear() {
        clipAmmo = -1;
        reserveAmmo = -1;
        weaponName = "";
        lastUpdateMs = 0L;
        ammoText = Text.literal("");
        weaponText = Text.literal("");
        reloadKeyWasDown = false;
    }

    private static void render(DrawContext context) {
        if (!BetterUCConfig.INSTANCE.showAmmoHud) return;
        if (clipAmmo < 0 || reserveAmmo < 0) return;
        if (lastUpdateMs <= 0L) return;
        if (System.currentTimeMillis() - lastUpdateMs > DISPLAY_TIMEOUT_MS) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int x = BetterUCConfig.INSTANCE.ammoHudX;
        int y = BetterUCConfig.INSTANCE.ammoHudY;

        context.drawTextWithShadow(client.textRenderer, ammoText, x, y, 0xFFFFAA33);

        if (!weaponName.isBlank()) {
            context.drawTextWithShadow(client.textRenderer, weaponText, x, y + 10, 0xFF55FF55);
        }
    }

    private static String extractWeaponName(String raw) {
        String fallback = "";
        String[] lines = raw.split("\\R");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) continue;

            if (AMMO_PATTERN.matcher(trimmed).find()) continue;
            if (trimmed.matches("[0-9\\s/:]+")) continue;

            fallback = trimmed;
            break;
        }
        return fallback;
    }

    private static void applyOptimisticReload() {
        if (clipAmmo < 0 || reserveAmmo < 0) return;

        int magazineSize = resolveMagazineSize();
        if (magazineSize <= 0 || clipAmmo >= magazineSize) {
            lastUpdateMs = System.currentTimeMillis();
            return;
        }

        int missingAmmo = magazineSize - clipAmmo;
        int loadedAmmo = reserveAmmo > 0 ? Math.min(missingAmmo, reserveAmmo) : missingAmmo;
        if (loadedAmmo <= 0) return;

        clipAmmo += loadedAmmo;
        if (reserveAmmo > 0) {
            reserveAmmo = Math.max(0, reserveAmmo - loadedAmmo);
        }
        refreshDisplayText();
        lastUpdateMs = System.currentTimeMillis();
    }

    private static void rememberMagazineSize() {
        if (clipAmmo <= 0 || weaponName.isBlank()) return;

        String key = normalizeWeaponName(weaponName);
        int known = observedMagazineSizes.getOrDefault(key, 0);
        if (clipAmmo > known) {
            observedMagazineSizes.put(key, clipAmmo);
        }
    }

    private static int resolveMagazineSize() {
        if (weaponName.isBlank()) return clipAmmo;

        String key = normalizeWeaponName(weaponName);
        int observed = observedMagazineSizes.getOrDefault(key, 0);
        if ("ts19".equals(key)) {
            return Math.max(observed, DEFAULT_TS19_MAGAZINE_SIZE);
        }
        return observed > 0 ? observed : clipAmmo;
    }

    private static String normalizeWeaponName(String raw) {
        return raw == null
                ? ""
                : raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static void refreshDisplayText() {
        ammoText = Text.literal(clipAmmo + "/" + reserveAmmo);
        weaponText = weaponName.isBlank() ? Text.literal("") : Text.literal(weaponName);
    }
}
