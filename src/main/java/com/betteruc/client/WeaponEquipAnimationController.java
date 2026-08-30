package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class WeaponEquipAnimationController {

    public static final float VANILLA_STEP = 0.4F;
    static final float FAST_STEP = 0.8F;
    static final float INSTANT_STEP = 1.0F;

    private WeaponEquipAnimationController() {
    }

    public static float equipStep(ItemStack heldItem) {
        return equipStep(
                BetterUCConfig.INSTANCE.weaponEquipAnimationEnabled,
                BetterUCConfig.INSTANCE.weaponEquipAnimationMode,
                WeaponNameMatcher.isSupportedWeapon(heldItem)
        );
    }

    public static float itemSwapScale(ItemStack heldItem, float vanillaScale) {
        return itemSwapScale(
                BetterUCConfig.INSTANCE.weaponEquipAnimationEnabled,
                WeaponNameMatcher.isSupportedWeapon(heldItem),
                vanillaScale
        );
    }

    static float itemSwapScale(boolean enabled, boolean supportedWeapon, float vanillaScale) {
        return enabled && supportedWeapon ? 1.0F : vanillaScale;
    }

    static float equipStep(boolean enabled, String mode, boolean supportedWeapon) {
        if (!enabled || !supportedWeapon) {
            return VANILLA_STEP;
        }
        return "instant".equals(normalizeMode(mode)) ? INSTANT_STEP : FAST_STEP;
    }

    public static String modeLabel() {
        return "instant".equals(normalizeMode(BetterUCConfig.INSTANCE.weaponEquipAnimationMode))
                ? "Sofort"
                : "Schnell";
    }

    public static void cycleMode() {
        BetterUCConfig.INSTANCE.weaponEquipAnimationMode =
                "instant".equals(normalizeMode(BetterUCConfig.INSTANCE.weaponEquipAnimationMode))
                        ? "fast"
                        : "instant";
    }

    static String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        return "instant".equals(normalized) ? "instant" : "fast";
    }
}
