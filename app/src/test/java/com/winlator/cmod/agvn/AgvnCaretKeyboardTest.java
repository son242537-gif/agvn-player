/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgvnCaretKeyboardTest {
    @Test
    public void aFieldBringsTheKeyboardUpUnlessItIsUp() {
        assertTrue(AgvnCaretKeyboard.raises(AgvnMouseLook.TEXT, false, 0));
        assertTrue("a field the game draws", AgvnCaretKeyboard.raises(AgvnMouseLook.TYPING, false, 0));
        assertFalse(AgvnCaretKeyboard.raises(AgvnMouseLook.TYPING, true, 0));
        assertFalse("no field", AgvnCaretKeyboard.raises(0, false, 0));
    }

    @Test
    public void theGamesOwnFieldsStopGettingItOnceItDidNotHelp() {
        assertTrue(AgvnCaretKeyboard.raises(AgvnMouseLook.TYPING, false, AgvnCaretKeyboard.UNUSED - 1));
        assertFalse(AgvnCaretKeyboard.raises(AgvnMouseLook.TYPING, false, AgvnCaretKeyboard.UNUSED));
        assertTrue("a caret of Windows is a field for sure",
                AgvnCaretKeyboard.raises(AgvnMouseLook.TEXT | AgvnMouseLook.TYPING, false, AgvnCaretKeyboard.UNUSED));
    }

    @Test
    public void theTypingBitReachesTheKeyboardAsTheCaretDoes() {
        AgvnMouseLook look = new AgvnMouseLook(() -> false, () -> 0L);
        List<Integer> told = new ArrayList<>();
        look.setOnText(told::add);
        look.reported(AgvnMouseLook.TYPING);
        look.reported(AgvnMouseLook.TYPING); // sent again a second later
        look.reported(AgvnMouseLook.TYPING | AgvnMouseLook.LOCKED);
        look.reported(0);
        assertEquals(Arrays.asList(AgvnMouseLook.TYPING, 0), told);
    }
}
