package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class ChatLinkifierTest {

    @Test
    void recognizesHttpAndWwwLinksWithoutTrailingPunctuation() {
        Component original = Component.literal(
                "Download: https://betteruc.de/download, Forum: www.example.com/test)."
        );

        Component linked = ChatLinkifier.linkify(original, true, true);
        List<LinkSegment> links = linkSegments(linked);

        assertEquals(original.getString(), linked.getString());
        assertEquals(2, links.size());
        assertEquals("https://betteruc.de/download", links.get(0).text());
        assertEquals(URI.create("https://betteruc.de/download"),
                assertInstanceOf(ClickEvent.OpenUrl.class, links.get(0).event()).uri());
        assertEquals("www.example.com/test", links.get(1).text());
        assertEquals(URI.create("https://www.example.com/test"),
                assertInstanceOf(ClickEvent.OpenUrl.class, links.get(1).event()).uri());
        assertTrue(links.get(0).style().isUnderlined());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), links.get(0).style().getColor());
        assertInstanceOf(HoverEvent.ShowText.class, links.get(0).style().getHoverEvent());
    }

    @Test
    void preservesExistingCommandActionsInsteadOfReplacingThem() {
        ClickEvent.RunCommand command = new ClickEvent.RunCommand("/reinfaccept request-42");
        Component original = Component.literal("https://betteruc.de")
                .withStyle(style -> style.withClickEvent(command));

        Component linked = ChatLinkifier.linkify(original, true, true);
        List<LinkSegment> links = linkSegments(linked);

        assertSame(original, linked);
        assertEquals(1, links.size());
        assertSame(command, links.get(0).event());
    }

    @Test
    void returnsOriginalComponentWhenFeatureIsDisabled() {
        Component original = Component.literal("https://betteruc.de");
        assertSame(original, ChatLinkifier.linkify(original, false, true));
    }

    private static List<LinkSegment> linkSegments(Component component) {
        List<LinkSegment> links = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty() && style.getClickEvent() != null) {
                links.add(new LinkSegment(text, style, style.getClickEvent()));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return links;
    }

    private record LinkSegment(String text, Style style, ClickEvent event) {
    }
}
