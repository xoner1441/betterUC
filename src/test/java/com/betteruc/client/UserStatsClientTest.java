package com.betteruc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UserStatsClientTest {

    @Test
    void tracksCurrentPlaytimeForUserControlPanel() {
        assertEquals(502, UserStatsClient.parsePlayTimeHours("14:30:53 - Spielzeit: 502 Stunden"));
        assertNull(UserStatsClient.parsePlayTimeHours("14:30:53 - Spielzeitbonus: 0 Punkte"));
    }
}
