/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnHeatRulesTest {
    private static final AgvnHeatRules.Reason NONE = AgvnHeatRules.Reason.NONE;
    private static final float NAN = Float.NaN;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void aCappedCpuWarnsAfterTwoSamplesThenWaits() {
        AgvnHeatRules rules = new AgvnHeatRules();
        long t = 1_000_000;
        assertEquals(NONE, rules.feed(t, 0.71f, NAN));
        assertEquals(AgvnHeatRules.Reason.CPU_CAPPED, rules.feed(t + 5_000, 0.71f, NAN));
        assertEquals(NONE, rules.feed(t + 10_000, 0.71f, NAN));
        assertEquals("5 minutes of quiet", NONE, rules.feed(t + 15_000, 0.71f, NAN));
        assertEquals("still capped after them", AgvnHeatRules.Reason.CPU_CAPPED, rules.feed(t + 400_000, 0.71f, NAN));
    }

    @Test
    public void fullSpeedOrOneSlowSampleIsFine() {
        AgvnHeatRules rules = new AgvnHeatRules();
        assertEquals(NONE, rules.feed(0, 0.70f, NAN));
        assertEquals("back to full speed resets the count", NONE, rules.feed(5_000, 1f, NAN));
        assertEquals(NONE, rules.feed(10_000, 0.70f, NAN));
        assertEquals(NONE, rules.feed(15_000, 0.90f, 0.5f));
    }

    @Test
    public void headroomNearThrottlingWarnsAndMissingReadingsDoNotReset() {
        AgvnHeatRules rules = new AgvnHeatRules();
        assertEquals(NONE, rules.feed(0, NAN, 0.9f));
        assertEquals("Android asked too often: NaN keeps the count", NONE, rules.feed(5_000, NAN, NAN));
        assertEquals(AgvnHeatRules.Reason.NEAR_THROTTLING, rules.feed(10_000, NAN, 0.88f));
    }

    @Test
    public void readsTheFastestClusterCap() throws Exception {
        File cpufreq = tmp.newFolder("cpufreq");
        policy(cpufreq, "policy0", 2_150_000, 2_150_000); // small cores, not capped
        policy(cpufreq, "policy2", 3_532_800, 2_745_600); // POCO F8 Pro performance cores
        policy(cpufreq, "policy7", 4_320_000, 3_072_000); // prime core
        assertEquals(3_072_000f / 4_320_000f, AgvnHeatRules.fastestCap(cpufreq), 1e-6);
        assertTrue(Float.isNaN(AgvnHeatRules.fastestCap(new File(tmp.getRoot(), "none"))));
        File unreadable = tmp.newFolder("empty");
        new File(unreadable, "policy0").mkdirs();
        assertTrue(Float.isNaN(AgvnHeatRules.fastestCap(unreadable)));
    }

    private static void policy(File dir, String name, long hardwareKhz, long allowedKhz) throws Exception {
        File p = new File(dir, name);
        p.mkdirs();
        Files.write(new File(p, "cpuinfo_max_freq").toPath(), (hardwareKhz + "\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(new File(p, "scaling_max_freq").toPath(), (allowedKhz + "\n").getBytes(StandardCharsets.US_ASCII));
    }
}
