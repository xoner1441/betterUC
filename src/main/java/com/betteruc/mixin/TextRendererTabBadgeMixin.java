package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import com.betteruc.client.TabBadgeRenderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Font.class, priority = 50)
public abstract class TextRendererTabBadgeMixin {
    @Unique
    private static final Identifier BUC_BADGE_FONT = Identifier.fromNamespaceAndPath("betteruc", "buc_badges");
    @Unique
    private static final String USER_BADGE = "\uE100";
    @Unique
    private static final String ADMIN_BADGE = "\uE101";
    @Unique
    private static final String VIP_BADGE = "\uE102";
    @Unique
    private static final String HELPER_BADGE = "\uE103";
    @Unique
    private static final String PARTNER_BADGE = "\uE104";
    @Unique
    private static boolean betteruc$preparingBadge;

    @Inject(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void betteruc$prepareStringWithTabBadge(
            String text,
            float x,
            float y,
            int color,
            boolean shadow,
            int backgroundColor,
            CallbackInfoReturnable<Font.PreparedText> cir
    ) {
        Font textRenderer = (Font) (Object) this;
        betteruc$appendBadgeIfNeeded(textRenderer, cir, text, textRenderer.width(text), x, y, shadow);
    }

    @Inject(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void betteruc$prepareOrderedTextWithTabBadge(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            boolean seeThrough,
            int backgroundColor,
            CallbackInfoReturnable<Font.PreparedText> cir
    ) {
        Font textRenderer = (Font) (Object) this;
        betteruc$appendBadgeIfNeeded(textRenderer, cir, betteruc$plainText(text), textRenderer.width(text), x, y, shadow);
    }

    @Unique
    private static void betteruc$appendBadgeIfNeeded(
            Font textRenderer,
            CallbackInfoReturnable<Font.PreparedText> cir,
            String renderedText,
            int textWidth,
            float x,
            float y,
            boolean shadow
    ) {
        if (betteruc$preparingBadge || !TabBadgeRenderState.isPlayerListRendering()) return;
        if (!betteruc$isLikelyTabListText(x, y, textWidth)) return;

        String role = PingRelayClient.tabBadgeRoleForRenderedText(renderedText);
        if (role.isBlank()) return;

        Font.PreparedText original = cir.getReturnValue();
        if (original == null) return;

        betteruc$preparingBadge = true;
        try {
            FormattedCharSequence badgeText = betteruc$badgeForRole(role).getVisualOrderText();
            Font.PreparedText badge = textRenderer.prepareText(
                    badgeText,
                    x + textWidth + 1.0F,
                    y,
                    0xFFFFFFFF,
                    shadow,
                    false,
                    0
            );
            cir.setReturnValue(new betteruc$CompositeGlyphDrawable(original, badge));
        } finally {
            betteruc$preparingBadge = false;
        }
    }

    @Unique
    private static boolean betteruc$isLikelyTabListText(float x, float y, int textWidth) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }

        int scaledWidth = client.getWindow().getGuiScaledWidth();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        if (scaledWidth <= 0 || scaledHeight <= 0 || textWidth <= 0) {
            return false;
        }

        float minX = Math.max(34.0F, scaledWidth * 0.12F);
        float maxY = Math.max(96.0F, scaledHeight * 0.62F);
        return x >= minX
                && x + textWidth <= scaledWidth - 20.0F
                && y >= 12.0F
                && y <= maxY
                && textWidth <= 180;
    }

    @Unique
    private static String betteruc$plainText(FormattedCharSequence text) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    @Unique
    private static MutableComponent betteruc$badgeForRole(String role) {
        return switch (role) {
            case "admin" -> betteruc$badge(ADMIN_BADGE);
            case "helper" -> betteruc$badge(HELPER_BADGE);
            case "partner" -> betteruc$badge(PARTNER_BADGE);
            case "vip" -> betteruc$badge(VIP_BADGE);
            default -> betteruc$badge(USER_BADGE);
        };
    }

    @Unique
    private static MutableComponent betteruc$badge(String glyph) {
        return Component.literal(glyph)
                .withStyle(ChatFormatting.WHITE)
                .withStyle(style -> style.withFont(new FontDescription.Resource(BUC_BADGE_FONT)));
    }

    @Unique
    private record betteruc$CompositeGlyphDrawable(
            Font.PreparedText original,
            Font.PreparedText badge
    ) implements Font.PreparedText {
        @Override
        public void visit(Font.GlyphVisitor glyphDrawer) {
            original.visit(glyphDrawer);
            badge.visit(glyphDrawer);
        }

        @Nullable
        @Override
        public ScreenRectangle bounds() {
            return betteruc$union(original.bounds(), badge.bounds());
        }

        private static ScreenRectangle betteruc$union(@Nullable ScreenRectangle first, @Nullable ScreenRectangle second) {
            if (first == null) return second;
            if (second == null) return first;
            int left = Math.min(first.left(), second.left());
            int top = Math.min(first.top(), second.top());
            int right = Math.max(first.right(), second.right());
            int bottom = Math.max(first.bottom(), second.bottom());
            return new ScreenRectangle(left, top, right - left, bottom - top);
        }
    }
}
