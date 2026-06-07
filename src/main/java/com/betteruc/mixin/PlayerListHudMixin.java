package com.betteruc.mixin;

import com.betteruc.client.PingRelayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = PlayerListHud.class, priority = 100)
public abstract class PlayerListHudMixin {
    private static final Identifier BUC_BADGE_FONT = Identifier.of("betteruc", "buc_badges");
    private static final String USER_BADGE = "\uE100";
    private static final String ADMIN_BADGE = "\uE101";
    private static final String VIP_BADGE = "\uE102";
    private static final String HELPER_BADGE = "\uE103";
    private static final String PARTNER_BADGE = "\uE104";

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private Text header;

    @Shadow
    private Text footer;

    @Shadow
    private List<PlayerListEntry> collectPlayerEntries() {
        throw new AssertionError();
    }

    @Shadow
    public abstract Text getPlayerName(PlayerListEntry entry);

    @Inject(method = "render", at = @At("TAIL"))
    private void betteruc$renderTablistBadges(
            DrawContext context,
            int scaledWindowWidth,
            Scoreboard scoreboard,
            ScoreboardObjective objective,
            CallbackInfo ci
    ) {
        if (client == null || client.textRenderer == null || client.getNetworkHandler() == null) return;

        List<PlayerListEntry> entries = collectPlayerEntries();
        if (entries.isEmpty()) return;

        TextRenderer textRenderer = client.textRenderer;
        int nameWidth = 0;
        int scoreWidth = betteruc$scoreWidth(objective);
        for (PlayerListEntry entry : entries) {
            nameWidth = Math.max(nameWidth, textRenderer.getWidth(getPlayerName(entry)));
        }

        int playerCount = entries.size();
        int rows = playerCount;
        int columns = 1;
        while (rows > 20) {
            columns++;
            rows = (playerCount + columns - 1) / columns;
        }

        boolean showAvatars = client.isInSingleplayer()
                || client.getNetworkHandler().getConnection().isEncrypted();
        int columnWidth = Math.min(
                columns * ((showAvatars ? 9 : 0) + nameWidth + scoreWidth + 13),
                scaledWindowWidth - 50
        ) / columns;
        int left = scaledWindowWidth / 2 - (columnWidth * columns + (columns - 1) * 5) / 2;
        int top = betteruc$entryTop(scaledWindowWidth, columnWidth * columns + (columns - 1) * 5);

        for (int i = 0; i < playerCount; i++) {
            PlayerListEntry entry = entries.get(i);
            MutableText badge = betteruc$badgeFor(entry);
            if (badge == null) continue;

            int column = i / rows;
            int row = i % rows;
            int cellX = left + column * columnWidth + column * 5;
            int y = top + row * 9;
            int textX = cellX + (showAvatars ? 9 : 0);
            int badgeX = textX + textRenderer.getWidth(getPlayerName(entry)) + 1;
            context.drawTextWithShadow(textRenderer, badge, badgeX, y, 0xFFFFFFFF);
        }
    }

    private int betteruc$scoreWidth(ScoreboardObjective objective) {
        if (objective == null) return 0;
        if (objective.getRenderType() == ScoreboardCriterion.RenderType.HEARTS) return 90;
        return 0;
    }

    private int betteruc$entryTop(int scaledWindowWidth, int listWidth) {
        TextRenderer textRenderer = client.textRenderer;
        int top = 10;
        int maxWidth = listWidth;
        List<OrderedText> headerLines = null;
        List<OrderedText> footerLines = null;

        if (header != null) {
            headerLines = textRenderer.wrapLines(header, scaledWindowWidth - 50);
            for (OrderedText line : headerLines) {
                maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
            }
        }

        if (footer != null) {
            footerLines = textRenderer.wrapLines(footer, scaledWindowWidth - 50);
            for (OrderedText line : footerLines) {
                maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
            }
        }

        if (headerLines != null) {
            top += headerLines.size() * 9 + 1;
        }

        return top;
    }

    private static MutableText betteruc$badgeFor(PlayerListEntry entry) {
        if (!PingRelayClient.hasBetterUCBadge(entry)) return null;
        if (PingRelayClient.hasAdminBadge(entry)) {
            return betteruc$badge(ADMIN_BADGE);
        }
        if (PingRelayClient.hasHelperBadge(entry)) {
            return betteruc$badge(HELPER_BADGE);
        }
        if (PingRelayClient.hasPartnerBadge(entry)) {
            return betteruc$badge(PARTNER_BADGE);
        }
        if (PingRelayClient.hasVipBadge(entry)) {
            return betteruc$badge(VIP_BADGE);
        }
        return betteruc$badge(USER_BADGE);
    }

    private static MutableText betteruc$badge(String glyph) {
        return Text.literal(glyph)
                .formatted(Formatting.WHITE)
                .styled(style -> style.withFont(new StyleSpriteSource.Font(BUC_BADGE_FONT)));
    }
}
