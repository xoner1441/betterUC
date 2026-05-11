package com.betteruc;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterUCMod implements ModInitializer {
    public static final String MOD_ID = "betteruc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("betterUC loaded!");
    }
}
