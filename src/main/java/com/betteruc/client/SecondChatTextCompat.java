package com.betteruc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

public final class SecondChatTextCompat {

    private SecondChatTextCompat() {
    }

    public static List<FormattedCharSequence> wrap(Font font, Component message, int width) {
        return font.split(message, Math.max(1, width));
    }

    public static Component copyWithResolvedStyles(Component message) {
        if (message == null) return Component.empty();
        List<StyledSegment> segments = new ArrayList<>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) {
                segments.add(new StyledSegment(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent resolved = Component.empty();
        ColorRepair repair = colorRepair(message.getString());
        TextColor shopBodyColor = repair == ColorRepair.SHOP
                ? findShopBodyColor(segments)
                : null;
        TextColor[] carriedColor = {null};
        for (StyledSegment segment : segments) {
            Style resolvedStyle = segment.style();
            TextColor color = resolvedStyle.getColor();
            if (repair == ColorRepair.NEWS) {
                if (!isUncolored(color)) {
                    carriedColor[0] = color;
                } else if (carriedColor[0] != null) {
                    resolvedStyle = resolvedStyle.withColor(carriedColor[0]);
                }
            } else if (repair == ColorRepair.SHOP && isUncolored(color) && shopBodyColor != null) {
                resolvedStyle = resolvedStyle.withColor(shopBodyColor);
            }
            resolved.append(Component.literal(segment.text()).setStyle(resolvedStyle));
        }
        return resolved;
    }

    private static ColorRepair colorRepair(String raw) {
        if (raw == null || raw.isBlank()) return ColorRepair.NONE;
        String message = raw.stripLeading()
                .replaceAll("\\p{Cf}", "")
                .replaceFirst("^\\[?\\d{1,2}:\\d{2}:\\d{2}]?\\s*", "");
        if (message.startsWith("News von ")) return ColorRepair.NEWS;
        if (message.startsWith("[Shop] ")) return ColorRepair.SHOP;
        return ColorRepair.NONE;
    }

    private static TextColor findShopBodyColor(List<StyledSegment> segments) {
        TextColor aqua = TextColor.fromLegacyFormat(ChatFormatting.AQUA);
        for (StyledSegment segment : segments) {
            if (aqua.equals(segment.style().getColor())) {
                return aqua;
            }
        }

        TextColor gold = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
        for (StyledSegment segment : segments) {
            TextColor color = segment.style().getColor();
            if (!isUncolored(color) && !gold.equals(color)) {
                return color;
            }
        }
        return aqua;
    }

    private static boolean isUncolored(TextColor color) {
        return color == null || TextColor.fromLegacyFormat(ChatFormatting.WHITE).equals(color);
    }

    public static Style styleAtWidth(Font font, FormattedCharSequence text, int x) {
        if (font == null || text == null || x < 0) return null;

        int[] cursor = {0};
        Style[] result = {null};
        text.accept((index, style, codePoint) -> {
            int width = Math.max(1, font.width(FormattedCharSequence.codepoint(codePoint, style)));
            if (x >= cursor[0] && x < cursor[0] + width) {
                result[0] = style;
                return false;
            }
            cursor[0] += width;
            return true;
        });
        return result[0];
    }

    private enum ColorRepair {
        NONE,
        NEWS,
        SHOP
    }

    private record StyledSegment(String text, Style style) {
    }
}
