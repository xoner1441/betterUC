package com.betteruc.client;

import net.minecraft.client.MinecraftClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ClientScheduler {

    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "betterUCDelayScheduler");
                thread.setDaemon(true);
                return thread;
            });

    private ClientScheduler() {
    }

    public static void runDelayedOnClient(MinecraftClient client, long delayMs, Runnable task) {
        if (client == null || task == null) return;
        Object scheduledNetworkHandler = client.getNetworkHandler();
        long safeDelayMs = Math.max(0L, delayMs);
        DELAY_EXECUTOR.schedule(() -> client.execute(() -> {
            if (client.getNetworkHandler() != scheduledNetworkHandler) return;
            task.run();
        }), safeDelayMs, TimeUnit.MILLISECONDS);
    }
}
