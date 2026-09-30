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
        for (String b : new String[]{"a", "b", "c"}) slots.onFrame(1, buffer(b));
        assertFalse(slots.onVsync(1).show);
        AgvnFrameSlots.Tick t = slots.onVsync(1);
        assertTrue(t.show);
        runAll(t.releases);
        assertEquals(Arrays.asList("a"), released);
        // it draws its next frame in time; nothing moves until the second vsync
        slots.onFrame(1, buffer("a2"));
        assertFalse(slots.onVsync(1).show);
        t = slots.onVsync(1);
        assertTrue(t.show);
        runAll(t.releases);
        assertEquals(Arrays.asList("a", "b"), released);
    }

    @Test
    public void aGameAtTheScreensRateNeverStalls() {
        for (int buffers = 2; buffers <= 4; buffers++) {
            for (double draw : new double[]{0.05, 0.3, 0.9}) {
                int[] r = play(1, buffers, new double[]{draw}, 600);
                String what = buffers + " buffers, " + draw + " vsync per frame: ";
                assertTrue(what + r[0] + " frames", r[0] >= 590 && r[0] <= 600 + buffers);
                assertTrue(what + "waited " + r[1] + " vsyncs", r[1] <= 1);
            }
        }
    }

    @Test
    public void unevenFramesNeverLoseABuffer() {
        // 01/10: at 60 FPS on 60 Hz the game froze for 5 s every few seconds. A frame longer than a vsync followed by a
        // quick one lost a buffer each time, until the game held none (the old logic stalls after 6-15 frames here).
        double[][] patterns = {{0.3, 1.3, 0.2, 0.9}, {0.8, 1.1, 0.4}, {1.2, 0.1}, {0.5, 2.4, 0.1, 0.1}};
        for (int n = 1; n <= 3; n++) {
            for (int buffers = 2; buffers <= 4; buffers++) {
                for (double[] pattern : patterns) {
                    int[] r = play(n, buffers, pattern, 900);
                    String what = "N=" + n + ", " + buffers + " buffers, " + Arrays.toString(pattern) + ": ";
                    double perFrame = 0; // each frame takes its drawing time, and at least N vsyncs under the limit
                    for (double d : pattern) perFrame += Math.max(n, d) / pattern.length;
                    double expected = 900 / perFrame;
                    assertTrue(what + r[0] + " frames, expected about " + expected, r[0] >= expected * 0.9);
                    assertTrue(what + "waited " + r[1] + " vsyncs", r[1] <= n + 1);
                }
            }
        }
    }

    @Test
    public void theLimitHoldsAndASlowGameIsNeverBlocked() {
        int[] fast = play(2, 3, new double[]{0.3}, 600); // 30 FPS on 60 Hz
        assertTrue(fast[0] + " frames", fast[0] >= 295 && fast[0] <= 303);
        assertTrue("waited " + fast[1], fast[1] <= 2);
        int[] slow = play(2, 3, new double[]{2.6}, 600); // slower than the limit
        assertTrue(slow[0] + " frames", slow[0] >= 225);
        assertEquals("never waits for a buffer", 0, slow[1]);
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
        assertEquals("one per window", Arrays.asList("a", "x"), released);
        assertFalse(slots.holdsNothing());
        runAll(slots.drain());
        assertEquals(Arrays.asList("a", "x", "b"), released);
        assertTrue(slots.holdsNothing());
    }

    /**
     * A game with {@code buffers} images whose frames take {@code draw} vsyncs in turn, starting the next one as soon
     * as it has a free image, for {@code vsyncs} vsyncs. Returns {frames presented, longest wait for an image in vsyncs}.
     */
    private static int[] play(int vsyncsPerFrame, int buffers, double[] draw, int vsyncs) {
        final int steps = 20;
        AgvnFrameSlots slots = new AgvnFrameSlots();
        slots.setVsyncsPerFrame(vsyncsPerFrame);
        int[] free = {buffers};
        int presented = 0, longestWait = 0, waitStart = -1;
        double drawVsyncs = draw[0];
        double drawn = -1; // progress of the frame being drawn; -1 = not drawing
        for (int step = 0; step < vsyncs * steps; step++) {
            if (drawn < 0 && free[0] > 0) {
                free[0]--;
                drawn = 0;
                if (waitStart >= 0) longestWait = Math.max(longestWait, step - waitStart);
                waitStart = -1;
            }
            if (drawn >= 0) {
                drawn += 1.0 / steps;
                if (drawn >= drawVsyncs - 1e-9) {
                    slots.onFrame(1, () -> free[0]++);
                    presented++;
                    drawVsyncs = draw[presented % draw.length];
                    drawn = -1;
                }
            } else if (waitStart < 0) {
                waitStart = step;
            }
            if ((step + 1) % steps == 0) runAll(slots.onVsync(1).releases);
        }
        if (waitStart >= 0) longestWait = Math.max(longestWait, vsyncs * steps - waitStart);
        return new int[]{presented, (longestWait + steps - 1) / steps};
    }
}
