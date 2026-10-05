/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnFrameStallsTest {
    private static final long MS = 1_000_000L;

    @Test
    public void shortPausesAreNotLogged() {
        AgvnFrameStalls stalls = new AgvnFrameStalls();
        assertNull("the first frame", stalls.onFrame(1000 * MS, AgvnFrameStalls.Mode.TIMER, 60, 0));
        assertNull(stalls.onFrame(1990 * MS, AgvnFrameStalls.Mode.TIMER, 60, 0));
        AgvnFrameStalls.Stall stall = stalls.onFrame(3000 * MS, AgvnFrameStalls.Mode.TIMER, 60, 0);
        assertEquals(1010, stall.ms);
        assertFalse("a timer gives every buffer back within a frame", stall.limitHeld());
        assertTrue(stall.vietnamese(), stall.vietnamese().contains("bộ hẹn giờ"));
    }

    @Test
    public void aGameThatStopsByItselfUnderTheVsyncLimit() {
        // 01/10: frames came back 5090 ms later; every buffer was back on the next vsync and the thread kept running
        AgvnFrameStalls stalls = new AgvnFrameStalls();
        stalls.onFrame(1000 * MS, AgvnFrameStalls.Mode.VSYNC, 60, 0);
        for (long t = 1017; t < 6000; t += 17) {
            stalls.onTick(t * MS);
            if (t == 1017) stalls.onBack(t * MS);
        }
        stalls.onStop(6000 * MS, 0); // nothing held when it gave up after 5 s
        AgvnFrameStalls.Stall stall = stalls.onFrame(6090 * MS, AgvnFrameStalls.Mode.VSYNC, 60, 0);
        assertEquals(5090, stall.ms);
        assertEquals(17, stall.lastBackMs);
        assertEquals(17, stall.longestPauseMs);
        assertEquals(0, stall.drained);
        assertFalse(stall.english(), stall.limitHeld());
        assertTrue(stall.vietnamese(), stall.vietnamese().endsWith("không phải do giới hạn FPS giữ game"));
    }

    @Test
    public void buffersHeldUntilTheLimiterGaveUpAreTheLimitsDoing() {
        AgvnFrameStalls stalls = new AgvnFrameStalls();
        stalls.onFrame(1000 * MS, AgvnFrameStalls.Mode.VSYNC, 60, 0);
        for (long t = 1017; t < 6000; t += 17) stalls.onTick(t * MS); // ticks, but no buffer went back
        stalls.onStop(6000 * MS, 2);
        AgvnFrameStalls.Stall stall = stalls.onFrame(6050 * MS, AgvnFrameStalls.Mode.VSYNC, 60, 0);
        assertEquals(2, stall.drained);
        assertEquals(5000, stall.lastBackMs);
        assertTrue(stall.limitHeld());
        assertTrue(stall.english(), stall.english().endsWith("the FPS limit may have held the game"));
    }

    @Test
    public void aStuckVsyncThreadIsTheLimitsDoing() {
        AgvnFrameStalls stalls = new AgvnFrameStalls();
        stalls.onFrame(1000 * MS, AgvnFrameStalls.Mode.VSYNC, 30, 0);
        stalls.onTick(1017 * MS);
        stalls.onTick(3200 * MS); // the thread did not run for 2.2 s
        stalls.onBack(3200 * MS);
        AgvnFrameStalls.Stall stall = stalls.onFrame(3210 * MS, AgvnFrameStalls.Mode.VSYNC, 30, 1);
        assertEquals(2183, stall.longestPauseMs);
        assertEquals(1, stall.held);
        assertEquals(-1, stall.drained);
        assertTrue(stall.limitHeld());
    }

    @Test
    public void lowLimitsMayTakeLongerToGiveEverythingBack() {
        // 10 FPS on 60 Hz: 6 vsyncs a frame, the 4th held buffer goes back after 400 ms
        AgvnFrameStalls stalls = new AgvnFrameStalls();
        stalls.onFrame(1000 * MS, AgvnFrameStalls.Mode.VSYNC, 10, 0);
        for (long t = 1017; t < 2500; t += 17) stalls.onTick(t * MS);
        stalls.onBack(1400 * MS);
        assertFalse(stalls.onFrame(2500 * MS, AgvnFrameStalls.Mode.VSYNC, 10, 0).limitHeld());
    }
}
