/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AgvnArchiveNamesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String decoded(String name, String storedAs) {
        List<byte[]> raw = new ArrayList<>();
        raw.add("data/readme.txt".getBytes(StandardCharsets.US_ASCII));
        raw.add(name.getBytes(Charset.forName(storedAs)));
        return new String(raw.get(1), AgvnArchiveNames.charsetFor(raw));
    }

    @Test
    public void namesOfOldZipsAreReadInTheirOwnCharset() {
        Assume.assumeTrue(Charset.isSupported("windows-31j") && Charset.isSupported("GBK") && Charset.isSupported("IBM437"));
        assertEquals("Truyện kể/Chương 1.txt", decoded("Truyện kể/Chương 1.txt", "UTF-8"));
        assertEquals("ゲーム/セーブデータ.dat", decoded("ゲーム/セーブデータ.dat", "windows-31j"));
        assertEquals("画像/立ち絵①.png", decoded("画像/立ち絵①.png", "windows-31j")); // ① is CP932 only, not Shift-JIS
        assertEquals("游戏/存档说明.txt", decoded("游戏/存档说明.txt", "GBK"));
        assertEquals("Pokémon/café.txt", decoded("Pokémon/café.txt", "IBM437"));
        assertEquals("plain.txt", decoded("plain.txt", "US-ASCII"));
    }

    @Test
    public void entryPathsAreCleaned() {
        assertEquals("Game/www/index.html", AgvnArchiveNames.clean("Game\\www\\index.html"));
        assertEquals("Game/a.txt", AgvnArchiveNames.clean("/Game//./a.txt"));
        assertEquals("Windows/x.dll", AgvnArchiveNames.clean("C:\\Windows\\x.dll"));
        assertEquals("Chương 1_ Mở đầu_.txt", AgvnArchiveNames.clean("Chương 1: Mở đầu?.txt"));
        assertEquals("Game", AgvnArchiveNames.clean("Game/"));
        assertNull(AgvnArchiveNames.clean("../../Download/evil.sh"));
        assertNull(AgvnArchiveNames.clean("Game/..\\..\\evil.sh"));
        assertNull(AgvnArchiveNames.clean("/"));
    }

    @Test
    public void nothingIsWrittenOutsideTheFolder() throws Exception {
        File root = tmp.newFolder("game");
        assertEquals(new File(root, "a/b.txt"), AgvnArchiveNames.target(root, "a/b.txt"));
        assertNull(AgvnArchiveNames.target(root, "../game2/b.txt"));
        assertNull(AgvnArchiveNames.target(root, null));
        assertNotNull(AgvnArchiveNames.target(root, "..a/b"));
    }

    @Test
    public void aLoneTopFolderIsFound() {
        assertEquals("My Game", AgvnArchiveNames.singleTop(Arrays.asList("My Game", "My Game/a.exe", "My Game/data/b", null)));
        assertEquals("Ver1", AgvnArchiveNames.singleTop(Arrays.asList("Ver1/a.exe")));
        assertNull("loose files", AgvnArchiveNames.singleTop(Arrays.asList("a.exe", "data/b")));
        assertNull("two folders", AgvnArchiveNames.singleTop(Arrays.asList("Ver1/a.exe", "Bonus/b")));
        assertNull("a lone file", AgvnArchiveNames.singleTop(Arrays.asList("a.exe")));
        assertNull("part of the game, not its folder", AgvnArchiveNames.singleTop(Arrays.asList("www/index.html", "www/js/a.js")));
        assertNull(AgvnArchiveNames.singleTop(new ArrayList<String>()));
    }

    @Test
    public void folderNamesAreSafeAndFree() throws Exception {
        assertEquals("Game 1.0", AgvnArchiveNames.folderName(" Game 1.0. "));
        assertEquals("a_b_c", AgvnArchiveNames.folderName("a/b:c"));
        assertEquals("Game", AgvnArchiveNames.folderName(".."));
        assertEquals("Game", AgvnArchiveNames.folderName(null));
        File root = tmp.newFolder("AGVN");
        assertEquals(new File(root, "Game"), AgvnArchiveNames.freeFolder(root, "Game"));
        assertTrue(new File(root, "Game").mkdir());
        assertEquals(new File(root, "Game (2)"), AgvnArchiveNames.freeFolder(root, "Game"));
        assertTrue(new File(root, "Game (2)").createNewFile()); // a file of that name counts too
        assertEquals(new File(root, "Game (3)"), AgvnArchiveNames.freeFolder(root, "Game"));
        assertEquals(new File(root, ".Game.agvn-dang-giai-nen"), AgvnArchiveNames.partialFolder(root, "Game"));
    }
}
