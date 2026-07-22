package com.betteruc.hud;

import com.betteruc.client.ClientCompat;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmmoHud {

    private static final Pattern AMMO_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,3})\\s*/\\s*(\\d{1,4})(?!\\d)");
    private static final long DISPLAY_TIMEOUT_MS = 12_000L;
    private static final long RELOAD_CONFIRM_TIMEOUT_MS = 3_000L;
    private static final int MAX_PLAUSIBLE_CLIP_AMMO = 250;
    private static final int MAX_PLAUSIBLE_RESERVE_AMMO = 9_999;
    private static final int NORMAL_AMMO_COLOR = 0xFFFFAA33;
    private static final int LOW_AMMO_COLOR = 0xFFFF4D5A;
    private static final int WEAPON_COLOR = 0xFF7CFF8A;

    private static int clipAmmo = -1;
    private static int reserveAmmo = -1;
    private static String weaponName = "";
    private static long lastUpdateMs = 0L;
    private static Component ammoText = Component.literal("");
    private static Component weaponText = Component.literal("");
    private static boolean reloadKeyWasDown = false;
    private static boolean reloadAwaitingConfirmation = false;
    private static long reloadRequestedAtMs = 0L;
    private static String lastHeldItemFingerprint = "";
    private static String activeWeaponFingerprint = "";
    private static WeaponProfile activeWeaponProfile = WeaponProfile.UNKNOWN;
    private static boolean lowAmmoActive = false;
    private static final Set<String> learnedWeaponFingerprints = new HashSet<>();

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "ammo"), (context, tickCounter) -> {
            if (ModernHudRenderer.shouldRenderGameplayHud()) render(context);
        });
    }

    public static void updateFromOverlay(Component overlayMessage) {
        if (overlayMessage == null) return;

        String raw = overlayMessage.getString();
        if (raw == null || raw.isBlank()) return;
        if (isProgressOverlay(raw)) return;

        Matcher matcher = AMMO_PATTERN.matcher(raw);
        if (!matcher.find()) return;

        int parsedClip;
        int parsedReserve;
        try {
            parsedClip = Integer.parseInt(matcher.group(1));
            parsedReserve = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return;
        }
        if (parsedClip > MAX_PLAUSIBLE_CLIP_AMMO || parsedReserve > MAX_PLAUSIBLE_RESERVE_AMMO) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ItemStack heldItem = client.player.getMainHandItem();
        String heldFingerprint = itemFingerprint(heldItem);
        if (heldFingerprint.isBlank()) return;

        WeaponProfile profile = WeaponProfile.fromItemName(heldItem.getHoverName().getString());
        boolean reloadConfirmed = reloadAwaitingConfirmation;
        learnKr47Magazine(profile, parsedClip, reloadConfirmed);
        int magazineSize = magazineSize(profile);
        if (magazineSize > 0 && parsedClip > magazineSize) return;

        boolean weaponChanged = !heldFingerprint.equals(activeWeaponFingerprint);

        clipAmmo = parsedClip;
        reserveAmmo = parsedReserve;
        weaponName = extractWeaponName(raw);
        if (weaponName.isBlank() && profile != WeaponProfile.UNKNOWN) {
            weaponName = profile.displayName;
        }
        learnedWeaponFingerprints.add(heldFingerprint);
        activeWeaponFingerprint = heldFingerprint;
        activeWeaponProfile = profile;
        lastHeldItemFingerprint = heldFingerprint;
        reloadAwaitingConfirmation = false;
        reloadRequestedAtMs = 0L;
        refreshDisplayText();
        lastUpdateMs = System.currentTimeMillis();
        updateLowAmmoWarning(client, weaponChanged);
    }

    public static void tickReloadKey(Minecraft client) {
        if (client == null || client.player == null) {
            reloadKeyWasDown = false;
            return;
        }
        if (ClientCompat.hasScreen(client)) {
            reloadKeyWasDown = false;
            return;
        }

        String heldFingerprint = itemFingerprint(client.player.getMainHandItem());
        if (!heldFingerprint.equals(lastHeldItemFingerprint)) {
            lastHeldItemFingerprint = heldFingerprint;
            invalidateDisplayedAmmo();
        }

        if (reloadAwaitingConfirmation
                && System.currentTimeMillis() - reloadRequestedAtMs > RELOAD_CONFIRM_TIMEOUT_MS) {
            reloadAwaitingConfirmation = false;
            reloadRequestedAtMs = 0L;
            refreshDisplayText();
        }

        boolean qDown = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS;
        if (qDown && !reloadKeyWasDown) {
            requestServerConfirmedReload(heldFingerprint);
        }
        reloadKeyWasDown = qDown;
    }

    public static void clear() {
        clipAmmo = -1;
        reserveAmmo = -1;
        weaponName = "";
        lastUpdateMs = 0L;
        ammoText = Component.literal("");
        weaponText = Component.literal("");
        reloadKeyWasDown = false;
        reloadAwaitingConfirmation = false;
        reloadRequestedAtMs = 0L;
        lastHeldItemFingerprint = "";
        activeWeaponFingerprint = "";
        activeWeaponProfile = WeaponProfile.UNKNOWN;
        lowAmmoActive = false;
        learnedWeaponFingerprints.clear();
    }

    private static void render(GuiGraphicsExtractor context) {
        if (!BetterUCConfig.INSTANCE.showAmmoHud) return;
        if (clipAmmo < 0 || reserveAmmo < 0) return;
        if (lastUpdateMs <= 0L) return;
        if (System.currentTimeMillis() - lastUpdateMs > DISPLAY_TIMEOUT_MS) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        String heldFingerprint = itemFingerprint(client.player.getMainHandItem());
        if (heldFingerprint.isBlank()
                || !heldFingerprint.equals(activeWeaponFingerprint)
                || !learnedWeaponFingerprints.contains(heldFingerprint)) return;

        int x = BetterUCConfig.INSTANCE.ammoHudX;
        int y = BetterUCConfig.INSTANCE.ammoHudY;

        String style = BetterUCConfig.INSTANCE.ammoHudStyle;
        String ammoValue = ammoText.getString();
        String weaponValue = currentWeaponLine();
        int magazineSize = magazineSize(activeWeaponProfile);
        float magazineProgress = magazineSize <= 0 ? -1.0F : clipAmmo / (float) magazineSize;
        boolean lowAmmo = isLowAmmo(magazineSize);
        int ammoColor = lowAmmo ? LOW_AMMO_COLOR : NORMAL_AMMO_COLOR;
        int weaponColor = clipAmmo == 0 ? LOW_AMMO_COLOR : WEAPON_COLOR;
        String ammoDisplay = BetterUCConfig.prefixedHudText(
                BetterUCConfig.INSTANCE.ammoHudPrefixEnabled,
                BetterUCConfig.INSTANCE.ammoHudPrefix,
                ammoValue
        );
        Component ammoDisplayText = Component.literal(ammoDisplay);
        String moduleLabel = BetterUCConfig.hudModuleLabel(
                BetterUCConfig.INSTANCE.ammoHudPrefixEnabled,
                BetterUCConfig.INSTANCE.ammoHudPrefix
        );
        ModernHudRenderer.drawScaledWithGradient(
                context,
                x,
                y,
                BetterUCConfig.INSTANCE.ammoHudScale,
                BetterUCConfig.INSTANCE.ammoHudGradientEnabled,
                BetterUCConfig.INSTANCE.ammoHudGradientColor,
                () -> {
            if (BetterUCConfig.isStylizedHudStyle(style)) {
                ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.ammoHudCustomFont, ammoDisplayText, 0, 0, ammoColor);
                if (!weaponValue.isBlank()) {
                    ModernHudRenderer.drawStyledText(context, client.font, style, BetterUCConfig.INSTANCE.ammoHudCustomFont,
                            Component.literal(weaponValue), 0, 11, weaponColor);
                }
                drawCompactMagazineBar(context, client, ammoDisplay, weaponValue, magazineProgress, ammoColor, 23);
            } else if (!BetterUCConfig.isModernHudStyle(style)) {
                ModernHudRenderer.drawHudTextWithShadow(context, client.font, ammoDisplayText, 0, 0, ammoColor);
                if (!weaponValue.isBlank()) {
                    ModernHudRenderer.drawHudTextWithShadow(context, client.font, weaponValue, 0, 10, weaponColor);
                }
                drawCompactMagazineBar(context, client, ammoDisplay, weaponValue, magazineProgress, ammoColor, 21);
            } else {
                if (BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled && magazineProgress >= 0.0F) {
                    ModernHudRenderer.drawTwoLineProgressModule(
                            context, client, 0, 0, moduleLabel, ammoValue, weaponValue,
                            magazineProgress, ammoColor, weaponColor
                    );
                } else {
                    ModernHudRenderer.drawTwoLineModule(
                            context, client, 0, 0, moduleLabel, ammoValue, weaponValue, ammoColor, weaponColor
                    );
                }
            }
        });
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

    private static boolean isProgressOverlay(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return normalized.contains("battle pass")
                || normalized.contains("battlepass")
                || normalized.contains("sommer pass")
                || normalized.contains("fortschritt")
                || normalized.contains("aufgabe");
    }

    private static void requestServerConfirmedReload(String heldFingerprint) {
        if (clipAmmo < 0 || reserveAmmo < 0) return;
        if (heldFingerprint.isBlank() || !heldFingerprint.equals(activeWeaponFingerprint)) return;

        reloadAwaitingConfirmation = true;
        reloadRequestedAtMs = System.currentTimeMillis();
        refreshDisplayText();
        lastUpdateMs = reloadRequestedAtMs;
    }

    private static String normalizeWeaponName(String raw) {
        return raw == null
                ? ""
                : raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static String itemFingerprint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return "";
        return id + "|" + normalizeWeaponName(stack.getHoverName().getString());
    }

    private static void invalidateDisplayedAmmo() {
        clipAmmo = -1;
        reserveAmmo = -1;
        weaponName = "";
        lastUpdateMs = 0L;
        activeWeaponFingerprint = "";
        activeWeaponProfile = WeaponProfile.UNKNOWN;
        lowAmmoActive = false;
        reloadAwaitingConfirmation = false;
        reloadRequestedAtMs = 0L;
        ammoText = Component.literal("");
        weaponText = Component.literal("");
    }

    private static void refreshDisplayText() {
        ammoText = Component.literal(clipAmmo + "/" + reserveAmmo);
        String displayName = weaponName;
        if (reloadAwaitingConfirmation) {
            displayName = displayName.isBlank() ? "Nachladen..." : displayName + " | Nachladen...";
        }
        weaponText = displayName.isBlank() ? Component.literal("") : Component.literal(displayName);
    }

    private static String currentWeaponLine() {
        String displayName = weaponName;
        if (displayName.isBlank() && activeWeaponProfile != WeaponProfile.UNKNOWN) {
            displayName = activeWeaponProfile.displayName;
        }
        if (clipAmmo == 0) {
            return displayName.isBlank() ? "NACHLADEN" : displayName + " | NACHLADEN";
        }
        if (!reloadAwaitingConfirmation) return displayName;

        int dots = (int) ((System.currentTimeMillis() / 350L) % 3L) + 1;
        String reloadText = "Nachladen" + ".".repeat(dots);
        return displayName.isBlank() ? reloadText : displayName + " | " + reloadText;
    }

    private static void drawCompactMagazineBar(
            GuiGraphicsExtractor context,
            Minecraft client,
            String ammoDisplay,
            String weaponDisplay,
            float progress,
            int color,
            int y
    ) {
        if (!BetterUCConfig.INSTANCE.ammoHudMagazineBarEnabled || progress < 0.0F) return;
        int width = Math.max(24, Math.max(client.font.width(ammoDisplay), client.font.width(weaponDisplay)));
        context.fill(0, y, width, y + 2, 0x66313A47);
        int filled = Math.round(width * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            context.fill(0, y, filled, y + 2, color);
        }
    }

    private static void updateLowAmmoWarning(Minecraft client, boolean weaponChanged) {
        int magazineSize = magazineSize(activeWeaponProfile);
        boolean lowNow = isLowAmmo(magazineSize);
        if (lowNow && (weaponChanged || !lowAmmoActive)
                && BetterUCConfig.INSTANCE.ammoHudLowAmmoWarningEnabled
                && BetterUCConfig.INSTANCE.ammoHudLowAmmoSoundEnabled
                && client.player != null) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 0.45F, 1.65F);
        }
        lowAmmoActive = lowNow;
    }

    private static boolean isLowAmmo(int magazineSize) {
        if (!BetterUCConfig.INSTANCE.ammoHudLowAmmoWarningEnabled || magazineSize <= 0 || clipAmmo < 0) {
            return false;
        }
        return clipAmmo * 100 <= magazineSize * BetterUCConfig.INSTANCE.ammoHudLowAmmoThresholdPercent;
    }

    private static int magazineSize(WeaponProfile profile) {
        if (profile == WeaponProfile.KR47) {
            return BetterUCConfig.INSTANCE.ammoHudKr47MagazineSize;
        }
        return profile.magazineSize;
    }

    private static void learnKr47Magazine(WeaponProfile profile, int parsedClip, boolean reloadConfirmed) {
        if (profile != WeaponProfile.KR47) return;

        int learned = BetterUCConfig.INSTANCE.ammoHudKr47MagazineSize;
        if (parsedClip == 30 && learned != 30) {
            BetterUCConfig.INSTANCE.ammoHudKr47MagazineSize = 30;
            BetterUCConfig.save();
        } else if (parsedClip == 25 && learned == 0 && reloadConfirmed) {
            BetterUCConfig.INSTANCE.ammoHudKr47MagazineSize = 25;
            BetterUCConfig.save();
        }
    }

    private enum WeaponProfile {
        P69("P69", 15, "p69"),
        SCATTER3("Scatter3", 25, "scatter3"),
        TS19("TS19", 21, "ts19"),
        EXTENSO18("Extenso18", 5, "extenso18"),
        VIPER9("Viper9", 5, "viper9"),
        KR47("KR47", 0, "kr47"),
        AX12("AX12", 25, "ax12"),
        UNKNOWN("", 0, "");

        private final String displayName;
        private final int magazineSize;
        private final String nameKey;

        WeaponProfile(String displayName, int magazineSize, String nameKey) {
            this.displayName = displayName;
            this.magazineSize = magazineSize;
            this.nameKey = nameKey;
        }

        private static WeaponProfile fromItemName(String itemName) {
            String normalized = normalizeWeaponName(itemName);
            for (WeaponProfile profile : values()) {
                if (profile != UNKNOWN && normalized.contains(profile.nameKey)) {
                    return profile;
                }
            }
            return UNKNOWN;
        }
    }
}
