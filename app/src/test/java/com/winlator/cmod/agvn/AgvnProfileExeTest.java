/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class AgvnProfileExeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File game() throws Exception {
        File dir = tmp.newFolder("Oppai Spy Gakuen");
        for (String f : new String[]{"BootStrap.exe", "hspy.exe", "hspy.cf", "data.xp3", "Uninstall.exe", "ファイル破損チェックツール.exe"})
            assertTrue(new File(dir, f).createNewFile());
        return dir;
    }

    @Test
    public void theEnginesExeIsPickedNotTheMakersStartMenu() throws Exception {
        File dir = game();
        assertEquals(GameExeResolver.Engine.KIRIKIRI, GameExeResolver.detectEngine(dir));
        assertEquals("hspy.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.KIRIKIRI));
        assertTrue(new File(dir, "hspy.cf").delete()); // without the settings file: by name, no start menu or tool
        // a JVM without a UTF-8 locale writes the Japanese name as "????": that file is no part of this check then
        for (File f : dir.listFiles()) if (f.getName().contains("?")) assertTrue(f.delete());
        assertEquals("hspy.exe", GameExeResolver.resolveExe(dir, GameExeResolver.Engine.KIRIKIRI));
        assertTrue(GameExeResolver.isHelper("bootstrap.exe"));
    }

    @Test
    public void aProfileEditedLaterIsHeard() throws Exception {
        File dir = game();
        File entry = new File(dir, "BootStrap.exe");
        assertEquals("hspy.exe", AgvnProfileExe.follow("hspy.exe", dir, entry));
        assertEquals("", AgvnProfileExe.follow("BootStrap.exe", dir, entry)); // they agree: nothing to follow
        assertEquals("", AgvnProfileExe.follow("", dir, entry));
        assertEquals("", AgvnProfileExe.follow(null, dir, entry));
        assertNull(AgvnProfileExe.follow("gone.exe", dir, entry));
        assertNull(AgvnProfileExe.follow("../other/hspy.exe", dir, entry));
        assertNull(AgvnProfileExe.follow("hspy.cf", dir, entry));
        File sub = new File(dir, "bin");
        assertTrue(sub.mkdirs() && new File(sub, "real.exe").createNewFile());
        assertNull(AgvnProfileExe.follow("bin/real.exe", dir, entry)); // another folder: the game is added again
    }

    @Test
    public void aFitRefusedForOneWindowIsAskedAgainForAnother() {
        assertTrue(AgvnFitMath.otherWindow("400x640", 1288, 769)); // the start menu, then the game
        assertFalse(AgvnFitMath.otherWindow("1280x720", 1288, 746)); // the same window with its frame
        assertFalse(AgvnFitMath.otherWindow("", 1288, 769)); // a choice made before windows were noted stays
        assertFalse(AgvnFitMath.otherWindow(null, 1288, 769));
        assertFalse(AgvnFitMath.otherWindow("abcxdef", 1, 1));
    }
}
