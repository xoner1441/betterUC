package com.betteruc.mixin;

import com.betteruc.client.TrashFilterClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class TrashFilterScreenMixin {
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void betteruc$drawTrashHighlightBackground(DrawContext context, Slot slot, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!TrashFilterClient.shouldHighlight(screen, slot)) return;

        context.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, 0xAA15803D);
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void betteruc$drawTrashHighlightBorder(DrawContext context, Slot slot, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!TrashFilterClient.shouldHighlight(screen, slot)) return;

        context.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, 0xFF4ADE80);
        context.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, 0xFF4ADE80);
        context.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, 0xFF4ADE80);
        context.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, 0xFF4ADE80);
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void betteruc$preventEarlyTrashClose(CallbackInfo ci) {
        if (TrashFilterClient.shouldPreventClose((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
