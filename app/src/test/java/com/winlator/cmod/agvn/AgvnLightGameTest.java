/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Phone copies of "Chạy nhẹ" games that come without the Windows .exe (JoiPlay packs). */
public class AgvnLightGameTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static File write(File dir, String rel, String text) throws Exception {
        File f = new File(dir, rel);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    public void exeLessRgssGameImportsWithAPlaceholderExe() throws Exception {
        File ace = tmp.newFolder("Ace");
        write(ace, "Game.ini", "[Game]\nLibrary=System\\RGSS301.dll\nRTP=RPGVXAce\n");
        write(ace, "Game.rgss3a", "");
        assertTrue(AgvnLightGame.canRun(ace, GameExeResolver.detectEngine(ace)));
        assertEquals("Game.exe", AgvnProfileValidator.validate(AgvnProfile.defaultFor("Ace"), ace));
        assertEquals("Game.exe", AgvnLightGame.placeholderExe(ace, GameExeResolver.Engine.RPGMAKER));
    }

    @Test
    public void exeLessMvGameImportsAndAFolderWithoutGameStillFails() throws Exception {
        File mv = tmp.newFolder("MV");
        write(mv, "www/index.html", "");
        write(mv, "www/js/rpg_core.js", "");
        assertEquals("Game.exe", AgvnProfileValidator.validate(AgvnProfile.defaultFor("MV"), mv));

        File page = tmp.newFolder("Page");
        write(page, "index.html", "");
        try {
            AgvnProfileValidator.validate(AgvnProfile.defaultFor("Page"), page);
            fail("a web page without a game engine is not a game");
        } catch (AgvnProfileException expected) {
            assertTrue(expected.getMessage().contains(".exe"));
        }
    }

    @Test
    public void exeLessWebGameWithItsPackageJsonImports() throws Exception {
        // a Cocos Creator page game without NW.js (POCO F8 Pro, 03/11/2026): "Không tìm thấy file .exe"
        File web = tmp.newFolder("Web");
        write(web, "index.html", "<canvas></canvas>");
        write(web, "package.json", "{\"name\":\"web\",\"main\":\"index.html\",\"window\":{\"width\":1280}}");
        assertEquals(GameExeResolver.Engine.UNKNOWN, GameExeResolver.detectEngine(web));
        assertTrue(AgvnLightGame.canRun(web, GameExeResolver.Engine.UNKNOWN));
        assertTrue("listed by \"Thêm game\" without a profile", AgvnGameScanner.isGameDir(web));
        AgvnProfile html = AgvnProfile.defaultFor("Web");
        html.runner = "html";
        assertEquals("Game.exe", AgvnProfileValidator.validate(html, web));
        assertEquals("Game.exe", AgvnProfileValidator.validate(AgvnProfile.defaultFor("Web"), web));

        File node = tmp.newFolder("Node");
        write(node, "index.html", "");
        write(node, "package.json", "{\"main\":\"index.html\",\"window\":{},\"node-main\":\"boot.js\"}");
        assertFalse("one that needs Node runs only on Windows, which it has no exe for",
                AgvnLightGame.canRun(node, GameExeResolver.Engine.UNKNOWN));
    }

    @Test
    public void pickedGameFilesLeadToTheGameFolder() throws Exception {
        File mv = tmp.newFolder("MVgame");
        assertEquals(mv, AgvnLightGame.folderOf(new File(mv, "www/index.html")));
        assertEquals(mv, AgvnLightGame.folderOf(new File(mv, "Game.ini")));
        assertTrue(AgvnLightGame.isGameFile("index.html"));
        assertTrue(AgvnLightGame.isGameFile("Game.ini"));
        assertTrue(AgvnLightGame.isGameFile("Game.rgss3a"));
        assertTrue(AgvnLightGame.isGameFile("Data.RGSSAD"));
        assertFalse(AgvnLightGame.isGameFile("config.ini"));
        assertFalse(AgvnLightGame.isGameFile("readme.txt"));
    }

    @Test
    public void windowsIsOfferedOnlyWhenTheShortcutsExeExists() throws Exception {
        File game = tmp.newFolder("Game");
        File exe = write(game, "Game.exe", "");
        String exec = "Exec=env WINEPREFIX=\"/data/user/0/com.agvn.player/files/imagefs/home/xuser-1/.wine\" wine \"";
        File withExe = write(game, "a.desktop", "[Desktop Entry]\nName=A\n" + exec + exe.getAbsolutePath() + "\"\n");
        File phoneCopy = write(game, "b.desktop", "[Desktop Entry]\nName=B\n" + exec + new File(game, "Missing.exe").getAbsolutePath() + "\"\n");
        File noExec = write(game, "c.desktop", "[Desktop Entry]\nName=C\n");
        assertTrue(AgvnHtmlGame.exeExists(withExe));
        assertFalse(AgvnHtmlGame.exeExists(phoneCopy));
        assertTrue(AgvnHtmlGame.exeExists(noExec));
    }
}
