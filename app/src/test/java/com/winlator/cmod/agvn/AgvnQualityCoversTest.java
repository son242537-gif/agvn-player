package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

public class AgvnQualityCoversTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void withFpsKeepsOtherVariables() {
        assertEquals("A=1 DXVK_FRAME_RATE=24", AgvnQuality.withFps("A=1 DXVK_FRAME_RATE=60", 24));
        assertEquals("A=1", AgvnQuality.withFps("A=1 DXVK_FRAME_RATE=60", 0));
        assertEquals("DXVK_FRAME_RATE=30", AgvnQuality.withFps(null, 30));
    }

    @Test
    public void mediumNeverHeavierThanPreset() {
        DeviceTierRules.Preset preset = new DeviceTierRules.Preset();
        preset.fps = 27;
        preset.resolution = "960x544";
        preset.texturePool = 768;
        LaunchPresetResolver.Effective eff = AgvnQuality.capped(new LaunchPresetResolver.Effective("1920x1080", 0, 2048), preset);
        assertEquals("960x544", eff.resolution);
        assertEquals(27, eff.fps);
        assertEquals(768, eff.texturePool);
        eff = AgvnQuality.capped(new LaunchPresetResolver.Effective("854x480", 24, 512), preset);
        assertEquals("854x480", eff.resolution);
        assertEquals(24, eff.fps);
        assertEquals(512, eff.texturePool);
    }

    @Test
    public void findsLocalCoverAndProjectRoot() throws Exception {
        File root = tmp.newFolder("AV Director");
        File exe = new File(root, "Game/Binaries/Win64/Game-Win64-Shipping.exe");
        assertTrue(exe.getParentFile().mkdirs());
        assertTrue(exe.createNewFile());
        assertEquals(new File(root, "Game"), AgvnCovers.gameDirFor(null, exe));
        assertNull(AgvnCovers.findLocal(root));
        assertTrue(new File(root, "Header.JPG").createNewFile());
        assertTrue(new File(root, "Poster.png").createNewFile());
        assertEquals("Poster.png", AgvnCovers.findLocal(root).getName());
        assertEquals(root, AgvnCovers.gameDirFor("", exe));
        assertEquals(root, AgvnCovers.gameDirFor(root.getPath(), exe));
    }

    @Test
    public void wrapsLongNames() {
        assertEquals(Arrays.asList("Legend Cleaner", "Gamehub Lite"), AgvnCovers.wrap("Legend Cleaner Gamehub Lite", 16, 3));
        assertEquals(Arrays.asList("a b", "c d"), AgvnCovers.wrap("a b c d e f", 3, 2));
    }
}
