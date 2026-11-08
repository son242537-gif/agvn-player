/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Romantic Escape (Unity 2022.3, Adreno 830, 27/10/2026) held the cursor at (640,360) and its camera did not follow a
 * dragged finger: while a game holds the cursor, a finger's movement goes to it instead.
 */
public class AgvnMouseLookTest {
    @Test
    public void heldWhenAgvnWinhandlerSaysSoOrTheGameKeepsPuttingThePointerBack() {
        assertTrue("agvn-winhandler.exe: hidden and clipped", AgvnMouseLook.locked(AgvnMouseLook.LOCKED, 0, 99_999));
        assertFalse("a caret alone holds nothing", AgvnMouseLook.locked(AgvnMouseLook.TEXT, 0, 99_999));
        assertFalse(AgvnMouseLook.locked(0, 0, 0));
        // a game that reads the mouse through the cursor puts it back on one point at every frame
        int warps = 1;
        for (int i = 0; i < 4; i++) warps = AgvnMouseLook.warps(warps, true, 16);
        assertEquals(5, warps);
        assertTrue(AgvnMouseLook.locked(0, warps, 16));
        assertFalse("it stopped a while ago", AgvnMouseLook.locked(0, warps, AgvnMouseLook.WARP_HOLD_MS + 1));
        assertFalse("twice is not yet", AgvnMouseLook.locked(0, 2, 16));
        assertEquals("another point starts again", 1, AgvnMouseLook.warps(warps, false, 16));
        assertEquals("long after the last one", 1, AgvnMouseLook.warps(warps, true, AgvnMouseLook.WARP_WINDOW_MS + 1));
    }

    @Test
    public void aCaretIsToldWhenItComesAndGoes() {
        AgvnMouseLook look = new AgvnMouseLook(() -> false, () -> 0L);
        List<Integer> told = new ArrayList<>();
        look.setOnText(told::add);
        look.reported(0);
        look.reported(AgvnMouseLook.LOCKED);
        look.reported(AgvnMouseLook.LOCKED | AgvnMouseLook.TEXT);
        look.reported(AgvnMouseLook.TEXT); // the same caret, sent again a second later
        look.reported(0);
        assertEquals(java.util.Arrays.asList(AgvnMouseLook.TEXT, 0), told);
    }

    /**
     * A game that reads the mouse through the cursor puts it back only after a move: after a pause, the first touch
     * jumped to the finger and the camera turned toward it (08/11/2026). It holds the cursor until it shows it again.
     */
    @Test
    public void aGameThatPutsThePointerBackHoldsItUntilItsCursorShows() {
        boolean[] hidden = {true};
        long[] now = {1000};
        AgvnMouseLook look = new AgvnMouseLook(() -> hidden[0], () -> now[0]);
        for (int i = 0; i < AgvnMouseLook.WARPS; i++) {
            look.warped(640, 360);
            now[0] += 16;
        }
        assertTrue(look.locked());
        now[0] += 60_000; // the player stopped for a minute: no warp since
        assertTrue("still held: its cursor is hidden", look.locked());
        hidden[0] = false; // a menu, its cursor shows
        assertFalse(look.locked());
        hidden[0] = true;
        assertFalse("hidden again but no warp yet: a tap goes where the finger is", look.locked());
    }

    @Test
    public void whileTheCursorIsHeldADragClicksNothingAndATapClicks() {
        AgvnMouseLook look = new AgvnMouseLook(() -> true, () -> 0L);
        com.winlator.cmod.xserver.Pointer.Button left = com.winlator.cmod.xserver.Pointer.Button.BUTTON_LEFT;
        assertFalse("not held: the press goes now", look.pressLater(left));
        look.reported(AgvnMouseLook.LOCKED);
        assertTrue(look.pressLater(left));
        look.move(500, 300);
        look.move(505, 302); // a finger that shakes a little
        assertEquals("a tap", left, look.takePress());
        look.released();
        assertTrue(look.pressLater(left));
        look.move(500, 300);
        look.move(560, 280);
        look.move(620, 270);
        assertEquals("a drag turned the camera", null, look.takePress());
    }
}
