/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.inputcontrols.Binding;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

/** Key list of the in-game binding picker and the editor's resize steps. */
public class AgvnBindingLabelsTest {
    @Test
    public void buttonListHasEveryKeyAndMouseButtonOnce() {
        List<Binding> list = AgvnBindingLabels.buttonBindings();
        assertEquals(list.size(), new HashSet<>(list).size());
        for (Binding b : list) {
            assertNotEquals(Binding.NONE, b);
            assertFalse(b.name(), b.isGamepad());
            assertFalse(b.name(), b.isMouseMove());
        }
        for (Binding b : Binding.values()) {
            boolean key = b != Binding.NONE && b.isKeyboard();
            boolean mouseButton = b.isMouse() && !b.isMouseMove();
            assertEquals(b.name(), key || mouseButton, list.contains(b));
        }
    }

    @Test
    public void commonKeysComeFirst() {
        List<Binding> list = AgvnBindingLabels.buttonBindings();
        assertEquals(Binding.MOUSE_LEFT_BUTTON, list.get(0));
        assertTrue(list.indexOf(Binding.MOUSE_SCROLL_DOWN) < list.indexOf(Binding.KEY_ENTER));
        assertTrue(list.indexOf(Binding.KEY_BKSP) < list.indexOf(Binding.KEY_UP));
        assertTrue(list.indexOf(Binding.KEY_D) < list.indexOf(Binding.KEY_B)); // W A S D before the alphabet
        assertTrue(list.indexOf(Binding.KEY_Z) < list.indexOf(Binding.KEY_0));
        assertTrue(list.indexOf(Binding.KEY_9) < list.indexOf(Binding.KEY_F1));
        assertTrue(list.indexOf(Binding.KEY_F12) < list.indexOf(Binding.KEY_DEL));
        for (char c = 'A'; c <= 'Z'; c++) assertTrue(list.contains(Binding.valueOf("KEY_" + c)));
        for (int i = 1; i <= 12; i++) assertTrue(list.contains(Binding.valueOf("KEY_F" + i)));
    }

    @Test
    public void namesAreShortAndVietnameseWhereNeeded() {
        assertNotEquals(0, AgvnBindingLabels.nameRes(Binding.MOUSE_LEFT_BUTTON));
        assertNotEquals(0, AgvnBindingLabels.nameRes(Binding.MOUSE_SCROLL_UP));
        assertNotEquals(0, AgvnBindingLabels.nameRes(Binding.KEY_UP));
        assertNotEquals(0, AgvnBindingLabels.nameRes(Binding.KEY_BKSP));
        assertEquals(0, AgvnBindingLabels.nameRes(Binding.KEY_A));
        assertEquals("A", AgvnBindingLabels.keyName(Binding.KEY_A));
        assertEquals("7", AgvnBindingLabels.keyName(Binding.KEY_7));
        assertEquals("F11", AgvnBindingLabels.keyName(Binding.KEY_F11));
        assertEquals("Shift", AgvnBindingLabels.keyName(Binding.KEY_SHIFT_L));
        assertEquals("Esc", AgvnBindingLabels.keyName(Binding.KEY_ESC));
        assertEquals("NP5", AgvnBindingLabels.keyName(Binding.KEY_KP_5));
        assertEquals("PgUp", AgvnBindingLabels.keyName(Binding.KEY_PG_UP));
        assertEquals("[", AgvnBindingLabels.keyName(Binding.KEY_BRACKET_LEFT));
        for (Binding b : AgvnBindingLabels.buttonBindings()) {
            if (AgvnBindingLabels.nameRes(b) == 0) assertTrue(b.name(), AgvnBindingLabels.keyName(b).length() <= 8);
        }
    }

    @Test
    public void directionSetsAreUpRightDownLeft() {
        assertEquals(AgvnBindingLabels.DIRECTION_SETS.length, AgvnBindingLabels.DIRECTION_SET_LABELS.length);
        for (Binding[] set : AgvnBindingLabels.DIRECTION_SETS) assertEquals(4, set.length);
        assertEquals(Binding.KEY_UP, AgvnBindingLabels.DIRECTION_SETS[0][0]);
        assertEquals(Binding.KEY_RIGHT, AgvnBindingLabels.DIRECTION_SETS[0][1]);
        assertEquals(Binding.KEY_W, AgvnBindingLabels.DIRECTION_SETS[1][0]);
        assertEquals(Binding.KEY_D, AgvnBindingLabels.DIRECTION_SETS[1][1]);
        assertEquals(Binding.KEY_A, AgvnBindingLabels.DIRECTION_SETS[1][3]);
        assertTrue(AgvnBindingLabels.DIRECTION_SETS[2][2].isMouseMove());
    }

    @Test
    public void resizeStepsByATenthWithinLimits() {
        assertEquals(0.8f, AgvnControlsEditor.nextScale(0.7f, 1), 1e-6);
        assertEquals(0.6f, AgvnControlsEditor.nextScale(0.7f, -1), 1e-6);
        assertEquals(0.5f, AgvnControlsEditor.nextScale(0.5f, -1), 1e-6);
        assertEquals(0.5f, AgvnControlsEditor.nextScale(0.55f, -1), 1e-6);
        assertEquals(2.5f, AgvnControlsEditor.nextScale(2.5f, 1), 1e-6);
        assertEquals(2.5f, AgvnControlsEditor.nextScale(2.45f, 1), 1e-6);
        assertEquals(1.0f, AgvnControlsEditor.nextScale(0.9f, 1), 1e-6);
        float s = 0.5f;
        for (int i = 0; i < 30; i++) s = AgvnControlsEditor.nextScale(s, 1);
        assertEquals(2.5f, s, 1e-6);
    }
}
