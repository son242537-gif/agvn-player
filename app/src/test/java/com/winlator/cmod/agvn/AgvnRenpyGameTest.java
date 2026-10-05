/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/** Which Ren'Py games "Chạy nhẹ" takes (Ren'Py 8 only), and the environment its engine gets. */
public class AgvnRenpyGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A Ren'Py game folder: game/script.rpyc, renpy/, MyGame.py and MyGame.exe, plus {@code extra} paths. */
    private File game(String name, String... extra) throws Exception {
        File dir = tmp.newFolder(name);
        for (String rel : new String[]{"game/script.rpyc", "renpy/common/00start.rpyc", "MyGame.py", "MyGame.exe"}) touch(dir, rel);
        for (String rel : extra) {
            if (rel.endsWith("/")) new File(dir, rel).mkdirs();
            else touch(dir, rel);
        }
        return dir;
    }

    private static void touch(File dir, String rel) throws Exception {
        File f = new File(dir, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), new byte[0]);
    }

    @Test
    public void renpy8IsPython3AndRenpy6And7ArePython2() throws Exception {
        assertEquals(3, AgvnRenpyGame.pythonOf(game("r85", "lib/py3-windows-x86_64/", "lib/python3.12/")));
        assertEquals(3, AgvnRenpyGame.pythonOf(game("r80", "lib/python3.9/")));
        assertEquals(2, AgvnRenpyGame.pythonOf(game("r78", "lib/py2-windows-x86_64/", "lib/python2.7/")));
        assertEquals(2, AgvnRenpyGame.pythonOf(game("r6", "lib/windows-i686/", "lib/pythonlib2.7/")));
        // files of an older version left over by an update: the Python 3 folders tell the version that runs
        assertEquals(3, AgvnRenpyGame.pythonOf(game("updated", "lib/py2-windows-x86_64/", "lib/py3-windows-x86_64/")));
        // no lib/ (a stripped copy): the compiled engine modules tell
        assertEquals(3, AgvnRenpyGame.pythonOf(game("nolib3", "renpy/__pycache__/bootstrap.cpython-39.pyc")));
        assertEquals(2, AgvnRenpyGame.pythonOf(game("nolib2", "renpy/bootstrap.pyo")));
        assertEquals(0, AgvnRenpyGame.pythonOf(game("unknown")));
    }

    @Test
    public void onlyRenpy8GamesWithTheirGameFolderRunLight() throws Exception {
        File r8 = game("r8", "lib/py3-windows-x86_64/");
        assertTrue(AgvnRenpyGame.canRun(r8));
        assertFalse(AgvnRenpyGame.canRun(game("r7", "lib/py2-windows-x86_64/")));
        File noGame = game("nogame", "lib/py3-linux-x86_64/");
        new File(noGame, "game/script.rpyc").delete();
        new File(noGame, "game").delete();
        assertFalse(AgvnRenpyGame.canRun(noGame));
        assertFalse(AgvnRenpyGame.canRun(null));

        assertEquals(GameExeResolver.Engine.RENPY, GameExeResolver.detectEngine(r8));
        assertTrue(AgvnRenpyGame.useRenpy(null, GameExeResolver.Engine.RENPY, r8));
        AgvnProfile p = AgvnProfile.defaultFor("r8");
        assertTrue(AgvnRenpyGame.useRenpy(p, GameExeResolver.Engine.RENPY, r8));
        p.runner = "wine";
        assertFalse(AgvnRenpyGame.useRenpy(p, GameExeResolver.Engine.RENPY, r8));
        assertFalse(AgvnRenpyGame.useRenpy(null, GameExeResolver.Engine.UNITY, r8));
    }

    @Test
    public void runnerValuesForBothLightRunners() {
        assertTrue(AgvnHtmlGame.isValidRunner("renpy"));
        assertTrue(AgvnHtmlGame.isLight("renpy"));
        assertTrue(AgvnHtmlGame.isLight("html"));
        assertFalse(AgvnHtmlGame.isLight("wine"));
        assertFalse(AgvnHtmlGame.isLight(""));
    }

    @Test
    public void theStartNameIsTheGamesOwnScript() throws Exception {
        assertEquals("MyGame", AgvnRenpyGame.scriptName(game("named")));
        File bare = tmp.newFolder("bare");
        new File(bare, "folder.py").mkdirs(); // a folder, not the start file
        assertEquals("main", AgvnRenpyGame.scriptName(bare));
    }

    @Test
    public void savesStayInTheGameFolderAndLogsGoToTheAppFolder() throws Exception {
        File dir = game("env", "lib/py3-windows-x86_64/");
        File engine = new File("/data/user/0/com.agvn.player/files/renpy8");
        File logs = new File("/storage/emulated/0/AGVN-Player/renpy/env");
        File quit = new File("/data/user/0/com.agvn.player/cache/agvn-renpy-quit");
        Map<String, String> env = AgvnRenpyGame.environment(engine, dir, logs, "/data/app/base.apk", quit);
        assertEquals(engine.getPath(), env.get("ANDROID_PRIVATE"));
        assertEquals(logs.getPath(), env.get("ANDROID_PUBLIC"));
        // Ren'Py's first save folder is ANDROID_OLD_PUBLIC/game/saves: the one a PC (and Wine) uses
        assertEquals(dir.getPath(), env.get("ANDROID_OLD_PUBLIC"));
        assertEquals("/data/app/base.apk", env.get("ANDROID_APK"));
        assertEquals(dir.getPath(), env.get("AGVN_RENPY_BASEDIR"));
        assertEquals("MyGame", env.get("AGVN_RENPY_NAME"));
        assertEquals(quit.getPath(), env.get("AGVN_RENPY_QUIT_FILE"));
    }
}
