package com.betteruc.mixin;

import com.betteruc.client.AutoBuyClient;
import com.betteruc.client.TrashFilterClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class TrashFilterScreenMixin {
    private static final int BETTERUC_CANCEL_WIDTH = 150;
    private static final int BETTERUC_CANCEL_HEIGHT = 18;

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;

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
        boolean renderHighlights = TrashFilterClient.shouldRenderHighlights(screen);
        boolean renderAutoBuyCancel = AutoBuyClient.shouldShowCancelButton(screen);
        if (!renderHighlights && !renderAutoBuyCancel) return;

        context.nextStratum();
        if (renderHighlights) {
            for (Slot slot : access.getMenu().slots) {
                if (!TrashFilterClient.shouldHighlight(screen, slot)) continue;

                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                context.fill(x, y, x + 16, y + 16, 0x6634D399);
                context.outline(x, y, 16, 16, 0xFF4ADE80);
            }
        }

        if (renderAutoBuyCancel) {
            betteruc$drawAutoBuyCancel(context, mouseX, mouseY);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void betteruc$preventEarlyTrashClose(CallbackInfo ci) {
        if (TrashFilterClient.shouldPreventClose((Screen) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betteruc$clickAutoBuyCancel(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Screen screen = (Screen) (Object) this;
        if (event.button() != 0) return;
        if (AutoBuyClient.shouldShowCancelButton(screen)
                && betteruc$isInsideCancel(event.x(), event.y())) {
            AutoBuyClient.cancel(Minecraft.getInstance(), true);
            cir.setReturnValue(true);
            return;
        }

        if (!AutoBuyClient.shouldShowCancelButton(screen)) return;

        Slot clickedSlot = betteruc$slotAt(event.x(), event.y(), screen);
        if (clickedSlot != null) {
            AutoBuyClient.rememberProductSelection(Minecraft.getInstance(), screen, clickedSlot);
        }
    }

    private void betteruc$drawAutoBuyCancel(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY
    ) {
        int x = betteruc$cancelX();
        int y = betteruc$cancelY();
        boolean hovered = betteruc$isInsideCancel(mouseX, mouseY);
        int background = hovered ? 0xEE7F1D1D : 0xDD3F1717;
        int border = hovered ? 0xFFFF6B6B : 0xFFDC4C4C;
        String label = "Auto-Kauf abbrechen";
        Minecraft client = Minecraft.getInstance();

        context.fill(x, y, x + BETTERUC_CANCEL_WIDTH, y + BETTERUC_CANCEL_HEIGHT, background);
        context.outline(x, y, BETTERUC_CANCEL_WIDTH, BETTERUC_CANCEL_HEIGHT, border);
        int textX = x + (BETTERUC_CANCEL_WIDTH - client.font.width(label)) / 2;
        context.text(client.font, Component.literal(label), textX, y + 5, 0xFFFFFFFF);
    }

    private int betteruc$cancelX() {
        return leftPos + Math.max(0, (imageWidth - BETTERUC_CANCEL_WIDTH) / 2);
    }

    private int betteruc$cancelY() {
        return Math.max(4, topPos - BETTERUC_CANCEL_HEIGHT - 4);
    }

    private boolean betteruc$isInsideCancel(double mouseX, double mouseY) {
        int x = betteruc$cancelX();
        int y = betteruc$cancelY();
        return mouseX >= x && mouseX < x + BETTERUC_CANCEL_WIDTH
                && mouseY >= y && mouseY < y + BETTERUC_CANCEL_HEIGHT;
    }

    private Slot betteruc$slotAt(double mouseX, double mouseY, Screen screen) {
        if (!(screen instanceof MenuAccess<?> access)) return null;

        for (Slot slot : access.getMenu().slots) {
            int slotX = leftPos + slot.x;
            int slotY = topPos + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }
        return null;
    }
}
