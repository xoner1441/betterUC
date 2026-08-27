package com.betteruc.client;

import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandToggleControllerTest {

    @Test
    void togglesBetweenBothVanillaHands() {
        assertEquals(HumanoidArm.LEFT, HandToggleController.nextHand(HumanoidArm.RIGHT));
        assertEquals(HumanoidArm.RIGHT, HandToggleController.nextHand(HumanoidArm.LEFT));
    }

    @Test
    void labelsBothHandsInGerman() {
        assertEquals("LINKS", HandToggleController.handLabel(HumanoidArm.LEFT));
        assertEquals("RECHTS", HandToggleController.handLabel(HumanoidArm.RIGHT));
    }
}
