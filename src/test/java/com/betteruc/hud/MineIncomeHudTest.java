package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineIncomeHudTest {

    @Test
    void parsesMineAndMinenIncomeFormats() {
        assertEquals(30L, MineIncomeHud.parseMineIncome(
                "[PayDay] Du bekommst deine Mine Einnahmen von 30$ am PayDay ausgezahlt"
        ));
        assertEquals(12_345L, MineIncomeHud.parseMineIncome(
                "22:07:45 [PayDay] Du bekommst deine Minen-Einnahmen von 12.345$ am PayDay ausgezahlt."
        ));
    }

    @Test
    void ignoresOtherPaydayAndPlayerMessages() {
        assertNull(MineIncomeHud.parseMineIncome("======== PayDay ========"));
        assertNull(MineIncomeHud.parseMineIncome(
                "[PayDay] Du bekommst deine Job Einnahmen von 30$ am PayDay ausgezahlt"
        ));
        assertNull(MineIncomeHud.parseMineIncome(
                "Spieler: [PayDay] Du bekommst deine Mine Einnahmen von 30$ am PayDay ausgezahlt"
        ));
    }

    @Test
    void hudOnlyAppearsWhileIncomeIsWaitingForPayday() {
        assertFalse(MineIncomeHud.shouldShow(0L));
        assertFalse(MineIncomeHud.shouldShow(-1L));
        assertTrue(MineIncomeHud.shouldShow(30L));
    }
}
