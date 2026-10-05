/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** What adapts to the phone: DXVK by its Vulkan, the 60 Hz screen of the "Chạy nhẹ" games, thiet-bi.txt. */
public class AgvnAllPhonesTest {
    private static final int VK_1_1 = (1 << 22) | (1 << 12), VK_1_3 = (1 << 22) | (3 << 12), VK_1_4 = (1 << 22) | (4 << 12);

    @Test
    public void dxvkFollowsWhatVulkanCanRun() {
        assertEquals(AgvnDxvkPick.NEW, AgvnDxvkPick.version("Adreno (TM) 506", true, VK_1_1)); // Turnip brings 1.3
        assertEquals(AgvnDxvkPick.OLD, AgvnDxvkPick.version("Adreno (TM) 506", false, VK_1_1));
        assertEquals(AgvnDxvkPick.OLD, AgvnDxvkPick.version("PowerVR Rogue GE8320", false, VK_1_1));
        assertEquals(AgvnDxvkPick.NEW, AgvnDxvkPick.version("Samsung Xclipse 940", false, VK_1_3));
        assertEquals(AgvnDxvkPick.NEW, AgvnDxvkPick.version("PowerVR D-Series DXT-48-1536", false, VK_1_4));
        assertEquals(AgvnDxvkPick.OLD, AgvnDxvkPick.version("Mali-G715 MC7", false, VK_1_3)); // as upstream
        assertEquals(AgvnDxvkPick.NEW, AgvnDxvkPick.version("", false, 0)); // the phone does not say: as before
        assertTrue(AgvnDxvkPick.atLeast(VK_1_3, 1, 3));
        assertFalse(AgvnDxvkPick.atLeast(VK_1_1, 1, 3));
        assertEquals("1.3", AgvnDxvkPick.name(VK_1_3 | 284)); // the patch is left out
        assertEquals("?", AgvnDxvkPick.name(0));
    }

    @Test
    public void everySarekKeepsTheWrapperPassOff() {
        // upstream's own build and one a player installed (its DXVK HUD: "DXVK-Sarek v1.11.0-async", 04/10)
        assertTrue(AgvnDxvkPick.isSarek("1.11.1-sarek"));
        assertTrue(AgvnDxvkPick.isSarek("Sarek-1.11.0-async-0"));
        assertFalse(AgvnDxvkPick.isSarek(AgvnDxvkPick.OLD));
        assertFalse(AgvnDxvkPick.isSarek(null));
    }

    @Test
    public void dxvkVersionReplacedAlone() {
        // Container.DEFAULT_DXWRAPPERCONFIG's start (the class itself needs the phone's GPU to load)
        String upstream = "version=2.3.1,framerate=0,async=0,asyncCache=0,vkd3dVersion=None,vkd3dLevel=12_1,ddrawrapper=none";
        String config = AgvnDxvkPick.withVersion(upstream, AgvnDxvkPick.OLD);
        assertEquals(upstream.replace("version=2.3.1,", "version=1.10.3,"), config); // vkd3dVersion is not DXVK's
        assertEquals("version=2.3.1,async=1", AgvnDxvkPick.withVersion("async=1", AgvnDxvkPick.NEW));
        assertEquals("version=2.3.1", AgvnDxvkPick.withVersion("", AgvnDxvkPick.NEW));
    }

    @Test
    public void dxvk2StepsDownAtStartOnAnOldPhoneDriver() {
        assertEquals(AgvnDxvkPick.OLD, AgvnDxvkPick.forLaunch("2.3.1", "System", VK_1_1));
        assertEquals("1.10.3-arm64ec-async", AgvnDxvkPick.forLaunch("2.3.1-arm64ec-gplasync", "System", VK_1_1));
        assertEquals("2.3.1", AgvnDxvkPick.forLaunch("2.3.1", "System", VK_1_3)); // the driver runs it
        assertEquals("2.3.1", AgvnDxvkPick.forLaunch("2.3.1", "turnip26.2.0", VK_1_1)); // Turnip brings 1.3
        assertEquals("2.3.1", AgvnDxvkPick.forLaunch("2.3.1", "System", 0)); // the phone does not say
        assertEquals("1.10.3", AgvnDxvkPick.forLaunch("1.10.3", "System", VK_1_1));
        assertEquals(null, AgvnDxvkPick.forLaunch(null, "System", VK_1_1));
    }

    @Test
    public void godotGetsOpenGl33OnZink() {
        com.winlator.cmod.core.EnvVars env = new com.winlator.cmod.core.EnvVars("ZINK_DEBUG=compact");
        AgvnGlDriver.forGodot("GODOT", env);
        assertEquals("3.3", env.get("MESA_GL_VERSION_OVERRIDE"));
        assertEquals("330", env.get("MESA_GLSL_VERSION_OVERRIDE"));
        com.winlator.cmod.core.EnvVars own = new com.winlator.cmod.core.EnvVars("MESA_GL_VERSION_OVERRIDE=4.6");
        AgvnGlDriver.forGodot("GODOT", own); // the player's value stays
        assertEquals("4.6", own.get("MESA_GL_VERSION_OVERRIDE"));
        com.winlator.cmod.core.EnvVars unity = new com.winlator.cmod.core.EnvVars("");
        AgvnGlDriver.forGodot("UNITY", unity);
        assertFalse(unity.has("MESA_GL_VERSION_OVERRIDE"));
    }

    private static AgvnRefreshCap.Mode mode(int id, int w, int h, float hz) {
        return new AgvnRefreshCap.Mode(id, w, h, hz);
    }

    @Test
    public void lightGamesAskFor60HzAtTheSameSize() {
        List<AgvnRefreshCap.Mode> modes = Arrays.asList(mode(1, 1220, 2712, 120), mode(2, 1220, 2712, 90),
                mode(3, 1220, 2712, 60), mode(4, 1080, 2400, 60), mode(5, 1220, 2712, 30));
        assertEquals(3, AgvnRefreshCap.pick(modes, modes.get(0), 60));
        assertEquals(3, AgvnRefreshCap.pick(modes, modes.get(2), 60)); // at 60 now, kept there when the game animates
        assertEquals(4, AgvnRefreshCap.pick(Arrays.asList(mode(4, 1080, 2400, 60), mode(6, 1080, 2400, 144)),
                mode(6, 1080, 2400, 144), 60));
        assertEquals(-1, AgvnRefreshCap.pick(Collections.singletonList(mode(7, 720, 1600, 60)), mode(7, 720, 1600, 60), 60));
        assertEquals(-1, AgvnRefreshCap.pick(Collections.singletonList(mode(8, 720, 1600, 120)), mode(8, 720, 1600, 120), 60));
        assertEquals(9, AgvnRefreshCap.pick(Arrays.asList(mode(9, 720, 1600, 59.94f), mode(10, 720, 1600, 90)),
                mode(10, 720, 1600, 90), 60));
    }

    @Test
    public void deviceReportLines() {
        assertEquals("8 nhân: 2×4,32 + 6×3,53 GHz",
                AgvnDeviceReport.cpu(new long[]{3532800, 3532800, 3532800, 3532800, 3532800, 3532800, 4320000, 4320000}));
        assertEquals("8 nhân: 1×2,40 + 3×2,20 + 2×1,80 GHz",
                AgvnDeviceReport.cpu(new long[]{1800000, 1800000, -1, -1, 2200000, 2200000, 2200000, 2400000}));
        assertEquals("4 nhân", AgvnDeviceReport.cpu(new long[]{-1, -1, -1, -1}));
        assertEquals("1220×2712 · 60/90/120 Hz · đang 120 Hz",
                AgvnDeviceReport.screen(1220, 2712, Arrays.asList(120f, 59.94f, 90f, 60f), 120.00001f));
        assertEquals("trạng thái nhiệt: nóng", AgvnDeviceReport.thermal(3));
        assertEquals("trạng thái nhiệt: không đọc được", AgvnDeviceReport.thermal(-1));
    }
}
