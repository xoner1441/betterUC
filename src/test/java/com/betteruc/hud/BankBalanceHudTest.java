package com.betteruc.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankBalanceHudTest {

    @Test
    void bankBalanceAcceptsOnlyCompleteServerMessage() {
        assertEquals(123_564, BankBalanceHud.parseBankBalanceMessage(
                "21:10:04 Ihr Bankguthaben beträgt: +123.564$"
        ));
        assertNull(BankBalanceHud.parseBankBalanceMessage(
                "21:10:04 FBI pixel412: 21:08:58 Ihr Bankguthaben beträgt: +123.564$"
        ));
        assertNull(BankBalanceHud.parseBankBalanceMessage(
                "21:10:04 [FBI] pixel412: Ihr Bankguthaben beträgt: +123.564$"
        ));
    }

    @Test
    void dailyRewardHeaderSupportsSmallCapsServerMessage() {
        assertTrue(BankBalanceHud.matchesDailyRewardHeader(
                "12:34:56 » ᴅᴀɪʟʏ ʀᴇᴡᴀʀᴅ • Tag 6 abgeholt! (Staffel 1)"
        ));
        assertFalse(BankBalanceHud.matchesDailyRewardHeader(
                "12:34:56 » Eine normale Belohnung wurde abgeholt!"
        ));
        assertFalse(BankBalanceHud.matchesDailyRewardHeader(
                "12:34:56 FBI pixel412: ᴅᴀɪʟʏ ʀᴇᴡᴀʀᴅ • Tag 6 abgeholt! (Staffel 1)"
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
    void partialAtmCapacityMessageIsRecognizedWithVariableAmounts() {
        assertTrue(BankBalanceHud.matchesFullAtmMessage(
                "00:12:10 Du versuchst 40.649$ einzuzahlen, der Bankautomat hat aber "
                        + "nur Platz f\u00FCr 40.088$. Fortfahren? [Best\u00E4tigen]"
        ));
        assertTrue(BankBalanceHud.matchesFullAtmMessage(
                "Du versuchst 1$ einzuzahlen, der Bankautomat hat aber nur Platz fuer 999$. "
                        + "Fortfahren? [Bestaetigen]"
        ));
    }

    @Test
    void unrelatedBankMessagesDoNotTriggerForcedDeposit() {
        assertFalse(BankBalanceHud.matchesFullAtmMessage(
                "12:34:56 Ihr Bankguthaben beträgt: 100.000$"
        ));
    }
}
