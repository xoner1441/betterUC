package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerListHud.class, priority = 100)
public abstract class PlayerListHudMixin {
    private static final Identifier BUC_BADGE_FONT = Identifier.of("betteruc", "buc_badges");
    private static final String USER_BADGE = "\uE100";
    private static final String ADMIN_BADGE = "\uE101";
    private static final String VIP_BADGE = "\uE102";
    private static final String HELPER_BADGE = "\uE103";
    private static final String PARTNER_BADGE = "\uE104";

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    public abstract Text getPlayerName(PlayerListEntry entry);

    @Inject(method = "renderLatencyIcon", at = @At("TAIL"))
    private void betteruc$renderTablistBadge(
            DrawContext context,
            int width,
            int x,
            int y,
            PlayerListEntry entry,
            CallbackInfo ci
    ) {
        MutableText badge = betteruc$badgeFor(entry);
        if (badge == null) return;
        if (client == null || client.textRenderer == null || client.getNetworkHandler() == null) return;

        TextRenderer textRenderer = client.textRenderer;
        boolean showAvatars = client.isInSingleplayer()
                || client.getNetworkHandler().getConnection().isEncrypted();
        int textX = x + (showAvatars ? 9 : 0);
        int badgeX = textX + textRenderer.getWidth(getPlayerName(entry)) + 1;
        int maxBadgeX = x + width - 12 - textRenderer.getWidth(badge);
        if (maxBadgeX > textX) {
            badgeX = Math.min(badgeX, maxBadgeX);
        }
        context.drawTextWithShadow(textRenderer, badge, badgeX, y, 0xFFFFFFFF);
    }

    private static MutableText betteruc$badgeFor(PlayerListEntry entry) {
        if (!PingRelayClient.hasBetterUCBadge(entry)) return null;
        if (PingRelayClient.hasAdminBadge(entry)) {
            return betteruc$badge(ADMIN_BADGE);
        }
        if (PingRelayClient.hasHelperBadge(entry)) {
            return betteruc$badge(HELPER_BADGE);
        }
        if (PingRelayClient.hasPartnerBadge(entry)) {
            return betteruc$badge(PARTNER_BADGE);
        }
        if (PingRelayClient.hasVipBadge(entry)) {
            return betteruc$badge(VIP_BADGE);
        }
        return betteruc$badge(USER_BADGE);
    }

    private static MutableText betteruc$badge(String glyph) {
        return Text.literal(glyph)
                .formatted(Formatting.WHITE)
                .styled(style -> style.withFont(new StyleSpriteSource.Font(BUC_BADGE_FONT)));
    }
}
