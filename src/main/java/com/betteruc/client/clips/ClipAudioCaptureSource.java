package com.betteruc.client.clips;

interface ClipAudioCaptureSource extends AutoCloseable {
    void awaitSamples() throws Exception;
    WindowsProcessAudioCapture.Samples read() throws Exception;
    default String diagnostics() { return ""; }
    void close();
}
