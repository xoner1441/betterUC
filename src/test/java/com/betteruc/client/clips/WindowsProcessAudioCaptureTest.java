package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "betteruc.clipAudioHardwareTest", matches = "true")
class WindowsProcessAudioCaptureTest {
    @Test void capturesCurrentProcessOutputWithMonotonicTimestamps() throws Exception {
        // Explicit opt-in test; outputs a quiet synthetic tone in this JVM, never opens an input/microphone.
        AudioFormat format = new AudioFormat(48_000, 16, 2, true, false);
        try (var capture = WindowsProcessAudioCapture.openCurrentProcess();
             SourceDataLine speaker = AudioSystem.getSourceDataLine(format)) {
            speaker.open(format);
            speaker.start();
            byte[] tone = new byte[48_000 * 4];
            for (int i = 0; i < 48_000; i++) {
                short value = (short) (Math.sin(i * 2 * Math.PI * 880 / 48_000) * 1200);
                for (int channel = 0; channel < 2; channel++) {
                    tone[i * 4 + channel * 2] = (byte) value;
                    tone[i * 4 + channel * 2 + 1] = (byte) (value >> 8);
                }
            }
            AtomicReference<Throwable> playError = new AtomicReference<>();
            Thread player = new Thread(() -> {
                try { speaker.write(tone, 0, tone.length); speaker.drain(); }
                catch (Throwable error) { playError.set(error); }
            });
            player.start();
            long begin = System.nanoTime();
            long previous = Long.MIN_VALUE;
            long frames = 0, nonzero = 0;
            while (System.nanoTime() - begin < 2_000_000_000L) {
                capture.awaitSamples();
                WindowsProcessAudioCapture.Samples packet;
                while ((packet = capture.read()) != null) {
                    assertTrue(packet.startNanos() >= previous);
                    assertTrue(Math.abs(packet.startNanos() - System.nanoTime()) < 1_000_000_000L);
                    previous = packet.startNanos();
                    frames += packet.pcm().length / 4;
                    for (byte value : packet.pcm()) if (value != 0) nonzero++;
                }
            }
            player.join(3000);
            assertFalse(player.isAlive());
            assertNull(playError.get());
            assertTrue(frames >= 24_000, "Expected audio packets, got " + frames);
            assertTrue(nonzero > 1000, "Synthetic tone must be present");
            System.out.println("Current-process WASAPI capture succeeded: " + frames + " frames; monotonic timestamps mapped to video clock.");
            assertTrue(capture.diagnostics().contains("Spielton-Takt"));
        }
    }

    @Test void processLoopbackKeepsTwoTestTonesSeparatedByRealPlaybackSilence() throws Exception {
        // Still only this JVM's synthetic audio. No microphone or system/TeamSpeak recording.
        AudioFormat format = new AudioFormat(48_000, 16, 2, true, false);
        AtomicReference<Throwable> playError = new AtomicReference<>();
        var intervals = new ArrayList<long[]>();
        try (var capture = WindowsProcessAudioCapture.openCurrentProcess()) {
            Thread player = new Thread(() -> {
                try {
                    for (int burst = 0; burst < 2; burst++) {
                        try (SourceDataLine speaker = AudioSystem.getSourceDataLine(format)) {
                            speaker.open(format); speaker.start();
                            byte[] tone = new byte[12_000 * 4];
                            for (int i = 0; i < 12_000; i++) {
                                short value = (short) (Math.sin(i * 2 * Math.PI * 880 / 48_000) * 1200);
                                for (int c = 0; c < 2; c++) {
                                    tone[i * 4 + c * 2] = (byte) value;
                                    tone[i * 4 + c * 2 + 1] = (byte) (value >> 8);
                                }
                            }
                            speaker.write(tone, 0, tone.length); speaker.drain();
                        }
                        if (burst == 0) Thread.sleep(900); // Close the render stream between bursts.
                    }
                } catch (Throwable error) { playError.set(error); }
            }, "clip-synthetic-audio-test");
            player.setDaemon(true);
            player.start();
            long begin = System.nanoTime();
            while (System.nanoTime() - begin < 3_500_000_000L) {
                capture.awaitSamples();
                WindowsProcessAudioCapture.Samples packet;
                while ((packet = capture.read()) != null) {
                    var pcm = java.nio.ByteBuffer.wrap(packet.pcm()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < packet.pcm().length / 4; i++) {
                        if (Math.abs(pcm.getShort(i * 4)) < 100) continue;
                        long time = packet.startNanos() + Math.round(i * 1_000_000_000.0 / 48000);
                        if (intervals.isEmpty() || time - intervals.getLast()[1] > 200_000_000L) intervals.add(new long[]{time, time});
                        else intervals.getLast()[1] = time;
                    }
                }
            }
            player.join(1000);
            assertFalse(player.isAlive()); assertNull(playError.get());
            assertEquals(2, intervals.size(), "Expected two separate synthetic tone bursts");
            assertTrue(intervals.get(1)[0] - intervals.getFirst()[1] > 650_000_000L,
                    "A playback pause must not collapse when loopback omits silence packets");
            System.out.println("Process loopback retained the pause between two synthetic tones: " + capture.diagnostics());
        }
    }
}
