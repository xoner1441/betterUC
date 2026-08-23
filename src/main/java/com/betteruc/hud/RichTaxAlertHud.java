package com.betteruc.hud;

import com.betteruc.config.BetterUCConfig;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 50;
    private static final int BACKGROUND = 0xEC0D1117;
    private static final int BORDER = 0xAA475569;
    private static final int ACCENT = 0xFFFF5555;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFFCBD5E1;
    private static final int TRACK = 0xFF293241;
    private static final Set<Integer> WARNING_MINUTES = Set.of(5, 3, 2, 1);
    private static final Pattern PAYDAY_WARNING_PATTERN = Pattern.compile(
            "(?:^| )info du hast in (\\d+) minute(?:n)? deinen payday(?: |$)"
    );

    private static final Set<Integer> handledWarningMinutes = new HashSet<>();
    private static long shownAtMs;
    private static long visibleUntilMs;
    private static int shownBalance = -1;
    private static int shownWarningMinutes = -1;

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
        int warningMinutes = paydayWarningMinutes(raw);
        if (warningMinutes < 0 || !handledWarningMinutes.add(warningMinutes)) return;

        evaluateBalance(client, BankBalanceHud.getCurrentBankBalance(), warningMinutes);
    }

    public static void resetForNewPayday() {
        handledWarningMinutes.clear();
    }

    public static void clear() {
        handledWarningMinutes.clear();
        shownAtMs = 0L;
        visibleUntilMs = 0L;
        shownBalance = -1;
        shownWarningMinutes = -1;
    }

    public static boolean matchesPaydayWarning(String raw) {
        return paydayWarningMinutes(raw) >= 0;
    }

    static int paydayWarningMinutes(String raw) {
        String folded = fold(raw);
        Matcher matcher = PAYDAY_WARNING_PATTERN.matcher(folded);
        if (!matcher.find()) return -1;

        try {
            int minutes = Integer.parseInt(matcher.group(1));
            return WARNING_MINUTES.contains(minutes) ? minutes : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void evaluateBalance(Minecraft client, int balance, int warningMinutes) {
        if (!BetterUCConfig.INSTANCE.richTaxAlertEnabled) return;
        if (balance <= TAX_THRESHOLD) return;
        show(client, balance, warningMinutes);
    }

    private static void show(Minecraft client, int balance, int warningMinutes) {
        if (client == null || client.player == null) return;

        long now = System.currentTimeMillis();
        shownAtMs = now;
        visibleUntilMs = now + DISPLAY_DURATION_MS;
        shownBalance = balance;
        shownWarningMinutes = warningMinutes;

        MutableComponent message = Component.literal("[betterUC] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Reichensteuer-Warnung! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("Bank: " + BankBalanceHud.formatMoney(balance) + "$")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | PayDay in " + minuteLabel(warningMinutes) + ".")
                        .withStyle(ChatFormatting.GRAY));
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
        String detail = "Grenze: 100.000$ | PayDay in " + shownWarningMinutes + " Min.";
        context.text(client.font, Component.literal(detail), x + 11, y + 31, TEXT_MUTED);

        int barX = x + 11;
        int barY = y + PANEL_HEIGHT - 5;
        int barWidth = PANEL_WIDTH - 22;
        context.fill(barX, barY, barX + barWidth, barY + 2, TRACK);
        int filled = (int) Math.round(barWidth * clamp01(remainingMs / (double) DISPLAY_DURATION_MS));
        context.fill(barX, barY, barX + filled, barY + 2, ACCENT);
    }

    private static String minuteLabel(int minutes) {
        return minutes + (minutes == 1 ? " Minute" : " Minuten");
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
