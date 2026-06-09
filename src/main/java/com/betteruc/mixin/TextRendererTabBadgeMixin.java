package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import com.betteruc.client.TabBadgeRenderState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextRenderer.class, priority = 50)
public abstract class TextRendererTabBadgeMixin {
    @Unique
    private static final Identifier BUC_BADGE_FONT = Identifier.of("betteruc", "buc_badges");
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
            method = "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
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
            CallbackInfoReturnable<TextRenderer.GlyphDrawable> cir
    ) {
        TextRenderer textRenderer = (TextRenderer) (Object) this;
        betteruc$appendBadgeIfNeeded(textRenderer, cir, text, textRenderer.getWidth(text), x, y, shadow);
    }

    @Inject(
            method = "prepare(Lnet/minecraft/text/OrderedText;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void betteruc$prepareOrderedTextWithTabBadge(
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            int backgroundColor,
            CallbackInfoReturnable<TextRenderer.GlyphDrawable> cir
    ) {
        TextRenderer textRenderer = (TextRenderer) (Object) this;
        betteruc$appendBadgeIfNeeded(textRenderer, cir, betteruc$plainText(text), textRenderer.getWidth(text), x, y, shadow);
    }

    @Unique
    private static void betteruc$appendBadgeIfNeeded(
            TextRenderer textRenderer,
            CallbackInfoReturnable<TextRenderer.GlyphDrawable> cir,
            String renderedText,
            int textWidth,
            float x,
            float y,
            boolean shadow
    ) {
        if (betteruc$preparingBadge || !TabBadgeRenderState.isPlayerListRendering()) return;

        String role = PingRelayClient.tabBadgeRoleForRenderedText(renderedText);
        if (role.isBlank()) return;

        TextRenderer.GlyphDrawable original = cir.getReturnValue();
        if (original == null) return;

        betteruc$preparingBadge = true;
        try {
            OrderedText badgeText = betteruc$badgeForRole(role).asOrderedText();
            TextRenderer.GlyphDrawable badge = textRenderer.prepare(
                    badgeText,
                    x + textWidth + 1.0F,
                    y,
                    0xFFFFFFFF,
                    shadow,
                    0
            );
            cir.setReturnValue(new betteruc$CompositeGlyphDrawable(original, badge));
        } finally {
            betteruc$preparingBadge = false;
        }
    }

    @Unique
    private static String betteruc$plainText(OrderedText text) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    @Unique
    private static MutableText betteruc$badgeForRole(String role) {
        return switch (role) {
            case "admin" -> betteruc$badge(ADMIN_BADGE);
            case "helper" -> betteruc$badge(HELPER_BADGE);
            case "partner" -> betteruc$badge(PARTNER_BADGE);
            case "vip" -> betteruc$badge(VIP_BADGE);
            default -> betteruc$badge(USER_BADGE);
        };
    }

    @Unique
    private static MutableText betteruc$badge(String glyph) {
        return Text.literal(glyph)
                .formatted(Formatting.WHITE)
                .styled(style -> style.withFont(new StyleSpriteSource.Font(BUC_BADGE_FONT)));
    }

    @Unique
    private record betteruc$CompositeGlyphDrawable(
            TextRenderer.GlyphDrawable original,
            TextRenderer.GlyphDrawable badge
    ) implements TextRenderer.GlyphDrawable {
        @Override
        public void draw(TextRenderer.GlyphDrawer glyphDrawer) {
            original.draw(glyphDrawer);
            badge.draw(glyphDrawer);
        }

        @Nullable
        @Override
        public ScreenRect getScreenRect() {
            return betteruc$union(original.getScreenRect(), badge.getScreenRect());
        }

        private static ScreenRect betteruc$union(@Nullable ScreenRect first, @Nullable ScreenRect second) {
            if (first == null) return second;
            if (second == null) return first;
            int left = Math.min(first.getLeft(), second.getLeft());
            int top = Math.min(first.getTop(), second.getTop());
            int right = Math.max(first.getRight(), second.getRight());
            int bottom = Math.max(first.getBottom(), second.getBottom());
            return new ScreenRect(left, top, right - left, bottom - top);
        }
    }
}
