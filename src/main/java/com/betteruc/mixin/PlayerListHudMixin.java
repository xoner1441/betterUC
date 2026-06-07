package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    private static final Identifier BUC_BADGE_FONT = Identifier.of("betteruc", "buc_badges");
    private static final String USER_BADGE = "\uE100";
    private static final String ADMIN_BADGE = "\uE101";
    private static final String VIP_BADGE = "\uE102";
    private static final String HELPER_BADGE = "\uE103";
    private static final String PARTNER_BADGE = "\uE104";

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void betteruc$appendTablistBadge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!PingRelayClient.hasBetterUCBadge(entry)) return;

        MutableText name = cir.getReturnValue().copy();
        if (PingRelayClient.hasAdminBadge(entry)) {
            name.append(betteruc$badge(ADMIN_BADGE));
        } else if (PingRelayClient.hasHelperBadge(entry)) {
            name.append(betteruc$badge(HELPER_BADGE));
        } else if (PingRelayClient.hasPartnerBadge(entry)) {
            name.append(betteruc$badge(PARTNER_BADGE));
        } else if (PingRelayClient.hasVipBadge(entry)) {
            name.append(betteruc$badge(VIP_BADGE));
        } else {
            name.append(betteruc$badge(USER_BADGE));
        }
        cir.setReturnValue(name);
    }

    private static MutableText betteruc$badge(String glyph) {
        return Text.literal(glyph)
                .formatted(Formatting.WHITE)
                .styled(style -> style.withFont(new StyleSpriteSource.Font(BUC_BADGE_FONT)));
    }
}
