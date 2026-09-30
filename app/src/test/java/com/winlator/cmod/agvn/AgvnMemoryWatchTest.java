/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AgvnMemoryWatchTest {
    private static final AgvnMemoryRules.Reason NONE = AgvnMemoryRules.Reason.NONE;
    private static final long S = 1000, START = 1_000_000;

    @Test
    public void lowFreeRamWarnsAfterTwoSamplesThenWaits() {
        AgvnMemoryRules rules = new AgvnMemoryRules();
        long t = START + 10 * S;
        assertEquals(NONE, rules.feed(t, START, 1100, 3000, t));
        assertEquals(AgvnMemoryRules.Reason.LOW_FREE, rules.feed(t + 5 * S, START, 1100, 3000, t));
        assertEquals("cooldown", NONE, rules.feed(t + 10 * S, START, 1100, 3000, t));
        assertEquals("cooldown", NONE, rules.feed(t + 15 * S, START, 1100, 3000, t));
        assertEquals("under 600 MB breaks the cooldown", AgvnMemoryRules.Reason.LOW_FREE, rules.feed(t + 20 * S, START, 500, 3000, t));
    }

    @Test
    public void memoryClimbingWhileIdleIsALeak() {
        // the report: +288 MB every 16 s at a black screen, nobody touching anything
        AgvnMemoryRules rules = new AgvnMemoryRules();
        AgvnMemoryRules.Reason seen = NONE;
        long used = 500;
        for (long t = START; t <= START + 240 * S && seen == NONE; t += 5 * S) {
            used += 90;
            seen = rules.feed(t, START, 4000, used, START);
        }
        assertEquals(AgvnMemoryRules.Reason.GROWING, seen);
    }

    @Test
    public void loadingOrPlayingIsNotALeak() {
        AgvnMemoryRules loading = new AgvnMemoryRules();
        long used = 500;
        for (long t = START; t < START + 90 * S; t += 5 * S) {
            used += 200; // fast growth, but in the first 90 s
            assertEquals(NONE, loading.feed(t, START, 4000, used, START));
        }
        AgvnMemoryRules playing = new AgvnMemoryRules();
        for (long t = START + 100 * S; t < START + 300 * S; t += 5 * S) {
            used += 200; // fast growth while the player keeps touching the screen
            assertEquals(NONE, playing.feed(t, START, 4000, used, t - S));
        }
    }

    @Test
    public void readsProcFiles() {
        assertEquals(4_901_234, AgvnMemoryProbe.kb("MemTotal:       11000000 kB\nMemAvailable:    4901234 kB\n", "MemAvailable:"));
        assertEquals(-1, AgvnMemoryProbe.kb("MemTotal: 1 kB\n", "MemAvailable:"));
        assertArrayEquals(new long[]{302_125_056, 4711},
                AgvnMemoryProbe.dmabuf("pos:\t0\nflags:\t02000002\nmnt_id:\t13\nino:\t4711\nsize:\t302125056\ncount:\t2\nexp_name:\tqcom,system\n"));
        assertArrayEquals(new long[]{4096, -1}, AgvnMemoryProbe.dmabuf("size:\t4096\nexp_name:\tsystem\n"));
        assertNull(AgvnMemoryProbe.dmabuf("pos:\t0\nflags:\t02\n"));
        assertEquals("1,2 GB", AgvnMemoryWatch.gb(1229));
    }
}
