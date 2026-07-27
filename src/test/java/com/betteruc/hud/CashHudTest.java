package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CashHudTest {

    @Test
    void casinoPurchaseUsesNegativeCashAmountInsteadOfTokenCount() {
        CashHud.CasinoCashDelta delta = CashHud.parseCasinoCashDelta(
                "12:34:56 » ᴄᴀsɪɴᴏ • Gekauft: 100 Jetons (-10.000$)"
        );

        assertNotNull(delta);
        assertEquals('-', delta.sign());
        assertEquals(10_000, delta.amount());
    }

    @Test
    void casinoSaleAddsPayoutAndIgnoresTaxAmount() {
        CashHud.CasinoCashDelta delta = CashHud.parseCasinoCashDelta(
                "12:34:56 » ᴄᴀsɪɴᴏ • Verkauft: 100 Jetons (+9.000$, Steuer -1.000$)"
        );

        assertNotNull(delta);
        assertEquals('+', delta.sign());
        assertEquals(9_000, delta.amount());
    }
}
