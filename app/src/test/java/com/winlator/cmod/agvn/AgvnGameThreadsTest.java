/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgvnGameThreadsTest {
    private static final int UID = 10234, SELF = 900;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A /proc stat line with utime and stime (fields 14, 15) and the core (field 39). */
    private static String stat(int id, String name, long utime, long stime, int core) {
        StringBuilder s = new StringBuilder().append(id).append(" (").append(name).append(") S");
        for (int field = 4; field <= 52; field++) {
            s.append(' ').append(field == 14 ? utime : field == 15 ? stime : field == 39 ? core : 0);
        }
        return s.append('\n').toString();
    }

    private static void write(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void process(File proc, int pid, int uid, String name, long ticks) throws IOException {
        write(new File(proc, pid + "/status"), "Name:\t" + name + "\nUid:\t" + uid + "\t" + uid + "\t" + uid + "\t" + uid + "\n");
        write(new File(proc, pid + "/stat"), stat(pid, name, ticks, 0, 0));
    }

    private void thread(File proc, int pid, int tid, String name, long ticks, int core) throws IOException {
        write(new File(proc, pid + "/task/" + tid + "/stat"), stat(tid, name, ticks, 0, core));
    }

    @Test
    public void readsStatLinesWhateverTheThreadIsCalled() {
        AgvnGameThreads.Sample s = AgvnGameThreads.parse(stat(4321, "Game (x86) v1.2", 250, 30, 6));
        assertEquals(4321, s.id);
        assertEquals("Game (x86) v1.2", s.name);
        assertEquals(280, s.ticks);
        assertEquals(6, s.core);
        assertNull(AgvnGameThreads.parse("garbage"));
        assertNull(AgvnGameThreads.parse("12 (x) S 1 2"));
        assertNull(AgvnGameThreads.parse(null));
    }

    @Test
    public void theBusiestThreadOverASpan() throws IOException {
        File proc = tmp.newFolder("proc");
        thread(proc, 500, 500, "Game.exe", 1000, 2);
        thread(proc, 500, 501, "dxvk-cs", 400, 1);
        AgvnGameThreads.Snapshot from = AgvnGameThreads.read(proc, 500, 0);
        thread(proc, 500, 500, "Game.exe", 1190, 3); // 190 ticks in 2 s at 100 ticks/s: 95% of a core
        thread(proc, 500, 501, "dxvk-cs", 420, 1);
        thread(proc, 500, 502, "loader", 30, 0); // started meanwhile
        List<AgvnGameThreads.Busy> busy = AgvnGameThreads.busiest(from, AgvnGameThreads.read(proc, 500, 2_000_000_000L), 100);
        assertEquals(3, busy.size());
        assertEquals("Game.exe", busy.get(0).name);
        assertEquals(95, busy.get(0).percent);
        assertEquals(3, busy.get(0).core);
        assertEquals(15, busy.get(1).percent);
        assertEquals(10, busy.get(2).percent);
        assertEquals(120, AgvnGameThreads.total(busy));
        assertArrayEquals("from 15% of a core, at most 2", new int[]{500, 502}, AgvnGameThreads.top(busy, 15, 2));
        assertArrayEquals(new int[]{500}, AgvnGameThreads.top(busy, 30, 3));
        assertNull("a process that is gone", AgvnGameThreads.read(proc, 777, 0));
    }

    @Test
    public void theGameIsTheWindowsProcessOrElseThisUsersBusiestOne() throws IOException {
        File proc = tmp.newFolder("proc");
        process(proc, SELF, UID, "com.agvn.player", 90_000); // this app: never the game
        process(proc, 600, UID, "wineserver", 50_000);
        process(proc, 601, UID, "explorer.exe", 200);
        process(proc, 602, UID, "Game.exe", 3_000);
        process(proc, 300, 0, "system", 99_999); // not ours
        Map<Integer, Long> seen = new HashMap<>();
        assertEquals("the window's own process", 601, AgvnGameThreads.findGame(proc, 601, UID, SELF, seen));
        assertEquals("a window pid that is not ours", 602, AgvnGameThreads.findGame(proc, 300, UID, SELF, seen));
        process(proc, 601, UID, "explorer.exe", 2_000); // grew 1800 since the last call, the game only 100
        process(proc, 602, UID, "Game.exe", 3_100);
        assertEquals("the most CPU since the last call", 601, AgvnGameThreads.findGame(proc, 0, UID, SELF, seen));
        assertEquals(0, AgvnGameThreads.findGame(new File(proc, "none"), 0, UID, SELF, seen));
    }

    @Test
    public void cpuTimeFromSchedstatOrTicks() throws IOException {
        File proc = tmp.newFolder("proc");
        thread(proc, 500, 501, "Game.exe", 250, 0);
        assertEquals("ticks at 100 per second", 2_500_000_000L, AgvnGameThreads.cpuNs(proc, 500, 501, 100));
        write(new File(proc, "500/task/501/schedstat"), "2512345678 100 42\n");
        assertEquals(2_512_345_678L, AgvnGameThreads.cpuNs(proc, 500, 501, 100));
        assertEquals(-1, AgvnGameThreads.cpuNs(proc, 500, 999, 100));
        assertFalse("no status: not a process of ours", AgvnGameThreads.ownedBy(proc, 500, UID));
    }
}
