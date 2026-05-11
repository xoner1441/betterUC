package com.betteruc.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CarMarkerHud {

    private static final double RENDER_RANGE_BLOCKS = 96.0;
    private static final int MARKER_COLOR = 0xFF6CF27D;
    private static final double LABEL_HEIGHT_OFFSET = 1.75;
    private static final float NEAR_CLIP = 0.05F;
    private static final float SCREEN_CULL_MARGIN = 0.15F;

    private static Vec3d markerPos = null;
    private static String markerDimension = null;

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    public static void setMarker(MinecraftClient client, Vec3d pos) {
        if (client == null || client.world == null || pos == null) return;
        markerPos = pos;
        markerDimension = client.world.getRegistryKey().getValue().toString();
    }

    public static void clear() {
        markerPos = null;
        markerDimension = null;
    }

    private static void render(DrawContext context) {
        if (markerPos == null || markerDimension == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.gameRenderer == null) return;
        if (!markerDimension.equals(client.world.getRegistryKey().getValue().toString())) return;

        Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());
        double distSq = playerPos.squaredDistanceTo(markerPos);
        if (distSq > RENDER_RANGE_BLOCKS * RENDER_RANGE_BLOCKS) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return;

        Vec3d worldPos = markerPos.add(0.0, LABEL_HEIGHT_OFFSET, 0.0);
        Vec3d rel = worldPos.subtract(camera.getPos());
        Vector3f cameraSpace = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        cameraSpace.rotate(new Quaternionf(camera.getRotation()).conjugate());

        // In front of the camera is negative Z after inverse camera rotation.
        if (cameraSpace.z >= -NEAR_CLIP) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        if (screenWidth <= 0 || screenHeight <= 0) return;

        float fov = (float) client.options.getFov().getValue();
        float tanHalfFov = (float) Math.tan(Math.toRadians(Math.max(1.0f, fov)) * 0.5);
        if (tanHalfFov <= 0.0f) return;

        float aspect = (float) screenWidth / (float) screenHeight;
        float ndcX = (cameraSpace.x / -cameraSpace.z) / (tanHalfFov * aspect);
        float ndcY = (cameraSpace.y / -cameraSpace.z) / tanHalfFov;

        if (Math.abs(ndcX) > (1.0f + SCREEN_CULL_MARGIN) || Math.abs(ndcY) > (1.0f + SCREEN_CULL_MARGIN)) {
            return;
        }

        int screenX = Math.round((ndcX * 0.5f + 0.5f) * screenWidth);
        int screenY = Math.round((0.5f - ndcY * 0.5f) * screenHeight);
        int distance = (int) Math.round(Math.sqrt(distSq));
        Text label = Text.literal("Auto geparkt (" + distance + "m)");
        int drawX = screenX - client.textRenderer.getWidth(label) / 2;
        int drawY = screenY;
        context.drawTextWithShadow(client.textRenderer, label, drawX, drawY, MARKER_COLOR);
    }
}
