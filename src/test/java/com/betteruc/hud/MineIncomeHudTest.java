package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
