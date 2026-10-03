/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** What each "Tự sửa lỗi" fix writes, and what a game remembers between sessions. */
public class AgvnFixesTest {
    @Test
    public void dxvkSwapsGenerationAndKeepsItsBuild() {
        assertEquals("1.10.3", AgvnFixEdits.otherDxvk("2.3.1"));
        assertEquals("2.3.1", AgvnFixEdits.otherDxvk("1.10.3"));
        assertEquals("1.10.3-arm64ec-async", AgvnFixEdits.otherDxvk("2.3.1-arm64ec-gplasync"));
        assertEquals("2.3.1-arm64ec-gplasync", AgvnFixEdits.otherDxvk("1.10.3-arm64ec-async"));
        assertEquals("1.10.3", AgvnFixEdits.otherDxvk(""));
        assertEquals("2.3.1-arm64ec-gplasync", AgvnFixEdits.arm64ecDxvk("2.3.1"));
        assertEquals("1.10.3-arm64ec-async", AgvnFixEdits.arm64ecDxvk("1.10.3"));
        assertNull(AgvnFixEdits.arm64ecDxvk("1.10.3-arm64ec-async"));
        // the names are the bundled DXVK archives
        for (String v : Arrays.asList(AgvnFixEdits.DXVK_OLD, AgvnFixEdits.DXVK_NEW, AgvnFixEdits.ARM64EC_OLD, AgvnFixEdits.ARM64EC_NEW))
            assertTrue(v, new File("src/main/assets/dxwrapper/dxvk-" + v + ".tzst").isFile());
    }

    @Test
    public void configValuesChangeAlone() {
        String dxvk = "version=2.3.1,framerate=0,async=0,vkd3dVersion=None";
        assertEquals("2.3.1", AgvnFixEdits.configValue(dxvk, "version", ','));
        assertEquals("version=1.10.3,framerate=0,async=0,vkd3dVersion=None", AgvnFixEdits.withConfigValue(dxvk, "version", "1.10.3", ','));
        String driver = "vulkanVersion=1.3;version=turnip26.2.0;blacklistedExtensions=;maxDeviceMemory=0";
        assertEquals("turnip26.2.0", AgvnFixEdits.configValue(driver, "version", ';'));
        assertEquals("vulkanVersion=1.3;version=System;blacklistedExtensions=;maxDeviceMemory=0",
                AgvnFixEdits.withConfigValue(driver, "version", "System", ';'));
        assertEquals("version=System", AgvnFixEdits.withConfigValue("", "version", "System", ';'));
    }

    @Test
    public void godotArgumentsAreAddedOnceAndTakenBack() {
        String args = AgvnFixEdits.withArgs("--fullscreen", AgvnFixEdits.GODOT4_ARGS);
        assertEquals("--fullscreen --rendering-method mobile --rendering-driver vulkan", args);
        assertEquals(args, AgvnFixEdits.withArgs(args, AgvnFixEdits.GODOT4_ARGS));
        assertEquals("--fullscreen", AgvnFixEdits.withoutArgs(args, AgvnFixEdits.GODOT4_ARGS));
        assertEquals(AgvnFixEdits.GODOT3_ARGS, AgvnFixEdits.withArgs(null, AgvnFixEdits.GODOT3_ARGS));
    }

    @Test
    public void turnipRenderingMode() {
        // the container's Winlator default forces sysmem: the game's own TU_DEBUG replaces it, noconform stays
        assertEquals("TU_DEBUG=noconform,gmem", AgvnFixEdits.withRenderMode("", "noconform,sysmem", "gmem"));
        assertEquals("TU_DEBUG=noconform", AgvnFixEdits.withRenderMode("", "noconform,sysmem", ""));
        assertEquals("TU_DEBUG=", AgvnFixEdits.withRenderMode("", "sysmem", "")); // empty wins over the container's
        assertEquals("WINEESYNC=1", AgvnFixEdits.withRenderMode("WINEESYNC=1 TU_DEBUG=gmem TU_AUTOTUNE_ALGO=profiled", "", ""));
        assertEquals("TU_DEBUG=gmem", AgvnFixEdits.withRenderMode("TU_DEBUG=sysmem", "", "gmem"));
        assertEquals(Arrays.asList("noconform", "sysmem"), AgvnFixEdits.renderFlags("", "noconform,sysmem"));
        assertEquals(Collections.singletonList("gmem"), AgvnFixEdits.renderFlags("TU_DEBUG=gmem", "noconform,sysmem"));
    }

    @Test
    public void missingDllsMapToWindowsComponents() throws IOException {
        Map<String, List<String>> components;
        try (Reader in = Files.newBufferedReader(new File("src/main/assets/wincomponents/wincomponents.json").toPath(), StandardCharsets.UTF_8)) {
            components = new Gson().fromJson(in, new TypeToken<LinkedHashMap<String, List<String>>>() {}.getType());
        }
        assertEquals("direct3d", AgvnFixEdits.componentFor("D3DX9_43.dll", components));
        assertEquals("xaudio", AgvnFixEdits.componentFor("XAudio2_7.dll", components));
        assertEquals("vcrun2010", AgvnFixEdits.componentFor("MSVCP100.dll", components));
        assertNull(AgvnFixEdits.componentFor("UnityPlayer.dll", components));
        String on = "direct3d=1,directsound=0,xaudio=0,vcrun2010=1";
        assertEquals("direct3d=1,directsound=0,xaudio=1,vcrun2010=1", AgvnFixEdits.withComponent(on, "xaudio"));
        assertNull("on already", AgvnFixEdits.withComponent(on, "direct3d"));
        assertNull("not in the list", AgvnFixEdits.withComponent(on, "directmusic"));
    }

    @Test
    public void goodSettingsAreKeptAndCompared() {
        Properties state = new Properties();
        assertFalse(AgvnGoodConfig.hasGood(state));
        AgvnGoodConfig.addTried(state, "dxvk-other");
        Map<String, String> good = new LinkedHashMap<>();
        good.put("screenSize", "854x480");
        good.put("dxwrapperConfig", "version=2.3.1");
        AgvnGoodConfig.setGood(state, good);
        assertTrue(AgvnGoodConfig.hasGood(state));
        assertTrue("a session that ran well forgets the fixes tried", AgvnGoodConfig.tried(state).isEmpty());
        assertEquals(good, AgvnGoodConfig.good(state));
        Map<String, String> now = new LinkedHashMap<>(good);
        now.put("screenSize", "640x360");
        now.put("envVars", "TU_DEBUG=gmem");
        assertEquals(Arrays.asList("screenSize", "envVars"), AgvnGoodConfig.changed(good, now));
        assertTrue(AgvnGoodConfig.changed(good, good).isEmpty());
        AgvnGoodConfig.addTried(state, "render-gmem");
        AgvnGoodConfig.addTried(state, "render-gmem");
        assertEquals(Collections.singleton("render-gmem"), AgvnGoodConfig.tried(state));
    }

    @Test
    public void aRefusedScreenStopsLowerSteps() {
        Properties state = new Properties();
        assertFalse(AgvnGoodConfig.refused(state, "640x360"));
        state.setProperty(AgvnGoodConfig.TOO_SMALL, "640x360");
        assertTrue(AgvnGoodConfig.refused(state, "640x360"));
        assertFalse(AgvnGoodConfig.refused(state, "854x480"));
        assertEquals(409920, AgvnGoodConfig.pixels("854x480"));
        assertEquals(0, AgvnGoodConfig.pixels("854"));
        // every Đồ họa step names a screen the check can read
        for (AgvnQuality.Level level : AgvnQuality.Level.values())
            if (level != AgvnQuality.Level.AUTO) assertTrue(level.name(), AgvnGoodConfig.pixels(level.resolution) > 0);
    }
}
