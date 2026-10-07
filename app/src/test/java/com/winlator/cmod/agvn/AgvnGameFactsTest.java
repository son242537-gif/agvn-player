/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
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
    public void anUnknownGameSaysWhatItsFolderHolds() throws Exception {
        // Isekai NTR Inn, 07/10/2026: one exe, no DLL, no engine log, and still "UNKNOWN" after a second log
        File exe = touch("Inn/IsekaiNTRInn.exe");
        touch("Inn/readme.txt");
        assertTrue(new File(tmp.getRoot(), "Inn/data").mkdirs());
        try (RandomAccessFile big = new RandomAccessFile(new File(tmp.getRoot(), "Inn/Big.dat"), "rw")) {
            big.setLength(3L << 20);
        }
        assertEquals(Arrays.asList("Game: IsekaiNTRInn.exe · engine UNKNOWN · DLL cạnh exe: không có",
                "Thư mục game: Big.dat 3 MB, data/, IsekaiNTRInn.exe, readme.txt"), AgvnGameFacts.facts(exe, "UNKNOWN"));
        assertEquals("a known engine: one line", 1, AgvnGameFacts.facts(touch("Wolf/Game.exe"), "WOLFRPG").size());
    }

    @Test
    public void aModdedGameSaysWhatItsModsHold() throws Exception {
        // Rina, 07/10/2026: BepInEx never wrote its LogOutput.log
        File exe = touch("Rina/Game.exe");
        touch("Rina/winhttp.dll");
        touch("Rina/doorstop_config.ini");
        assertTrue(new File(tmp.getRoot(), "Rina/BepInEx/core").mkdirs());
        assertTrue(new File(tmp.getRoot(), "Rina/dotnet").mkdirs());
        touch("Rina/BepInEx/LogOutput.log");
        List<String> facts = AgvnGameFacts.facts(exe, "UNITY");
        assertEquals("Thư mục game: BepInEx/, doorstop_config.ini, dotnet/, Game.exe", facts.get(1));
        assertEquals("Thư mục BepInEx: core/, LogOutput.log trống", facts.get(2));
        assertEquals(3, facts.size());
        assertEquals("Thư mục trống: trống", AgvnGameFacts.listing("Thư mục trống", tmp.newFolder("empty"), true));
        assertEquals("12 KB", AgvnGameFacts.size(12 << 10));
        assertEquals("402 MB", AgvnGameFacts.size(402L << 20));
        assertEquals("1,5 GB", AgvnGameFacts.size(3L << 29));
    }

    @Test
    public void aLongListIsCut() throws Exception {
        File exe = touch("Big/Big.exe");
        for (int i = 0; i < AgvnGameFacts.MAX_DLLS + 3; i++) touch(String.format("Big/lib%02d.dll", i));
        assertTrue(AgvnGameFacts.describe(exe, "UNREAL").endsWith("lib39.dll (+3)"));
    }
}
