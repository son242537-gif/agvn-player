/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AgvnMainThreadPinTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private void policy(File cpufreq, String name, String cpus, long khz) throws IOException {
        File dir = new File(cpufreq, name);
        dir.mkdirs();
        write(new File(dir, "related_cpus"), cpus + "\n");
        write(new File(dir, "cpuinfo_max_freq"), khz + "\n");
    }

    private static void write(File f, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }

    @Test
    public void theFastestClusterOfEachKindOfPhone() throws IOException {
        File elite = tmp.newFolder("elite"); // Snapdragon 8 Elite: 6 + 2
        policy(elite, "policy0", "0 1 2 3 4 5", 3532800);
        policy(elite, "policy6", "6 7", 4320000);
        assertEquals(0xC0, AgvnMainThreadPin.fastestMask(elite));

        File gen1 = tmp.newFolder("gen1"); // 4 + 3 + 1
        policy(gen1, "policy0", "0 1 2 3", 1785600);
        policy(gen1, "policy4", "4 5 6", 2496000);
        policy(gen1, "policy7", "7", 2995200);
        assertEquals(0x80, AgvnMainThreadPin.fastestMask(gen1));

        File same = tmp.newFolder("same"); // one kind of core: nothing faster to move to
        policy(same, "policy0", "0-3", 2000000);
        policy(same, "policy4", "4-7", 2000000);
        assertEquals(0, AgvnMainThreadPin.fastestMask(same));
        assertEquals(0, AgvnMainThreadPin.fastestMask(new File(same, "missing")));
    }

    @Test
    public void cpuListsBothWays() {
        assertEquals(0xC0, AgvnMainThreadPin.cpuMask("6 7"));
        assertEquals(0x4F, AgvnMainThreadPin.cpuMask("0-3,6\n"));
        assertEquals(0, AgvnMainThreadPin.cpuMask("six"));
        assertEquals(0, AgvnMainThreadPin.cpuMask(null));
        assertEquals("6-7", AgvnMainThreadPin.list(0xC0));
        assertEquals("0-3,6", AgvnMainThreadPin.list(0x4F));
        assertEquals("0,2,4-5", AgvnMainThreadPin.list(0x35));
    }
}
