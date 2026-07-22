package com.betteruc.mixin;

import com.betteruc.client.TrashFilterClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class TrashFilterScreenMixin {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void betteruc$highlightTrashItems(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        Screen screen = (Screen) (Object) this;
        if (!(screen instanceof MenuAccess<?> access)) return;

        context.nextStratum();
        for (Slot slot : access.getMenu().slots) {
            if (!TrashFilterClient.shouldHighlight(screen, slot)) continue;

            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            context.fill(x, y, x + 16, y + 16, 0x6634D399);
            context.outline(x, y, 16, 16, 0xFF4ADE80);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void betteruc$preventEarlyTrashClose(CallbackInfo ci) {
        if (TrashFilterClient.shouldPreventClose((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
