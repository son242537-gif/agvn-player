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
import java.util.Arrays;
import java.util.List;

/**
 * Isekai NTR Inn (a PyInstaller game, Adreno 840, 27/10/2026) drew nothing at two starts; "Gửi nhật ký" had neither
 * the logs/ folder it wrote nor what it is made with.
 */
public class AgvnPythonGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File touch(String path) throws Exception {
        File f = new File(tmp.getRoot(), path);
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    @Test
    public void theLogsFolderBesideTheExeIsRead() throws Exception {
        File exe = touch("inn/IsekaiNTRInn.exe");
        File day = touch("inn/logs/2026-10-27.txt"), game = touch("inn/logs/game.log");
        touch("inn/logs/crash.dmp");
        touch("inn/save/slot1.txt");
        touch("inn/HUONG-DAN.txt");
        day.setLastModified(2000);
        game.setLastModified(1000);
        List<File> logs = AgvnEngineLogs.candidates(exe, exe.getParentFile(), "", tmp.getRoot());
        assertTrue(logs.contains(day));
        assertTrue(logs.contains(game));
        assertFalse("a dump is no text log", logs.contains(new File(tmp.getRoot(), "inn/logs/crash.dmp")));
        assertFalse("saves are not logs", logs.contains(new File(tmp.getRoot(), "inn/save/slot1.txt")));
        assertFalse("a guide is not one", logs.contains(new File(tmp.getRoot(), "inn/HUONG-DAN.txt")));
        assertEquals("newest first", Arrays.asList(day, game), AgvnGameDirLogs.beside(exe.getParentFile()));
        assertTrue(AgvnGameDirLogs.isLogDir("Logs"));
    }

    @Test
    public void whatAPythonGameIsMadeWith() throws Exception {
        File dir = tmp.newFolder("py");
        for (String f : new String[]{"_internal/python312.dll", "_internal/SDL2.dll", "_internal/SDL2_mixer.dll",
                "_internal/numpy/core.pyd", "_internal/base_library.zip"}) touch("py/" + f);
        assertTrue(new File(dir, "_internal/pygame").mkdirs());
        touch("py/Game.exe");
        assertEquals("Game Python: Python 3.12 · thư viện: pygame, numpy · DLL: SDL2.dll, SDL2_mixer.dll",
                AgvnPythonFacts.describe(dir));
    }

    @Test
    public void otherPythonStacks() {
        assertEquals("Game Python: Python 3.8 · thư viện: PyQt5 · DLL: opengl32sw.dll",
                AgvnPythonFacts.describe(Arrays.asList("python38.dll", "PyQt5", "opengl32sw.dll", "VCRUNTIME140.dll")));
        assertEquals("Game Python: Python 3.11 · thư viện: pyglet",
                AgvnPythonFacts.describe(Arrays.asList("python311.dll", "pyglet-2.0.10.dist-info", "pyglet")));
        assertEquals("Game Python: Python ? · thư viện: tkinter",
                AgvnPythonFacts.describe(Arrays.asList("_tkinter.pyd")));
        assertNull(AgvnPythonFacts.describe(Arrays.asList("data.bin", "readme.txt")));
    }
}
