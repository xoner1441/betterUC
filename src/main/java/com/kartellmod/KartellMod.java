package com.kartellmod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KartellMod implements ModInitializer {
    public static final String MOD_ID = "kartellmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Kartell Mod loaded!");
    }
}
