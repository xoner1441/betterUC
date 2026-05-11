package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmmoHud {

    private static final Pattern AMMO_PATTERN = Pattern.compile("(\\d{1,4})\\s*/\\s*(\\d{1,4})");
    private static final long DISPLAY_TIMEOUT_MS = 12_000L;

    private static int clipAmmo = -1;
    private static int reserveAmmo = -1;
    private static String weaponName = "";
    private static long lastUpdateMs = 0L;

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
        lastUpdateMs = System.currentTimeMillis();
    }

    public static void clear() {
        clipAmmo = -1;
        reserveAmmo = -1;
        weaponName = "";
        lastUpdateMs = 0L;
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

        String ammoText = clipAmmo + "/" + reserveAmmo;
        context.drawTextWithShadow(client.textRenderer, Text.literal(ammoText), x, y, 0xFFFFAA33);

        if (!weaponName.isBlank()) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(weaponName), x, y + 10, 0xFF55FF55);
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
}
