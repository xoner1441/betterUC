package com.betteruc.client.clips;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns a child-first-by-construction FFmpeg runtime whose parent is the JDK, never Fabric's shared mod loader. */
final class ClipEncoderRuntime {
    private static final String RESOURCE = "/META-INF/betteruc-runtime/betteruc-clip-runtime-isolated.jar";
    private static volatile RuntimeAccess shared;

    private ClipEncoderRuntime() {
    }

    static Encoder open(ClipSettings settings, Consumer<String> diagnostics) throws IOException {
        return access().open(settings, diagnostics);
    }

    static EncodedAudio encodeAudio(ClipPcmSource source) throws IOException {
        if (source == null || !source.hasCapturedAudio() || source.frames() <= 0) return null;
        byte[] pcm = new byte[Math.multiplyExact(source.frames(), ClipAudioBuffer.FRAME_BYTES)];
        ClipPcmSource.Reader reader = source.reader();
        byte[] chunk = new byte[Math.min(pcm.length, 8192 * ClipAudioBuffer.FRAME_BYTES)];
        int target = 0;
        while (target < pcm.length) {
            int frames = reader.read(chunk);
            if (frames <= 0) break;
            int bytes = Math.min(frames * ClipAudioBuffer.FRAME_BYTES, pcm.length - target);
            System.arraycopy(chunk, 0, pcm, target, bytes);
            target += bytes;
        }
        if (target != pcm.length) throw new IOException("Audiopuffer ist unvollständig");
        return access().encodeAudio(pcm, source.frames());
    }

    static void writeMp4(
            Path output,
            Header header,
            List<ClipPacket> videoPackets,
            EncodedAudio audio
    ) throws IOException {
        access().writeMp4(output, header, videoPackets, audio);
    }

    static String thumbnail(Path input, BooleanSupplier cancelled) throws Exception {
        return access().thumbnail(input, cancelled);
    }

    static String diagnosticSummary() {
        try {
            RuntimeAccess runtime = access();
            return "isoliert: " + runtime.jar + "\n"
                    + "ClassLoader: " + runtime.loader + "\n"
                    + "Bridge: " + runtime.entry.getProtectionDomain().getCodeSource().getLocation() + "\n"
                    + "Native-Status: " + runtime.runtimeInfo();
        } catch (Throwable error) {
            return "Isolierte Clip-Laufzeit nicht verfügbar: " + ClipDiagnostics.summary(error);
        }
    }

    static ClassLoader isolatedClassLoaderForTest() throws IOException {
        return access().loader;
    }

    private static RuntimeAccess access() throws IOException {
        RuntimeAccess current = shared;
        if (current != null) return current;
        synchronized (ClipEncoderRuntime.class) {
            if (shared == null) shared = createAccess();
            return shared;
        }
    }

    private static RuntimeAccess createAccess() throws IOException {
        Path jar = runtimeJar();
        URLClassLoader loader = new URLClassLoader(
                "betteruc-clip-runtime",
                new java.net.URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader()
        );
        String runtimeName = jar.getFileName().toString().replaceFirst("\\.jar$", "");
        Path nativeCache = jar.getParent().resolve("native-cache").resolve(runtimeName);
        try {
            Files.createDirectories(nativeCache);
            Class<?> javaCppLoader = Class.forName("org.bytedeco.javacpp.Loader", false, loader);
            // The cache field belongs to our private Loader class. Setting it directly avoids touching
            // JavaCPP's process-wide system property, which another mod could observe concurrently.
            var cacheField = javaCppLoader.getDeclaredField("cacheDir");
            cacheField.setAccessible(true);
            cacheField.set(null, nativeCache.toFile());
            java.io.File configuredCache = (java.io.File) javaCppLoader.getMethod("getCacheDir").invoke(null);
            if (!configuredCache.toPath().toAbsolutePath().normalize().equals(nativeCache)) {
                throw new IOException("Privater Native-Cache wurde nicht übernommen");
            }
            Class<?> entry = Class.forName("com.betteruc.client.clips.ClipRuntimeEntry", false, loader);
            return new RuntimeAccess(jar, loader, entry);
        } catch (Throwable error) {
            try { loader.close(); } catch (IOException ignored) { }
            throw io("Isolierte FFmpeg-Laufzeit konnte nicht geladen werden", error);
        }
    }

    private static Path runtimeJar() throws IOException {
        String override = System.getProperty("betteruc.clip.runtime.jar", "").trim();
        if (!override.isEmpty()) {
            Path path = Path.of(override).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) throw new IOException("Clip-Runtime fehlt: " + path);
            return path;
        }

        Path directory = FabricLoader.getInstance().getConfigDir()
                .resolve("betteruc").resolve("clip-runtime").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "runtime-", ".part");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception error) {
            throw new IOException("SHA-256 nicht verfügbar", error);
        }
        try (InputStream input = ClipEncoderRuntime.class.getResourceAsStream(RESOURCE);
             var output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
            if (input == null) throw new IOException("Eingebettete Clip-Runtime fehlt");
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read == 0) continue;
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (Throwable error) {
            Files.deleteIfExists(temporary);
            throw error;
        }
        String hash = HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        Path target = directory.resolve("betteruc-clip-runtime-" + hash + ".jar");
        if (Files.isRegularFile(target)) {
            if (Files.mismatch(temporary, target) == -1) {
                Files.deleteIfExists(temporary);
                return target;
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    private static IOException io(String message, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof IOException existing) return existing;
        return new IOException(message + ": " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()), cause);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            current = invocation.getCause();
        }
        return current;
    }

    record Header(ClipSettings settings, byte[] extraData) {
    }

    record EncodedAudio(byte[] extraData, int initialPadding, int frameSize, long samples, List<ClipPacket> packets) {
    }

    static final class Encoder implements AutoCloseable {
        private final RuntimeAccess runtime;
        private final Object delegate;
        private final ClipSettings settings;

        private Encoder(RuntimeAccess runtime, Object delegate, ClipSettings settings) {
            this.runtime = runtime;
            this.delegate = delegate;
            this.settings = settings;
        }

        String name() throws IOException { return (String) runtime.invoke(runtime.encoderName, delegate); }
        void encode(ByteBuffer pixels, long pts, Consumer<ClipPacket> sink) throws IOException {
            emit(runtime.invokePackets(runtime.encoderEncode, delegate, pixels, pts), sink);
        }
        void repeatLastFrame(long pts, Consumer<ClipPacket> sink) throws IOException {
            emit(runtime.invokePackets(runtime.encoderRepeat, delegate, pts), sink);
        }
        void flush(Consumer<ClipPacket> sink) throws IOException {
            emit(runtime.invokePackets(runtime.encoderFlush, delegate), sink);
        }
        Header header() throws IOException {
            return new Header(settings, (byte[]) runtime.invoke(runtime.encoderHeader, delegate));
        }
        @Override public void close() {
            try { runtime.invoke(runtime.encoderClose, delegate); } catch (IOException ignored) { }
        }
        private static void emit(List<ClipPacket> packets, Consumer<ClipPacket> sink) {
            for (ClipPacket packet : packets) sink.accept(packet);
        }
    }

    private static final class RuntimeAccess {
        private final Path jar;
        private final URLClassLoader loader;
        private final Class<?> entry;
        private final Method openEncoder;
        private final Method runtimeInfo;
        private final Method encodeAudio;
        private final Method writeMp4;
        private final Method thumbnail;
        private final Method encoderName;
        private final Method encoderEncode;
        private final Method encoderRepeat;
        private final Method encoderFlush;
        private final Method encoderHeader;
        private final Method encoderClose;

        private RuntimeAccess(Path jar, URLClassLoader loader, Class<?> entry) throws ReflectiveOperationException {
            this.jar = jar;
            this.loader = loader;
            this.entry = entry;
            openEncoder = entry.getMethod("openEncoder", int.class, int.class, int.class, int.class, int.class, Consumer.class);
            runtimeInfo = entry.getMethod("runtimeInfo");
            encodeAudio = entry.getMethod("encodeAudio", byte[].class, int.class);
            writeMp4 = entry.getMethod("writeMp4", String.class, int.class, int.class, int.class, int.class,
                    int.class, byte[].class, List.class, Object[].class);
            thumbnail = entry.getMethod("thumbnail", String.class, BooleanSupplier.class);
            Class<?> encoder = Class.forName("com.betteruc.client.clips.ClipRuntimeEntry$Encoder", false, loader);
            encoderName = encoder.getMethod("name");
            encoderEncode = encoder.getMethod("encode", ByteBuffer.class, long.class);
            encoderRepeat = encoder.getMethod("repeatLastFrame", long.class);
            encoderFlush = encoder.getMethod("flush");
            encoderHeader = encoder.getMethod("headerExtraData");
            encoderClose = encoder.getMethod("close");
        }

        private Encoder open(ClipSettings settings, Consumer<String> diagnostics) throws IOException {
            Object value = invoke(openEncoder, null, settings.width(), settings.height(), settings.fps(),
                    settings.seconds(), settings.bitrate(), diagnostics);
            return new Encoder(this, value, settings);
        }

        private EncodedAudio encodeAudio(byte[] pcm, int frames) throws IOException {
            Object[] raw = (Object[]) invoke(encodeAudio, null, pcm, frames);
            if (raw == null) return null;
            return new EncodedAudio((byte[]) raw[0], (Integer) raw[1], (Integer) raw[2], (Long) raw[3], decodePackets(raw[4]));
        }

        private void writeMp4(Path output, Header header, List<ClipPacket> video, EncodedAudio audio) throws IOException {
            ClipSettings settings = header.settings();
            invoke(writeMp4, null, output.toString(), settings.width(), settings.height(), settings.fps(), settings.seconds(),
                    settings.bitrate(), header.extraData(), encodePackets(video), encodeAudio(audio));
        }

        private String thumbnail(Path input, BooleanSupplier cancelled) throws IOException {
            return (String) invoke(thumbnail, null, input.toString(), cancelled);
        }

        private String runtimeInfo() throws IOException { return (String) invoke(runtimeInfo, null); }

        private Object invoke(Method method, Object target, Object... arguments) throws IOException {
            try { return method.invoke(target, arguments); }
            catch (Throwable error) { throw io("Isolierter FFmpeg-Aufruf fehlgeschlagen", error); }
        }

        private List<ClipPacket> invokePackets(Method method, Object target, Object... arguments) throws IOException {
            return decodePackets(invoke(method, target, arguments));
        }

        private static List<Object[]> encodePackets(List<ClipPacket> packets) {
            List<Object[]> result = new ArrayList<>(packets.size());
            for (ClipPacket packet : packets) result.add(new Object[]{packet.bytes(), packet.pts(), packet.dts(), packet.duration(), packet.keyframe()});
            return result;
        }

        private static Object[] encodeAudio(EncodedAudio audio) {
            return audio == null ? null : new Object[]{audio.extraData(), audio.initialPadding(), audio.frameSize(),
                    audio.samples(), encodePackets(audio.packets())};
        }

        private static List<ClipPacket> decodePackets(Object raw) {
            if (!(raw instanceof List<?> list)) return List.of();
            List<ClipPacket> result = new ArrayList<>(list.size());
            for (Object item : list) {
                Object[] packet = (Object[]) item;
                result.add(new ClipPacket((byte[]) packet[0], (Long) packet[1], (Long) packet[2], (Long) packet[3], (Boolean) packet[4]));
            }
            return result;
        }
    }
}
