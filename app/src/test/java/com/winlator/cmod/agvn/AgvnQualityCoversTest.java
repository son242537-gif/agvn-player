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
    public void fpsCapIsNoLongerForcedOnDxvk() {
        assertEquals("A=1", AgvnQuality.withoutFpsCap("A=1 DXVK_FRAME_RATE=60"));
        assertEquals("A=1", AgvnQuality.withoutFpsCap("A=1"));
        assertEquals("", AgvnQuality.withoutFpsCap(null));
    }

    @Test
    public void oldCapsMoveToTheInGameLimiter() {
        assertEquals("24", AgvnQuality.startFpsFromOldCap("A=1 DXVK_FRAME_RATE=24", false));
        assertEquals("40", AgvnQuality.startFpsFromOldCap("DXVK_FRAME_RATE=40", false));
        assertEquals("0", AgvnQuality.startFpsFromOldCap("DXVK_FRAME_RATE=40", true)); // Rất cao's old cap: off
        assertEquals("60", AgvnQuality.startFpsFromOldCap("DXVK_FRAME_RATE=60", true));
        assertEquals("", AgvnQuality.startFpsFromOldCap("A=1", false));
        assertEquals("", AgvnQuality.startFpsFromOldCap("DXVK_FRAME_RATE=abc", false));
        assertEquals("", AgvnQuality.startFpsFromOldCap(null, true));
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
            assertTrue(l.fps >= lastFps); // Trung bình and Cao both 30: a 60 Hz screen shows 20 or 30 evenly
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
        assertEquals(splash.getPath(), AgvnCoverSources.candidates(ue).get(0).name);
        assertEquals(splash.getPath(), AgvnCoverSources.candidates(new File(ue, "Proj")).get(0).name);

        File rpg = tmp.newFolder("RPG");
        File titles = new File(rpg, "www/img/titles1");
        assertTrue(titles.mkdirs());
        java.nio.file.Files.write(new File(titles, "small.png").toPath(), new byte[10]);
        java.nio.file.Files.write(new File(titles, "Big.png").toPath(), new byte[100]);
        java.nio.file.Files.write(new File(titles, "enc.png_").toPath(), new byte[1000]);
        java.util.List<AgvnArt> art = AgvnCoverSources.candidates(rpg);
        assertEquals(new File(titles, "enc.png_").getPath(), art.get(0).name); // encrypted titles count too
        assertNull(art.get(0).read()); // ...but this one is no RPGMV file, so the next is used
        assertEquals(new File(titles, "Big.png").getPath(), art.get(1).name);
        assertTrue(AgvnCoverSources.candidates(tmp.newFolder("Empty")).isEmpty());
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
