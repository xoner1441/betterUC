package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class ClipRuntimeIsolationTest {
    @Test
    void bytedecoClassesComeFromPrivateRuntimeEvenWhenParentAlsoContainsThem() throws Exception {
        ClassLoader isolated = ClipEncoderRuntime.isolatedClassLoaderForTest();
        Class<?> isolatedLoader = Class.forName("org.bytedeco.javacpp.Loader", false, isolated);
        Class<?> isolatedAvutil = Class.forName("org.bytedeco.ffmpeg.global.avutil", false, isolated);
        Class<?> sharedLoader = Class.forName("org.bytedeco.javacpp.Loader", false, getClass().getClassLoader());

        assertSame(isolated, isolatedLoader.getClassLoader());
        assertSame(isolated, isolatedAvutil.getClassLoader());
        assertNotSame(sharedLoader, isolatedLoader);
        assertTrue(isolatedLoader.getProtectionDomain().getCodeSource().getLocation().toString()
                .contains("betteruc-clip-runtime-isolated.jar"));
    }
}
