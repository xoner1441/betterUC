package com.betteruc.client.clips;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.BooleanSupplier;

/** All hashing/decoding stays on the upload worker; never reads the complete MP4 into memory. */
final class ClipUploadFile {
    record Prepared(long size, long modified, String md5, String poster) {}
    static Prepared prepare(Path path, BooleanSupplier cancelled) throws Exception {
        long size = Files.size(path), modified = Files.getLastModifiedTime(path).toMillis();
        if (size < 32 || size > 1_073_741_824L) throw new IOException("Clip muss kleiner als 1 GiB sein.");
        MessageDigest digest = MessageDigest.getInstance("MD5"); // R2 transport integrity, not authentication.
        try (var input = Files.newInputStream(path)) {
            byte[] chunk = new byte[262144];
            for (int read; (read = input.read(chunk)) >= 0;) {
                checkCancelled(cancelled);
                digest.update(chunk, 0, read);
            }
        }
        String poster = thumbnail(path, cancelled);
        if (size != Files.size(path) || modified != Files.getLastModifiedTime(path).toMillis()) {
            throw new IOException("Clip wurde während der Vorbereitung geändert.");
        }
        return new Prepared(size, modified, Base64.getEncoder().encodeToString(digest.digest()), poster);
    }
    static void checkCancelled(BooleanSupplier cancelled) throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
    static String thumbnail(Path path, BooleanSupplier cancelled) throws Exception {
        checkCancelled(cancelled);
        return ClipEncoderRuntime.thumbnail(path, cancelled);
    }

}
