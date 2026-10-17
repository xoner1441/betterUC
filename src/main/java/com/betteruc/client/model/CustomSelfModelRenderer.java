package com.betteruc.client.model;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CustomSelfModelRenderer {
    public static final Identifier TRANSPARENT_TEXTURE = Identifier.fromNamespaceAndPath(
            "betteruc", "textures/entity/transparent.png"
    );
    private static final Map<CustomSelfModelOption, CustomMeshData> MESHES =
            new EnumMap<>(CustomSelfModelOption.class);
    private static final Map<CustomSelfModelOption, RenderType> RENDER_TYPES =
            new EnumMap<>(CustomSelfModelOption.class);
    private static final Set<CustomSelfModelOption> LOAD_ATTEMPTED =
            EnumSet.noneOf(CustomSelfModelOption.class);

    private CustomSelfModelRenderer() {
    }

    public static boolean shouldReplace(AvatarRenderState state) {
        if (!BetterUCConfig.INSTANCE.customSelfModelEnabled || state == null) return false;
        if (state.isInvisible || state.isSpectator) return false;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.options.getCameraType().isFirstPerson()) return false;
        if (state.id != client.player.getId()) return false;
        return mesh(selectedModel()) != null;
    }

    public static void submit(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue) {
        CustomSelfModelOption model = selectedModel();
        CustomMeshData loaded = mesh(model);
        if (loaded == null) return;

        matrices.mulPose(Axis.YP.rotationDegrees(model.yawOffset()));
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        queue.submitCustomGeometry(matrices, renderType(model),
                (pose, vertices) -> renderMesh(loaded, pose, vertices, state.lightCoords, overlay));
    }

    private static void renderMesh(
            CustomMeshData data,
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay
    ) {
        int[] indices = data.indices();
        for (int offset = 0; offset < indices.length; offset += 3) {
            emitVertex(data, indices[offset], pose, vertices, light, overlay);
            emitVertex(data, indices[offset + 1], pose, vertices, light, overlay);
            emitVertex(data, indices[offset + 2], pose, vertices, light, overlay);
            // Entity render types use quads. Duplicating the final corner forms a degenerate
            // fourth edge while preserving the source triangle exactly.
            emitVertex(data, indices[offset + 2], pose, vertices, light, overlay);
        }
    }

    private static void emitVertex(
            CustomMeshData data,
            int index,
            PoseStack.Pose pose,
            VertexConsumer vertices,
            int light,
            int overlay
    ) {
        int p = index * 3;
        int uv = index * 2;
        vertices.addVertex(pose, data.positions()[p], data.positions()[p + 1], data.positions()[p + 2])
                .setColor(data.colors()[index])
                .setUv(data.uvs()[uv], data.uvs()[uv + 1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, data.normals()[p], data.normals()[p + 1], data.normals()[p + 2]);
    }

    public static CustomSelfModelOption selectedModel() {
        return CustomSelfModelOption.fromId(BetterUCConfig.INSTANCE.customSelfModelId);
    }

    private static RenderType renderType(CustomSelfModelOption model) {
        return RENDER_TYPES.computeIfAbsent(model, option -> RenderTypes.entityCutout(
                Identifier.fromNamespaceAndPath("betteruc", option.texturePath())
        ));
    }

    private static CustomMeshData mesh(CustomSelfModelOption model) {
        if (LOAD_ATTEMPTED.contains(model)) return MESHES.get(model);
        LOAD_ATTEMPTED.add(model);
        try (InputStream input = CustomSelfModelRenderer.class.getResourceAsStream(model.meshResource())) {
            CustomMeshData mesh = CustomMeshData.read(input);
            MESHES.put(model, mesh);
            BetterUCMod.LOGGER.info(
                    "Custom self model {} loaded: {} vertices, {} triangles",
                    model.id(), mesh.vertexCount(), mesh.triangleCount()
            );
        } catch (IOException | RuntimeException error) {
            BetterUCMod.LOGGER.error(
                    "Could not load custom self model {}; keeping the vanilla player model",
                    model.id(), error
            );
        }
        return MESHES.get(model);
    }
}
