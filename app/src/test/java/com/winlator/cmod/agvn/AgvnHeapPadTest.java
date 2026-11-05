/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** SRPG Studio's scripts read into one zero byte too few (AgvnHeapPad, 06/11/2026). */
public class AgvnHeapPadTest {
    @Test
    public void anSrpgStudioGameStartsWithItsHeapPadded() {
        assertEquals("agvn-winhandler.exe /heappad", AgvnHeapPad.starter(AgvnRawMouse.EXE, "SRPG_STUDIO"));
        String note = AgvnHeapPad.takeNote();
        assertTrue(note, note.startsWith("Game SRPG Studio: mở bằng agvn-winhandler.exe /heappad"));
        assertNull("once", AgvnHeapPad.takeNote());
    }

    @Test
    public void anyOtherGameOrWinlatorsStarterStartsAsBefore() {
        assertEquals(AgvnRawMouse.EXE, AgvnHeapPad.starter(AgvnRawMouse.EXE, "UNITY"));
        assertEquals(AgvnRawMouse.EXE, AgvnHeapPad.starter(AgvnRawMouse.EXE, ""));
        assertEquals("Winlator's winhandler.exe knows no /heappad", AgvnRawMouse.WINLATOR_EXE,
                AgvnHeapPad.starter(AgvnRawMouse.WINLATOR_EXE, "SRPG_STUDIO"));
        assertNull(AgvnHeapPad.takeNote());
    }
}
