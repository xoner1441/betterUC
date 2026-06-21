package com.betteruc.mixin;

import com.betteruc.client.TabBadgeRenderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudRenderContextMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD")
    )
    private void betteruc$beginPlayerListRender(
            GuiGraphicsExtractor context,
            int scaledWindowWidth,
            Scoreboard scoreboard,
            Objective objective,
            CallbackInfo ci
    ) {
        TabBadgeRenderState.beginPlayerListRender();
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("RETURN")
    )
    private void betteruc$endPlayerListRender(
            GuiGraphicsExtractor context,
            int scaledWindowWidth,
            Scoreboard scoreboard,
            Objective objective,
            CallbackInfo ci
    ) {
        TabBadgeRenderState.endPlayerListRender();
    }
}
