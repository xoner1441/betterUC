package com.betteruc.client.clips;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Standalone smoke test of the private runtime embedded in the shipped mod JAR. */
public final class ClipPackagedRuntimeProbe {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path scratch = Files.createTempDirectory(Path.of(args[1]), "packaged-probe-");
        System.setProperty("org.bytedeco.javacpp.cachedir", scratch.resolve("native-cache").toString());
        Path runtime = scratch.resolve("betteruc-clip-runtime-isolated.jar");
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("META-INF/betteruc-runtime/betteruc-clip-runtime-isolated.jar");
            if (entry == null) throw new IllegalStateException("Private clip runtime is missing");
            try (var input = zip.getInputStream(entry)) { Files.copy(input, runtime); }
        }
        System.out.println("Java: " + System.getProperty("java.runtime.version") + " / " + System.getProperty("java.vendor"));
        System.out.println("Private runtime: " + runtime + "; fresh cache: " + scratch);
        try (var loader = new URLClassLoader(new URL[]{runtime.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> entryType = Class.forName("com.betteruc.client.clips.ClipRuntimeEntry", true, loader);
            try (var encoder = (AutoCloseable) entryType.getMethod("openEncoder", int.class, int.class, int.class,
                            int.class, int.class, Consumer.class)
                    .invoke(null, 320, 180, 60, 5, 2_000_000, (Consumer<String>) System.out::println)) {
                System.out.println("PASS: packaged runtime + synthetic hardware encoding: "
                        + encoder.getClass().getMethod("name").invoke(encoder));
            }
        }
    }
}
