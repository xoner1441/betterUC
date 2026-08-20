package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatCopyClientTest {

    @Test
    void resolvesHoveredVanillaChatLineFromBottom() {
        assertEquals(0, ChatCopyClient.hoveredLineIndex(435, 480, 1.0D, 9, 0, 20, 10));
        assertEquals(1, ChatCopyClient.hoveredLineIndex(426, 480, 1.0D, 9, 0, 20, 10));
    }

    @Test
    void includesCurrentChatScrollPosition() {
        assertEquals(3, ChatCopyClient.hoveredLineIndex(431, 480, 1.0D, 9, 2, 20, 10));
    }

    @Test
    void rejectsClicksOutsideVisibleChatLines() {
        assertEquals(-1, ChatCopyClient.hoveredLineIndex(445, 480, 1.0D, 9, 0, 20, 10));
        assertEquals(-1, ChatCopyClient.hoveredLineIndex(300, 480, 1.0D, 9, 0, 20, 10));
    }
}
