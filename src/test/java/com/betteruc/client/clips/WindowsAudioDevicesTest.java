package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WindowsAudioDevicesTest {
    @Test void cyclesStableIdsAndNeverUsesNamesAsDeviceIdentity() {
        var devices = List.of(new WindowsAudioDevices.Device("one", "Headset äöü"), new WindowsAudioDevices.Device("two", "Headset äöü"));
        assertEquals("one", WindowsAudioDevices.next(devices, ""));
        assertEquals("two", WindowsAudioDevices.next(devices, "one"));
        assertEquals("", WindowsAudioDevices.next(devices, "two"));
        assertEquals("Headset äöü", WindowsAudioDevices.label(devices, "two"));
        assertTrue(WindowsAudioDevices.label(devices, "missing").contains("Gespeichertes Gerät"));
        assertTrue(WindowsAudioDevices.label(devices, "").contains("Windows-Standard"));
    }
    @Test @EnabledIfSystemProperty(named = "betteruc.clipHardwareTest", matches = "true")
    void nativeEnumerationListsDevicesWithoutStartingAnyAudioCapture() throws Exception {
        var devices = WindowsAudioDevices.list();
        for (var list : List.of(devices.outputs(), devices.inputs())) {
            assertEquals(list.size(), list.stream().map(WindowsAudioDevices.Device::id).distinct().count());
            for (var device : list) {
                assertFalse(device.id().isBlank());
                assertFalse(device.name().isBlank());
            }
        }
        System.out.println("Read-only audio enumeration: " + devices.outputs().size() + " outputs, " + devices.inputs().size() + " inputs. No capture started.");
    }
    @Test @EnabledIfSystemProperty(named = "betteruc.clipHardwareTest", matches = "true")
    void defaultEndpointsAcceptStereo48KhzWithoutStartingCapture() throws Exception {
        var devices = WindowsAudioDevices.list();
        if (!devices.outputs().isEmpty()) WindowsProcessAudioCapture.validateEndpointFormat(false, "");
        if (!devices.inputs().isEmpty()) WindowsProcessAudioCapture.validateEndpointFormat(true, "");
        System.out.println("Default endpoint format checks passed; no IAudioClient.Start / no microphone packets read.");
    }
}
