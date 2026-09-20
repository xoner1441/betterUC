package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void encoderFailureDiagnosticsStayInsidePrivateRuntime() throws Exception {
        ClassLoader isolated = ClipEncoderRuntime.isolatedClassLoaderForTest();
        Class<?> selection = Class.forName("com.betteruc.client.clips.ClipEncoderSelection", true, isolated);
        Class<?> attemptType = Class.forName(
                "com.betteruc.client.clips.ClipEncoderSelection$Attempt", true, isolated);
        Object failingAttempt = Proxy.newProxyInstance(isolated, new Class<?>[]{attemptType},
                (proxy, method, arguments) -> { throw new IOException("synthetic encoder failure"); });
        var diagnostics = new ArrayList<String>();
        var open = selection.getDeclaredMethod("open", attemptType, Consumer.class);
        open.setAccessible(true);

        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> open.invoke(null, failingAttempt, (Consumer<String>) diagnostics::add));

        assertInstanceOf(IOException.class, wrapper.getCause());
        assertTrue(wrapper.getCause().getMessage().contains("Hardware-Aufnahme nicht verfügbar"));
        assertTrue(diagnostics.stream().allMatch(line -> line.contains("synthetic encoder failure")));
    }
}
