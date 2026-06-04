package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void betteruc$appendTablistBadge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!PingRelayClient.hasBetterUCBadge(entry)) return;

        MutableText name = cir.getReturnValue().copy();
        if (PingRelayClient.hasAdminBadge(entry)) {
            name.append(Text.literal(" bUC").formatted(Formatting.RED, Formatting.BOLD));
        } else if (PingRelayClient.hasVipBadge(entry)) {
            name.append(Text.literal(" bUC").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        } else {
            name.append(Text.literal(" bUC").formatted(Formatting.GREEN, Formatting.BOLD));
        }
        cir.setReturnValue(name);
    }
}
