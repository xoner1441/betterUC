package com.betteruc.hud;

import com.betteruc.client.PingRelayClient;
import com.betteruc.config.BetterUCConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;

public final class PingHud {

    private static final int ACCENT = 0xFF38BDF8;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int COOLDOWN_BG = 0xE80D1117;
    private static final int COOLDOWN_BORDER = 0xAA334155;
    private static final int COOLDOWN_TRACK = 0xFF1E293B;
    private static final double MAX_VISIBLE_PING_DISTANCE = 128.0D;

    private PingHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("betteruc", "ping"), (context, tickCounter) -> render(context));
    }

    private static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        renderCooldown(context, client);
        if (!BetterUCConfig.INSTANCE.showPingHud) return;

        List<PingRelayClient.PingMarker> markers = PingRelayClient.activePings().stream()
                .filter(marker -> sameDimension(PingRelayClient.currentDimension(client), marker.dimension()))
                .filter(marker -> distanceToPlayer(client, marker) <= effectiveMaxDistance())
                .sorted(Comparator.comparingLong(PingRelayClient.PingMarker::createdAt).reversed())
                .limit(5)
                .toList();

        if (markers.isEmpty()) return;

        for (int i = markers.size() - 1; i >= 0; i--) {
            renderWorldMarker(context, client, markers.get(i));
        }
    }

    private static void renderCooldown(GuiGraphicsExtractor context, Minecraft client) {
        long remainingMs = PingRelayClient.pingCooldownRemainingMs();
        int durationMs = PingRelayClient.pingCooldownDurationMs();
        if (remainingMs <= 0L || durationMs <= 0) return;

        int panelWidth = 128;
        int panelHeight = 34;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        double elapsedMs = Math.max(0.0D, durationMs - remainingMs);
        double slideIn = clamp01(elapsedMs / 170.0D);
        double slideOut = clamp01(remainingMs / 170.0D);
        double visible = Math.min(slideIn, slideOut);
        int x = screenW - 10 - panelWidth + (int) Math.round((1.0D - visible) * (panelWidth + 14));
        int y = Math.max(10, screenH - 64);

        context.fill(x, y, x + panelWidth, y + panelHeight, COOLDOWN_BG);
        context.fill(x, y, x + 4, y + panelHeight, ACCENT);
        context.fill(x, y, x + panelWidth, y + 1, COOLDOWN_BORDER);
        context.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, COOLDOWN_BORDER);
        context.fill(x, y, x + 1, y + panelHeight, COOLDOWN_BORDER);
        context.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, COOLDOWN_BORDER);

        String label = "Ping Cooldown";
        String time = String.format(java.util.Locale.ROOT, "%.1fs", remainingMs / 1000.0D);
        context.text(client.font, Component.literal(label), x + 10, y + 6, TEXT_PRIMARY);
        context.text(client.font, Component.literal(time), x + panelWidth - 10 - client.font.width(time), y + 6, TEXT_MUTED);

        int barX = x + 10;
        int barY = y + 24;
        int barWidth = panelWidth - 20;
        context.fill(barX, barY, barX + barWidth, barY + 3, COOLDOWN_TRACK);
        int filledWidth = (int) Math.round(barWidth * clamp01(remainingMs / (double) durationMs));
        context.fill(barX, barY, barX + filledWidth, barY + 3, ACCENT);
    }

    private static double effectiveMaxDistance() {
        return Math.min(MAX_VISIBLE_PING_DISTANCE, Math.max(0, BetterUCConfig.INSTANCE.pingRelayMaxDistance));
    }

    private static void renderWorldMarker(GuiGraphicsExtractor context, Minecraft client, PingRelayClient.PingMarker marker) {
        long target = projectToScreen(client, marker);
        if (target == Long.MIN_VALUE) return;
        int targetX = unpackScreenX(target);
        int targetY = unpackScreenY(target);

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        PingRelayClient.PingType pingType = PingRelayClient.PingType.fromId(marker.pingType());
        String title = pingType.label() + " | " + safe(marker.sender());
        String label = Math.round(distanceToPlayer(client, marker)) + "m";
        int width = Math.max(64, Math.max(client.font.width(title), client.font.width(label)) + 20);
        String style = BetterUCConfig.INSTANCE.pingHudStyle;
        String font = BetterUCConfig.INSTANCE.pingHudCustomFont;
        boolean modernStyle = BetterUCConfig.isModernHudStyle(style);
        int height = modernStyle ? 31 : 24;
        float scale = BetterUCConfig.normalizeHudScale(BetterUCConfig.INSTANCE.pingHudScale);
        int scaledWidth = ModernHudRenderer.scaledSize(width, scale);
        int scaledHeight = ModernHudRenderer.scaledSize(height, scale);
        int markerGap = ModernHudRenderer.scaledSize(17, scale);
        int x = Mth.clamp(targetX - scaledWidth / 2, 8, Math.max(8, screenW - scaledWidth - 8));
        int y = Mth.clamp(targetY - scaledHeight - markerGap, 8, Math.max(8, screenH - scaledHeight - 8));
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
            GuiGraphicsExtractor context,
            Minecraft client,
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
                context.centeredText(client.font, Component.literal("!"), targetX, targetY - 18, accent);
                context.fill(targetX - cross, targetY, targetX + cross + 1, targetY + thickness, accent);
                context.fill(targetX, targetY - cross, targetX + thickness, targetY + cross + 1, accent);
            }
            case GATHER -> {
                context.centeredText(client.font, Component.literal("v"), targetX, targetY - 18, accent);
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
            GuiGraphicsExtractor context,
            Minecraft client,
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
            context.centeredText(client.font, Component.literal(title), width / 2, 6, accent);
            context.centeredText(client.font, Component.literal(label), width / 2, 18, TEXT_PRIMARY);
            return;
        }

        int titleX = (width - client.font.width(title)) / 2;
        int labelX = (width - client.font.width(label)) / 2;
        if (BetterUCConfig.isStylizedHudStyle(style)) {
            ModernHudRenderer.drawStyledText(context, client, style, font, title, titleX, 0, accent);
            ModernHudRenderer.drawStyledText(context, client, style, font, label, labelX, 12, TEXT_PRIMARY);
            return;
        }

        context.text(client.font, Component.literal(title), titleX, 0, accent);
        context.text(client.font, Component.literal(label), labelX, 12, TEXT_PRIMARY);
    }

    private static double distanceToPlayer(Minecraft client, PingRelayClient.PingMarker marker) {
        double dx = marker.x() - client.player.getX();
        double dy = marker.y() - client.player.getY();
        double dz = marker.z() - client.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static long projectToScreen(Minecraft client, PingRelayClient.PingMarker marker) {
        Vec3 target = new Vec3(marker.x(), marker.y(), marker.z());
        Vec3 cameraPos = client.gameRenderer.mainCamera().position();
        Vec3 toTarget = target.subtract(cameraPos);
        if (toTarget.lengthSqr() < 0.0001D) return Long.MIN_VALUE;

        Vec3 cameraLook = Vec3.directionFromRotation(client.gameRenderer.mainCamera().xRot(), client.gameRenderer.mainCamera().yRot());
        if (toTarget.normalize().dot(cameraLook.normalize()) <= 0.05D) {
            return Long.MIN_VALUE;
        }

        Vec3 look = client.player.getViewVector(1.0F);
        if (toTarget.normalize().dot(look.normalize()) <= 0.05D) {
            return Long.MIN_VALUE;
        }

        Vec3 projected = client.gameRenderer.projectPointToScreen(target);
        if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) return Long.MIN_VALUE;

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int x = (int) Math.round((projected.x + 1.0D) * 0.5D * screenW);
        int y = (int) Math.round((1.0D - projected.y) * 0.5D * screenH);

        if (x < -32 || x > screenW + 32 || y < -32 || y > screenH + 32) {
            return Long.MIN_VALUE;
        }
        return packScreenPoint(Mth.clamp(x, 0, screenW), Mth.clamp(y, 0, screenH));
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

    private static String trimToWidth(Minecraft client, String text, int width) {
        String safe = safe(text);
        if (client.font.width(safe) <= width) return safe;
        while (safe.length() > 1 && client.font.width(safe + "...") > width) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + "...";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Ping" : value.trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
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
