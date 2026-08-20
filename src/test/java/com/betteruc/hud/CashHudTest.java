package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void itemSaleAddsTheDollarAmountToCash() {
        Integer amount = CashHud.parsePlayerItemSaleAmount(
                "08:08:49 [Holzfäller] Du hast 38x Super-Holz für 228$ verkauft."
        );

        assertNotNull(amount);
        assertEquals(228, amount);
    }

    @Test
    void itemSaleAlsoAcceptsUeSpelling() {
        Integer amount = CashHud.parsePlayerItemSaleAmount(
                "08:08:49 [Holzfaeller] Du hast 2x Holz fuer 1.200$ verkauft."
        );

        assertNotNull(amount);
        assertEquals(1_200, amount);
    }

    @Test
    void fangComboAddsItsRewardToCash() {
        Integer amount = CashHud.parseFangComboCashAmount(
                "09:11:02 Combo! x66 Fang-Combo! +12$"
        );

        assertNotNull(amount);
        assertEquals(12, amount);
    }

    @Test
    void fangComboAcceptsGroupedRewardAmounts() {
        Integer amount = CashHud.parseFangComboCashAmount(
                "09:11:02 x120 Fang-Combo +1.250$"
        );

        assertNotNull(amount);
        assertEquals(1_250, amount);
    }

    @Test
    void recognizesCemeteryEntryAsTemporaryCashReset() {
        assertTrue(CashHud.isCemeteryEntryMessage(
                "02:03:40 Du bist nun für 5 Minuten auf dem Friedhof."
        ));
        assertTrue(CashHud.isCemeteryEntryMessage(
                "Du bist nun fuer 1 Minute auf dem Friedhof"
        ));
        assertFalse(CashHud.isCemeteryEntryMessage(
                "[Friedhof] Du lebst nun wieder."
        ));
    }

    @Test
    void repeatedSignedPurchaseAmountsAreSeparateTransactions() {
        assertFalse(CashHud.isComplementarySourcePair(
                CashHud.DeltaSource.SIGNED_LINE,
                CashHud.DeltaSource.SIGNED_LINE
        ));
    }

    @Test
    void signedAndContextLinesCanRepresentTheSameTransaction() {
        assertTrue(CashHud.isComplementarySourcePair(
                CashHud.DeltaSource.SIGNED_LINE,
                CashHud.DeltaSource.CONTEXT
        ));
        assertTrue(CashHud.isComplementarySourcePair(
                CashHud.DeltaSource.CONTEXT,
                CashHud.DeltaSource.SIGNED_LINE
        ));
    }

    @Test
    void repeatedContextTransactionsAreNotCollapsed() {
        assertFalse(CashHud.isComplementarySourcePair(
                CashHud.DeltaSource.CONTEXT,
                CashHud.DeltaSource.CONTEXT
        ));
    }
}
