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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/** Which RPG Maker games "Chạy nhẹ" with mkxp-z takes (XP, VX, VX Ace), and where it finds their RTP. */
public class AgvnRgssGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A game folder with {@code ini} as its .ini text ({@code iniName}) and empty files at {@code paths}. */
    private File game(String name, String iniName, String ini, String... paths) throws Exception {
        File dir = tmp.newFolder(name);
        if (ini != null) Files.write(new File(dir, iniName).toPath(), ini.getBytes(StandardCharsets.UTF_8));
        for (String rel : paths) touch(dir, rel);
        return dir;
    }

    private static void touch(File dir, String rel) throws Exception {
        File f = new File(dir, rel);
        if (rel.endsWith("/")) {
            f.mkdirs();
            return;
        }
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), new byte[0]);
    }

    @Test
    public void versionComesFromTheIniLibrary() throws Exception {
        assertEquals(1, AgvnRgssGame.rgssVersion(game("xp", "Game.ini",
                "[Game]\r\nLibrary=RGSS104E.dll\r\nScripts=Data\\Scripts.rxdata\r\nTitle=XP\r\nRTP1=Standard\r\n", "Game.exe")));
        assertEquals(2, AgvnRgssGame.rgssVersion(game("vx", "Game.ini",
                "[Game]\nRTP=RPGVX\nLibrary=RGSS202E.dll\nScripts=Data\\Scripts.rvdata\n", "Game.exe")));
        assertEquals(3, AgvnRgssGame.rgssVersion(game("ace", "Game.ini",
                "[Game]\nRTP=RPGVXAce\nLibrary=System\\RGSS301.dll\nScripts=Data\\Scripts.rvdata2\n", "Game.exe", "Game.rgss3a")));
    }

    @Test
    public void versionFromArchiveOrDataWhenTheIniDoesNotSay() throws Exception {
        assertEquals(2, AgvnRgssGame.rgssVersion(game("enc", "Game.ini", "[Game]\nTitle=T\n", "Game.exe", "Game.rgss2a")));
        assertEquals(3, AgvnRgssGame.rgssVersion(game("data", "Game.ini", "[Game]\nTitle=T\n", "Game.exe", "Data/Scripts.rvdata2")));
        assertEquals(1, AgvnRgssGame.rgssVersion(game("old", "Game.ini", "[Game]\nTitle=T\n", "Game.exe", "Data/Map001.rxdata")));
    }

    @Test
    public void renamedExeHasItsOwnIniAndExecName() throws Exception {
        File dir = game("renamed", "Sotsugyou.ini", "[Game]\nLibrary=RGSS300.dll\n", "Sotsugyou.exe", "Sotsugyou.rgss3a");
        assertEquals(3, AgvnRgssGame.rgssVersion(dir));
        assertEquals("Sotsugyou", AgvnRgssGame.readIni(dir).execName());
    }

    @Test
    public void notRgss() throws Exception {
        File rm2003 = game("rm2003", "RPG_RT.ini", "[RPG_RT]\nGameTitle=T\n", "RPG_RT.exe", "RPG_RT.ldb", "RPG_RT.lmt");
        assertEquals(0, AgvnRgssGame.rgssVersion(rm2003));
        assertFalse(AgvnRgssGame.canRun(rm2003));
        assertEquals(0, AgvnRgssGame.rgssVersion(game("none", "Game.ini", null, "Game.exe")));
        assertFalse(AgvnRgssGame.canRun(null));
    }

    @Test
    public void libraryNames() {
        assertEquals(1, AgvnRgssGame.fromLibrary("RGSS104E.dll"));
        assertEquals(2, AgvnRgssGame.fromLibrary("rgss202j.dll"));
        assertEquals(3, AgvnRgssGame.fromLibrary("System\\RGSS301.dll"));
        assertEquals(0, AgvnRgssGame.fromLibrary("foo.dll"));
        assertEquals(0, AgvnRgssGame.fromLibrary("RGSS"));
        assertEquals(0, AgvnRgssGame.fromLibrary(null));
    }

    @Test
    public void useRgssOnlyForRgssGamesAndNotWhenTheProfileSaysWine() throws Exception {
        File ace = game("acegame", "Game.ini", "[Game]\nLibrary=System\\RGSS301.dll\n", "Game.exe", "Game.rgss3a");
        assertTrue(AgvnRgssGame.useRgss(null, GameExeResolver.Engine.RPGMAKER, ace));
        AgvnProfile wine = new AgvnProfile();
        wine.runner = AgvnHtmlGame.RUNNER_WINE;
        assertFalse(AgvnRgssGame.useRgss(wine, GameExeResolver.Engine.RPGMAKER, ace));
        assertFalse(AgvnRgssGame.useRgss(null, GameExeResolver.Engine.RENPY, ace));
    }

    @Test
    public void rtpNamesInOrderWithoutRepeats() throws Exception {
        File xp = game("xprtp", "Game.ini", "[Game]\nRTP1=Standard\nRTP2=\nRTP3=Standard\nLibrary=RGSS104E.dll\n", "Game.exe");
        assertEquals(Collections.singletonList("Standard"), AgvnRgssGame.rtpNames(AgvnRgssGame.readIni(xp)));
        File ace = game("acertp", "Game.ini", "[Game]\nRTP=RPGVXAce\nLibrary=System\\RGSS301.dll\n", "Game.exe");
        assertEquals(Collections.singletonList("RPGVXAce"), AgvnRgssGame.rtpNames(AgvnRgssGame.readIni(ace)));
        assertTrue(AgvnRgssGame.rtpNames(null).isEmpty());
    }

    @Test
    public void rtpFromAgvnFolderThenWineInAnyLetterCase() throws Exception {
        File root = tmp.newFolder("phone");
        File agvnRtp = new File(root, "AGVN-Player/RTP");
        File driveC = new File(root, "drive_c");
        assertNull(AgvnRgssGame.findRtp("RPGVXAce", 3, agvnRtp, driveC));
        touch(driveC, "Program Files (x86)/Common Files/Enterbrain/RGSS3/RPGVXAce/Graphics/");
        assertEquals(new File(driveC, "Program Files (x86)/Common Files/Enterbrain/RGSS3/RPGVXAce"),
                AgvnRgssGame.findRtp("RPGVXAce", 3, agvnRtp, driveC));
        touch(agvnRtp, "rpgvxace/graphics/");
        assertEquals(new File(agvnRtp, "rpgvxace"), AgvnRgssGame.findRtp("RPGVXAce", 3, agvnRtp, driveC));
        touch(driveC, "Program Files/Common Files/Enterbrain/RGSS/Standard/Graphics/");
        assertEquals(new File(driveC, "Program Files/Common Files/Enterbrain/RGSS/Standard"),
                AgvnRgssGame.findRtp("Standard", 1, agvnRtp, driveC));
        touch(agvnRtp, "RPGVX/Audio/"); // a copy without Graphics/ is not an RTP
        assertNull(AgvnRgssGame.findRtp("RPGVX", 2, agvnRtp, null));
    }

    @Test
    public void enterbrainFolders() {
        assertEquals(Arrays.asList("RGSS", "RGSS2", "RGSS3"),
                Arrays.asList(AgvnRgssGame.enterbrainDir(1), AgvnRgssGame.enterbrainDir(2), AgvnRgssGame.enterbrainDir(3)));
    }
}
