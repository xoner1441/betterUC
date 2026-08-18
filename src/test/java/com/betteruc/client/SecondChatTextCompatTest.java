package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class SecondChatTextCompatTest {

    @Test
    void carriesNewsColorAcrossSiblingSegmentsMarkedWhiteByTheServer() {
        MutableComponent message = Component.empty()
                .append(Component.literal("News von ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\u2063Michellethereal_\u2063")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                .append(Component.literal(": Der Orden veranstaltet um 14:35 Uhr einen Jugendausflug.")
                        .withStyle(ChatFormatting.WHITE));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);
        List<Component> segments = copy.toFlatList();

        assertEquals(TextColor.GOLD, segments.get(0).getStyle().getColor());
        assertEquals(TextColor.GOLD, segments.get(1).getStyle().getColor());
        assertEquals(TextColor.GOLD, segments.get(2).getStyle().getColor());
    }

    @Test
    void restoresShopBodyColorFromTheLaterAquaSegment() {
        MutableComponent message = Component.empty()
                .append(Component.literal("[Shop] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Ein anonymer K\u00E4ufer ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("hat im ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("Shop").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" eingekauft!").withStyle(ChatFormatting.AQUA));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);
        List<Component> segments = copy.toFlatList();

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), segments.get(0).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), segments.get(1).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), segments.get(2).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), segments.get(3).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), segments.get(4).getStyle().getColor());
    }

    @Test
    void doesNotCarryRankBracketColorIntoNormalPlayerChat() {
        MutableComponent message = Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("11").withStyle(ChatFormatting.RED))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("ClumsyCrow_ sagt: YKvro"));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);
        List<Component> segments = copy.toFlatList();

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), segments.get(0).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), segments.get(1).getStyle().getColor());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), segments.get(2).getStyle().getColor());
        assertNull(segments.get(3).getStyle().getColor());
    }

    @Test
    void preservesClickableLinksForSecondChatWrapping() {
        ClickEvent.OpenUrl openUrl = new ClickEvent.OpenUrl(
                java.net.URI.create("https://betteruc.de/download")
        );
        Component message = Component.literal("https://betteruc.de/download")
                .withStyle(style -> style.withClickEvent(openUrl));

        Component copy = SecondChatTextCompat.copyWithResolvedStyles(message);

        assertEquals(openUrl, copy.toFlatList().get(0).getStyle().getClickEvent());
    }
}
