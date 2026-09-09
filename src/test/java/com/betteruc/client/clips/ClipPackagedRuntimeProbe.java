package com.betteruc.client.clips;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Standalone smoke test of the shipped nested JARs under a launcher's actual Java runtime. */
public final class ClipPackagedRuntimeProbe {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath();
        Path scratch = Files.createTempDirectory(Path.of(args[1]), "packaged-probe-");
        System.setProperty("org.bytedeco.javacpp.cachedir", scratch.resolve("native-cache").toString());
        var urls = new ArrayList<URL>();
        urls.add(jar.toUri().toURL());
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : zip.stream().filter(e -> e.getName().startsWith("META-INF/jars/")
                    && e.getName().endsWith(".jar")).toList()) {
                Path target = scratch.resolve(Path.of(entry.getName()).getFileName());
                try (var input = zip.getInputStream(entry)) { Files.copy(input, target); }
                urls.add(target.toUri().toURL());
            }
        }
        System.out.println("Java: " + System.getProperty("java.runtime.version") + " / " + System.getProperty("java.vendor"));
        System.out.println("Packaged JARs: " + (urls.size() - 1) + "; fresh cache: " + scratch);
        try (var loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Class<?> settingsType = Class.forName("com.betteruc.client.clips.ClipSettings", true, loader);
            Object settings = settingsType.getConstructor(int.class, int.class, int.class, int.class, int.class)
                    .newInstance(320, 180, 60, 5, 2_000_000);
            Class<?> encoderType = Class.forName("com.betteruc.client.clips.HardwareClipEncoder", true, loader);
            try (var encoder = (AutoCloseable) encoderType.getMethod("open", settingsType, Consumer.class)
                    .invoke(null, settings, (Consumer<String>) System.out::println)) {
                System.out.println("PASS: packaged runtime + synthetic hardware encoding: "
                        + encoderType.getMethod("name").invoke(encoder));
            }
        }
    }
}
