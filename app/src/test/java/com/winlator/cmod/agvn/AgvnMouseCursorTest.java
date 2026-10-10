/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.InputDevice;
import android.view.MotionEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A Windows game's mouse pointer in touch play (the maintainer's report, 10/10/2026). */
public class AgvnMouseCursorTest {
    @Test
    public void aMouseShowsThePointerInTouchPlayOnce() {
        List<Boolean> shown = new ArrayList<>();
        AgvnMouseCursor cursor = new AgvnMouseCursor(true, shown::add);
        cursor.mouse();
        cursor.mouse(); // every move of a held mouse: shown once
        assertEquals(Collections.singletonList(true), shown);
        int screen = InputDevice.SOURCE_TOUCHSCREEN, finger = MotionEvent.TOOL_TYPE_FINGER;
        cursor.on(screen, finger, MotionEvent.ACTION_DOWN); // back to touch: hidden
        cursor.on(screen, finger, MotionEvent.ACTION_MOVE);
        cursor.on(InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.ACTION_HOVER_MOVE); // mouse again
        cursor.on(screen, finger, MotionEvent.ACTION_DOWN);
        cursor.on(InputDevice.SOURCE_MOUSE, finger, MotionEvent.ACTION_DOWN); // a touchpad's tap shows it, not hides it
        assertEquals(Arrays.asList(true, false, true, false, true), shown);

        List<Boolean> untouched = new ArrayList<>();
        AgvnMouseCursor shownAnyway = new AgvnMouseCursor(false, untouched::add); // the game shows its pointer itself
        shownAnyway.mouse();
        shownAnyway.on(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN);
        assertEquals(Collections.emptyList(), untouched);
    }

    @Test
    public void miceAndFingersAreToldApart() {
        assertTrue(AgvnMouseCursor.fromMouse(InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE));
        assertTrue("a mouse that names no tool",
                AgvnMouseCursor.fromMouse(InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_UNKNOWN));
        assertTrue("a click on the screen sent as a touch", AgvnMouseCursor.fromMouse(InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.TOOL_TYPE_MOUSE));
        assertTrue("a touchpad on newer Android", AgvnMouseCursor.fromMouse(InputDevice.SOURCE_MOUSE,
                MotionEvent.TOOL_TYPE_FINGER));
        assertFalse(AgvnMouseCursor.fromMouse(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.TOOL_TYPE_FINGER));
        assertFalse(AgvnMouseCursor.fromMouse(InputDevice.SOURCE_STYLUS, MotionEvent.TOOL_TYPE_STYLUS));
        assertFalse(AgvnMouseCursor.fromMouse(InputDevice.SOURCE_JOYSTICK, MotionEvent.TOOL_TYPE_UNKNOWN));
        assertTrue(AgvnMouseCursor.fingerDown(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER));
        assertFalse(AgvnMouseCursor.fingerDown(MotionEvent.ACTION_MOVE, MotionEvent.TOOL_TYPE_FINGER));
        assertFalse(AgvnMouseCursor.fingerDown(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_MOUSE));
    }
}
