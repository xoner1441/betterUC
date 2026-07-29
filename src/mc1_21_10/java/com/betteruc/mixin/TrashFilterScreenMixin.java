package com.betteruc.mixin;

import com.betteruc.client.AutoBuyClient;
import com.betteruc.client.TrashFilterClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class TrashFilterScreenMixin {
    private static final int BETTERUC_CANCEL_WIDTH = 150;
    private static final int BETTERUC_CANCEL_HEIGHT = 18;

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;

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

    @Inject(method = "render", at = @At("TAIL"))
    private void betteruc$drawAutoBuyCancel(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
    ) {
        Screen screen = (Screen) (Object) this;
        if (!AutoBuyClient.shouldShowCancelButton(screen)) return;

        int buttonX = betteruc$cancelX();
        int buttonY = betteruc$cancelY();
        boolean hovered = betteruc$isInsideCancel(mouseX, mouseY);
        int background = hovered ? 0xEE7F1D1D : 0xDD3F1717;
        int border = hovered ? 0xFFFF6B6B : 0xFFDC4C4C;
        String label = "Auto-Kauf abbrechen";
        MinecraftClient client = MinecraftClient.getInstance();

        context.fill(buttonX, buttonY, buttonX + BETTERUC_CANCEL_WIDTH,
                buttonY + BETTERUC_CANCEL_HEIGHT, background);
        context.fill(buttonX, buttonY, buttonX + BETTERUC_CANCEL_WIDTH, buttonY + 1, border);
        context.fill(buttonX, buttonY + BETTERUC_CANCEL_HEIGHT - 1,
                buttonX + BETTERUC_CANCEL_WIDTH, buttonY + BETTERUC_CANCEL_HEIGHT, border);
        context.fill(buttonX, buttonY, buttonX + 1, buttonY + BETTERUC_CANCEL_HEIGHT, border);
        context.fill(buttonX + BETTERUC_CANCEL_WIDTH - 1, buttonY,
                buttonX + BETTERUC_CANCEL_WIDTH, buttonY + BETTERUC_CANCEL_HEIGHT, border);
        int textX = buttonX + (BETTERUC_CANCEL_WIDTH - client.textRenderer.getWidth(label)) / 2;
        context.drawTextWithShadow(client.textRenderer, Text.literal(label), textX, buttonY + 5, 0xFFFFFFFF);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betteruc$clickAutoBuyCancel(
            Click event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Screen screen = (Screen) (Object) this;
        if (event.button() != 0) return;
        if (AutoBuyClient.shouldShowCancelButton(screen)
                && betteruc$isInsideCancel(event.x(), event.y())) {
            AutoBuyClient.cancel(MinecraftClient.getInstance(), true);
            cir.setReturnValue(true);
            return;
        }

        Slot clickedSlot = betteruc$slotAt(event.x(), event.y());
        if (clickedSlot != null) {
            AutoBuyClient.rememberProductSelection(MinecraftClient.getInstance(), screen, clickedSlot);
        }
    }

    private int betteruc$cancelX() {
        return x + Math.max(0, (backgroundWidth - BETTERUC_CANCEL_WIDTH) / 2);
    }

    private int betteruc$cancelY() {
        return Math.max(4, y - BETTERUC_CANCEL_HEIGHT - 4);
    }

    private boolean betteruc$isInsideCancel(double mouseX, double mouseY) {
        int buttonX = betteruc$cancelX();
        int buttonY = betteruc$cancelY();
        return mouseX >= buttonX && mouseX < buttonX + BETTERUC_CANCEL_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + BETTERUC_CANCEL_HEIGHT;
    }

    private Slot betteruc$slotAt(double mouseX, double mouseY) {
        for (Slot slot : ((HandledScreen<?>) (Object) this).getScreenHandler().slots) {
            int slotX = x + slot.x;
            int slotY = y + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }
        return null;
    }
}
