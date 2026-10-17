/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** "Tự sửa lỗi" asks whether a reported problem is gone while the game draws, not while it loads with nothing shown. */
public class AgvnRepairAskTest {
    @Test
    public void waitsWhileTheGameShowsNothing() {
        assertFalse("drawing: asked", AgvnRepairAsk.waitsForFrames(0));
        assertFalse(AgvnRepairAsk.waitsForFrames(AgvnRepairAsk.STILL_MS - 1));
        assertTrue("logo drawn, then the first scene loads", AgvnRepairAsk.waitsForFrames(30_000));
        assertTrue("Immoral Bathhouse, 17/10/2026: about 2.5 min with nothing shown", AgvnRepairAsk.waitsForFrames(150_000));
        assertFalse("3 min with nothing: frozen, asked", AgvnRepairAsk.waitsForFrames(AgvnRepairAsk.FROZEN_MS));
    }

    @Test
    public void timeSinceTheLastFrame() {
        AgvnSessionTrack.start(1_000);
        assertEquals("no frame yet", Long.MAX_VALUE, AgvnSessionTrack.msSinceFrame(2_000));
        AgvnSessionTrack.onFrame(5_000);
        assertEquals(3_000, AgvnSessionTrack.msSinceFrame(8_000));
        assertEquals(0, AgvnSessionTrack.msSinceFrame(4_000));
    }
}
