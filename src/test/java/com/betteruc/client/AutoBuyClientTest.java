package com.betteruc.client;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoBuyClientTest {

    @Test
    void recognizesOnlyNamedPaymentOptions() {
        assertEquals(AutoBuyClient.PaymentMethod.CASH,
                AutoBuyClient.paymentMethod("minecraft:gold_ingot", "Bar bezahlen"));
        assertEquals(AutoBuyClient.PaymentMethod.CARD,
                AutoBuyClient.paymentMethod("minecraft:paper", "Mit Karte zahlen"));
        assertNull(AutoBuyClient.paymentMethod("minecraft:paper", "Menge 1"));
        assertNull(AutoBuyClient.paymentMethod("minecraft:barrier", "Mit Karte zahlen"));
    }

    @Test
    void readsDisplayedCardPriceIncludingServerFee() {
        assertEquals(41, AutoBuyClient.parsePaymentPrice(List.of(
                "Preis: 41$",
                "Menge: 1",
                "1% Gebühren"
        )));
        assertEquals(12_345, AutoBuyClient.parsePaymentPrice(List.of("§7Preis: §c12.345$")));
    }

    @Test
    void rejectsMissingOrInvalidPrices() {
        assertEquals(0, AutoBuyClient.parsePaymentPrice(List.of("Menge: 1", "1% Gebühren")));
        assertEquals(0, AutoBuyClient.parsePaymentPrice(List.of("Preis: 999999999999$")));
    }
}
