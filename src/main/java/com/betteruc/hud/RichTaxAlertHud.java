package com.betteruc.hud;

import com.betteruc.client.ClientScheduler;
import com.betteruc.client.ServerCommandUtil;
import com.betteruc.config.BetterUCConfig;
import java.text.Normalizer;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class RichTaxAlertHud {

    public static final int TAX_THRESHOLD = 100_000;

    private static final long DISPLAY_DURATION_MS = 8_000L;
    private static final long SLIDE_DURATION_MS = 220L;
    private static final long BANK_VALUE_FRESHNESS_MS = 120_000L;
    private static final long BANK_REFRESH_TIMEOUT_MS = 12_000L;
    private static final long BANK_REFRESH_DELAY_MS = 150L;
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 50;
    private static final int BACKGROUND = 0xEC0D1117;
    private static final int BORDER = 0xAA475569;
    private static final int ACCENT = 0xFFFF5555;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFFCBD5E1;
    private static final int TRACK = 0xFF293241;

    private static boolean warningHandled;
    private static boolean waitingForBankRefresh;
    private static long bankRefreshDeadlineMs;
    private static long shownAtMs;
    private static long visibleUntilMs;
    private static int shownBalance = -1;

    private RichTaxAlertHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("betteruc", "rich_tax_alert"),
                (context, tickCounter) -> {
                    if (ModernHudRenderer.shouldRenderGameplayHud()) {
                        render(context);
                    }
                }
        );
    }

    public static void handleChatLine(Minecraft client, String raw) {
        if (client == null || client.player == null) return;
        if (!BetterUCConfig.INSTANCE.richTaxAlertEnabled) return;
        if (warningHandled || !matchesPaydayWarning(raw)) return;

        warningHandled = true;
        int balance = BankBalanceHud.getCurrentBankBalance();
        long balanceAgeMs = BankBalanceHud.getBalanceAgeMs();
        if (balance >= 0 && balanceAgeMs <= BANK_VALUE_FRESHNESS_MS) {
            evaluateBalance(client, balance);
            return;
        }

        waitingForBankRefresh = true;
        bankRefreshDeadlineMs = System.currentTimeMillis() + BANK_REFRESH_TIMEOUT_MS;
        requestBankRefresh(client, 0);
    }

    public static void onBankBalanceUpdated(Minecraft client, int balance) {
        if (!waitingForBankRefresh) return;
        waitingForBankRefresh = false;
        bankRefreshDeadlineMs = 0L;
        evaluateBalance(client, balance);
    }

    public static void tick(Minecraft client) {
        if (!waitingForBankRefresh) return;
        if (System.currentTimeMillis() < bankRefreshDeadlineMs) return;
        evaluateFallbackBalance(client);
    }

    public static void resetForNewPayday() {
        warningHandled = false;
        waitingForBankRefresh = false;
        bankRefreshDeadlineMs = 0L;
    }

    public static void clear() {
        warningHandled = false;
        waitingForBankRefresh = false;
        bankRefreshDeadlineMs = 0L;
        shownAtMs = 0L;
        visibleUntilMs = 0L;
        shownBalance = -1;
    }

    public static boolean matchesPaydayWarning(String raw) {
        String folded = fold(raw);
        return folded.contains("info du hast in 5 minuten deinen payday");
    }

    private static void evaluateFallbackBalance(Minecraft client) {
        waitingForBankRefresh = false;
        bankRefreshDeadlineMs = 0L;
        evaluateBalance(client, BankBalanceHud.getCurrentBankBalance());
    }

    private static void evaluateBalance(Minecraft client, int balance) {
        if (!BetterUCConfig.INSTANCE.richTaxAlertEnabled) return;
        if (balance <= TAX_THRESHOLD) return;
        show(client, balance);
    }

    private static void requestBankRefresh(Minecraft client, int attempt) {
        long delayMs = attempt == 0 ? BANK_REFRESH_DELAY_MS : 700L;
        ClientScheduler.runDelayedOnClient(client, delayMs, () -> {
            if (!waitingForBankRefresh || client.player == null) return;
            if (System.currentTimeMillis() >= bankRefreshDeadlineMs) {
                evaluateFallbackBalance(client);
                return;
            }
            if (ServerCommandUtil.sendAutomatic(client, "bank")) return;
            if (attempt < 5) {
                requestBankRefresh(client, attempt + 1);
            } else {
                evaluateFallbackBalance(client);
            }
        });
    }

    private static void show(Minecraft client, int balance) {
        if (client == null || client.player == null) return;

        long now = System.currentTimeMillis();
        shownAtMs = now;
        visibleUntilMs = now + DISPLAY_DURATION_MS;
        shownBalance = balance;

        MutableComponent message = Component.literal("[betterUC] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Reichensteuer-Warnung! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("Bank: " + BankBalanceHud.formatMoney(balance) + "$")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | PayDay in 5 Minuten.").withStyle(ChatFormatting.GRAY));
        client.player.sendSystemMessage(message);

        if (BetterUCConfig.INSTANCE.richTaxAlertSoundEnabled) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8F, 0.8F);
        }
    }

    private static void render(GuiGraphicsExtractor context) {
        long now = System.currentTimeMillis();
        if (!BetterUCConfig.INSTANCE.richTaxAlertEnabled) return;
        if (shownBalance < 0 || now >= visibleUntilMs) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        long elapsedMs = Math.max(0L, now - shownAtMs);
        long remainingMs = Math.max(0L, visibleUntilMs - now);
        double slideIn = clamp01(elapsedMs / (double) SLIDE_DURATION_MS);
        double slideOut = clamp01(remainingMs / (double) SLIDE_DURATION_MS);
        double visible = smoothStep(Math.min(slideIn, slideOut));
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int x = screenWidth - 10 - PANEL_WIDTH
                + (int) Math.round((1.0D - visible) * (PANEL_WIDTH + 14));
        int y = 10;

        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BACKGROUND);
        context.fill(x, y, x + 4, y + PANEL_HEIGHT, ACCENT);
        context.fill(x, y, x + PANEL_WIDTH, y + 1, BORDER);
        context.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);
        context.fill(x, y, x + 1, y + PANEL_HEIGHT, BORDER);
        context.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, BORDER);

        context.text(client.font, Component.literal("Reichensteuer-Warnung"), x + 11, y + 6, ACCENT);
        String balanceText = "Bank: " + BankBalanceHud.formatMoney(shownBalance) + "$";
        context.text(client.font, Component.literal(balanceText), x + 11, y + 19, TEXT_PRIMARY);
        String detail = "Grenze: 100.000$ | PayDay in 5 Min.";
        context.text(client.font, Component.literal(detail), x + 11, y + 31, TEXT_MUTED);

        int barX = x + 11;
        int barY = y + PANEL_HEIGHT - 5;
        int barWidth = PANEL_WIDTH - 22;
        context.fill(barX, barY, barX + barWidth, barY + 2, TRACK);
        int filled = (int) Math.round(barWidth * clamp01(remainingMs / (double) DISPLAY_DURATION_MS));
        context.fill(barX, barY, barX + filled, barY + 2, ACCENT);
    }

    private static String fold(String raw) {
        if (raw == null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\u00A7[0-9A-FK-OR]", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double smoothStep(double value) {
        double safe = clamp01(value);
        return safe * safe * (3.0D - 2.0D * safe);
    }
}
