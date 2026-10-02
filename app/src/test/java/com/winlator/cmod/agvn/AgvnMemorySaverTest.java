/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.agvn.AgvnQuality.Level;
import com.winlator.cmod.core.WineRegistryEditor;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnMemorySaverTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void lowerStepsSaveMore() {
        assertEquals(128, AgvnMemorySaver.renpyCacheMb(Level.LOWEST));
        assertEquals(192, AgvnMemorySaver.renpyCacheMb(Level.LOW));
        assertEquals(256, AgvnMemorySaver.renpyCacheMb(Level.MEDIUM));
        assertEquals(0, AgvnMemorySaver.renpyCacheMb(Level.HIGH));
        assertEquals(0, AgvnMemorySaver.renpyCacheMb(Level.HIGHEST));
        assertTrue(AgvnMemorySaver.unityLowestQuality(Level.LOW));
        assertFalse(AgvnMemorySaver.unityLowestQuality(Level.MEDIUM));
        assertEquals("dxvk.maxChunkSize=16;dxvk.trackPipelineLifetime=True", AgvnMemorySaver.dxvkOptions(Level.LOWEST));
        assertEquals("dxvk.maxChunkSize=16", AgvnMemorySaver.dxvkOptions(Level.MEDIUM));
        assertEquals("", AgvnMemorySaver.dxvkOptions(Level.HIGH));
    }

    @Test
    public void dxvkOptionsKeepTheUsersConfig() {
        assertEquals("dxvk.maxChunkSize=16", AgvnMemorySaver.mergeDxvkConfig(null, "dxvk.maxChunkSize=16"));
        assertEquals("dxvk.maxChunkSize=16", AgvnMemorySaver.mergeDxvkConfig(" ", "dxvk.maxChunkSize=16"));
        assertEquals("dxgi.maxFrameRate = 30; d3d9.maxFrameRate = 30;dxvk.maxChunkSize=16",
                AgvnMemorySaver.mergeDxvkConfig("dxgi.maxFrameRate = 30; d3d9.maxFrameRate = 30", "dxvk.maxChunkSize=16"));
        assertEquals("a=1;dxvk.maxChunkSize=16", AgvnMemorySaver.mergeDxvkConfig("a=1;", "dxvk.maxChunkSize=16"));
        assertEquals("a=1", AgvnMemorySaver.mergeDxvkConfig("a=1", ""));
    }

    @Test
    public void renpyCacheIsWrittenAndRemoved() throws Exception {
        File gameDir = tmp.newFolder("vn");
        File game = new File(gameDir, "game");
        assertTrue(game.mkdir());
        File rpy = new File(game, AgvnRenpyCache.NAME + ".rpy");
        File rpyc = new File(game, AgvnRenpyCache.NAME + ".rpyc");

        AgvnRenpyCache.apply(gameDir, 192);
        String text = new String(Files.readAllBytes(rpy.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("init 999 python:\n    config.image_cache_size = None\n"));
        assertTrue(text.contains("config.image_cache_size_mb = 192\n"));

        AgvnRenpyCache.apply(gameDir, 128);
        assertTrue(new String(Files.readAllBytes(rpy.toPath()), StandardCharsets.UTF_8).contains("= 128\n"));

        assertTrue(rpyc.createNewFile()); // what Ren'Py compiles from it
        AgvnRenpyCache.apply(gameDir, 0);
        assertFalse(rpy.exists());
        assertFalse(rpyc.exists());
    }

    @Test
    public void renpyCacheLeavesOtherFoldersAlone() throws Exception {
        File notRenpy = tmp.newFolder("other");
        AgvnRenpyCache.apply(notRenpy, 128);
        assertEquals(0, notRenpy.list().length);
    }

    @Test
    public void unityPrefsKeyComesFromAppInfo() throws Exception {
        File dir = tmp.newFolder("unity");
        File data = new File(dir, "My Game_Data");
        assertTrue(data.mkdir());
        File appInfo = new File(data, "app.info");
        Files.write(appInfo.toPath(), "Some Studio\nMy Game".getBytes(StandardCharsets.UTF_8));
        File exe = new File(dir, "My Game.exe");

        assertEquals(appInfo, AgvnUnityQuality.findAppInfo(exe));
        assertEquals(appInfo, AgvnUnityQuality.findAppInfo(new File(dir, "Launcher.exe"))); // the only *_Data
        assertEquals("Software\\Some Studio\\My Game", AgvnUnityQuality.prefsKey(appInfo));

        Files.write(appInfo.toPath(), "Some Studio\n".getBytes(StandardCharsets.UTF_8));
        assertNull(AgvnUnityQuality.prefsKey(appInfo));
    }

    @Test
    public void unityQualityIsSetAndRemovedInUserReg() throws Exception {
        // WineRegistryEditor renames over existing files, which java.io.File cannot do on Windows.
        Assume.assumeTrue("rename-over needs a POSIX file system", File.separatorChar == '/');
        File userReg = tmp.newFile("user.reg");
        Files.write(userReg.toPath(), ("WINE REGISTRY Version 2\n;; All keys relative to \\\\User\\\\S-1-5-21-0-0-0-1000\n\n"
                + "#arch=win64\n").getBytes(StandardCharsets.UTF_8));
        String key = "Software\\Some Studio\\My Game";

        AgvnUnityQuality.set(userReg, key, true);
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            assertEquals(Integer.valueOf(0), editor.getDwordValue(key, AgvnUnityQuality.VALUE));
        }
        AgvnUnityQuality.set(userReg, key, false);
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            assertNull(editor.getDwordValue(key, AgvnUnityQuality.VALUE));
        }
    }
}
