package com.betteruc.client;

import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class SecondChatTextCompat {

    private SecondChatTextCompat() {
    }

    public static List<OrderedText> wrap(TextRenderer font, Text message, int width) {
        return font.wrapLines(message, Math.max(1, width));
    }

    public static Text copyWithResolvedStyles(Text message) {
        MutableText resolved = Text.empty();
        if (message == null) return resolved;

        for (Text segment : message.getWithStyle(Style.EMPTY)) {
            resolved.append(segment.copy());
        }
        return resolved;
    }

    public static Style styleAtWidth(TextRenderer font, OrderedText text, int x) {
        if (font == null || text == null || x < 0) return null;

        int[] cursor = {0};
        Style[] result = {null};
        text.accept((index, style, codePoint) -> {
            int width = Math.max(1, font.getWidth(OrderedText.styled(codePoint, style)));
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
