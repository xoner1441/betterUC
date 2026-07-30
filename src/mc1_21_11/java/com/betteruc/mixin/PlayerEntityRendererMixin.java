package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class PlayerEntityRendererMixin {
    private static final double BETTERUC_LABEL_MAX_DISTANCE_SQ = 48.0D * 48.0D;
    private static final float BETTERUC_LABEL_SCALE = 0.65F;
    private static final double BETTERUC_LABEL_WORLD_OFFSET = 0.23D;
    private static final double UNIQUE_CLIENT_STACK_OFFSET = 0.28D;
    private static final boolean UNIQUE_CLIENT_LOADED = detectUniqueClient();

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL")
    )
    private void betteruc$renderRoleLabel(
            LivingEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!BetterUCConfig.INSTANCE.showRoleHolograms) return;
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        if (playerState.nameLabelPos == null) return;
        if (playerState.squaredDistanceToCamera > BETTERUC_LABEL_MAX_DISTANCE_SQ) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        Entity entity = client.world.getEntityById(playerState.id);
        if (entity == null) return;

        String name = entity.getName().getString();
        String uuid = entity.getUuidAsString();
        String label = PingRelayClient.roleNameTagForPlayer(name, uuid);
        if (label.isBlank()) return;

        boolean admin = PingRelayClient.isAdminPlayer(name, uuid);
        boolean helper = PingRelayClient.isHelperPlayer(name, uuid);
        boolean partner = PingRelayClient.isPartnerPlayer(name, uuid);
        boolean vip = PingRelayClient.isVipPlayer(name, uuid);
        Text text = Text.literal("betterUC ").formatted(Formatting.GRAY)
                .append(Text.literal(label).formatted(roleColor(admin, helper, partner, vip), Formatting.BOLD));

        TextRenderer textRenderer = client.textRenderer;
        Vec3d pos = playerState.nameLabelPos;
        double labelY = pos.y + 0.5D + BETTERUC_LABEL_WORLD_OFFSET + uniqueClientStackOffset() + (playerState.extraEars ? 0.25D : 0.0D);
        float scale = 0.025F * BETTERUC_LABEL_SCALE;
        float x = -textRenderer.getWidth(text) / 2.0F;

        matrices.push();
        matrices.translate(pos.x, labelY, pos.z);
        matrices.multiply(cameraState.orientation);
        matrices.scale(scale, -scale, scale);
        queue.submitText(
                matrices,
                x,
                0.0F,
                text.asOrderedText(),
                false,
                TextLayerType.NORMAL,
                playerState.light,
                0xFFFFFFFF,
                0,
                0
        );
        matrices.pop();
    }

    private static Formatting roleColor(boolean admin, boolean helper, boolean partner, boolean vip) {
        if (admin) return Formatting.RED;
        if (helper) return Formatting.YELLOW;
        if (partner) return Formatting.AQUA;
        if (vip) return Formatting.DARK_PURPLE;
        return Formatting.WHITE;
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
