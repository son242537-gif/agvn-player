/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The pieces of the slow-start line: the log's last line, Ren'Py's "ready", Wine's CPU use, the clock. */
public class AgvnStartupProgressTest {
    @Test
    public void showsTheLastLineWithText() {
        String tail = "Loading script took 36.71s\r\nRunning init code\n    - Init at game/ch/bree/images.rpy:12\n\n";
        assertEquals("- Init at game/ch/bree/images.rpy:12", AgvnStartupProgress.lastLine(tail));
        assertEquals("", AgvnStartupProgress.lastLine("\n \n"));
        String longLine = new String(new char[200]).replace('\0', 'x');
        assertEquals(AgvnStartupProgress.LINE_CHARS + 1, AgvnStartupProgress.lastLine(longLine).length());
    }

    @Test
    public void renpySaysWhenItsFirstScreenIsUp() {
        assertFalse(AgvnStartupProgress.ready("Running init code took 520.33s\n"));
        assertTrue(AgvnStartupProgress.ready("Interface start took 0.31s\n")); // Ren'Py 6 and 7
        assertTrue(AgvnStartupProgress.ready("Interface start took 312 ms\nTotal time until interface ready: 530.1s.\n")); // Ren'Py 8
    }

    @Test
    public void cpuTicksComeFromFields14And15() {
        String stat = "4321 (Lo Se Sb.exe) R 1 4321 4321 0 -1 4194560 120 0 0 0 5400 600 0 0 20 0 9 0 100 2000000 30000";
        assertEquals(6000, AgvnStartupProgress.cpuTicks(stat));
        assertEquals(1.0, AgvnStartupProgress.share(6000, 60_000, 100), 1e-9); // a core busy for a minute
        assertTrue(AgvnStartupProgress.share(200, 60_000, 100) < 0.05); // 2 s in a minute: waiting
    }

    @Test
    public void aRedrawRightAfterATapMeansTheGameIsUp() {
        long start = 10_000;
        assertTrue(AgvnStartupProgress.answers(20_000, 20_400, start)); // tapped, the game redrew 0.4 s later
        assertFalse(AgvnStartupProgress.answers(20_000, 22_000, start)); // a redraw long after the tap
        assertFalse(AgvnStartupProgress.answers(0, 20_400, start)); // nobody has touched the game yet
        assertFalse(AgvnStartupProgress.answers(9_000, 9_500, start)); // a tap before this start
    }

    @Test
    public void clockReadsMinutesAndSeconds() {
        assertEquals("4:12", AgvnStartupProgress.clock(252_400));
        assertEquals("0:09", AgvnStartupProgress.clock(9_999));
    }

    @Test
    public void aSlowStartIsKeptForTheNextOne() {
        assertEquals(98_000, AgvnStartTimes.kept(98_000)); // "lần trước 1:38"
        assertEquals("a start that showed no line clears it", 0, AgvnStartTimes.kept(AgvnStartupProgress.QUIET_MS - 1));
    }
}
