/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AgvnInputHoldTest {
    private static final long PRESS = 10_000, FRAMES = 500, DRAWING = 9_990, IDLE = 1_000;

    @Test
    public void aZeroMsTapIsHeldAtLeast60Ms() {
        // 01/10: adb's 0 ms tap never advanced a Unity visual novel, a 150 ms hold always did
        assertEquals(60, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS, FRAMES, DRAWING));
        assertEquals(53, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 7, FRAMES, DRAWING));
    }

    @Test
    public void andUntilTheGameHasDrawnTwoFrames() {
        assertEquals("one frame so far: look again soon", 8,
                AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 70, FRAMES + 1, PRESS + 60));
        assertEquals(0, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 70, FRAMES + 2, PRESS + 65));
        assertEquals("a slow game (10 FPS) gets its two frames", 8,
                AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 150, FRAMES + 1, PRESS + 100));
    }

    @Test
    public void aGameThatDrawsNothingWaitsOnlyForTheTime() {
        // loading, or a program that does not draw through the Present extension (the Wine desktop)
        assertEquals(0, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 60, FRAMES, IDLE));
        assertEquals(30, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 30, FRAMES, IDLE));
    }

    @Test
    public void neverLongerThan250Ms() {
        // a longer hold could read as a long press
        assertEquals(0, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 250, FRAMES + 1, PRESS + 240));
        assertEquals(0, AgvnInputHold.waitMs(PRESS, FRAMES, PRESS + 900, FRAMES, PRESS + 890));
    }
}
