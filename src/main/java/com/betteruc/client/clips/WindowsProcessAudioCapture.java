package com.betteruc.client.clips;

import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** WASAPI capture using Minecraft's existing JNA. Each factory opens exactly the requested source, no fallback. */
final class WindowsProcessAudioCapture implements ClipAudioCaptureSource {
    private static final int S_OK = 0, E_NOINTERFACE = 0x80004002, E_FAIL = 0x80004005;
    private static final Guid.GUID IUNKNOWN = guid("00000000-0000-0000-C000-000000000046");
    private static final Guid.GUID IAGILE = guid("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90");
    private static final Guid.GUID COMPLETION = guid("41D949AB-9862-444A-80F6-C261334DA5EB");
    private static final Guid.GUID AUDIO_CLIENT = guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
    private static final Guid.GUID CAPTURE_CLIENT = guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317");
    private static final Map<Long, Activation> ACTIVATIONS = new ConcurrentHashMap<>();

    // Keep the callback trampolines alive for the process lifetime, including late Windows completions.
    public interface Query extends StdCallLibrary.StdCallCallback { int invoke(Pointer self, Pointer iid, Pointer out); }
    public interface Ref extends StdCallLibrary.StdCallCallback { int invoke(Pointer self); }
    public interface Complete extends StdCallLibrary.StdCallCallback { int invoke(Pointer self, Pointer operation); }
    private static final Query QUERY = (self, iid, out) -> {
        Activation state = ACTIVATIONS.get(Pointer.nativeValue(self));
        if (state == null || out == null) return E_NOINTERFACE;
        Guid.GUID requested = new Guid.GUID(iid);
        if (!requested.equals(IUNKNOWN) && !requested.equals(IAGILE) && !requested.equals(COMPLETION)) {
            out.setPointer(0, Pointer.NULL);
            return E_NOINTERFACE;
        }
        state.references.incrementAndGet();
        out.setPointer(0, self);
        return S_OK;
    };
    private static final Ref ADD_REF = self -> {
        Activation state = ACTIVATIONS.get(Pointer.nativeValue(self));
        return state == null ? 0 : state.references.incrementAndGet();
    };
    private static final Ref RELEASE = self -> {
        Activation state = ACTIVATIONS.get(Pointer.nativeValue(self));
        if (state == null) return 0;
        int count = state.references.decrementAndGet();
        if (count == 0) {
            ACTIVATIONS.remove(Pointer.nativeValue(self));
            state.object.close(); // Same lifetime rule as COM's `delete this` in Release().
            state.parameters.close();
            state.variant.close();
        }
        return count;
    };
    private static final Complete COMPLETE = (self, operation) -> {
        Activation state = ACTIVATIONS.get(Pointer.nativeValue(self));
        if (state == null) return E_FAIL;
        Pointer owned = null;
        try {
            IntByReference result = new IntByReference(E_FAIL);
            PointerByReference audio = new PointerByReference();
            check(call(operation, 3, result, audio), "Audio-Aktivierung abrufen");
            owned = audio.getValue();
            check(result.getValue(), "Prozess-Audio aktivieren");
            if (owned == null) throw new IOException("Windows lieferte kein Audio-Interface");
            PointerByReference client = new PointerByReference();
            check(call(owned, 0, AUDIO_CLIENT.getPointer(), client), "IAudioClient abfragen");
            state.result.complete(client.getValue());
        } catch (Throwable error) { state.result.completeExceptionally(error); }
        finally { release(owned); }
        return S_OK;
    };
    private static final Memory VTABLE = vtable();

    private static final class Activation {
        final Memory object = new Memory(Native.POINTER_SIZE);
        final Memory parameters = new Memory(12);
        final Memory variant = new Memory(24);
        final AtomicInteger references = new AtomicInteger(1);
        final CompletableFuture<Pointer> result = new CompletableFuture<>();
        Activation() {
            object.setPointer(0, VTABLE);
            parameters.clear();
            parameters.setInt(0, 1); // AUDIOCLIENT_ACTIVATION_TYPE_PROCESS_LOOPBACK
            parameters.setInt(4, Math.toIntExact(ProcessHandle.current().pid()));
            parameters.setInt(8, 0); // INCLUDE_TARGET_PROCESS_TREE. Never exclude it / capture the system.
            variant.clear();
            variant.setShort(0, (short) 65); // VT_BLOB, x64 PROPVARIANT union starts at byte 8.
            variant.setInt(8, 12);
            variant.setPointer(16, parameters);
            ACTIVATIONS.put(Pointer.nativeValue(object), this);
        }
    }

    record Samples(long startNanos, byte[] pcm, boolean discontinuity) {}
    private Pointer client;
    private Pointer capture;
    private WinNT.HANDLE event;
    private boolean comInitialized;
    private long anchorNanos;
    private long anchorQpc100ns;
    private ClipAudioSampleClock microphoneClock;
    private ClipLoopbackClock loopbackClock;
    private String deviceFormat = "";
    private String audioLabel = "Spielton";

    static WindowsProcessAudioCapture openCurrentProcess() throws Exception {
        if (!System.getProperty("os.name", "").startsWith("Windows") || Native.POINTER_SIZE != 8) {
            throw new IOException("Spielton benötigt Windows x64 mit Prozess-Loopback-Unterstützung");
        }
        WindowsProcessAudioCapture result = new WindowsProcessAudioCapture();
        try { result.open(); return result; }
        catch (Exception | LinkageError error) { result.close(); throw error; }
    }

    static WindowsProcessAudioCapture openEndpoint(boolean microphone, String deviceId) throws Exception {
        return openEndpoint(microphone, deviceId, true);
    }

    /** Diagnostic only: initialize the requested format, but never Start or read a capture stream. */
    static void validateEndpointFormat(boolean microphone, String deviceId) throws Exception {
        try (var ignored = openEndpoint(microphone, deviceId, false)) { }
    }

    private static WindowsProcessAudioCapture openEndpoint(boolean microphone, String deviceId, boolean start) throws Exception {
        WindowsAudioDevices.requireWindows();
        WindowsProcessAudioCapture result = new WindowsProcessAudioCapture();
        try {
            check(Ole32.INSTANCE.CoInitializeEx(null, 0).intValue(), "Audio-COM initialisieren");
            result.comInitialized = true;
            Pointer enumerator = null, device = null;
            try {
                enumerator = WindowsAudioDevices.enumerator();
                device = WindowsAudioDevices.resolve(enumerator, microphone, deviceId);
                PointerByReference audio = new PointerByReference();
                check(call(device, 3, AUDIO_CLIENT.getPointer(), 1, Pointer.NULL, audio), "Audiogerät aktivieren");
                result.client = audio.getValue();
            } finally { release(device); release(enumerator); }
            if (microphone) {
                result.microphoneClock = new ClipAudioSampleClock();
                result.audioLabel = "Mikrofon";
            } else {
                result.loopbackClock = new ClipLoopbackClock();
                result.audioLabel = "Ausgabeton";
            }
            result.readDeviceFormat();
            result.startStream(!microphone, start);
            return result;
        } catch (Exception | LinkageError error) { result.close(); throw error; }
    }

    private void open() throws Exception {
        check(Ole32.INSTANCE.CoInitializeEx(null, 0).intValue(), "Audio-COM initialisieren");
        comInitialized = true;
        Activation activation = new Activation();
        PointerByReference operation = new PointerByReference();
        try {
            Function activate = NativeLibrary.getInstance("Mmdevapi").getFunction("ActivateAudioInterfaceAsync", Function.ALT_CONVENTION);
            check(activate.invokeInt(new Object[]{new WString("VAD\\Process_Loopback"), AUDIO_CLIENT.getPointer(),
                    activation.variant, activation.object, operation}), "Prozess-Audio anfordern");
            try { client = activation.result.get(8, TimeUnit.SECONDS); }
            catch (TimeoutException | InterruptedException error) {
                // Activation cannot be cancelled. A late successful result still owns a COM reference.
                activation.result.thenAccept(WindowsProcessAudioCapture::release);
                throw error;
            }
        } finally {
            release(operation.getValue());
            RELEASE.invoke(activation.object);
        }
        loopbackClock = new ClipLoopbackClock();
        deviceFormat = "Minecraft-Prozess / 48000 Hz Stereo";
        startStream(true, true);
    }

    private void startStream(boolean loopback, boolean start) throws IOException {
        try (Memory format = new Memory(20)) {
            format.clear();
            format.setShort(0, (short) 1); // PCM S16_LE
            format.setShort(2, (short) ClipAudioBuffer.CHANNELS);
            format.setInt(4, ClipAudioBuffer.SAMPLE_RATE);
            format.setInt(8, ClipAudioBuffer.SAMPLE_RATE * ClipAudioBuffer.FRAME_BYTES);
            format.setShort(12, (short) ClipAudioBuffer.FRAME_BYTES);
            format.setShort(14, (short) 16);
            // Shared mode + automatic 48 kHz/stereo conversion; microphone is NOT loopback.
            check(call(client, 3, 0, 0x80000000 | 0x08000000 | 0x00040000 | (loopback ? 0x00020000 : 0),
                    0L, 0L, format, Pointer.NULL), "Audio-Puffer initialisieren");
        }
        PointerByReference service = new PointerByReference();
        check(call(client, 14, CAPTURE_CLIENT.getPointer(), service), "Audio-Capture-Service öffnen");
        capture = service.getValue();
        event = Kernel32.INSTANCE.CreateEvent(null, false, false, null);
        if (event == null) throw new IOException("Audio-Ereignis konnte nicht erstellt werden");
        check(call(client, 13, event), "Audio-Ereignis setzen");
        calibrateClock();
        if (start) check(call(client, 10), "Audioaufnahme starten");
    }

    /** Device format only, no sample data. Useful for diagnosing USB/virtual audio drivers. */
    private void readDeviceFormat() {
        PointerByReference format = new PointerByReference();
        if (call(client, 8, format) < 0 || format.getValue() == null) return;
        Pointer value = format.getValue();
        try {
            deviceFormat = value.getInt(4) + " Hz / " + Short.toUnsignedInt(value.getShort(2))
                    + " Kanäle / " + Short.toUnsignedInt(value.getShort(14)) + " Bit -> 48000 Hz Stereo";
        } finally { Ole32.INSTANCE.CoTaskMemFree(value); }
    }

    @Override public String diagnostics() {
        return deviceFormat + " | " + (microphoneClock != null ? microphoneClock.diagnostics()
                : loopbackClock != null ? loopbackClock.diagnostics(audioLabel) : "Audio startet");
    }

    private void calibrateClock() throws IOException {
        NativeLibrary kernel = NativeLibrary.getInstance("kernel32");
        LongByReference frequency = new LongByReference();
        LongByReference counter = new LongByReference();
        if (kernel.getFunction("QueryPerformanceFrequency").invokeInt(new Object[]{frequency}) == 0) throw new IOException("Keine QPC-Frequenz");
        long before = System.nanoTime();
        if (kernel.getFunction("QueryPerformanceCounter").invokeInt(new Object[]{counter}) == 0) throw new IOException("Kein QPC-Zeitstempel");
        long after = System.nanoTime();
        anchorNanos = before + (after - before) / 2;
        // GetBuffer timestamps are QPC converted to 100 ns, not raw QPC ticks.
        anchorQpc100ns = Math.round(counter.getValue() * (10_000_000.0 / frequency.getValue()));
    }

    public void awaitSamples() throws IOException {
        if (Kernel32.INSTANCE.WaitForSingleObject(event, 25) == -1) throw new IOException("Warten auf Spielton fehlgeschlagen");
    }

    public Samples read() throws IOException {
        IntByReference available = new IntByReference();
        check(call(capture, 5, available), "Audio-Paket abfragen");
        if (available.getValue() == 0) {
            if (loopbackClock != null) loopbackClock.noPacket(System.nanoTime());
            return null;
        }
        PointerByReference data = new PointerByReference();
        IntByReference frames = new IntByReference();
        IntByReference flags = new IntByReference();
        LongByReference qpc = new LongByReference();
        LongByReference devicePosition = new LongByReference();
        check(call(capture, 3, data, frames, flags, devicePosition, qpc), "Audio-Paket lesen");
        try {
            int count = frames.getValue();
            if (count < 0 || count > ClipAudioBuffer.SAMPLE_RATE * 2) throw new IOException("Ungültige Audio-Paketgröße");
            if (count == 0) {
                if (loopbackClock != null) loopbackClock.noPacket(System.nanoTime());
                return null;
            }
            byte[] pcm = (flags.getValue() & 2) != 0 ? new byte[count * ClipAudioBuffer.FRAME_BYTES]
                    : data.getValue().getByteArray(0, count * ClipAudioBuffer.FRAME_BYTES);
            long timestamp;
            if (microphoneClock != null) {
                timestamp = microphoneClock.timestamp(count, devicePosition.getValue(),
                        anchorNanos + (qpc.getValue() - anchorQpc100ns) * 100,
                        (flags.getValue() & 4) == 0, (flags.getValue() & 1) != 0, System.nanoTime());
            } else {
                long arrival = System.nanoTime();
                long reported = anchorNanos + (qpc.getValue() - anchorQpc100ns) * 100;
                boolean valid = (flags.getValue() & 4) == 0 && Math.abs(reported - arrival) <= 2_000_000_000L;
                int queued = count;
                if (!valid && loopbackClock.needsQueueAnchor()) {
                    IntByReference padding = new IntByReference();
                    if (call(client, 6, padding) >= 0) queued = Math.max(count, padding.getValue());
                }
                timestamp = loopbackClock.timestamp(count, devicePosition.getValue(), reported, valid,
                        (flags.getValue() & 1) != 0, arrival, queued);
            }
            return new Samples(timestamp, pcm, (flags.getValue() & 1) != 0);
        } finally { check(call(capture, 4, frames.getValue()), "Audio-Paket freigeben"); }
    }

    private static Memory vtable() {
        Memory result = new Memory(4L * Native.POINTER_SIZE);
        result.setPointer(0, CallbackReference.getFunctionPointer(QUERY));
        result.setPointer(Native.POINTER_SIZE, CallbackReference.getFunctionPointer(ADD_REF));
        result.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(RELEASE));
        result.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(COMPLETE));
        return result;
    }
    static Guid.GUID guid(String value) { Guid.GUID result = new Guid.GUID(value); result.write(); return result; }
    static int call(Pointer object, int index, Object... parameters) {
        Object[] arguments = new Object[parameters.length + 1];
        arguments[0] = object;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        return Function.getFunction(object.getPointer(0).getPointer((long) index * Native.POINTER_SIZE), Function.ALT_CONVENTION).invokeInt(arguments);
    }
    static void release(Pointer object) { if (object != null && Pointer.nativeValue(object) != 0) call(object, 2); }
    static void check(int result, String message) throws IOException {
        if (result < 0) throw new IOException(message + String.format(Locale.ROOT, " (Windows 0x%08X)", result));
    }
    @Override public void close() {
        if (client != null) call(client, 11);
        release(capture); capture = null;
        release(client); client = null;
        if (event != null) { Kernel32.INSTANCE.CloseHandle(event); event = null; }
        if (comInitialized) { Ole32.INSTANCE.CoUninitialize(); comInitialized = false; }
    }
}
