package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;

public final class HandToggleController {

    private static HumanoidArm sessionOriginalHand;
    private static boolean transientChangeActive;

    private HandToggleController() {
    }

    public static void tick(Minecraft client, KeyMapping toggleKey) {
        if (toggleKey == null) return;

        int queuedPresses = 0;
        while (toggleKey.consumeClick()) {
            queuedPresses++;
        }
        if ((queuedPresses & 1) == 0) return;

        BetterUCConfig config = BetterUCConfig.INSTANCE;
        if (!config.handToggleEnabled
                || client == null
                || client.player == null
                || ClientCompat.hasScreen(client)) {
            return;
        }

        HumanoidArm current = client.options.mainHand().get();
        if (!config.handToggleRememberLastHand && !transientChangeActive) {
            sessionOriginalHand = current;
            transientChangeActive = true;
        }

        HumanoidArm next = nextHand(current);
        client.options.mainHand().set(next);
        client.options.broadcastOptions();

        if (config.handToggleRememberLastHand) {
            client.options.save();
            sessionOriginalHand = null;
            transientChangeActive = false;
        }

        if (config.handToggleNotificationEnabled && client.gui != null && client.gui.hud != null) {
            client.gui.hud.setOverlayMessage(
                    Component.literal("Haupthand: ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(handLabel(next)).withStyle(ChatFormatting.WHITE)),
                    false
            );
        }
    }

    public static String currentHandLabel(Minecraft client) {
        if (client == null || client.options == null) return "Unbekannt";
        return handLabel(client.options.mainHand().get());
    }

    public static void reset(Minecraft client) {
        if (client != null && client.options != null && transientChangeActive) {
            if (BetterUCConfig.INSTANCE.handToggleRememberLastHand) {
                client.options.save();
            } else if (sessionOriginalHand != null) {
                client.options.mainHand().set(sessionOriginalHand);
                client.options.broadcastOptions();
                client.options.save();
            }
        }
        sessionOriginalHand = null;
        transientChangeActive = false;
    }

    static HumanoidArm nextHand(HumanoidArm current) {
        return current == HumanoidArm.LEFT ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    static String handLabel(HumanoidArm arm) {
        return arm == HumanoidArm.LEFT ? "LINKS" : "RECHTS";
    }
}
