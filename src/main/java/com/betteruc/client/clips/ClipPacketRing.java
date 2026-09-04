package com.betteruc.client.clips;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Keeps complete H.264 keyframe groups, bounded by duration and bytes, never raw video frames. */
public final class ClipPacketRing {
    private final ArrayDeque<ClipPacket> packets = new ArrayDeque<>();
    private final long maxTicks;
    private final long maxBytes;
    private long bytes;
    private boolean memoryLimited;

    public ClipPacketRing(long maxTicks, long maxBytes) {
        this.maxTicks = maxTicks;
        this.maxBytes = maxBytes;
    }

    public synchronized void add(ClipPacket packet) {
        if (packets.isEmpty() && !packet.keyframe()) return;
        packets.addLast(packet);
        bytes += packet.bytes().length;
        long cutoff = packet.pts() - maxTicks;
        while (packets.size() > 1) {
            ClipPacket nextKey = null;
            boolean first = true;
            for (ClipPacket candidate : packets) {
                if (!first && candidate.keyframe()) { nextKey = candidate; break; }
                first = false;
            }
            if (nextKey == null || (nextKey.pts() > cutoff && bytes <= maxBytes)) break;
            if (nextKey.pts() > cutoff && bytes > maxBytes) memoryLimited = true;
            while (packets.peekFirst() != nextKey) bytes -= packets.removeFirst().bytes().length;
        }
        // An oversized GOP cannot be retained safely. Resume only on the next keyframe.
        if (bytes > maxBytes) {
            clear();
            memoryLimited = true;
        }
    }

    public synchronized List<ClipPacket> snapshot() {
        return List.copyOf(new ArrayList<>(packets));
    }

    public synchronized double seconds(int fps) {
        if (packets.isEmpty()) return 0;
        return (packets.peekLast().pts() - packets.peekFirst().pts() + 1.0) / fps;
    }

    public synchronized long bytes() { return bytes; }
    public synchronized boolean memoryLimited() { return memoryLimited; }

    public synchronized void clear() {
        packets.clear();
        bytes = 0;
        memoryLimited = false;
    }
}
