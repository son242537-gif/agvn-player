/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgvnFramePacingTest {
    private final List<String> released = new ArrayList<>();

    private Runnable buffer(String name) {
        return () -> released.add(name);
    }

    private static void runAll(List<Runnable> rs) {
        for (Runnable r : rs) r.run();
    }

    @Test
    public void limitsBecomeWholeVsyncs() {
        assertEquals(2, AgvnFramePacing.vsyncsPerFrame(60, 30));
        assertEquals(3, AgvnFramePacing.vsyncsPerFrame(60, 20));
        assertEquals("24 FPS on 60 Hz would mix 33 and 50 ms frames", 3, AgvnFramePacing.vsyncsPerFrame(60, 24));
        assertEquals("30 would be 11% above 27", 3, AgvnFramePacing.vsyncsPerFrame(60, 27));
        assertEquals(2, AgvnFramePacing.vsyncsPerFrame(60, 28));
        assertEquals("never 60 for a 45 limit", 2, AgvnFramePacing.vsyncsPerFrame(60, 45));
        assertEquals(1, AgvnFramePacing.vsyncsPerFrame(60, 60));
        assertEquals(1, AgvnFramePacing.vsyncsPerFrame(60, 120));
        assertEquals(5, AgvnFramePacing.vsyncsPerFrame(120, 24));
        assertEquals(4, AgvnFramePacing.vsyncsPerFrame(120, 30));
        assertEquals(3, AgvnFramePacing.vsyncsPerFrame(120, 40));
        assertEquals(3, AgvnFramePacing.vsyncsPerFrame(90, 30));
        assertEquals(1, AgvnFramePacing.vsyncsPerFrame(60, 0));
        for (double hz : new double[]{60, 90, 120, 144}) {
            for (int fps = 5; fps <= 120; fps += 5) {
                assertTrue(hz + " Hz, " + fps + " FPS", AgvnFramePacing.effectiveFps(hz, fps) <= fps * 1.10 + 1e-9);
            }
        }
    }

    @Test
    public void theLabelShowsWhatReallyRuns() {
        assertEquals("30 FPS", AgvnFramePacing.label(30, 60));
        assertEquals("25 → 20 FPS", AgvnFramePacing.label(25, 60));
        assertEquals("24 FPS", AgvnFramePacing.label(24, 120));
        assertEquals("24 → 22,5 FPS", AgvnFramePacing.label(24, 90));
    }

    @Test
    public void aFastGameGetsOneBufferEveryTwoVsyncsOldestFirst() {
        AgvnFrameSlots slots = new AgvnFrameSlots();
        slots.setVsyncsPerFrame(2);
        // the game fills its three buffers at once, then waits for one back
        for (String b : new String[]{"a", "b", "c"}) assertTrue(slots.onFrame(1, buffer(b)).isEmpty());
        assertFalse(slots.onVsync(1).show);
        AgvnFrameSlots.Tick t = slots.onVsync(1);
        assertTrue(t.show);
        runAll(t.releases);
        assertEquals(Arrays.asList("a"), released);
        // it draws its next frame in time; nothing moves until the second vsync
        assertTrue(slots.onFrame(1, buffer("a2")).isEmpty());
        assertFalse(slots.onVsync(1).show);
        t = slots.onVsync(1);
        assertTrue(t.show);
        runAll(t.releases);
        assertEquals(Arrays.asList("a", "b"), released);
    }

    @Test
    public void aSlowGameIsNotHeldBackFurther() {
        AgvnFrameSlots slots = new AgvnFrameSlots();
        slots.setVsyncsPerFrame(2);
        for (String b : new String[]{"a", "b", "c"}) slots.onFrame(1, buffer(b));
        slots.onVsync(1);
        runAll(slots.onVsync(1).releases); // "a" back
        // the next frame takes 3 vsyncs: the missed vsyncs redraw only what else changed and release nothing
        slots.onVsync(1);
        AgvnFrameSlots.Tick waiting = slots.onVsync(1);
        assertTrue(waiting.show);
        assertTrue(waiting.releases.isEmpty());
        assertTrue(slots.onVsync(1).releases.isEmpty());
        // the late frame comes: the game gets its oldest buffer at once, not the one on screen
        runAll(slots.onFrame(1, buffer("a2")));
        assertEquals(Arrays.asList("a", "b"), released);
        AgvnFrameSlots.Tick shown = slots.onVsync(1);
        assertTrue("the late frame goes on screen at the next vsync", shown.show);
        assertTrue("already released", shown.releases.isEmpty());
    }

    @Test
    public void missedVsyncsCountAndTurningOffGivesEverythingBack() {
        AgvnFrameSlots slots = new AgvnFrameSlots();
        slots.setVsyncsPerFrame(3);
        slots.onFrame(1, buffer("a"));
        slots.onFrame(2, buffer("x")); // another window keeps its own queue
        slots.onFrame(1, buffer("b"));
        AgvnFrameSlots.Tick t = slots.onVsync(3); // the vsync thread was late by two vsyncs
        assertTrue(t.show);
        runAll(t.releases);
        assertEquals(Arrays.asList("a", "x"), released);
        assertFalse(slots.holdsNothing());
        runAll(slots.drain());
        assertEquals(Arrays.asList("a", "x", "b"), released);
        assertTrue(slots.holdsNothing());
    }
}
