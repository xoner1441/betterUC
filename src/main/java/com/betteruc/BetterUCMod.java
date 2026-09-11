package com.betteruc;

import com.betteruc.client.BloodEffectParticles;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterUCMod implements ModInitializer {
    public static final String MOD_ID = "betteruc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BloodEffectParticles.register();
        LOGGER.info("betterUC loaded!");
    }
}
