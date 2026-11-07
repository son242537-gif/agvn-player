/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The game's exit code, as su-kien.txt says it and as the doctor reads it. */
public class AgvnGameEndTest {
    @Test
    public void crashCodesAreTheWindowsErrors() {
        assertTrue(AgvnGameEnd.crash(0xC0000005));
        assertTrue(AgvnGameEnd.crash(0xC0000409));
        assertFalse("told to end", AgvnGameEnd.crash(AgvnGameEnd.CONTROL_C));
        assertFalse(AgvnGameEnd.crash(0));
        assertFalse(AgvnGameEnd.crash(1));
        assertFalse("-1 from a program that could not start .NET", AgvnGameEnd.crash(0xFFFFFFFF));
    }

    @Test
    public void theDoctorGetsTheCrashCodeOfThisSession() {
        AgvnSessionTrack.start(1000L);
        assertEquals(0, AgvnGameEnd.crashCode());
        AgvnGameEnd.ended(0xC0000005);
        assertEquals(0xC0000005, AgvnGameEnd.crashCode());
        AgvnSessionTrack.start(2000L);
        assertEquals("a new session forgets it", 0, AgvnGameEnd.crashCode());
        AgvnGameEnd.ended(0);
        assertEquals(0, AgvnGameEnd.crashCode());
        assertEquals("0xC0000005 (lỗi truy cập bộ nhớ)", AgvnGameEnd.code(0xC0000005));
        assertEquals("exit(-1) is no crash", "0xFFFFFFFF", AgvnGameEnd.code(-1));
        assertEquals("0xC0000017 (game văng)", AgvnGameEnd.code(0xC0000017));
    }
}
