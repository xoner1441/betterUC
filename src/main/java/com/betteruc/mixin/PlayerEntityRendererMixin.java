package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import com.betteruc.config.BetterUCConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
    private static final double BETTERUC_LABEL_MAX_DISTANCE_SQ = 48.0D * 48.0D;
    private static final float BETTERUC_LABEL_SCALE = 0.65F;
    private static final double BETTERUC_LABEL_WORLD_OFFSET = 0.23D;
    private static final double UNIQUE_CLIENT_STACK_OFFSET = 0.28D;
    private static final boolean UNIQUE_CLIENT_LOADED = detectUniqueClient();

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL")
    )
    private void betteruc$renderRoleLabel(
            AvatarRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!BetterUCConfig.INSTANCE.showRoleHolograms) return;
        if (state == null || state.nameTagAttachment == null) return;
        if (state.distanceToCameraSq > BETTERUC_LABEL_MAX_DISTANCE_SQ) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        Entity entity = client.level.getEntity(state.id);
        if (entity == null) return;

        String name = entity.getName().getString();
        String uuid = entity.getStringUUID();
        String label = PingRelayClient.roleNameTagForPlayer(name, uuid);
        if (label.isBlank()) return;

        boolean admin = PingRelayClient.isAdminPlayer(name, uuid);
        boolean helper = PingRelayClient.isHelperPlayer(name, uuid);
        boolean partner = PingRelayClient.isPartnerPlayer(name, uuid);
        boolean vip = PingRelayClient.isVipPlayer(name, uuid);
        Component text = Component.literal("betterUC ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(label).withStyle(roleColor(admin, helper, partner, vip), ChatFormatting.BOLD));

        Font textRenderer = client.font;
        Vec3 pos = state.nameTagAttachment;
        double labelY = pos.y + 0.5D + BETTERUC_LABEL_WORLD_OFFSET + uniqueClientStackOffset() + (state.showExtraEars ? 0.25D : 0.0D);
        float scale = 0.025F * BETTERUC_LABEL_SCALE;
        float x = -textRenderer.width(text) / 2.0F;

        matrices.pushPose();
        matrices.translate(pos.x, labelY, pos.z);
        matrices.mulPose(cameraState.orientation);
        matrices.scale(scale, -scale, scale);
        queue.submitText(
                matrices,
                x,
                0.0F,
                text.getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                state.lightCoords,
                0xFFFFFFFF,
                0,
                0
        );
        matrices.popPose();
    }

    private static ChatFormatting roleColor(boolean admin, boolean helper, boolean partner, boolean vip) {
        if (admin) return ChatFormatting.RED;
        if (helper) return ChatFormatting.YELLOW;
        if (partner) return ChatFormatting.AQUA;
        if (vip) return ChatFormatting.DARK_PURPLE;
        return ChatFormatting.WHITE;
    }

    private static double uniqueClientStackOffset() {
        return UNIQUE_CLIENT_LOADED ? UNIQUE_CLIENT_STACK_OFFSET : 0.0D;
    }

    private static boolean detectUniqueClient() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("unique")) return true;
        return loader.getAllMods().stream().anyMatch(mod -> {
            String id = mod.getMetadata().getId();
            String name = mod.getMetadata().getName();
            return equalsIgnoreCase(id, "unique:client")
                    || equalsIgnoreCase(name, "unique:client")
                    || equalsIgnoreCase(name, "Unique Client");
        });
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }
}
