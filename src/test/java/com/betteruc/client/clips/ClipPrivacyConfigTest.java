package com.betteruc.client.clips;

import com.betteruc.config.BetterUCConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClipPrivacyConfigTest {
    @Test void recordingDefaultsOffAndCannotBeEnabledByCloud(@TempDir Path directory) throws Exception {
        // The standalone unit-test JVM has no launched game and thus no Fabric config directory.
        var loader = FabricLoader.getInstance();
        var configDirectory = loader.getClass().getDeclaredField("configDir");
        configDirectory.setAccessible(true);
        var previousDirectory = configDirectory.get(loader);
        configDirectory.set(loader, directory);
        var previous = BetterUCConfig.INSTANCE;
        try {
            BetterUCConfig.INSTANCE = new BetterUCConfig();
            assertFalse(BetterUCConfig.INSTANCE.clipsEnabled);
            assertFalse(BetterUCConfig.INSTANCE.clipsGameAudioEnabled);
            assertFalse(BetterUCConfig.INSTANCE.clipsMicrophoneEnabled);
            assertEquals(ClipAudioOptions.Mode.OFF, ClipAudioOptions.fromConfig(BetterUCConfig.INSTANCE).mode());
            assertEquals(30, BetterUCConfig.INSTANCE.clipBufferSeconds);
            assertEquals(1080, BetterUCConfig.INSTANCE.clipResolutionHeight);
            assertEquals(60, BetterUCConfig.INSTANCE.clipFramesPerSecond);
            assertEquals("", BetterUCConfig.INSTANCE.clipStorageParent);
            JsonObject remote = new JsonObject();
            remote.addProperty("clipsEnabled", true);
            remote.addProperty("clipsGameAudioEnabled", true);
            remote.addProperty("clipAudioMode", "SYSTEM");
            remote.addProperty("clipsMicrophoneEnabled", true);
            remote.addProperty("clipOutputDevice", "remote-output");
            remote.addProperty("clipInputDevice", "remote-microphone");
            remote.addProperty("clipOutputVolume", 1);
            remote.addProperty("clipMicrophoneVolume", 1);
            remote.addProperty("clipBufferSeconds", 60);
            remote.addProperty("clipResolutionHeight", 720);
            remote.addProperty("clipFramesPerSecond", 30);
            remote.addProperty("clipStorageParent", directory.resolve("remote-location").toString());
            BetterUCConfig.applyCloudSettings(remote);
            assertFalse(ClipAudioOptions.fromConfig(BetterUCConfig.INSTANCE).enabled());
            assertEquals("", BetterUCConfig.INSTANCE.clipOutputDevice);
            assertEquals("", BetterUCConfig.INSTANCE.clipInputDevice);
            assertEquals(100, BetterUCConfig.INSTANCE.clipOutputVolume);
            assertEquals(100, BetterUCConfig.INSTANCE.clipMicrophoneVolume);
            assertFalse(BetterUCConfig.INSTANCE.clipsEnabled);
            assertFalse(BetterUCConfig.INSTANCE.clipsGameAudioEnabled);
            assertEquals(30, BetterUCConfig.INSTANCE.clipBufferSeconds);
            assertEquals(1080, BetterUCConfig.INSTANCE.clipResolutionHeight);
            assertEquals(60, BetterUCConfig.INSTANCE.clipFramesPerSecond);
            assertEquals("", BetterUCConfig.INSTANCE.clipStorageParent);
            BetterUCConfig.INSTANCE.clipsEnabled = true;
            JsonObject snapshot = BetterUCConfig.cloudSettingsSnapshot();
            for (String key : remote.keySet()) assertFalse(snapshot.has(key), "Audio consent/device must stay local: " + key);
            assertFalse(snapshot.has("clipsEnabled"));
            assertFalse(snapshot.has("clipsGameAudioEnabled"));
            assertFalse(snapshot.has("clipBufferSeconds"));
            assertFalse(snapshot.has("clipResolutionHeight"));
            assertFalse(snapshot.has("clipFramesPerSecond"));
            BetterUCConfig.INSTANCE.clipStorageParent = directory.resolve("private-local-path").toString();
            assertFalse(BetterUCConfig.cloudSettingsSnapshot().has("clipStorageParent"));

            Gson gson = new Gson();
            var legacy = gson.fromJson("{\"clipsEnabled\":true,\"clipsGameAudioEnabled\":true}", BetterUCConfig.class);
            assertTrue(legacy.clipsEnabled);
            assertTrue(legacy.clipsGameAudioEnabled);
            assertEquals(ClipAudioOptions.Mode.GAME, ClipAudioOptions.fromConfig(legacy).mode());
            assertFalse(ClipAudioOptions.fromConfig(legacy).microphone());
            legacy.clipAudioMode = "INVALID";
            assertEquals(ClipAudioOptions.Mode.OFF, ClipAudioOptions.fromConfig(legacy).mode());
            legacy.clipAudioMode = "SYSTEM";
            legacy.clipsMicrophoneEnabled = true;
            legacy.clipOutputDevice = "headset-output";
            legacy.clipInputDevice = "my-mic";
            legacy.clipOutputVolume = 75;
            legacy.clipMicrophoneVolume = 85;
            assertEquals(1080, legacy.clipResolutionHeight);
            assertEquals(60, legacy.clipFramesPerSecond);
            assertEquals("", legacy.clipStorageParent);
            legacy.clipResolutionHeight = 720;
            legacy.clipFramesPerSecond = 30;
            legacy.clipBufferSeconds = 137;
            legacy.clipStorageParent = directory.resolve("Meine Clips äöü").toString();
            var restored = gson.fromJson(gson.toJson(legacy), BetterUCConfig.class);
            assertEquals(720, restored.clipResolutionHeight);
            assertEquals(30, restored.clipFramesPerSecond);
            assertEquals(137, restored.clipBufferSeconds);
            assertEquals(legacy.clipStorageParent, restored.clipStorageParent);
            assertTrue(restored.clipsEnabled);
            assertTrue(restored.clipsGameAudioEnabled);
            assertEquals(ClipAudioOptions.fromConfig(legacy), ClipAudioOptions.fromConfig(restored));
            assertTrue(ClipAudioOptions.fromConfig(restored).microphone());
            assertEquals(ClipAudioOptions.Mode.SYSTEM, ClipAudioOptions.fromConfig(restored).mode());
        } finally {
            BetterUCConfig.INSTANCE = previous;
            configDirectory.set(loader, previousDirectory);
        }
    }
}
