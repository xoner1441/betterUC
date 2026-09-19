package com.betteruc.client;

import com.betteruc.config.BetterUCConfig;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodEffectSettingsTest {
    @Test void defaultsSyncAndCloudValuesAreSanitized(@TempDir Path directory) throws Exception {
        // BetterUCConfig resolves its config file during class initialization.
        var loader = FabricLoader.getInstance();
        var configDirectory = loader.getClass().getDeclaredField("configDir");
        configDirectory.setAccessible(true);
        var previousDirectory = configDirectory.get(loader);
        configDirectory.set(loader, directory);
        BetterUCConfig previous = BetterUCConfig.INSTANCE;
        try {
            BetterUCConfig.INSTANCE = new BetterUCConfig();
            JsonObject snapshot = BetterUCConfig.cloudSettingsSnapshot();

            assertTrue(snapshot.has("bloodEffectIntensityPercent"));
            assertTrue(snapshot.has("bloodEffectMotif"));
            assertTrue(snapshot.has("bloodEffectSizePercent"));
            assertTrue(snapshot.has("bloodEffectLifetimePercent"));
            assertTrue(snapshot.has("bloodEffectParticlePercent"));
            assertTrue(snapshot.has("bloodEffectColor"));
            assertEquals(100, BetterUCConfig.INSTANCE.bloodEffectIntensityPercent);
            assertEquals("blood", BetterUCConfig.INSTANCE.bloodEffectMotif);
            assertEquals(0xFFD01824, BetterUCConfig.INSTANCE.bloodEffectColor);

            JsonObject remote = new JsonObject();
            remote.addProperty("bloodEffectMode", "3d");
            remote.addProperty("bloodEffectMotif", "NEON");
            remote.addProperty("bloodEffectIntensityPercent", 999);
            remote.addProperty("bloodEffectSizePercent", -10);
            remote.addProperty("bloodEffectLifetimePercent", 999);
            remote.addProperty("bloodEffectParticlePercent", 0);
            remote.addProperty("bloodEffectColor", 0x00123456);
            BetterUCConfig.applyCloudSettings(remote);

            assertEquals("volumetric", BetterUCConfig.INSTANCE.bloodEffectMode);
            assertEquals("neon", BetterUCConfig.INSTANCE.bloodEffectMotif);
            assertEquals(150, BetterUCConfig.INSTANCE.bloodEffectIntensityPercent);
            assertEquals(50, BetterUCConfig.INSTANCE.bloodEffectSizePercent);
            assertEquals(200, BetterUCConfig.INSTANCE.bloodEffectLifetimePercent);
            assertEquals(25, BetterUCConfig.INSTANCE.bloodEffectParticlePercent);
            assertEquals(0xFF123456, BetterUCConfig.INSTANCE.bloodEffectColor);

            for (String motif : new String[]{"STARS", "HEARTS", "CLASSIC"}) {
                JsonObject update = new JsonObject();
                update.addProperty("bloodEffectMotif", motif);
                BetterUCConfig.applyCloudSettings(update);
                assertEquals(motif.toLowerCase(java.util.Locale.ROOT), BetterUCConfig.INSTANCE.bloodEffectMotif);
            }
            assertEquals(BloodEffectClient.Motif.CLASSIC, BloodEffectClient.motif());
            assertFalse(BloodEffectClient.replace(null, 0, 0, 0));
            BloodEffectClient.cycleMotif();
            assertEquals(BloodEffectClient.Motif.BLOOD, BloodEffectClient.motif());
        } finally {
            BetterUCConfig.INSTANCE = previous;
            configDirectory.set(loader, previousDirectory);
        }
    }
}
