package com.betteruc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementTypeMatcherTest {

    @Test
    void classifiesAllSupportedReinforcementTypes() {
        assertType(ReinforcementTypeMatcher.Type.NORMAL,
                "Unterst\u00FCtzung ben\u00F6tigt! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.URGENT,
                "Dringend! El Commandant\u00E9 FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.MEDIC,
                "Medic ben\u00F6tigt! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.HOSTAGE,
                "Geiselnahme! El Commandant\u00E9 FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.CONTRACT,
                "Contract! El Commandant\u00E9 FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.TRAINING,
                "Training! El Commandant\u00E9 FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.DRUGS,
                "Drogenabnahme! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.BODY_GUARD,
                "Leichenbewachung! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.BOMB,
                "Bombe! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Bank! (12 Meter entfernt)");
        assertType(ReinforcementTypeMatcher.Type.PLANTAGE,
                "Plantage! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung in der N\u00E4he von Plantage! (12 Meter entfernt)");
    }

    @Test
    void recognizesOnlyThePlayerWhoRequestedTheReinforcement() {
        String message = "Plantage! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung "
                + "in der N\u00E4he von Plantage! (12 Meter entfernt)";

        assertTrue(ReinforcementTypeMatcher.isRequestedBy(message, "FABI1441"));
        assertTrue(ReinforcementTypeMatcher.isRequestedBy(message, "fabi1441"));
        assertFalse(ReinforcementTypeMatcher.isRequestedBy(message, "AndererSpieler"));
    }

    @Test
    void doesNotTreatTheLocationAsTheReinforcementType() {
        assertType(ReinforcementTypeMatcher.Type.NORMAL,
                "Unterst\u00FCtzung ben\u00F6tigt! Calder\u00F3n Kartell FABI1441 ben\u00F6tigt Unterst\u00FCtzung "
                        + "in der N\u00E4he von Plantage! (12 Meter entfernt)");
    }

    @Test
    void ignoresActionAndUnrelatedChatLines() {
        assertNull(ReinforcementTypeMatcher.classify("Route anzeigen | Unterwegs"));
        assertNull(ReinforcementTypeMatcher.classify("FABI1441 ben\u00F6tigt Unterst\u00FCtzung beim Sortieren."));
    }

    private static void assertType(ReinforcementTypeMatcher.Type expected, String message) {
        assertEquals(expected, ReinforcementTypeMatcher.classify(message));
    }
}
