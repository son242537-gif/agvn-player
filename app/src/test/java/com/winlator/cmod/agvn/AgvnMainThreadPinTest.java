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

/** {@link AgvnCpuCores}, which {@link AgvnMainThreadPin} pins with. */
public class AgvnMainThreadPinTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void policy(File cpu, String name, String cpus, long khz) throws IOException {
        write(new File(cpu, "cpufreq/" + name + "/related_cpus"), cpus + "\n");
        write(new File(cpu, "cpufreq/" + name + "/cpuinfo_max_freq"), khz + "\n");
    }

    @Test
    public void theFastestClusterOfEachKindOfPhone() throws IOException {
        File elite = tmp.newFolder("elite"); // Snapdragon 8 Elite: 6 + 2
        policy(elite, "policy0", "0 1 2 3 4 5", 3532800);
        policy(elite, "policy6", "6 7", 4320000);
        assertEquals(0xC0, AgvnCpuCores.fastest(elite));

        File gen1 = tmp.newFolder("gen1"); // 4 + 3 + 1
        policy(gen1, "policy0", "0 1 2 3", 1785600);
        policy(gen1, "policy4", "4 5 6", 2496000);
        policy(gen1, "policy7", "7", 2995200);
        assertEquals(0x80, AgvnCpuCores.fastest(gen1));

        File same = tmp.newFolder("same"); // one kind of core: nothing faster to move to
        policy(same, "policy0", "0-3", 2000000);
        policy(same, "policy4", "4-7", 2000000);
        assertEquals(0, AgvnCpuCores.fastest(same));
        assertEquals(0, AgvnCpuCores.fastest(new File(same, "missing")));
    }

    @Test
    public void eachCoresFilesWhenThePoliciesCannotBeRead() throws IOException {
        File cpu = tmp.newFolder("cpu"); // no cpufreq/policy* directory to list
        for (int n = 0; n < 8; n++) write(new File(cpu, "cpu" + n + "/cpufreq/cpuinfo_max_freq"), n < 6 ? "3532800" : "4320000");
        assertEquals(0xC0, AgvnCpuCores.fastest(cpu));

        File capacity = tmp.newFolder("capacity"); // no cpufreq at all: the scheduler's capacity
        for (int n = 0; n < 8; n++) write(new File(capacity, "cpu" + n + "/cpu_capacity"), n == 7 ? "1024" : n >= 4 ? "870" : "325");
        assertEquals(0x80, AgvnCpuCores.fastest(capacity));
    }

    @Test
    public void theCoresAThreadMayUse() throws IOException {
        File proc = tmp.newFolder("proc");
        write(new File(proc, "3155/status"), "Name:\tWithTheDevilish\nCpus_allowed:\tff\nCpus_allowed_list:\t0-7\n");
        assertEquals(0xFF, AgvnCpuCores.allowed(proc, 3155));
        assertEquals(0, AgvnCpuCores.allowed(proc, 9999));
    }

    @Test
    public void whatTheLogsSayAboutCoresAndCpusets() throws IOException {
        File cpu = tmp.newFolder("sys");
        write(new File(cpu, "cpu6/online"), "1\n");
        write(new File(cpu, "cpu6/cpufreq/scaling_cur_freq"), "2803200\n");
        write(new File(cpu, "cpu6/cpufreq/cpuinfo_max_freq"), "4320000\n");
        write(new File(cpu, "cpu6/cpufreq/scaling_max_freq"), "3532800\n");
        write(new File(cpu, "cpu7/online"), "0\n");
        assertEquals("cpu6 on, 2803 of 4320 MHz (allowed 3532); cpu7 off, ? of ? MHz (allowed ?)",
                AgvnCpuCores.state(cpu, 0xC0));
        File proc = tmp.newFolder("proc2"), cpuset = tmp.newFolder("cpuset");
        write(new File(proc, "6861/cpuset"), "/top-app\n");
        write(new File(cpuset, "top-app/cpus"), "0-7\n");
        assertEquals("/top-app (cpus 0-7)", AgvnCpuCores.cpuset(proc, cpuset, 6861));
        assertEquals("?", AgvnCpuCores.cpuset(proc, cpuset, 1));
    }

    @Test
    public void cpuListsBothWays() {
        assertEquals(0xC0, AgvnCpuCores.mask("6 7"));
        assertEquals(0x4F, AgvnCpuCores.mask("0-3,6\n"));
        assertEquals(0, AgvnCpuCores.mask("six"));
        assertEquals(0, AgvnCpuCores.mask(null));
        assertEquals("6-7", AgvnCpuCores.list(0xC0));
        assertEquals("0-3,6", AgvnCpuCores.list(0x4F));
        assertEquals("0,2,4-5", AgvnCpuCores.list(0x35));
    }
}
