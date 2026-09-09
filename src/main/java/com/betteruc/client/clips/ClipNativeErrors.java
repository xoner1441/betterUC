package com.betteruc.client.clips;

import java.nio.charset.StandardCharsets;
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;

/** Decode only the NUL-terminated error text, never the unused native buffer tail. */
final class ClipNativeErrors {
    private ClipNativeErrors() {}

    static String describe(int code) {
        byte[] buffer = new byte[256];
        av_strerror(code, buffer, buffer.length);
        String text = decode(buffer);
        return (text.isBlank() ? "FFmpeg-Fehler" : text) + " (" + code + ")";
    }

    static String decode(byte[] buffer) {
        int length = 0;
        while (length < buffer.length && buffer[length] != 0) length++;
        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }
}
