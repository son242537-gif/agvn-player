/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.EnvVars;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class AgvnSessionLogTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void unityLogsComeFromTheWindowsUserFolder() throws Exception {
        File game = tmp.newFolder("game"), profile = tmp.newFolder("xuser");
        File exe = new File(game, "WithTheDevilishHer.exe");
        new File(game, "WithTheDevilishHer_Data").mkdirs();
        Files.write(new File(game, "WithTheDevilishHer_Data/app.info").toPath(), "Studio\nWith The Devilish Her".getBytes(StandardCharsets.UTF_8));
        List<File> logs = AgvnEngineLogs.candidates(exe, game, "UNITY", profile);
        File dir = new File(profile, "AppData/LocalLow/Studio/With The Devilish Her");
        assertTrue(logs.contains(new File(dir, "Player.log")));
        assertTrue(logs.contains(new File(dir, "Player-prev.log")));
        assertTrue("DxLib's Log.txt is always looked for", logs.contains(new File(game, "Log.txt")));
    }

    @Test
    public void renpyAndDxLibLogsSitNextToTheExe() throws Exception {
        File game = tmp.newFolder("vn");
        List<File> logs = AgvnEngineLogs.candidates(new File(game, "Game.exe"), game, "RENPY", tmp.getRoot());
        assertTrue(logs.contains(new File(game, "traceback.txt")));
        assertTrue(logs.contains(new File(game, "log.txt")));
    }

    @Test
    public void bigLogsKeepTheirStartAndEnd() throws Exception {
        File small = tmp.newFile("small.log");
        Files.write(small.toPath(), "short".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AgvnEngineLogs.copyShortened(small, out);
        assertEquals("short", out.toString("UTF-8"));

        File big = tmp.newFile("big.log");
        byte[] data = new byte[(int) AgvnEngineLogs.MAX_BYTES + 5000];
        Arrays.fill(data, (byte) 'x');
        data[0] = 'S';
        data[data.length - 1] = 'E';
        Files.write(big.toPath(), data);
        out.reset();
        AgvnEngineLogs.copyShortened(big, out);
        String copied = out.toString("UTF-8");
        assertTrue(copied.startsWith("S"));
        assertTrue(copied.endsWith("E"));
        assertTrue(copied.contains("bỏ bớt 5000 byte"));
        assertTrue(out.size() < AgvnEngineLogs.MAX_BYTES + 200);
    }

    @Test
    public void notesNameTheLogsAndAPlayersSwipe() throws Exception {
        File roaming = tmp.newFolder("Roaming"), godot = new File(roaming, "PartyMe/logs/godot.log");
        assertTrue(godot.getParentFile().mkdirs());
        Files.write(godot.toPath(), "Godot Engine v4.3.stable.official".getBytes(StandardCharsets.UTF_8));
        String notes = "start=1\ngame=Rebirth Pub\nlog=/g/Player.log\nlogscan=" + roaming.getPath() + "\n";
        assertEquals(Arrays.asList(new File("/g/Player.log"), godot), AgvnSessionNotes.engineLogs(notes, 0));
        assertFalse(AgvnSessionNotes.removedByPlayer(notes));
        // swiped away from the recent apps: AGVN ends itself (SIGKILL), and nothing is asked about it
        assertTrue(AgvnSessionNotes.removedByPlayer(notes + AgvnSessionNotes.REMOVED + "=1791035699000\n"));
    }

    @Test
    public void onlyGraphicsAndWineVariablesAreKept() {
        EnvVars env = new EnvVars("WRAPPER_EMULATE_BCN=0 HOME=/home/xuser DXVK_HUD=fps WINEDEBUG=-all PATH=/usr/bin");
        assertEquals("DXVK_HUD=fps\nWINEDEBUG=-all\nWRAPPER_EMULATE_BCN=0\n", AgvnSessionLog.graphicsEnv(env));
    }

    @Test
    public void keepsTheFiveNewestSessions() throws Exception {
        File gameLogs = tmp.newFolder("logs");
        for (int i = 1; i <= 7; i++) new File(gameLogs, "2026100" + i + "-120000").mkdirs();
        AgvnLogFolders.prune(gameLogs);
        String[] left = gameLogs.list();
        Arrays.sort(left);
        assertArrayEquals(new String[]{"20261003-120000", "20261004-120000", "20261005-120000", "20261006-120000", "20261007-120000"}, left);
        assertEquals("A_B_ C", AgvnLogFolders.safeName("A/B: C"));
        assertEquals("game", AgvnLogFolders.safeName("  "));
    }
}
