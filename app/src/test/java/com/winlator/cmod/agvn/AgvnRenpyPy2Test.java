/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
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
import java.util.List;
import java.util.Map;

/**
 * A Ren'Py 7 game (Python 2) on "Chạy nhẹ", on trial: who picks it, where its saves go, and what "Tự sửa lỗi" offers a
 * Ren'Py game on Windows (a Ren'Py 7.3.5 game, POCO F8 Pro, 07/11/2026).
 */
public class AgvnRenpyPy2Test {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File game(String name, String... paths) throws Exception {
        File dir = tmp.newFolder(name);
        for (String rel : new String[]{"game/script.rpyc", "MyGame.py", "MyGame.exe"}) write(dir, rel, "");
        for (String rel : paths) {
            if (rel.endsWith("/")) new File(dir, rel).mkdirs();
            else write(dir, rel, rel);
        }
        return dir;
    }

    private static void write(File dir, String rel, String text) throws Exception {
        File f = new File(dir, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void onlyThePlayerOrTheProfileTakesARenpy7GameToChayNhe() throws Exception {
        File r7 = game("r735", "lib/windows-i686/", "lib/pythonlib2.7/");
        assertTrue(AgvnRenpyPy2.canTry(r7));
        assertFalse(AgvnRenpyGame.canRun(r7));
        assertFalse("not by itself: games that run well in Wine stay there",
                AgvnRenpyGame.useRenpy(null, GameExeResolver.Engine.RENPY, r7));
        AgvnProfile p = AgvnProfile.defaultFor("r735");
        p.runner = "renpy";
        assertTrue("the profile asks for it", AgvnRenpyGame.useRenpy(p, GameExeResolver.Engine.RENPY, r7));
        assertEquals(AgvnHtmlGame.RUNNER_RENPY,
                AgvnLightRecheck.runner(null, GameExeResolver.Engine.RENPY.name(), p, r7));
        assertNull("no runner of its own stays Wine's", AgvnLightRecheck.runner(null, GameExeResolver.Engine.RENPY.name(), null, r7));
        assertFalse("no game/ folder", AgvnRenpyPy2.canTry(tmp.newFolder("empty")));
        assertFalse(AgvnRenpyPy2.canTry(game("r8", "lib/py3-windows-x86_64/")));
    }

    @Test
    public void theTrialKeepsItsOwnSavesWithACopyOfTheGames() throws Exception {
        File r7 = game("saves", "lib/py2-windows-x86_64/", "game/saves/1-1-LT1.save", "game/saves/persistent");
        File logs = new File(tmp.getRoot(), "AGVN-Player/renpy/saves");
        assertEquals(2, AgvnRenpyPy2.prepareSaves(r7, logs));
        File own = AgvnRenpyPy2.savesDir(logs);
        assertTrue(new File(own, "persistent").isFile());
        Files.write(new File(own, "persistent").toPath(), "Ren'Py 8".getBytes(StandardCharsets.UTF_8));
        assertEquals("copied once, never over the trial's own", 0, AgvnRenpyPy2.prepareSaves(r7, logs));
        assertEquals("game/saves/persistent", new String(Files.readAllBytes(new File(r7, "game/saves/persistent").toPath()),
                StandardCharsets.UTF_8));
        Map<String, String> env = AgvnRenpyGame.environment(new File("/data/renpy8"), r7, logs, "/base.apk", new File("/q"));
        assertEquals("Ren'Py's first save folder is ANDROID_OLD_PUBLIC/game/saves", logs.getPath(), env.get("ANDROID_OLD_PUBLIC"));
        assertEquals("1", env.get("AGVN_RENPY_PY2"));
        Map<String, String> r8 = AgvnRenpyGame.environment(new File("/data/renpy8"), game("r85", "lib/py3-windows-x86_64/"),
                logs, "/base.apk", new File("/q"));
        assertFalse(r8.containsKey("AGVN_RENPY_PY2"));
    }

    @Test
    public void angleIsTheOldRendererUpToRenpy73() throws Exception {
        assertArrayEquals(new int[]{7, 3}, AgvnRenpyWine.version("(7, 3, 5)\n"));
        assertNull(AgvnRenpyWine.version(""));
        File v735 = game("v735");
        write(v735, "game/script_version.txt", "(7, 3, 5)");
        assertEquals("angle", AgvnRenpyWine.renderer(v735));
        File v74 = game("v74");
        write(v74, "game/script_version.txt", "(7, 4, 11)");
        assertEquals("angle2", AgvnRenpyWine.renderer(v74));
        assertEquals("no version: lib/windows-i686 is Ren'Py up to 7.3", "angle",
                AgvnRenpyWine.renderer(game("old", "lib/windows-i686/")));
        assertEquals("angle2", AgvnRenpyWine.renderer(game("new", "lib/py2-windows-x86_64/")));
    }

    @Test
    public void aRenpyGamesLogsAreItsOwn() throws Exception {
        File dir = game("logs");
        File exe = new File(dir, "MyGame.exe");
        List<File> renpy = AgvnEngineLogs.candidates(exe, dir, "RENPY", tmp.getRoot());
        assertTrue(renpy.contains(new File(dir, "log.txt")));
        for (String other : new String[]{"BepInEx/LogOutput.log", "MelonLoader/Latest.log", "ue4ss/UE4SS.log", "Log.txt"})
            assertFalse(other, renpy.contains(new File(dir, other)));
        List<File> unknown = AgvnEngineLogs.candidates(exe, dir, "", tmp.getRoot());
        assertTrue(unknown.contains(new File(dir, "BepInEx/LogOutput.log")));
        assertTrue(unknown.contains(new File(dir, "ue4ss/UE4SS.log")));
        assertFalse(AgvnEngineLogs.candidates(exe, dir, "UNITY", tmp.getRoot()).contains(new File(dir, "ue4ss/UE4SS.log")));
    }
}
