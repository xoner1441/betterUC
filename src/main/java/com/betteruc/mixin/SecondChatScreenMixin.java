package com.betteruc.mixin;

import com.betteruc.client.ChatCopyClient;
import com.betteruc.gui.SecondChatOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class SecondChatScreenMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void betteruc$renderChatTabs(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        SecondChatOverlay.render((Screen) (Object) this, context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betteruc$clickChatTabs(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SecondChatOverlay.mouseClicked((Screen) (Object) this, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 2 && ChatCopyClient.copyHoveredMainChatLine(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void betteruc$scrollChatTabs(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SecondChatOverlay.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }
}
