package com.betteruc.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class SecondChatTextCompat {

    private SecondChatTextCompat() {
    }

    public static List<FormattedCharSequence> wrap(Font font, Component message, int width) {
        return font.split(message, Math.max(1, width));
    }

    public static Component copyWithResolvedStyles(Component message) {
        if (message == null) return Component.empty();
        return copyWithResolvedStyles(message, Style.EMPTY);
    }

    private static MutableComponent copyWithResolvedStyles(Component message, Style inheritedStyle) {
        Style resolvedStyle = message.getStyle().applyTo(inheritedStyle);
        MutableComponent resolved = MutableComponent.create(message.getContents()).setStyle(resolvedStyle);

        for (Component sibling : message.getSiblings()) {
            resolved.append(copyWithResolvedStyles(sibling, resolvedStyle));
        }
        return resolved;
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
}
