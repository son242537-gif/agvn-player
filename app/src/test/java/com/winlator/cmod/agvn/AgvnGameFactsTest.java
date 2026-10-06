/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

/** What a session log says about the game it ran, and the mod loaders' logs it copies. */
public class AgvnGameFactsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File touch(String path) throws Exception {
        File f = new File(tmp.getRoot(), path);
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
        return f;
    }

    @Test
    public void aModdedUnityGameSaysSo() throws Exception {
        // Rina, 07/10/2026: Unity's Player.log stayed empty, and only Wine's DLL overrides hinted at BepInEx
        File exe = touch("Rina/Rina.exe");
        touch("Rina/winhttp.dll");
        touch("Rina/GameAssembly.dll");
        touch("Rina/UnityPlayer.dll");
        assertTrue(new File(tmp.getRoot(), "Rina/BepInEx").mkdirs());
        assertEquals("Game: Rina.exe · engine UNITY · DLL cạnh exe: GameAssembly.dll, UnityPlayer.dll, winhttp.dll"
                + " · mod: BepInEx", AgvnGameFacts.describe(exe, "UNITY"));
        List<File> logs = AgvnEngineLogs.candidates(exe, exe.getParentFile(), "UNITY", tmp.getRoot());
        assertTrue(logs.contains(new File(exe.getParentFile(), "BepInEx/LogOutput.log")));
        assertTrue(logs.contains(new File(exe.getParentFile(), "MelonLoader/Latest.log")));
    }

    @Test
    public void aGameAloneInItsFolder() throws Exception {
        File exe = touch("Inn/Game.exe");
        touch("Inn/Data.wolf");
        assertEquals("Game: Game.exe · engine chưa rõ · DLL cạnh exe: không có", AgvnGameFacts.describe(exe, ""));
    }

    @Test
    public void aLongListIsCut() throws Exception {
        File exe = touch("Big/Big.exe");
        for (int i = 0; i < AgvnGameFacts.MAX_DLLS + 3; i++) touch(String.format("Big/lib%02d.dll", i));
        assertTrue(AgvnGameFacts.describe(exe, "UNREAL").endsWith("lib39.dll (+3)"));
    }
}
