package com.betteruc.client.clips;

/** Immutable replay snapshot, streamed in small blocks by the AAC export worker. */
interface ClipPcmSource {
    int frames();
    boolean hasCapturedAudio();
    Reader reader();
    interface Reader { int read(byte[] destination); }
}
