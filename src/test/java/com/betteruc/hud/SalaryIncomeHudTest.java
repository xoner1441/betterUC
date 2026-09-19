package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalaryIncomeHudTest {

    @Test
    void parsesSalaryMessageWithTimestampAndThousandsSeparator() {
        assertEquals(10L, SalaryIncomeHud.parseSalaryIncome(
                "[PayDay] Du bekommst dein Gehalt von 10$ am PayDay ausgezahlt."
        ));
        assertEquals(12_345L, SalaryIncomeHud.parseSalaryIncome(
                "18:47:24 [PayDay] Du bekommst dein Gehalt von 12.345$ am PayDay ausgezahlt"
        ));
    }

    @Test
    void ignoresUnrelatedOrSpoofedMessages() {
        assertNull(SalaryIncomeHud.parseSalaryIncome("======== PayDay ========"));
        assertNull(SalaryIncomeHud.parseSalaryIncome(
                "[PayDay] Du bekommst deine Mine Einnahmen von 30$ am PayDay ausgezahlt"
        ));
        assertNull(SalaryIncomeHud.parseSalaryIncome(
                "Spieler: [PayDay] Du bekommst dein Gehalt von 10$ am PayDay ausgezahlt"
        ));
        assertNull(SalaryIncomeHud.parseSalaryIncome(
                "[PayDay] Du bekommst dein Gehalt von 0$ am PayDay ausgezahlt"
        ));
    }

    @Test
    void onlyShowsAWaitingSalaryAndFormatsIt() {
        assertFalse(SalaryIncomeHud.shouldShow(0L));
        assertTrue(SalaryIncomeHud.shouldShow(10L));
        assertEquals("12.345", SalaryIncomeHud.formatMoney(12_345L));
        assertEquals(25L, SalaryIncomeHud.saturatingAdd(10L, 15L));
        assertEquals(Long.MAX_VALUE, SalaryIncomeHud.saturatingAdd(Long.MAX_VALUE - 5L, 10L));
    }
}
