package com.betteruc.mixin;

import com.betteruc.client.WeaponEquipAnimationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"
            )
    )
    private float betteruc$removeSupportedWeaponToolSwapDelay(LocalPlayer player, float partialTick) {
        float vanillaScale = player.getItemSwapScale(partialTick);
        return WeaponEquipAnimationController.itemSwapScale(player.getMainHandItem(), vanillaScale);
    }

    @ModifyArgs(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;clamp(FFF)F",
                    ordinal = 2
            )
    )
    private void betteruc$accelerateSupportedWeaponEquipAnimation(Args args) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        float step = WeaponEquipAnimationController.equipStep(client.player.getMainHandItem());
        if (step <= WeaponEquipAnimationController.VANILLA_STEP) return;

        args.set(1, -step);
        args.set(2, step);
    }
}
