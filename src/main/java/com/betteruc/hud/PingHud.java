package com.betteruc.hud;

import com.betteruc.client.PingRelayClient;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public final class PingHud {

    private static final int ACCENT = 0xFF38BDF8;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;

    private PingHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        if (!BetterUCConfig.INSTANCE.showPingHud) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        List<PingRelayClient.PingMarker> markers = PingRelayClient.activePings().stream()
                .filter(marker -> PingRelayClient.currentDimension(client).equals(marker.dimension()))
                .filter(marker -> distanceToPlayer(client, marker) <= BetterUCConfig.INSTANCE.pingRelayMaxDistance)
                .sorted(Comparator.comparingLong(PingRelayClient.PingMarker::createdAt).reversed())
                .limit(5)
                .toList();

        if (markers.isEmpty()) return;

        for (int i = markers.size() - 1; i >= 0; i--) {
            renderWorldMarker(context, client, markers.get(i));
        }
    }

    private static void renderWorldMarker(DrawContext context, MinecraftClient client, PingRelayClient.PingMarker marker) {
        ScreenPoint target = projectToScreen(client, marker);
        if (target == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        String title = "Ping | " + safe(marker.sender());
        String label = Math.round(distanceToPlayer(client, marker)) + "m";
        int width = Math.max(64, Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(label)) + 20);
        String style = BetterUCConfig.INSTANCE.pingHudStyle;
        String font = BetterUCConfig.INSTANCE.pingHudCustomFont;
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        int height = modernStyle ? 31 : 24;
        float scale = BetterUCConfig.normalizeHudScale(BetterUCConfig.INSTANCE.pingHudScale);
        int scaledWidth = ModernHudRenderer.scaledSize(width, scale);
        int scaledHeight = ModernHudRenderer.scaledSize(height, scale);
        int markerGap = ModernHudRenderer.scaledSize(17, scale);
        int x = MathHelper.clamp(target.x() - scaledWidth / 2, 8, Math.max(8, screenW - scaledWidth - 8));
        int y = MathHelper.clamp(target.y() - scaledHeight - markerGap, 8, Math.max(8, screenH - scaledHeight - 8));
        int accent = color(marker);

        ModernHudRenderer.drawScaled(context, x, y, scale, () ->
                drawMarkerBody(context, client, style, font, title, label, width, height, accent)
        );

        int cross = Math.max(4, ModernHudRenderer.scaledSize(5, scale));
        int thickness = Math.max(1, ModernHudRenderer.scaledSize(1, scale));
        context.fill(target.x() - cross, target.y(), target.x() + cross + 1, target.y() + thickness, accent);
        context.fill(target.x(), target.y() - cross, target.x() + thickness, target.y() + cross + 1, accent);
        int lineEndY = target.y() - 6;
        int lineStartY = y + scaledHeight;
        if (lineEndY > lineStartY) {
            context.fill(target.x() - thickness / 2, lineStartY, target.x() + thickness, lineEndY, accent);
        }
    }

    private static void drawMarkerBody(
            DrawContext context,
            MinecraftClient client,
            String style,
            String font,
            String title,
            String label,
            int width,
            int height,
            int accent
    ) {
        if (BetterUCConfig.isModernHudStyle(style)) {
            ModernHudRenderer.drawPanel(context, 0, 0, width, height, accent);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(title), width / 2, 6, accent);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), width / 2, 18, TEXT_PRIMARY);
            return;
        }

        int titleX = (width - client.textRenderer.getWidth(title)) / 2;
        int labelX = (width - client.textRenderer.getWidth(label)) / 2;
        if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, client, style, font, title, titleX, 0, accent);
            ModernHudRenderer.drawStyledText(context, client, style, font, label, labelX, 12, TEXT_PRIMARY);
            return;
        }

        context.drawTextWithShadow(client.textRenderer, Text.literal(title), titleX, 0, accent);
        context.drawTextWithShadow(client.textRenderer, Text.literal(label), labelX, 12, TEXT_PRIMARY);
    }

    private static double distanceToPlayer(MinecraftClient client, PingRelayClient.PingMarker marker) {
        double dx = marker.x() - client.player.getX();
        double dy = marker.y() - client.player.getY();
        double dz = marker.z() - client.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static ScreenPoint projectToScreen(MinecraftClient client, PingRelayClient.PingMarker marker) {
        Vec3d target = new Vec3d(marker.x(), marker.y(), marker.z());
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d toTarget = target.subtract(cameraPos);
        if (toTarget.lengthSquared() < 0.0001D) return null;

        Vec3d look = client.player.getRotationVec(1.0F);
        if (toTarget.normalize().dotProduct(look.normalize()) <= 0.05D) {
            return null;
        }

        Vec3d projected = client.gameRenderer.project(target);
        if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) return null;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int x = (int) Math.round((projected.x + 1.0D) * 0.5D * screenW);
        int y = (int) Math.round((1.0D - projected.y) * 0.5D * screenH);

        if (x < -32 || x > screenW + 32 || y < -32 || y > screenH + 32) {
            return null;
        }
        return new ScreenPoint(MathHelper.clamp(x, 0, screenW), MathHelper.clamp(y, 0, screenH));
    }

    private static int color(PingRelayClient.PingMarker marker) {
        String raw = marker.color() == null ? "" : marker.color().trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        try {
            return 0xFF000000 | (Integer.parseInt(raw, 16) & 0x00FFFFFF);
        } catch (Exception ignored) {
            return ACCENT;
        }
    }

    private static String trimToWidth(MinecraftClient client, String text, int width) {
        String safe = safe(text);
        if (client.textRenderer.getWidth(safe) <= width) return safe;
        while (safe.length() > 1 && client.textRenderer.getWidth(safe + "...") > width) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + "...";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Ping" : value.trim();
    }

    private record ScreenPoint(int x, int y) {
    }
}
