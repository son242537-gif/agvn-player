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
    public void oldRatCaoCapIsDroppedButOtherCapsStay() {
        assertEquals("A=1", AgvnQuality.withoutOldHighestCap("A=1 DXVK_FRAME_RATE=40"));
        assertEquals("A=1 DXVK_FRAME_RATE=60", AgvnQuality.withoutOldHighestCap("A=1 DXVK_FRAME_RATE=60"));
        assertEquals("", AgvnQuality.withoutOldHighestCap(null));
        assertEquals("A=1", AgvnQuality.withFps("A=1 DXVK_FRAME_RATE=40", AgvnQuality.Level.HIGHEST.fps));
    }

    @Test
    public void sliderStepsGoFromCoolToSharp() {
        assertEquals(AgvnQuality.Level.LOWEST, AgvnQuality.Level.atStep(0));
        assertEquals(AgvnQuality.Level.HIGHEST, AgvnQuality.Level.atStep(4));
        assertEquals(AgvnQuality.Level.HIGHEST, AgvnQuality.Level.atStep(9));
        assertEquals(2, AgvnQuality.Level.MEDIUM.step());
        int lastFps = 0;
        for (int step = 0; step < 4; step++) {
            AgvnQuality.Level l = AgvnQuality.Level.atStep(step);
            assertTrue(l.fps > lastFps);
            lastFps = l.fps;
        }
        assertEquals(0, AgvnQuality.Level.HIGHEST.fps); // Rất cao: no cap, the game and its cheat menu decide
        assertEquals(AgvnQuality.Level.HIGH, AgvnQuality.recommended(DeviceTier.FLAGSHIP));
        assertEquals(AgvnQuality.Level.LOW, AgvnQuality.recommended(DeviceTier.YEU));
        assertEquals(AgvnQuality.Level.AUTO, AgvnQuality.Level.of("bogus"));
    }

    @Test
    public void findsEngineArt() throws Exception {
        File ue = tmp.newFolder("UE Game");
        File splash = new File(ue, "Proj/Content/Splash/Splash.bmp");
        assertTrue(splash.getParentFile().mkdirs());
        assertTrue(splash.createNewFile());
        assertEquals(splash, AgvnCoverSources.find(ue));
        assertEquals(splash, AgvnCoverSources.find(new File(ue, "Proj")));

        File rpg = tmp.newFolder("RPG");
        File titles = new File(rpg, "www/img/titles1");
        assertTrue(titles.mkdirs());
        java.nio.file.Files.write(new File(titles, "small.png").toPath(), new byte[10]);
        java.nio.file.Files.write(new File(titles, "Big.png").toPath(), new byte[100]);
        java.nio.file.Files.write(new File(titles, "enc.png_").toPath(), new byte[1000]);
        assertEquals("Big.png", AgvnCoverSources.find(rpg).getName());
        assertNull(AgvnCoverSources.find(tmp.newFolder("Empty")));
    }

    @Test
    public void findsLocalCoverAndProjectRoot() throws Exception {
        File root = tmp.newFolder("AV Director");
        File exe = new File(root, "Game/Binaries/Win64/Game-Win64-Shipping.exe");
        assertTrue(exe.getParentFile().mkdirs());
        assertTrue(exe.createNewFile());
        assertEquals(new File(root, "Game"), AgvnCovers.gameDirFor(null, exe));
        assertNull(AgvnCoverSources.findLocal(root));
        assertTrue(new File(root, "Header.JPG").createNewFile());
        assertTrue(new File(root, "Poster.png").createNewFile());
        assertEquals("Poster.png", AgvnCoverSources.findLocal(root).getName());
        assertEquals(root, AgvnCovers.gameDirFor("", exe));
        assertEquals(root, AgvnCovers.gameDirFor(root.getPath(), exe));
    }

    @Test
    public void wrapsLongNames() {
        assertEquals(Arrays.asList("Legend Cleaner", "Gamehub Lite"), AgvnCovers.wrap("Legend Cleaner Gamehub Lite", 16, 3));
        assertEquals(Arrays.asList("a b", "c d"), AgvnCovers.wrap("a b c d e f", 3, 2));
    }
}
