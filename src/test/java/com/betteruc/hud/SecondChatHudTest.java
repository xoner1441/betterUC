package com.betteruc.hud;

import com.betteruc.config.SecondChatTabConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecondChatHudTest {

    @Test
    void globalAndCustomTimestampPatternsAreResolvedPerTab() {
        SecondChatTabConfig tab = new SecondChatTabConfig("Test");
        assertEquals("[HH:mm:ss]", SecondChatHud.timestampPattern(tab, "[HH:mm:ss]"));

        tab.timestampUseGlobalFormat = false;
        tab.timestampFormat = "HH:mm »";
        assertEquals("HH:mm »", SecondChatHud.timestampPattern(tab, "[HH:mm:ss]"));
    }

    @Test
    void invalidPatternFallsBackToSecondsFormat() {
        long timestamp = 1_700_000_000_000L;
        String expected = DateTimeFormatter.ofPattern("[HH:mm:ss]")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp));
        assertEquals(expected, SecondChatHud.formatTimestamp(timestamp, "[invalid"));
    }
}
