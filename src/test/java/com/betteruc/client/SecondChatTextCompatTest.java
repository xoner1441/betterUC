package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class SecondChatTextCompatTest {

    @Test
    void keepsParentColorAroundStyledPlayerName() {
        MutableComponent message = Component.literal("News von ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\u200CdruqCindy\u200C").withStyle(ChatFormatting.UNDERLINE))
                .append(Component.literal(": Heute Abend 20:00 Uhr!"));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);
        List<Component> segments = copy.toFlatList();

        assertEquals(TextColor.GOLD, segments.get(0).getStyle().getColor());
        assertEquals(TextColor.GOLD, segments.get(1).getStyle().getColor());
        assertEquals(TextColor.GOLD, segments.get(2).getStyle().getColor());
    }
}
