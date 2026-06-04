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
                .filter(marker -> sameDimension(PingRelayClient.currentDimension(client), marker.dimension()))
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
        long target = projectToScreen(client, marker);
        if (target == Long.MIN_VALUE) return;
        int targetX = unpackScreenX(target);
        int targetY = unpackScreenY(target);

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        PingRelayClient.PingType pingType = PingRelayClient.PingType.fromId(marker.pingType());
        String title = pingType.label() + " | " + safe(marker.sender());
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
        int x = MathHelper.clamp(targetX - scaledWidth / 2, 8, Math.max(8, screenW - scaledWidth - 8));
        int y = MathHelper.clamp(targetY - scaledHeight - markerGap, 8, Math.max(8, screenH - scaledHeight - 8));
        int accent = color(marker);

        ModernHudRenderer.drawScaled(context, x, y, scale, () ->
                drawMarkerBody(context, client, style, font, title, label, width, height, accent)
        );

        drawTargetMarker(context, client, targetX, targetY, accent, scale, pingType);
        int lineEndY = targetY - 6;
        int lineStartY = y + scaledHeight;
        if (lineEndY > lineStartY) {
            int thickness = Math.max(1, ModernHudRenderer.scaledSize(1, scale));
            context.fill(targetX - thickness / 2, lineStartY, targetX + thickness, lineEndY, accent);
        }
    }

    private static void drawTargetMarker(
            DrawContext context,
            MinecraftClient client,
            int targetX,
            int targetY,
            int accent,
            float scale,
            PingRelayClient.PingType pingType
    ) {
        int cross = Math.max(4, ModernHudRenderer.scaledSize(5, scale));
        int thickness = Math.max(1, ModernHudRenderer.scaledSize(1, scale));

        switch (pingType) {
            case DANGER -> {
                context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("!"), targetX, targetY - 18, accent);
                context.fill(targetX - cross, targetY, targetX + cross + 1, targetY + thickness, accent);
                context.fill(targetX, targetY - cross, targetX + thickness, targetY + cross + 1, accent);
            }
            case GATHER -> {
                context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("v"), targetX, targetY - 18, accent);
                context.fill(targetX, targetY - cross - 2, targetX + thickness, targetY + cross + 1, accent);
                context.fill(targetX - cross, targetY, targetX + cross + 1, targetY + thickness, accent);
            }
            default -> {
                context.fill(targetX - cross, targetY, targetX + cross + 1, targetY + thickness, accent);
                context.fill(targetX, targetY - cross, targetX + thickness, targetY + cross + 1, accent);
            }
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

    private static long projectToScreen(MinecraftClient client, PingRelayClient.PingMarker marker) {
        Vec3d target = new Vec3d(marker.x(), marker.y(), marker.z());
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d toTarget = target.subtract(cameraPos);
        if (toTarget.lengthSquared() < 0.0001D) return Long.MIN_VALUE;

        Vec3d cameraLook = Vec3d.fromPolar(client.gameRenderer.getCamera().getPitch(), client.gameRenderer.getCamera().getYaw());
        if (toTarget.normalize().dotProduct(cameraLook.normalize()) <= 0.05D) {
            return Long.MIN_VALUE;
        }

        Vec3d look = client.player.getRotationVec(1.0F);
        if (toTarget.normalize().dotProduct(look.normalize()) <= 0.05D) {
            return Long.MIN_VALUE;
        }

        Vec3d projected = client.gameRenderer.project(target);
        if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) return Long.MIN_VALUE;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int x = (int) Math.round((projected.x + 1.0D) * 0.5D * screenW);
        int y = (int) Math.round((1.0D - projected.y) * 0.5D * screenH);

        if (x < -32 || x > screenW + 32 || y < -32 || y > screenH + 32) {
            return Long.MIN_VALUE;
        }
        return packScreenPoint(MathHelper.clamp(x, 0, screenW), MathHelper.clamp(y, 0, screenH));
    }

    private static long packScreenPoint(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackScreenX(long point) {
        return (int) (point >> 32);
    }

    private static int unpackScreenY(long point) {
        return (int) point;
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

    private static boolean sameDimension(String current, String marker) {
        String currentNormalized = normalizeDimension(current);
        String markerNormalized = normalizeDimension(marker);
        return !currentNormalized.isEmpty() && currentNormalized.equals(markerNormalized);
    }

    private static String normalizeDimension(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
