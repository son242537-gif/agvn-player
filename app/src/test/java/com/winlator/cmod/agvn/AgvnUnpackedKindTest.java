/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.winlator.cmod.agvn.AgvnUnpackedKind.Kind;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** What an unpacked archive holds when it is no game the app can add, and what goes with the outer folder. */
public class AgvnUnpackedKindTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File dir(String name, String... files) throws Exception {
        File dir = tmp.newFolder(name);
        for (String f : files) {
            if (f.endsWith("/")) new File(dir, f).mkdirs();
            else AgvnUnpackFixtures.write(new File(dir, f), AgvnUnpackFixtures.bytes("x"));
        }
        return dir;
    }

    @Test
    public void aGameIsTheScannersWord() throws Exception {
        assertEquals(Kind.GAME, AgvnUnpackedKind.classify(dir("unity", "Game.exe", "UnityPlayer.dll", "Game_Data/app.info")).kind);
        assertEquals("a wrapper folder around the game", Kind.GAME, AgvnUnpackedKind.classify(dir("wrap", "Hoa/Hoa.exe", "Hoa/UnityPlayer.dll")).kind);
        assertEquals("a phone copy without an exe that Chạy nhẹ runs", Kind.GAME,
                AgvnUnpackedKind.classify(dir("mv", "www/index.html", "www/js/rpg_core.js", "www/data/System.json", "package.json")).kind);
        assertEquals("an exe of an engine no rule knows, with the game's data beside it", Kind.GAME,
                AgvnUnpackedKind.classify(dir("unsure", "X/play.exe", "X/data.pak")).kind);
        assertEquals("a game with its Android version beside it is a game", Kind.GAME,
                AgvnUnpackedKind.classify(dir("both", "Game.exe", "UnityPlayer.dll", "Game_Data/app.info", "game.apk")).kind);
    }

    @Test
    public void theAndroidVersionIsNamed() throws Exception {
        File d = dir("apk", "Hoa/hoa-1.2.apk", "Hoa/readme.txt");
        AgvnUnpackedKind k = AgvnUnpackedKind.classify(d);
        assertEquals(Kind.ANDROID, k.kind);
        assertEquals("the one apk can be installed", new File(d, "Hoa/hoa-1.2.apk"), k.file);
        k = AgvnUnpackedKind.classify(dir("apks", "a.apk", "b.apk", "main.1.com.game.obb"));
        assertEquals(Kind.ANDROID, k.kind);
        assertNull("several: the player chooses in the file manager", k.file);
        k = AgvnUnpackedKind.classify(dir("xapk", "game.xapk"));
        assertEquals(Kind.ANDROID, k.kind);
        assertNull("not a file Android's installer takes", k.file);
        assertEquals(Kind.ANDROID, AgvnUnpackedKind.classify(dir("obb", "Android/obb/com.game/main.1.com.game.obb")).kind);
        assertEquals("with an installer for PC beside it: not said to be Android", Kind.NOTHING,
                AgvnUnpackedKind.classify(dir("mixed", "setup.exe", "game.apk")).kind);
    }

    @Test
    public void anArchiveInTheArchiveIsFound() throws Exception {
        File d = dir("inner", "Hoa/Hoa.rar", "Hoa/Pass.txt", "Hoa/link.url");
        AgvnUnpackedKind k = AgvnUnpackedKind.classify(d);
        assertEquals(Kind.INNER_ARCHIVE, k.kind);
        assertEquals(new File(d, "Hoa/Hoa.rar"), k.file);
        d = dir("set", "Game.7z.001", "Game.7z.002", "Game.7z.003");
        k = AgvnUnpackedKind.classify(d);
        assertEquals("a split archive is one archive", Kind.INNER_ARCHIVE, k.kind);
        assertEquals(new File(d, "Game.7z.001"), k.file);
        d = dir("series", "Game.part1.zip", "Game.part2.zip");
        assertEquals(new File(d, "Game.part1.zip"), AgvnUnpackedKind.classify(d).file);
        assertEquals("two archives: the player picks", Kind.NOTHING, AgvnUnpackedKind.classify(dir("two", "A.zip", "B.zip")).kind);
        assertEquals("the Android version with its data packed", Kind.ANDROID, AgvnUnpackedKind.classify(dir("apkzip", "a.apk", "obb.zip")).kind);
    }

    @Test
    public void anotherSystemsVersionIsNamed() throws Exception {
        AgvnUnpackedKind k = AgvnUnpackedKind.classify(dir("mac", "Hoa.app/Contents/MacOS/Hoa", "Hoa.app/Contents/Info.plist"));
        assertEquals(Kind.OTHER_OS, k.kind);
        assertEquals("macOS", k.system);
        assertEquals("macOS", AgvnUnpackedKind.classify(dir("dmg", "Hoa.dmg")).system);
        k = AgvnUnpackedKind.classify(dir("linux", "Hoa/Hoa.x86_64", "Hoa/Hoa_Data/level0", "Hoa/UnityPlayer.so"));
        assertEquals(Kind.OTHER_OS, k.kind);
        assertEquals("Linux", k.system);
        assertEquals("a patch, pictures, a text", Kind.NOTHING, AgvnUnpackedKind.classify(dir("patch", "data/Map001.json", "readme.txt")).kind);
        assertEquals(Kind.NOTHING, AgvnUnpackedKind.classify(dir("empty")).kind);
        assertEquals("a lone installer", Kind.NOTHING, AgvnUnpackedKind.classify(dir("setup", "setup.exe")).kind);
    }

    @Test
    public void theOuterFolderGoesOnceTheInnerArchiveIsOut() throws Exception {
        File games = tmp.newFolder("AGVN");
        File outer = new File(games, "Hoa"), game = new File(games, "Hoa (2)");
        AgvnUnpackFixtures.write(new File(outer, "Hoa.rar"), AgvnUnpackFixtures.bytes("rar"));
        AgvnUnpackFixtures.write(new File(game, "Hoa.exe"), AgvnUnpackFixtures.bytes("MZ"));
        File now = AgvnUnpackCheck.replaceOuter(outer, game, games);
        assertEquals("the game takes the name the outer folder had", outer, now);
        assertEquals("MZ", AgvnUnpackFixtures.read(new File(games, "Hoa/Hoa.exe")));
        assertEquals(1, games.list().length);
        // a folder that is not directly in the games folder is never removed
        File far = tmp.newFolder("Download", "Hoa");
        AgvnUnpackFixtures.write(new File(far, "keep.txt"), AgvnUnpackFixtures.bytes("x"));
        assertEquals(now, AgvnUnpackCheck.replaceOuter(far, now, games));
        assertEquals(1, far.list().length);
        assertEquals("the game itself is never removed", now, AgvnUnpackCheck.replaceOuter(now, now, games));
        assertEquals(1, now.list().length);
    }

    @Test
    public void whereACopyIsIsSaidInThePlayersWords() {
        assertEquals("Bộ nhớ trong/Download/Hoa", AgvnImportTwin.where("/storage/emulated/0/Download/Hoa", "/storage/emulated/0", "Bộ nhớ trong"));
        assertEquals("Bộ nhớ trong", AgvnImportTwin.where("/storage/emulated/0", "/storage/emulated/0", "Bộ nhớ trong"));
        assertEquals("/storage/1A2B-3C4D/Games/Hoa", AgvnImportTwin.where("/storage/1A2B-3C4D/Games/Hoa", "/storage/emulated/0", "Bộ nhớ trong"));
        assertEquals("/storage/emulated/01/x", AgvnImportTwin.where("/storage/emulated/01/x", "/storage/emulated/0", "Bộ nhớ trong"));
    }
}
