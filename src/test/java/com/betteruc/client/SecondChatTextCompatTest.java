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
    void carriesNewsColorAcrossSiblingSegmentsWithoutOwnColor() {
        MutableComponent message = Component.empty()
                .append(Component.literal("News von ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\u200CMichellethereal_\u200C").withStyle(ChatFormatting.UNDERLINE))
                .append(Component.literal(": Der Orden veranstaltet um 14:35 Uhr einen Jugendausflug."));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);
        List<Component> segments = copy.toFlatList();

        assertEquals(TextColor.GOLD, segments.get(0).getStyle().getColor());
        assertEquals(TextColor.GOLD, segments.get(1).getStyle().getColor());
        assertEquals(TextColor.GOLD, segments.get(2).getStyle().getColor());
    }

    @Test
    void carriesPrefixColorUntilServerSetsAnotherColor() {
        MutableComponent message = Component.empty()
                .append(Component.literal("[Shop] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Ein anonymer K\u00E4ufer "))
                .append(Component.literal("hat im Shop eingekauft!").withStyle(ChatFormatting.AQUA));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);
        List<Component> segments = copy.toFlatList();

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), segments.get(0).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), segments.get(1).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), segments.get(2).getStyle().getColor());
    }
}
