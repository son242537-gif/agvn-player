/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** When Wine last spoke of a movie, before the game's end (Demonion II, 10/11/2026). */
public class AgvnMovieEndTest {
    @Test
    public void winegstreamersLinesMarkAMovie() {
        AgvnMovieEnd.reset();
        assertEquals("none yet", -1, AgvnMovieEnd.msBefore(1000));
        AgvnMovieEnd.line("info:  DXVK: Using 8 compiler threads", 100);
        assertEquals(-1, AgvnMovieEnd.msBefore(1000));
        AgvnMovieEnd.line("0:00:00.019819480 22712 0xb40000773b2cc9e0 WARN                    WINE "
                + "wg_parser.c:1690:src_query_cb: Unhandled query type caps.", 28_000);
        AgvnMovieEnd.line("0:00:00.145314219 22712 0xb40000773b2cf500 WARN                    WINE "
                + "wg_parser.c:999:sink_event_cb: Ignoring \"stream-start\" event.", 30_024);
        AgvnMovieEnd.line("wine: setpriority 6 for pid -1 failed: 3", 30_500); // not a movie's
        assertEquals(1_776, AgvnMovieEnd.msBefore(31_800));
        assertEquals("Proton 10's source", 200, msAfter("0:00:02.164378100 22842 0xb400007c7b4a32e0 ERROR"
                + "                   WINE wg_source.c:449:sink_event_caps: Cannot decode caps audio/mpeg", 40_000, 40_200));
        assertEquals("no end yet", -1, AgvnMovieEnd.msBefore(0));
        assertEquals("a line after the end", -1, AgvnMovieEnd.msBefore(39_000));
        AgvnMovieEnd.reset();
        assertEquals("a new game", -1, AgvnMovieEnd.msBefore(41_000));
    }

    private static long msAfter(String line, long at, long end) {
        AgvnMovieEnd.line(line, at);
        return AgvnMovieEnd.msBefore(end);
    }
}
