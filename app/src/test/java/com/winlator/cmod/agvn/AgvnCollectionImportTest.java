/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/** A folder that holds several games (a collection): one game per exe, from the scan to the library. */
public class AgvnCollectionImportTest {
    private static final GameExeResolver.Engine UNKNOWN = GameExeResolver.Engine.UNKNOWN;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A folder with the given files; a path ending in '/' is created as an empty directory. */
    private File folder(String name, String... paths) throws IOException {
        File dir = tmp.newFolder(name);
        for (String p : paths) {
            File f = new File(dir, p);
            f.getParentFile().mkdirs();
            assertTrue(p.endsWith("/") ? f.mkdirs() : f.createNewFile());
        }
        return dir;
    }

    private File collection() throws IOException {
        return folder("My Collection", "game1.exe", "game2.exe", "game3.exe", "gameLauncher.exe", "codex64.dll",
                "data/", "Wallpaper/");
    }

    @Test
    public void everyGameOfACollectionIsListedButNotItsLauncher() throws Exception {
        File dir = collection();
        assertEquals(UNKNOWN, GameExeResolver.detectEngine(dir));
        assertTrue(AgvnGameScanner.isGameDir(dir));
        assertEquals(Arrays.asList("game1.exe", "game2.exe", "game3.exe"), GameExeResolver.gameExes(dir, UNKNOWN));
        assertEquals("game1.exe", GameExeResolver.resolveExe(dir, UNKNOWN));
    }

    @Test
    public void oneGameWithItsHelpersStaysOneGame() throws Exception {
        File dir = folder("single", "AutoUpdate.exe", "Config.exe", "CrashReporter.exe", "Game.exe", "Settings.exe",
                "oalinst.exe", "unins000.exe", "vcredist_x86.exe", "x.dll");
        assertEquals("helpers sort first but are never the game", "Game.exe", GameExeResolver.resolveExe(dir, UNKNOWN));
        assertEquals(Collections.singletonList("Game.exe"), GameExeResolver.gameExes(dir, UNKNOWN));
        // only helpers: the old rule still picks one (no launcher, setup or config in the name)
        File helpers = folder("helpers", "Config.exe", "Updater.exe", "x.dll");
        assertEquals("Updater.exe", GameExeResolver.resolveExe(helpers, UNKNOWN));
    }

    @Test
    public void aKnownEngineOrAToolsFolderIsOneGame() throws Exception {
        File wolf = folder("wolf", "Config.exe", "Game.exe", "GameEn.exe", "Data.wolf");
        assertEquals(GameExeResolver.Engine.WOLFRPG, GameExeResolver.detectEngine(wolf));
        assertEquals(Collections.singletonList("Game.exe"), GameExeResolver.gameExes(wolf, GameExeResolver.Engine.WOLFRPG));
        String[] tools = new String[GameExeResolver.MAX_GAMES + 2];
        for (int i = 0; i <= GameExeResolver.MAX_GAMES; i++) tools[i] = "t" + i + ".exe";
        tools[GameExeResolver.MAX_GAMES + 1] = "x.dll";
        assertEquals(1, GameExeResolver.gameExes(folder("tools", tools), UNKNOWN).size());
        assertTrue(GameExeResolver.gameExes(tmp.newFolder("none"), UNKNOWN).isEmpty());
    }

    @Test
    public void eachGameGetsItsOwnExeAndName() throws Exception {
        File dir = collection();
        AgvnGameImporter.Candidate first = AgvnGameImporter.load(dir);
        assertEquals("game1.exe", first.exe);
        assertEquals("My Collection", first.profile.name);
        assertNull(first.variant);
        AgvnGameImporter.Candidate second = AgvnGameImporter.load(dir, AgvnProfileCatalog.EMPTY, "game2.exe");
        assertEquals("game2.exe", second.exe);
        assertEquals("My Collection - game2", second.profile.name);
        assertEquals("game2.exe", second.variant);
        // the launcher, picked by hand, is a game of its own too
        assertEquals("gameLauncher.exe", AgvnGameImporter.load(dir, AgvnProfileCatalog.EMPTY, "gameLauncher.exe").exe);
    }

    @Test
    public void variantNamesAreSafeFileNames() {
        assertEquals("My Collection - game2", AgvnProfile.variantName("My Collection", "game2.exe"));
        assertEquals("X - Game", AgvnProfile.variantName("X", "bin/Game.EXE"));
        assertEquals("X - a b", AgvnProfile.variantName("X", "a:b.exe"));
    }

    @Test
    public void theLibraryKnowsEachGameOfACollectionByItsExe() {
        // Android paths, as shortcuts have them on the phone (the publish script runs these tests on Windows too)
        String folder = "/storage/emulated/0/Download/My Collection";
        File dir = new File(folder);
        AgvnLibraryIndex library = new AgvnLibraryIndex(Collections.emptyList());
        AgvnLibraryIndex.Existing imported = new AgvnLibraryIndex.Existing(null, "My Collection");
        AgvnLibraryIndex.Existing byHand = new AgvnLibraryIndex.Existing(null, "game2");
        library.add(folder, "\"" + folder + "/game1.exe\"", imported);
        library.add("", "\"" + folder + "/game2.exe\"", byHand);
        assertSame(imported, library.find(dir, "game1.exe"));
        assertSame("a shortcut made by hand is not taken over by another game", byHand, library.find(dir, "game2.exe"));
        assertNull("not added yet, though its folder has shortcuts", library.find(dir, "game3.exe"));
        assertSame("one game per folder: any shortcut of the folder", imported, library.find(dir, null));
        assertNull(new AgvnLibraryIndex(Collections.emptyList()).find(dir, "game1.exe"));
    }
}
