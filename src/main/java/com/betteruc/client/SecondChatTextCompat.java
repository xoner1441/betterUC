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
        MutableComponent resolved = Component.empty();
        if (message == null) return resolved;

        for (Component segment : message.toFlatList(Style.EMPTY)) {
            resolved.append(segment.copy());
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
