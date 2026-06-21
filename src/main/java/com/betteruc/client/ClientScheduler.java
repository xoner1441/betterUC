package com.betteruc.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;

public final class ClientScheduler {

    private static final ScheduledExecutorService DELAY_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "betterUCDelayScheduler");
                thread.setDaemon(true);
                return thread;
            });

    private ClientScheduler() {
    }

    public static void runDelayedOnClient(Minecraft client, long delayMs, Runnable task) {
        if (client == null || task == null) return;
        Object scheduledNetworkHandler = client.getConnection();
        long safeDelayMs = Math.max(0L, delayMs);
        DELAY_EXECUTOR.schedule(() -> client.execute(() -> {
            if (client.getConnection() != scheduledNetworkHandler) return;
            task.run();
        }), safeDelayMs, TimeUnit.MILLISECONDS);
    }
}
