/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AgvnSaveTransferTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String text) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static AgvnSaveTransfer.Source source(String name, byte[] data) {
        return new AgvnSaveTransfer.Source() {
            @Override public String name() { return name; }
            @Override public InputStream open() { return new ByteArrayInputStream(data); }
        };
    }

    @Test
    public void enginesSaveInTheirOwnFolders() throws Exception {
        File game = tmp.newFolder("game");
        assertEquals("game/game/saves", AgvnSaveLocations.inGameFolder(game, "RENPY").get(0).zipPath);
        assertEquals("game/save", AgvnSaveLocations.inGameFolder(game, "RPGMAKER_MV").get(0).zipPath); // MZ
        assertTrue(new File(game, "www").mkdir());
        assertEquals("game/www/save", AgvnSaveLocations.inGameFolder(game, "RPGMAKER_MV").get(0).zipPath);
        AgvnSaveLocations.Location rpg = AgvnSaveLocations.inGameFolder(game, "RPGMAKER").get(0);
        assertTrue(rpg.takes("Save01.rvdata2"));
        assertTrue(rpg.takes("Save3.rxdata"));
        assertFalse(rpg.takes("Game.ini"));
        assertTrue(AgvnSaveLocations.inGameFolder(game, "NSCRIPTER").get(0).takes("gloval.sav"));
        assertTrue(AgvnSaveLocations.inGameFolder(game, "UNITY").isEmpty());
    }

    @Test
    public void profileFoldersPutTheEngineFirstAndSkipOverlaps() throws Exception {
        File game = tmp.newFolder("MyGame");
        File exe = new File(game, "MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe");
        assertEquals(Arrays.asList("AppData/Local/MyGame/Saved/SaveGames", "Documents/My Games/Other"),
                AgvnSaveLocations.inProfile(exe, game, "UNREAL",
                        Arrays.asList("AppData/Local/MyGame", "Documents/My Games/Other")));
        write(new File(game, "Unity Game_Data/app.info"), "Studio\nUnity Game");
        assertEquals(Collections.singletonList("AppData/LocalLow/Studio/Unity Game"),
                AgvnSaveLocations.inProfile(new File(game, "Unity Game.exe"), game, "UNITY", Collections.emptyList()));
    }

    @Test
    public void exportThenRestoreKeepsEveryPlace() throws Exception {
        File game = tmp.newFolder("vn"), profile = tmp.newFolder("xuser");
        write(new File(game, "game/saves/1-1-LT1.save"), "slot1");
        write(new File(game, "game/saves/persistent"), "p");
        write(new File(game, "game/script.rpy"), "not a save");
        List<AgvnSaveLocations.Location> places = new ArrayList<>(AgvnSaveLocations.inGameFolder(game, "RENPY"));
        places.add(new AgvnSaveLocations.Location(new File(profile, "AppData/Roaming/RenPy/vn"), "profile/AppData/Roaming/RenPy/vn", null));
        write(new File(profile, "AppData/Roaming/RenPy/vn/sub/x.save"), "deep");

        File zip = tmp.newFile("out.zip");
        assertEquals(4, AgvnSaveTransfer.export(places, Collections.singletonList("\"k_h1\"=dword:00000001"), "VN", zip));
        try (InputStream in = new FileInputStream(zip)) { assertTrue(AgvnSaveTransfer.isAgvnZip(in)); }

        write(new File(game, "game/saves/1-1-LT1.save"), "changed");
        new File(profile, "AppData/Roaming/RenPy/vn/sub/x.save").delete();
        List<String> prefs = new ArrayList<>();
        try (InputStream in = new FileInputStream(zip)) { assertEquals(3, AgvnSaveTransfer.restore(in, places, prefs)); }
        assertEquals("slot1", read(new File(game, "game/saves/1-1-LT1.save")));
        assertEquals("deep", read(new File(profile, "AppData/Roaming/RenPy/vn/sub/x.save")));
        assertEquals(Collections.singletonList("\"k_h1\"=dword:00000001"), prefs);
        assertEquals("not a save", read(new File(game, "game/script.rpy")));
    }

    @Test
    public void restoreSkipsEntriesOutsideSavePlaces() throws Exception {
        File game = tmp.newFolder("rpg");
        List<AgvnSaveLocations.Location> places = AgvnSaveLocations.inGameFolder(game, "RPGMAKER");
        assertEquals(new File(game, "Save01.rvdata2").getCanonicalFile(), AgvnSaveTransfer.targetFor(places, "game/Save01.rvdata2"));
        assertNull(AgvnSaveTransfer.targetFor(places, "game/Game.exe"));
        assertNull(AgvnSaveTransfer.targetFor(places, "game/Data/Save01.rvdata2"));
        assertNull(AgvnSaveTransfer.targetFor(places, "profile/x.save"));
        assertNull(AgvnSaveTransfer.inside(game, "../escape.save"));
        assertNull(AgvnSaveTransfer.inside(game, ""));
    }

    @Test
    public void plainFilesAndZipsGoToTheMainPlace() throws Exception {
        File game = tmp.newFolder("rpg");
        AgvnSaveLocations.Location rpg = AgvnSaveLocations.inGameFolder(game, "RPGMAKER").get(0);
        assertEquals(1, AgvnSaveTransfer.copyInto(Arrays.asList(
                source("Save02.rvdata2", "two".getBytes(StandardCharsets.UTF_8)),
                source("Game.exe", "no".getBytes(StandardCharsets.UTF_8))), rpg));
        assertEquals("two", read(new File(game, "Save02.rvdata2")));
        assertFalse(new File(game, "Game.exe").exists());

        File vn = tmp.newFolder("vn");
        AgvnSaveLocations.Location saves = AgvnSaveLocations.inGameFolder(vn, "RENPY").get(0);
        File zip = tmp.newFile("pc-saves.zip");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
            for (String name : new String[]{"saves/1-1-LT1.save", "saves/persistent"}) {
                out.putNextEntry(new java.util.zip.ZipEntry(name));
                out.write(name.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        assertEquals(2, AgvnSaveTransfer.copyInto(Collections.singletonList(source("pc-saves.zip", Files.readAllBytes(zip.toPath()))), saves));
        assertTrue(new File(vn, "game/saves/1-1-LT1.save").isFile()); // the shared "saves/" top folder is dropped
        assertEquals("", AgvnSaveTransfer.sharedTopFolder(Arrays.asList("a/x", "b/y")));
    }

    @Test
    public void playerPrefsLinesSplitAndSurviveARoundTrip() throws Exception {
        assertArrayEquals(new String[]{"a\"b", "dword:00000002"}, AgvnSavePrefs.split("\"a\\\"b\"=dword:00000002"));
        assertArrayEquals(new String[]{null, "\"x\""}, AgvnSavePrefs.split("@=\"x\""));
        assertNull(AgvnSavePrefs.split("#time=1"));
        assertFalse(AgvnSavePrefs.supported("Software\\スタジオ\\Game"));

        // WineRegistryEditor renames over existing files, which java.io.File cannot do on Windows.
        Assume.assumeTrue("rename-over needs a POSIX file system", File.separatorChar == '/');
        File userReg = tmp.newFile("user.reg");
        write(userReg, "WINE REGISTRY Version 2\n\n[Software\\\\Studio\\\\Game] 1700000000\n#time=1\n"
                + "\"Level_h1\"=dword:00000007\n\"Name_h2\"=hex:41,42,\\\n  43,00\n\n[Software\\\\Other] 1\n\"x\"=dword:1\n");
        String key = "Software\\Studio\\Game";
        List<String> lines = AgvnSavePrefs.read(userReg, key);
        assertEquals(Arrays.asList("\"Level_h1\"=dword:00000007", "\"Name_h2\"=hex:41,42,43,00"), lines);

        File fresh = tmp.newFile("fresh.reg");
        write(fresh, "WINE REGISTRY Version 2\n\n");
        assertEquals(2, AgvnSavePrefs.write(fresh, key, lines));
        assertEquals(lines, AgvnSavePrefs.read(fresh, key));
    }
}
