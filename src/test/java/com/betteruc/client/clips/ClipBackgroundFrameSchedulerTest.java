package com.betteruc.client.clips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipBackgroundFrameSchedulerTest {
    @Test void repeatsOnlyAReadyFrameAfterOneBackgroundSecond() {
        long start=10_000_000_000L, lastWall=start+100, now=lastWall+1_000_000_000L;
        assertEquals(-1,ClipBackgroundFrameScheduler.duePts(false,true,start,lastWall,now,10,60));
        assertEquals(-1,ClipBackgroundFrameScheduler.duePts(true,false,start,lastWall,now,10,60));
        assertEquals(-1,ClipBackgroundFrameScheduler.duePts(true,true,start,lastWall,now-1,10,60));
        assertEquals(60,ClipBackgroundFrameScheduler.duePts(true,true,start,lastWall,now,10,60));
    }

    @Test void timelineNeverMovesBackwardAndLongIntervalsDoNotOverflowTheFraction() {
        long start=1_000_000_000L;
        assertEquals(501,ClipBackgroundFrameScheduler.duePts(true,true,start,start,
                start+1_000_000_000L,500,60));
        assertEquals(18_030,ClipBackgroundFrameScheduler.duePts(true,true,start,start,
                start+300_500_000_000L,10,60));
    }
}
