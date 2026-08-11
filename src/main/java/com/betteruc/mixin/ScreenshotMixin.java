package com.betteruc.mixin;

import com.betteruc.client.ScreenshotActionsClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @Inject(
            method = "lambda$grab$3(Lcom/mojang/blaze3d/platform/NativeImage;Ljava/io/File;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;close()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private static void betteruc$afterScreenshotSaved(
            NativeImage image,
            File file,
            Consumer<Component> callback,
            CallbackInfo ci
    ) {
        ScreenshotActionsClient.onScreenshotSaved(file.toPath());
    }
}
