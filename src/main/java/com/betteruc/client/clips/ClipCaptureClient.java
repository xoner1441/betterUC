package com.betteruc.client.clips;

import com.betteruc.BetterUCMod;
import com.betteruc.config.BetterUCConfig;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures only Minecraft's completed framebuffer. GPU resources stay on the render thread. */
public final class ClipCaptureClient {
    private static final ExecutorService EXPORT = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "betteruc-clip-export");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean EXPORTING = new AtomicBoolean();
    // Native folder dialogs can stay open indefinitely; never block capture or export on them.
    private static final ExecutorService STORAGE = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "betteruc-clip-storage");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean STORAGE_BUSY = new AtomicBoolean();
    private static final ExecutorService AUDIO_DEVICES = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "betteruc-clip-audio-devices");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean DEVICES_BUSY = new AtomicBoolean();
    private static WindowsAudioDevices.Devices audioDevices = new WindowsAudioDevices.Devices(java.util.List.of(), java.util.List.of());
    private static String deviceStatus = "Geräte zuerst laden / aktualisieren";
    private static KeyMapping saveKey;
    private static ClipRecorderSession session;
    private static CaptureBuffers buffers;
    private static Object capturedLevel;
    private static long epochNanos;
    private static long lastTick = -1;
    private static boolean stopping;
    private static boolean announceNextCapture = true;
    private static Object captureToken;
    private static String failure = "";

    private ClipCaptureClient() {}

    public static void initialize(KeyMapping.Category category) {
        saveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.betteruc.clip_save",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (saveKey.consumeClick()) saveClip();
            ClipToastHud.tick();
            ClipUploadClient.tick();
            if (client.level == null) announceNextCapture = true;
            if (!BetterUCConfig.INSTANCE.clipsEnabled || client.level == null || !client.isWindowActive()) stopSession();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stopping = true;
            stopSession();
            EXPORT.shutdown();
            STORAGE.shutdown();
            AUDIO_DEVICES.shutdown();
            ClipUploadClient.shutdown();
        });
        ClipToastHud.register();
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("buclip")
                .executes(context -> { notifyUser(statusLabel() + " | " + details()); return 1; })
                .then(ClientCommands.literal("save").executes(context -> { saveClip(); return 1; }))
                .then(ClientCommands.literal("folder").executes(context -> { openFolder(); return 1; }))
                .then(ClientCommands.literal("upload").executes(context -> {
                    ClipUploadClient.requestOpen(null); return 1;
                }).then(ClientCommands.argument("id",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                    String id=com.mojang.brigadier.arguments.StringArgumentType.getString(context,"id");
                    ClipUploadClient.requestOpen(id); return 1;
                }))));
    }

    public static void setEnabled(boolean enabled) {
        BetterUCConfig.INSTANCE.clipsEnabled = enabled;
        failure = "";
        stopSession();
        ClipToastHud.clearCapture();
        announceNextCapture = enabled;
        if (!enabled) ClipToastHud.show(ClipNotice.info("Clip-Aufnahme ausgeschaltet."));
    }

    public static void setGameAudioEnabled(boolean enabled) {
        setAudioMode(enabled ? ClipAudioOptions.Mode.GAME : ClipAudioOptions.Mode.OFF);
    }

    public static void setAudioMode(ClipAudioOptions.Mode mode) {
        BetterUCConfig.INSTANCE.clipAudioMode = mode.name();
        BetterUCConfig.INSTANCE.clipsGameAudioEnabled = mode == ClipAudioOptions.Mode.GAME;
        restartForQualityChange();
    }
    public static void setMicrophoneEnabled(boolean enabled) {
        BetterUCConfig.INSTANCE.clipsMicrophoneEnabled = enabled;
        restartForQualityChange();
    }
    public static String audioDeviceStatus() { return deviceStatus; }
    public static String audioDeviceLabel(boolean microphone) {
        return WindowsAudioDevices.label(microphone ? audioDevices.inputs() : audioDevices.outputs(),
                microphone ? BetterUCConfig.INSTANCE.clipInputDevice : BetterUCConfig.INSTANCE.clipOutputDevice);
    }
    public static void cycleAudioDevice(boolean microphone) {
        var devices = microphone ? audioDevices.inputs() : audioDevices.outputs();
        if (devices.isEmpty()) {
            ClipToastHud.show(ClipNotice.info("Geräteliste laden; Standard bleibt verfügbar."));
            return;
        }
        if (microphone) BetterUCConfig.INSTANCE.clipInputDevice = WindowsAudioDevices.next(devices, BetterUCConfig.INSTANCE.clipInputDevice);
        else BetterUCConfig.INSTANCE.clipOutputDevice = WindowsAudioDevices.next(devices, BetterUCConfig.INSTANCE.clipOutputDevice);
        restartForQualityChange();
    }
    public static void resetAudioDevice(boolean microphone) {
        if (microphone) BetterUCConfig.INSTANCE.clipInputDevice = "";
        else BetterUCConfig.INSTANCE.clipOutputDevice = "";
        restartForQualityChange();
    }
    public static void reloadAudioDevices(Runnable refreshed) {
        if (!DEVICES_BUSY.compareAndSet(false, true)) return;
        deviceStatus = "Geräte werden geladen …";
        refreshed.run();
        AUDIO_DEVICES.execute(() -> {
            try {
                var devices = WindowsAudioDevices.list();
                Minecraft.getInstance().execute(() -> {
                    audioDevices = devices;
                    deviceStatus = devices.outputs().size() + " Ausgaben / " + devices.inputs().size() + " Eingaben";
                    DEVICES_BUSY.set(false);
                    refreshed.run();
                });
            } catch (Exception | LinkageError error) {
                BetterUCMod.LOGGER.warn("Clip audio device enumeration failed", error);
                Minecraft.getInstance().execute(() -> {
                    deviceStatus = "Geräteliste nicht verfügbar; siehe /buclip";
                    DEVICES_BUSY.set(false);
                    ClipToastHud.show(ClipNotice.warning("Audiogeräte nicht verfügbar", "Details stehen in latest.log."));
                    refreshed.run();
                });
            }
        });
    }

    public static void cycleDuration() {
        setDuration(ClipSettings.nextDurationPreset(BetterUCConfig.INSTANCE.clipBufferSeconds));
    }

    public static void setDuration(int seconds) {
        if (seconds < ClipSettings.MIN_SECONDS || seconds > ClipSettings.MAX_SECONDS) {
            throw new IllegalArgumentException("Cliplänge muss zwischen 5 und 300 Sekunden liegen.");
        }
        int previous = ClipSettings.normalizeSeconds(BetterUCConfig.INSTANCE.clipBufferSeconds);
        BetterUCConfig.INSTANCE.clipBufferSeconds = seconds;
        if (previous != seconds) {
            stopSession();
            announceNextCapture = true;
        }
    }

    public static void cycleResolution() {
        int current = ClipSettings.normalizeResolutionHeight(BetterUCConfig.INSTANCE.clipResolutionHeight);
        BetterUCConfig.INSTANCE.clipResolutionHeight = current == 1080 ? 720 : 1080;
        restartForQualityChange();
    }

    public static void cycleFrameRate() {
        int current = ClipSettings.normalizeFps(BetterUCConfig.INSTANCE.clipFramesPerSecond);
        BetterUCConfig.INSTANCE.clipFramesPerSecond = current == 60 ? 30 : 60;
        restartForQualityChange();
    }

    private static void restartForQualityChange() {
        stopSession();
        announceNextCapture = true;
    }

    public static String statusLabel() {
        if (!BetterUCConfig.INSTANCE.clipsEnabled) return "Aus";
        if (!failure.isEmpty()) return "Nicht verfügbar (siehe Details)";
        if (EXPORTING.get()) return "Wird gespeichert";
        if (session == null) return "Wartet auf Spiel / Fokus";
        if (!session.ready()) return "Startet / pausiert";
        return String.format(Locale.ROOT, "%.0f / %d s | %s", session.seconds(), session.settings().seconds(), session.audioStatus());
    }

    public static String details() {
        return recorderDetails() + " | Speicherort: " + storageLocationLabel();
    }

    private static String recorderDetails() {
        if (!failure.isEmpty()) return failure;
        if (session == null) return "Windows x64 | Hardware-Encoder erforderlich";
        ClipSettings settings = session.settings();
        return settings.width() + "x" + settings.height() + " | Ziel " + settings.fps() + " FPS | "
                + settings.bitrate() / 1_000_000 + " Mbit/s | " + session.details();
    }

    public static void saveClip() {
        if (EXPORTING.get()) return; // The persistent saving toast already explains repeated hotkeys.
        if (!failure.isEmpty()) { ClipToastHud.show(ClipNotice.recordingFailed("Details und Fehlerursache: /buclip")); return; }
        if (!BetterUCConfig.INSTANCE.clipsEnabled) { ClipToastHud.show(ClipNotice.info("Unter Client > Clips einschalten.")); return; }
        if (session == null || !session.ready()) { ClipToastHud.show(ClipNotice.info("Clip-Puffer startet / ist pausiert.")); return; }
        try { session.requestSave(directory()); }
        catch (RuntimeException error) { storageFailure(error); }
    }

    public static void showDetails() { notifyUser(details()); }
    private static Path gameDirectory() { return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize(); }
    private static Path directory() { return ClipStoragePaths.resolve(gameDirectory(), BetterUCConfig.INSTANCE.clipStorageParent); }

    public static String storageLocationLabel() {
        try { return directory().toString(); }
        catch (RuntimeException error) { return "Ungültiger Pfad – bitte neu wählen"; }
    }

    public static void chooseStorageParent(Runnable onFinished) {
        changeStorageParent(true, onFinished);
    }

    public static void resetStorageParent(Runnable onFinished) {
        changeStorageParent(false, onFinished);
    }

    private static void changeStorageParent(boolean choose, Runnable onFinished) {
        if (stopping || !STORAGE_BUSY.compareAndSet(false, true)) return;
        Minecraft client = Minecraft.getInstance();
        Path game = gameDirectory();
        String current = BetterUCConfig.INSTANCE.clipStorageParent;
        STORAGE.execute(() -> {
            try {
                String selected = "";
                if (choose) {
                    Path initial;
                    try { initial = ClipStoragePaths.resolve(game, current).getParent(); }
                    catch (RuntimeException error) { initial = game; }
                    if (!Files.isDirectory(initial)) initial = game;
                    selected = TinyFileDialogs.tinyfd_selectFolderDialog(
                            "betterUC: In diesem Ordner wird buclips erstellt", initial.toString());
                    if (selected == null || selected.isBlank()) return; // Cancel must not mutate config or create directories.
                }
                Path target = ClipStoragePaths.prepareSelection(game, selected);
                String parent = selected.isEmpty() ? "" : target.getParent().toString();
                client.execute(() -> {
                    if (stopping) return;
                    BetterUCConfig.INSTANCE.clipStorageParent = parent;
                    BetterUCConfig.save();
                    ClipToastHud.show(ClipNotice.info("Speicherort für neue Clips geändert."));
                });
            } catch (Exception | LinkageError error) { storageFailure(error); }
            finally {
                client.execute(() -> {
                    STORAGE_BUSY.set(false);
                    if (!stopping) onFinished.run();
                });
            }
        });
    }

    private static void storageFailure(Throwable error) {
        BetterUCMod.LOGGER.warn("[Clips] Speicherort nicht verfügbar", error);
        ClipToastHud.show(ClipNotice.warning("Speicherort nicht verfügbar", "Bitte Ordner und Schreibrechte prüfen."));
    }

    public static void openFolder() {
        if (stopping) return;
        Path target;
        try { target = directory(); }
        catch (RuntimeException error) { storageFailure(error); return; }
        STORAGE.execute(() -> {
            try {
                Files.createDirectories(target);
                Util.getPlatform().openPath(target);
            } catch (Exception error) { storageFailure(error); }
        });
    }

    private static void notifyUser(String message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) client.player.sendSystemMessage(Component.literal("§b[betterUC Clips] §f" + message));
        });
    }

    private static void stopSession() {
        captureToken = null;
        if (session != null) session.close();
        epochNanos = 0;
        lastTick = -1;
    }

    /** After GUI/HUD rendering, before presentation. Never waits for the encoder. */
    public static void onRenderedFrame(RenderTarget source) {
        try { capture(source); }
        catch (Throwable error) {
            BetterUCMod.LOGGER.warn("Clip framebuffer capture failed", error);
            reportCaptureFailure(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                    "Spielbild konnte nicht aufgenommen werden.");
        }
    }

    private static void reportCaptureFailure(String detail, String shortReason) {
        if (failure.isEmpty()) ClipToastHud.show(ClipNotice.recordingFailed(shortReason));
        failure = detail;
        stopSession();
    }

    private static void onSessionNotice(Object token, ClipNotice notice) {
        Minecraft.getInstance().execute(() -> {
            // Exports survive stopping/restarting capture; old audio/status messages do not.
            if (notice.exportEvent() || token == captureToken) ClipToastHud.show(notice);
        });
    }

    private static void capture(RenderTarget source) {
        Minecraft client = Minecraft.getInstance();
        if (session != null && session.finished()) {
            if (!session.failure().isEmpty() && captureToken != null) {
                reportCaptureFailure(session.failure(), "Hardware-Encoder konnte nicht aufnehmen.");
            }
            if (buffers != null && !buffers.idle()) return;
            if (buffers != null) { buffers.close(); buffers = null; }
            session = null;
        }
        if (stopping || !BetterUCConfig.INSTANCE.clipsEnabled || !failure.isEmpty()
                || client.level == null || client.player == null || !client.isWindowActive()) {
            stopSession();
            return;
        }
        if (source == null || source.width < 2 || source.height < 2 || source.getColorTextureView() == null) return;
        ClipSettings settings = ClipSettings.forViewport(source.width, source.height, BetterUCConfig.INSTANCE.clipBufferSeconds,
                BetterUCConfig.INSTANCE.clipResolutionHeight, BetterUCConfig.INSTANCE.clipFramesPerSecond);
        ClipAudioOptions audioOptions = ClipAudioOptions.fromConfig(BetterUCConfig.INSTANCE);
        if (session != null && (capturedLevel != client.level || !settings.equals(session.settings())
                || !session.audioOptions().sameSources(audioOptions))) {
            stopSession();
            return;
        }
        if (session == null) {
            String arch = System.getProperty("os.arch", "");
            if (!System.getProperty("os.name", "").startsWith("Windows")
                    || !(arch.equals("amd64") || arch.equals("x86_64"))) {
                reportCaptureFailure("Die Clip-Beta unterstützt derzeit nur Windows x64.", "Windows x64 wird benötigt.");
                return;
            }
            capturedLevel = client.level;
            Object token = new Object();
            captureToken = token;
            session = new ClipRecorderSession(settings, ClipStoragePaths.resolve(gameDirectory(), ""), message -> BetterUCMod.LOGGER.info("[Clips] {}", message),
                    notice -> onSessionNotice(token, notice), path -> {
                        BetterUCMod.LOGGER.info("[Clips] Gespeichert: {}", path);
                        ClipUploadClient.saved(path);
                    },
                    EXPORT, EXPORTING, audioOptions);
        }
        session.updateAudioLevels(audioOptions);
        if (!session.ready()) return;
        if (buffers == null) buffers = new CaptureBuffers(settings);
        long now = System.nanoTime();
        if (epochNanos == 0) {
            epochNanos = now;
            session.beginTimeline(epochNanos);
            if (announceNextCapture) {
                announceNextCapture = false;
                ClipToastHud.show(ClipNotice.recording(settings, session.audioStatus()));
            }
        }
        long tick = (now - epochNanos) * settings.fps() / 1_000_000_000L;
        if (tick <= lastTick) return;
        if (lastTick >= 0 && tick > lastTick + 1) session.skipped(tick - lastTick - 1);
        lastTick = tick;
        CaptureSlot slot = buffers.acquire();
        if (slot == null) { session.skipped(1); return; }
        ClipRecorderSession owner = session;
        boolean submitted = false;
        try {
            var commands = RenderSystem.getDevice().createCommandEncoder();
            try (var pass = commands.createRenderPass(() -> "betterUC clip downscale", buffers.target.getColorTextureView(),
                    Optional.of(new Vector4f(0, 0, 0, 1)))) {
                pass.setPipeline(ClipCapturePipeline.COLOR_COPY);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("InSampler", source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3, 1, 0, 0);
            }
            commands.copyTextureToBuffer(buffers.target.getColorTexture(), slot.gpu, 0L, () -> {
                try {
                    if (!owner.ready()) { slot.release(); return; }
                    try (var mapped = slot.gpu.map(true, false)) {
                        slot.cpu.clear();
                        slot.cpu.put(mapped.data());
                        slot.cpu.flip();
                    }
                    owner.submit(slot.cpu, tick, slot::release);
                } catch (Throwable error) {
                    slot.release();
                    owner.close();
                    BetterUCMod.LOGGER.warn("Clip readback failed", error);
                    Minecraft.getInstance().execute(() -> {
                        if (owner == session) reportCaptureFailure("GPU-Readback fehlgeschlagen: " + error.getMessage(),
                                "Spielbild konnte nicht ausgelesen werden.");
                    });
                }
            }, 0);
            submitted = true;
        } finally { if (!submitted) slot.release(); }
    }

    private static final class CaptureSlot implements AutoCloseable {
        final AtomicBoolean busy = new AtomicBoolean();
        final GpuBuffer gpu;
        final ByteBuffer cpu;
        CaptureSlot(int size) {
            gpu = RenderSystem.getDevice().createBuffer(() -> "betterUC clip readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, size);
            ByteBuffer allocated;
            try { allocated = MemoryUtil.memAlloc(size); }
            catch (Throwable error) { gpu.close(); throw error; }
            cpu = allocated;
        }
        void release() { busy.set(false); }
        @Override public void close() { gpu.close(); MemoryUtil.memFree(cpu); }
    }

    private static final class CaptureBuffers implements AutoCloseable {
        final TextureTarget target;
        final CaptureSlot[] slots = new CaptureSlot[3];
        CaptureBuffers(ClipSettings settings) {
            target = new TextureTarget("betterUC clip", settings.width(), settings.height(), false, GpuFormat.RGBA8_UNORM);
            try {
                for (int i = 0; i < slots.length; i++) slots[i] = new CaptureSlot(settings.width() * settings.height() * 4);
            } catch (Throwable error) { close(); throw error; }
        }
        CaptureSlot acquire() {
            for (CaptureSlot slot : slots) if (slot.busy.compareAndSet(false, true)) return slot;
            return null;
        }
        boolean idle() {
            for (CaptureSlot slot : slots) if (slot != null && slot.busy.get()) return false;
            return true;
        }
        @Override public void close() {
            for (CaptureSlot slot : slots) if (slot != null) slot.close();
            target.destroyBuffers();
        }
    }
}
