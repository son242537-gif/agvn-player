/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;

/** The report of 23/11/2026 (Dâm Từ Phủ): ▲ held first, a finger on the game must still reach the game. */
public class AgvnTouchGroupsTest {
    private static final int KEYS = AgvnTouchGroups.KEYS, GAME = AgvnTouchGroups.GAME;

    @Test
    public void aKeyHeldFirstLeavesTheNextFingerToTheGame() {
        AgvnTouchGroups g = new AgvnTouchGroups();
        g.down(0, KEYS); // ▲
        check(g.part(KEYS, MotionEvent.ACTION_DOWN, 0, new int[] {0}), MotionEvent.ACTION_DOWN, 0, 0);
        assertNull(g.part(GAME, MotionEvent.ACTION_DOWN, 0, new int[] {0}));
        g.down(1, GAME); // the finger that turns the view
        assertNull("the keys hear nothing of it", g.part(KEYS, MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}));
        check(g.part(GAME, MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}), MotionEvent.ACTION_DOWN, 0, 1);
        check(g.part(GAME, MotionEvent.ACTION_MOVE, 0, new int[] {0, 1}), MotionEvent.ACTION_MOVE, 0, 1);
        check(g.part(KEYS, MotionEvent.ACTION_MOVE, 0, new int[] {0, 1}), MotionEvent.ACTION_MOVE, 0, 0);
    }

    @Test
    public void theTurningFingerLiftedAndPutBackStillReachesTheGame() {
        AgvnTouchGroups g = new AgvnTouchGroups();
        g.down(0, KEYS);
        g.down(1, GAME);
        check(g.part(GAME, MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1}), MotionEvent.ACTION_UP, 0, 1);
        g.up(1);
        assertEquals(0, g.count(GAME));
        assertNull(g.part(GAME, MotionEvent.ACTION_MOVE, 0, new int[] {0}));
        g.down(1, GAME); // down again, ▲ still held
        check(g.part(GAME, MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}), MotionEvent.ACTION_DOWN, 0, 1);
    }

    @Test
    public void twoKeysAtOnceAreTwoFingersOfTheKeys() {
        AgvnTouchGroups g = new AgvnTouchGroups();
        g.down(0, GAME); // looking round first
        g.down(1, KEYS); // ▲
        g.down(2, KEYS); // Shift
        AgvnTouchGroups.Part p = g.part(KEYS, MotionEvent.ACTION_POINTER_DOWN, 2, new int[] {0, 1, 2});
        check(p, MotionEvent.ACTION_POINTER_DOWN, 1, 1, 2);
        p = g.part(KEYS, MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1, 2});
        check(p, MotionEvent.ACTION_POINTER_UP, 0, 1, 2);
        g.up(1);
        check(g.part(KEYS, MotionEvent.ACTION_POINTER_UP, 2, new int[] {0, 2}), MotionEvent.ACTION_UP, 0, 1);
        assertNull(g.part(GAME, MotionEvent.ACTION_POINTER_UP, 2, new int[] {0, 2}));
    }

    @Test
    public void aCancelReachesBothSides() {
        AgvnTouchGroups g = new AgvnTouchGroups();
        g.down(0, KEYS);
        g.down(1, GAME);
        check(g.part(KEYS, MotionEvent.ACTION_CANCEL, 0, new int[] {0, 1}), MotionEvent.ACTION_CANCEL, 0, 0);
        check(g.part(GAME, MotionEvent.ACTION_CANCEL, 0, new int[] {0, 1}), MotionEvent.ACTION_CANCEL, 0, 1);
        g.clear();
        assertEquals(0, g.count(KEYS) + g.count(GAME));
    }

    @Test
    public void aFingerKeepsItsSideAndOddIdsAreLeftOut() {
        AgvnTouchGroups g = new AgvnTouchGroups();
        g.down(3, GAME);
        g.down(3, KEYS);
        assertEquals(1, g.count(KEYS));
        assertEquals(0, g.count(GAME));
        g.down(40, KEYS);
        g.down(-1, GAME);
        assertEquals(1, g.count(KEYS));
        check(g.part(KEYS, MotionEvent.ACTION_MOVE, 3, new int[] {40, 3}), MotionEvent.ACTION_MOVE, 0, 1);
        assertTrue(AgvnTouchGroups.isTouch(MotionEvent.ACTION_POINTER_UP));
        assertTrue(!AgvnTouchGroups.isTouch(MotionEvent.ACTION_OUTSIDE));
    }

    /** {@code p} is {@code action} about its finger {@code actionIndex}, with the event's fingers {@code indices}. */
    private static void check(AgvnTouchGroups.Part p, int action, int actionIndex, int... indices) {
        assertEquals(action, p.action);
        assertEquals(actionIndex, p.actionIndex);
        assertArrayEquals(indices, p.indices);
    }
}
