package com.betteruc.client.clips;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static com.betteruc.client.clips.WindowsProcessAudioCapture.*;

/** Read-only MMDevice enumeration. Listing devices never opens an audio stream or microphone. */
public final class WindowsAudioDevices {
    public record Device(String id, String name) {}
    public record Devices(List<Device> outputs, List<Device> inputs) {
        public Devices { outputs = List.copyOf(outputs); inputs = List.copyOf(inputs); }
    }
    private static final Guid.GUID ENUMERATOR_CLASS = guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final Guid.GUID ENUMERATOR_INTERFACE = guid("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private WindowsAudioDevices() {}

    public static Devices list() throws IOException {
        requireWindows();
        check(Ole32.INSTANCE.CoInitializeEx(null, 0).intValue(), "Audio-Geräte initialisieren");
        Pointer enumerator = null;
        try {
            enumerator = enumerator();
            return new Devices(list(enumerator, false), list(enumerator, true));
        } finally { release(enumerator); Ole32.INSTANCE.CoUninitialize(); }
    }

    static void requireWindows() throws IOException {
        if (!System.getProperty("os.name", "").startsWith("Windows") || Native.POINTER_SIZE != 8) {
            throw new IOException("Audioaufnahme benötigt Windows x64");
        }
    }
    static Pointer enumerator() throws IOException {
        PointerByReference result = new PointerByReference();
        check(Ole32.INSTANCE.CoCreateInstance(ENUMERATOR_CLASS, null, 1, ENUMERATOR_INTERFACE, result).intValue(),
                "Audio-Geräte öffnen");
        return result.getValue();
    }
    static Pointer resolve(Pointer enumerator, boolean microphone, String id) throws IOException {
        PointerByReference result = new PointerByReference();
        if (id == null || id.isEmpty()) {
            check(call(enumerator, 4, microphone ? 1 : 0, 1, result), "Windows-Standardgerät auswählen"); // eMultimedia
        } else {
            // An unavailable explicit device must never fall back to a different (private) input.
            check(call(enumerator, 5, new WString(id), result), "Gewähltes Audiogerät nicht verfügbar");
        }
        return result.getValue();
    }
    private static List<Device> list(Pointer enumerator, boolean microphone) throws IOException {
        PointerByReference out = new PointerByReference();
        check(call(enumerator, 3, microphone ? 1 : 0, 1, out), "Aktive Audio-Geräte auflisten");
        Pointer collection = out.getValue();
        try {
            IntByReference count = new IntByReference();
            check(call(collection, 3, count), "Audio-Geräte zählen");
            if (count.getValue() < 0 || count.getValue() > 1024) throw new IOException("Ungültige Geräteliste");
            var devices = new ArrayList<Device>();
            for (int i = 0; i < count.getValue(); i++) {
                PointerByReference item = new PointerByReference();
                check(call(collection, 4, i, item), "Audiogerät lesen");
                Pointer device = item.getValue();
                try {
                    PointerByReference id = new PointerByReference();
                    check(call(device, 5, id), "Gerätekennung lesen");
                    String key;
                    try { key = id.getValue().getWideString(0); }
                    finally { Ole32.INSTANCE.CoTaskMemFree(id.getValue()); }
                    devices.add(new Device(key, friendlyName(device)));
                } finally { release(device); }
            }
            return devices;
        } finally { release(collection); }
    }
    private static String friendlyName(Pointer device) {
        Pointer store = null;
        try (Memory key = new Memory(20); Memory value = new Memory(24)) {
            PointerByReference out = new PointerByReference();
            check(call(device, 4, 0, out), "Gerätename öffnen"); // STGM_READ
            store = out.getValue();
            var format = guid("A45C254E-DF1C-4EFD-8020-67D146A850E0");
            key.write(0, format.getPointer().getByteArray(0, 16), 0, 16);
            key.setInt(16, 14); // PKEY_Device_FriendlyName
            value.clear();
            try {
                check(call(store, 5, key, value), "Gerätename lesen");
                if (value.getShort(0) == 31 && value.getPointer(8) != null) return value.getPointer(8).getWideString(0);
            } finally { NativeLibrary.getInstance("Ole32").getFunction("PropVariantClear").invokeInt(new Object[]{value}); }
        } catch (IOException ignored) { }
        finally { release(store); }
        return "Audiogerät ohne Namen";
    }
    public static String label(List<Device> devices, String id) {
        if (id == null || id.isEmpty()) return "Windows-Standard (bei Aufnahmestart)";
        return devices.stream().filter(d -> d.id().equals(id)).map(Device::name).findFirst().orElse("Gespeichertes Gerät (Liste neu laden)");
    }
    public static String next(List<Device> devices, String id) {
        if (devices.isEmpty()) return "";
        if (id == null || id.isEmpty()) return devices.getFirst().id();
        for (int i = 0; i < devices.size(); i++) if (devices.get(i).id().equals(id)) {
            return i + 1 < devices.size() ? devices.get(i + 1).id() : "";
        }
        return "";
    }
}
