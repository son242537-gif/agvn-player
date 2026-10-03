/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A slow game while it plays, and whether it drew its own window at all. */
public class AgvnSlowWatchTest {
    private static List<AgvnSlowWatch.Sample> steady(float fps, int gpu, double cpu) {
        List<AgvnSlowWatch.Sample> window = new ArrayList<>();
        for (int i = 0; i < AgvnSlowWatch.WINDOW; i++) window.add(new AgvnSlowWatch.Sample(fps, gpu, cpu));
        return window;
    }

    @Test
    public void whatIsBusyWhenTheGameIsSlow() {
        assertEquals("slow-gpu", AgvnSlowWatch.verdict(steady(16.3f, 100, 0.4), 24, AgvnSlowWatch.SLOW_FPS)); // the Adreno 620 title screen
        assertEquals("slow-cpu", AgvnSlowWatch.verdict(steady(14, -1, 0.97), 0, AgvnSlowWatch.SLOW_FPS));
        assertEquals("slow-cpu", AgvnSlowWatch.verdict(steady(14, 40, 0.97), 0, AgvnSlowWatch.SLOW_FPS));
        assertEquals("slow", AgvnSlowWatch.verdict(steady(14, -1, 0.5), 0, AgvnSlowWatch.SLOW_FPS)); // the CPU is idle: likely the GPU
        assertEquals("slow-unknown", AgvnSlowWatch.verdict(steady(14, -1, -1), 0, AgvnSlowWatch.SLOW_FPS)); // an HTML page
        assertNull("GPU idle, CPU idle: the game holds its own pace", AgvnSlowWatch.verdict(steady(14, 30, 0.5), 0, AgvnSlowWatch.SLOW_FPS));
        assertNull("smooth", AgvnSlowWatch.verdict(steady(30, 100, 0.5), 0, AgvnSlowWatch.SLOW_FPS));
        assertNull("the player's own cap", AgvnSlowWatch.verdict(steady(14.5f, 100, 0.5), 15, AgvnSlowWatch.SLOW_FPS));
        assertNull("not a minute yet", AgvnSlowWatch.verdict(steady(14, 100, 0.5).subList(0, 6), 0, AgvnSlowWatch.SLOW_FPS));
        List<AgvnSlowWatch.Sample> still = steady(14, 100, 0.5);
        still.set(3, new AgvnSlowWatch.Sample(0, 5, 0.01)); // a visual novel waiting for a tap
        assertNull(AgvnSlowWatch.verdict(still, 0, AgvnSlowWatch.SLOW_FPS));
        assertEquals(-1, AgvnSlowWatch.median(steady(14, -1, -1), 'g'), 0);
        assertEquals(16.3, AgvnSlowWatch.median(steady(16.3f, 100, 0.4), 'f'), 0.01);
    }

    @Test
    public void gpuLoadFiles() {
        assertEquals(45, AgvnGpuLoad.parse("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "45 %\n"));
        assertEquals(26, AgvnGpuLoad.parse("/sys/class/kgsl/kgsl-3d0/gpubusy", "  123   456\n"));
        assertEquals(-1, AgvnGpuLoad.parse("/sys/class/kgsl/kgsl-3d0/gpubusy", "0 0"));
        assertEquals(37, AgvnGpuLoad.parse("/sys/class/misc/mali0/device/utilization", "37"));
        assertEquals(100, AgvnGpuLoad.parse("/sys/module/ged/parameters/gpu_loading", "250"));
        assertEquals(37, AgvnGpuLoad.parse("/sys/class/devfreq/gpu/load", "37@585000000Hz"));
        assertEquals(64, AgvnGpuLoad.parse("/proc/mtk_mali/utilization", "ACTIVE=64 IDLE=36 "));
        assertEquals(-1, AgvnGpuLoad.parse("/sys/kernel/gpu/gpu_busy", "n/a"));
        assertEquals(-1, AgvnGpuLoad.parse("/sys/kernel/gpu/gpu_busy", null));
        // Mali's gpuinfo counts GPU milliseconds: two readings 1 s apart with 400 ms of GPU time is 40%
        AgvnGpuLoad mali = new AgvnGpuLoad();
        assertEquals(-1, mali.sample("/sys/class/misc/mali0/device/gpuinfo", "Mali-G57 1000", 5_000));
        assertEquals(40, mali.sample("/sys/class/misc/mali0/device/gpuinfo", "Mali-G57 1400", 6_000));
        assertTrue(AgvnGpuLoad.looksLikeGpu("/sys/class/devfreq/13000000.mali"));
        assertTrue(AgvnGpuLoad.looksLikeGpu("/sys/class/devfreq/3d00000.qcom,kgsl-3d0"));
        assertFalse(AgvnGpuLoad.looksLikeGpu("/sys/class/devfreq/ddr"));
    }

    @Test
    public void lightGamesAgainstTheirOwnFrameRate() {
        // RPG Maker XP runs 40 frames a second: 28 is slow there, a Windows game at 28 is not
        assertEquals("slow-cpu", AgvnSlowWatch.verdict(steady(28, -1, 0.95), 0, 40 * AgvnLightSlow.SLOW_SHARE));
        assertNull(AgvnSlowWatch.verdict(steady(28, -1, 0.95), 0, AgvnSlowWatch.SLOW_FPS));
        // Ren'Py has no frame rate: only a thread busy all the time counts
        assertEquals("slow-cpu", AgvnSlowWatch.verdict(steady(-1, -1, 0.97), 0, AgvnSlowWatch.SLOW_FPS));
        assertNull(AgvnSlowWatch.verdict(steady(-1, 90, 0.6), 0, AgvnSlowWatch.SLOW_FPS));
    }

    @Test
    public void theBarSaysTheFrameRateOnlyWhenThereIsOne() throws Exception {
        if (AgvnDoctorTest.catalog == null) AgvnDoctorTest.load();
        AgvnProblemCatalog.Finding renpy = AgvnDoctorTest.catalog.finding("slow-cpu", AgvnSlowWatch.params(steady(-1, -1, 0.97)));
        assertEquals("Game đang chạy chậm", renpy.title());
        assertTrue(renpy.cause(), renpy.cause().contains("97%"));
        AgvnProblemCatalog.Finding windows = AgvnDoctorTest.catalog.finding("slow-gpu", AgvnSlowWatch.params(steady(16.3f, 100, 0.4)));
        assertEquals("Game đang chạy chậm (16 FPS)", windows.title());
    }

    @Test
    public void busiestThreadInCores() {
        Map<String, Long> before = new HashMap<>(), after = new HashMap<>();
        before.put("100/100", 1000L);
        before.put("100/101", 50L);
        after.put("100/100", 1450L); // 450 ticks in 5 s at 100 ticks/s: 0.9 of a core
        after.put("100/101", 100L);
        after.put("100/102", 999L); // a new thread: no earlier reading, not counted
        assertEquals(0.9, AgvnLoadProbe.busiest(before, after, 5000, 100), 1e-9);
        assertEquals(-1, AgvnLoadProbe.busiest(new HashMap<>(), after, 5000, 100), 0);
    }

    @Test
    public void onlyTheGamesOwnWindowCounts() {
        AgvnSessionTrack.start(1_000);
        for (int i = 0; i < 50; i++) AgvnSessionTrack.onWindowUpdate(300, 150, 854, 480, true); // an error box
        assertFalse(AgvnSessionTrack.bigWindowSeen());
        assertFalse(AgvnSessionTrack.started());
        for (int i = 0; i < 50; i++) AgvnSessionTrack.onWindowUpdate(854, 480, 854, 480, false); // not an application window
        assertFalse(AgvnSessionTrack.started());
        for (int i = 0; i < AgvnSessionTrack.STARTED_UPDATES; i++) AgvnSessionTrack.onWindowUpdate(640, 480, 1280, 720, true);
        assertTrue("a 640×480 window on a 1280×720 screen is the game's", AgvnSessionTrack.started());
        assertEquals(AgvnSessionTrack.STARTED_UPDATES, AgvnSessionTrack.takeFrames());
        assertEquals(0, AgvnSessionTrack.takeFrames());
        assertEquals(9, AgvnSessionTrack.seconds(10_500));
    }

    @Test
    public void whoEndedTheGame() {
        AgvnSessionTrack.start(0);
        AgvnSessionTrack.wineEnded(); // the game closed itself
        AgvnSessionTrack.exitStarted();
        assertTrue(AgvnSessionTrack.endedByGame());
        assertFalse(AgvnSessionTrack.playerQuit());
        AgvnSessionTrack.start(0);
        AgvnSessionTrack.exitStarted(); // "Thoát" in the menu, then Wine ends because it is stopped
        AgvnSessionTrack.wineEnded();
        assertTrue(AgvnSessionTrack.playerQuit());
        assertFalse(AgvnSessionTrack.endedByGame());
    }
}
