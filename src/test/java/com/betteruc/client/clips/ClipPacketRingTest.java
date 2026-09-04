package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClipPacketRingTest {
    private ClipPacket packet(long pts, boolean key) { return new ClipPacket(new byte[10], pts, pts, 1, key); }

    @Test void waitsForFirstKeyframe() {
        var ring = new ClipPacketRing(60, 1000);
        ring.add(packet(0, false));
        assertTrue(ring.snapshot().isEmpty());
        ring.add(packet(1, true));
        assertEquals(1, ring.snapshot().getFirst().pts());
    }

    @Test void durationTrimKeepsWholeKeyframeGroupsAndSnapshots() {
        var ring = new ClipPacketRing(120, 100_000);
        for (int i = 0; i < 180; i++) ring.add(packet(i, i % 60 == 0));
        var before = ring.snapshot();
        ring.add(packet(180, true));
        assertEquals(60, ring.snapshot().getFirst().pts());
        assertTrue(ring.snapshot().getFirst().keyframe());
        assertEquals(0, before.getFirst().pts());
        assertEquals(180, before.size());
        assertEquals(1210, ring.bytes());
        assertEquals(121.0 / 60, ring.seconds(60), 0.0001);
        assertFalse(ring.memoryLimited());
        ring.clear();
        assertTrue(ring.snapshot().isEmpty());
        assertEquals(0, ring.bytes());
    }

    @Test void boundsBytesEvenForOversizedKeyframeGroups() {
        var ring = new ClipPacketRing(120, 25);
        ring.add(packet(0, true));
        ring.add(packet(1, false));
        ring.add(packet(2, true));
        assertEquals(2, ring.snapshot().getFirst().pts());
        assertTrue(ring.memoryLimited());
        ring.add(packet(3, false));
        ring.add(packet(4, false));
        assertTrue(ring.snapshot().isEmpty());
        ring.add(packet(5, false));
        assertTrue(ring.snapshot().isEmpty());
        ring.add(packet(6, true));
        assertEquals(10, ring.bytes());
    }

    @Test void preservesDroppedFrameTiming() {
        var ring = new ClipPacketRing(1800, 1000);
        ring.add(packet(100, true));
        ring.add(packet(159, false));
        assertEquals(1, ring.seconds(60));
        assertEquals(159, ring.snapshot().getLast().pts());
    }

    @Test void viewportMaintainsAspectWithoutUpscaling() {
        assertEquals(new ClipSettings(1920, 1080, 60, 30, 25_000_000), ClipSettings.forViewport(3840, 2160, 30));
        var small = ClipSettings.forViewport(1281, 721, 15);
        assertEquals(1280, small.width());
        assertEquals(720, small.height());
        var wide = ClipSettings.forViewport(3440, 1440, 60);
        assertEquals(1920, wide.width());
        assertEquals(802, wide.height());
        assertEquals(30, ClipSettings.forViewport(1920, 1080, -5).seconds());
        assertThrows(IllegalArgumentException.class, () -> ClipSettings.forViewport(0, 1080, 30));
        assertTrue(wide.maxBufferBytes() <= ClipSettings.MAX_VIDEO_BUFFER_BYTES);
    }

    @Test void fiveMinuteReplayRetainsWholeKeyframesWithoutOldSixtySecondLimit() {
        var ring = new ClipPacketRing(300 * 60, 1_000_000);
        for (int i = 0; i < 600 * 60; i++) ring.add(packet(i, i % 60 == 0));
        assertTrue(ring.seconds(60) >= 300 && ring.seconds(60) <= 301);
        assertTrue(ring.snapshot().getFirst().keyframe());
        assertTrue(ring.snapshot().getFirst().pts() > 0);
        assertFalse(ring.memoryLimited());
    }

    @Test void memoryPressureCanShortenLongReplayAndIsReported() {
        var ring = new ClipPacketRing(300 * 60, 1000);
        for (int i = 0; i < 300; i++) ring.add(packet(i, i % 60 == 0));
        assertTrue(ring.bytes() <= 1000);
        assertTrue(ring.memoryLimited());
        assertTrue(ring.seconds(60) < 2);
        ring.clear();
        assertFalse(ring.memoryLimited());
    }
}
