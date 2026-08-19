package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class AutomationController {
    private AutomationController() {
    }

    public static boolean isDropDrinkEnabled() {
        return BetterUCConfig.INSTANCE.autoDropDrinkEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_DROPDRINK);
    }

    public static boolean isFisherEnabled() {
        return BetterUCConfig.INSTANCE.autoFisherEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_FISHER);
    }

    public static boolean isWinzerEnabled() {
        return BetterUCConfig.INSTANCE.autoWinzerEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_WINZER);
    }

    public static boolean isGaertnerEnabled() {
        return BetterUCConfig.INSTANCE.autoGaertnerEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_GAERTNER);
    }

    public static boolean isMuellmannEnabled() {
        return BetterUCConfig.INSTANCE.autoMuellmannEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_MUELLMANN);
    }

    public static boolean isMoneyTransportEnabled() {
        return BetterUCConfig.INSTANCE.autoMoneyTransportEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_MONEY_TRANSPORT);
    }

    public static boolean isTransportEnabled() {
        return BetterUCConfig.INSTANCE.autoTransportEnabled
                && RemoteFeatureFlagsClient.isEnabled(RemoteFeatureFlagsClient.AUTO_TRANSPORT);
    }

    public static boolean isFirstAidEnabled() {
        return BetterUCConfig.INSTANCE.autoFirstAidEnabled;
    }

    public static boolean isAutoBuyEnabled() {
        return BetterUCConfig.INSTANCE.autoBuyEnabled;
    }

    public static int localEnabledCount() {
        int count = 0;
        if (BetterUCConfig.INSTANCE.autoDropDrinkEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoFisherEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoWinzerEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoGaertnerEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoMuellmannEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoMoneyTransportEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoTransportEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoFirstAidEnabled) count++;
        if (BetterUCConfig.INSTANCE.autoBuyEnabled) count++;
        return count;
    }

    public static void sendDropDrinkDisabledMessage(Minecraft client) {
        if (client == null || client.player == null) return;
        if (!BetterUCConfig.INSTANCE.autoDropDrinkEnabled) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00A7c[betterUC] Auto-Dropdrink ist im ClickGUI deaktiviert."
            ));
            return;
        }
        RemoteFeatureFlagsClient.sendDisabledMessage(client, "Auto-Dropdrink");
    }
}
