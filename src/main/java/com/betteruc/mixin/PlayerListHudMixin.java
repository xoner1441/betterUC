package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerTabOverlay.class, priority = 100)
public abstract class PlayerListHudMixin {
    private static final Identifier BUC_BADGE_FONT = Identifier.fromNamespaceAndPath("betteruc", "buc_badges");
    private static final String USER_BADGE = "\uE100";
    private static final String ADMIN_BADGE = "\uE101";
    private static final String VIP_BADGE = "\uE102";
    private static final String HELPER_BADGE = "\uE103";
    private static final String PARTNER_BADGE = "\uE104";

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract Component getNameForDisplay(PlayerInfo entry);

    @Inject(
            method = "extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V",
            at = @At("TAIL")
    )
    private void betteruc$renderTablistBadge(
            GuiGraphicsExtractor context,
            int width,
            int x,
            int y,
            PlayerInfo entry,
            CallbackInfo ci
    ) {
        MutableComponent badge = betteruc$badgeFor(entry);
        if (badge == null) return;
        if (minecraft == null || minecraft.font == null || minecraft.getConnection() == null) return;

        Font textRenderer = minecraft.font;
        boolean showAvatars = minecraft.isLocalServer()
                || minecraft.getConnection().getConnection().isEncrypted();
        int textX = x + (showAvatars ? 9 : 0);
        int badgeX = textX + textRenderer.width(getNameForDisplay(entry)) + 1;
        int maxBadgeX = x + width - 12 - textRenderer.width(badge);
        if (maxBadgeX > textX) {
            badgeX = Math.min(badgeX, maxBadgeX);
        }
        context.text(textRenderer, badge, badgeX, y, 0xFFFFFFFF);
    }

    private static MutableComponent betteruc$badgeFor(PlayerInfo entry) {
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

    private static MutableComponent betteruc$badge(String glyph) {
        return Component.literal(glyph)
                .withStyle(ChatFormatting.WHITE)
                .withStyle(style -> style.withFont(new FontDescription.Resource(BUC_BADGE_FONT)));
    }
}
