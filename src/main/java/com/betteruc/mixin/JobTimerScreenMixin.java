package com.betteruc.mixin;

import com.betteruc.client.JobTimerClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides only Job-Center screens opened by betterUC's short background refresh. */
@Mixin(AbstractContainerScreen.class)
public abstract class JobTimerScreenMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void betteruc$hideAutomaticJobRefresh(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        if (JobTimerClient.shouldSuppress((Screen) (Object) this)) ci.cancel();
    }
}
