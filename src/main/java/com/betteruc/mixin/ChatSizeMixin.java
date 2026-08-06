package com.betteruc.mixin;

import com.betteruc.client.ClientCompat;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatSizeMixin {

    @Inject(method = "getWidth(D)I", at = @At("RETURN"), cancellable = true)
    private static void betteruc$customChatWidth(double value, CallbackInfoReturnable<Integer> cir) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (!config.secondChatPrimaryCustomSize) return;

        Minecraft client = Minecraft.getInstance();
        double scale = client == null || client.options == null
                ? 1.0D
                : Math.max(0.01D, client.options.chatScale().get());
        int padding = (int) Math.ceil(8.0D * scale);
        int screenWidth = ClientCompat.scaledWindowWidth(client, 854);
        int maximum = Math.max(40, screenWidth - padding);
        cir.setReturnValue(clamp(config.secondChatWidth - padding, 40, maximum));
    }

    @Inject(method = "getHeight(D)I", at = @At("RETURN"), cancellable = true)
    private static void betteruc$customChatHeight(double value, CallbackInfoReturnable<Integer> cir) {
        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (!config.secondChatPrimaryCustomSize) return;

        Minecraft client = Minecraft.getInstance();
        int screenHeight = ClientCompat.scaledWindowHeight(client, 480);
        cir.setReturnValue(clamp(config.secondChatHeight, 60, Math.max(60, screenHeight - 40)));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
