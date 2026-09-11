package com.betteruc.client;

/** Collapses the many block fragments of one server hit into one custom burst. */
final class BloodEffectBurstLimiter {
    private static final double CLUSTER_DISTANCE_SQUARED = 2.25D;
    private long lastTick = Long.MIN_VALUE;
    private double lastX;
    private double lastY;
    private double lastZ;

    void reset() {
        lastTick = Long.MIN_VALUE;
    }

    boolean accept(long tick, double x, double y, double z) {
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        if (tick == lastTick && dx * dx + dy * dy + dz * dz <= CLUSTER_DISTANCE_SQUARED) return false;
        lastTick = tick;
        lastX = x;
        lastY = y;
        lastZ = z;
        return true;
    }
}
