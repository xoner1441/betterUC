package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankBalanceHudTest {

    @Test
    void dailyRewardHeaderSupportsSmallCapsServerMessage() {
        assertTrue(BankBalanceHud.matchesDailyRewardHeader(
                "12:34:56 » ᴅᴀɪʟʏ ʀᴇᴡᴀʀᴅ • Tag 6 abgeholt! (Staffel 1)"
        ));
        assertFalse(BankBalanceHud.matchesDailyRewardHeader(
                "12:34:56 » Eine normale Belohnung wurde abgeholt!"
        ));
    }

    @Test
    void dailyRewardMoneyReadsOnlyDollarLine() {
        assertEquals(12_500, BankBalanceHud.parseDailyRewardMoney(" • + 12.500$"));
        assertNull(BankBalanceHud.parseDailyRewardMoney(" • + 50 XP"));
    }
    @Test
    void fullAtmMessageIsRecognizedWithTimestampAndAction() {
        assertTrue(BankBalanceHud.matchesFullAtmMessage(
                "12:34:56 Dieser Bankautomat ist voll. [Trotzdem einzahlen]"
        ));
    }

    @Test
    void unrelatedBankMessagesDoNotTriggerForcedDeposit() {
        assertFalse(BankBalanceHud.matchesFullAtmMessage(
                "12:34:56 Ihr Bankguthaben beträgt: 100.000$"
        ));
    }
}
