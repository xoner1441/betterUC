package com.betteruc.mixin;

import com.betteruc.BetterUCSuppressFlags;
import com.betteruc.ServerGate;
import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// priority = 2000 so this inject runs after NRC mixins and overrides their changes
@Mixin(value = PlayerEntityRenderer.class, priority = 2000)
public abstract class PlayerEntityRendererMixin {

    private static final int VOGELFREI_COLOR = 0x8B0000;

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void colorNametag(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!ServerGate.isAllowedServer(MinecraftClient.getInstance())) return;
        if (state.displayName == null) return;

        String name = entity.getStringifiedName();
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return;

        int color = -1;
        boolean removedRecently = BetterUCSuppressFlags.isRecentlyRemovedBlacklist(name);
        if (BetterUCConfig.isFaction(name)) {
            color = BetterUCConfig.INSTANCE.factionColor;
        } else if (BetterUCConfig.isBlacklist(name) && !removedRecently) {
            color = BetterUCConfig.INSTANCE.blacklistColor;
        }
        if (color == -1) {
            // Only clear style if this looks like styling we previously applied.
            if (!looksLikeBetterUCStyling(state.displayName)) {
                return;
            }
            String current = state.displayName.getString();
            if (current.startsWith("[V] ")) {
                current = current.substring(4);
            }
            state.displayName = Text.literal(current);
            return;
        }
        final int finalColor = color;

        String fullDisplay = state.displayName.getString();
        MutableText colored = Text.literal(fullDisplay)
                .styled(style -> style.withColor(TextColor.fromRgb(finalColor)).withBold(true));

        if (BetterUCConfig.isVogelfrei(name)) {
            MutableText vTag = Text.literal("[V] ")
                    .styled(style -> style
                            .withColor(TextColor.fromRgb(VOGELFREI_COLOR))
                            .withBold(true));
            state.displayName = vTag.append(colored);
        } else {
            state.displayName = colored;
        }
    }

    private boolean looksLikeBetterUCStyling(Text displayName) {
        if (displayName == null) return false;
        String raw = displayName.getString();
        if (raw.startsWith("[V] ")) return true;
        if (!Boolean.TRUE.equals(displayName.getStyle().isBold())) return false;

        TextColor color = displayName.getStyle().getColor();
        if (color == null) return false;

        int rgb = color.getRgb();
        return rgb == BetterUCConfig.INSTANCE.factionColor
                || rgb == BetterUCConfig.INSTANCE.blacklistColor;
    }
}
