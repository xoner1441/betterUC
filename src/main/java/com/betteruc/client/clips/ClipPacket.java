package com.betteruc.client.clips;

/** Encoded, independently owned bytes. Never modified after insertion into the replay buffer. */
public record ClipPacket(byte[] bytes, long pts, long dts, long duration, boolean keyframe) {}
