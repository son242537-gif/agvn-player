/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/** A new version of a game in the library shares the old one's container and gets the saves of its folder. */
public class AgvnGameVersionsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File write(String path, String text) throws Exception {
        File f = new File(tmp.getRoot(), path);
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private List<AgvnSaveLocations.Location> places(String folder, String engine) {
        return AgvnSaveLocations.inGameFolder(new File(tmp.getRoot(), folder), engine);
    }

    @Test
    public void theTitleWithoutItsVersion() {
        assertEquals("thornsin viet", AgvnGameVersions.key("Thornsin v0.7.5 Viet"));
        assertEquals("thornsin viet", AgvnGameVersions.key("Thornsin v0.7.6 Viet"));
        assertEquals("the too cute girl next door vi",
                AgvnGameVersions.key("The Too Cute Girl Next Door v1.044 VI"));
        assertEquals("duong ve", AgvnGameVersions.key("Đường Về 1.2"));
        // a sequel's number is not a version
        assertEquals("zombie retreat 2", AgvnGameVersions.key("Zombie Retreat 2"));
    }

    @Test
    public void sameGameAnotherVersion() {
        // Thorn Sin, 08/10/2026: AGVN's new release in a new folder; Unity keys saves on company and product
        String[] thorn = {"Studio", "ThornSin"};
        assertTrue(AgvnGameVersions.sameGame("Thornsin v0.7.6 Viet", "ThornSin.exe", thorn,
                "Thorn Sin", "thornsin.exe", new String[]{"studio", "thornsin"}));
        assertFalse(AgvnGameVersions.sameGame("Thornsin v0.7.6 Viet", "ThornSin.exe", thorn,
                "Thornsin v0.7.5 Viet", "ThornSin.exe", new String[]{"Studio", "OtherGame"}));
        // no app.info: the title without versions, and the same exe
        assertTrue(AgvnGameVersions.sameGame("Rina v1.2", "Game.exe", null, "Rina v1.1", "game.exe", null));
        assertFalse(AgvnGameVersions.sameGame("Rina v1.2", "Game.exe", null, "Other Game v1.1", "Game.exe", null));
        assertFalse(AgvnGameVersions.sameGame("Zombie Retreat 2", "Game.exe", null,
                "Zombie Retreat", "Game.exe", null));
        assertFalse(AgvnGameVersions.sameGame("Rina v1.2", "Rina.exe", null, "Rina v1.1", "Game.exe", null));
    }

    @Test
    public void savesOfTheOldFolderGoToTheNewOne() throws Exception {
        write("old/www/save/file1.rpgsave", "a");
        write("old/www/save/global.rpgsave", "g");
        new File(tmp.getRoot(), "new/www").mkdirs();
        List<AgvnSaveLocations.Location> from = places("old", "RPGMAKER_MV"), to = places("new", "RPGMAKER_MV");
        assertEquals(1, AgvnGameVersions.copyFolderSaves(from, to));
        assertTrue(new File(tmp.getRoot(), "new/www/save/file1.rpgsave").isFile());
        assertTrue(new File(tmp.getRoot(), "new/www/save/global.rpgsave").isFile());
    }

    @Test
    public void aSaveOfTheNewVersionIsNeverOverwritten() throws Exception {
        write("old/game/saves/1-1-LT1.save", "old");
        File kept = write("new/game/saves/1-1-LT1.save", "new");
        List<AgvnSaveLocations.Location> from = places("old", "RENPY"), to = places("new", "RENPY");
        assertEquals(0, AgvnGameVersions.copyFolderSaves(from, to));
        assertEquals("new", new String(Files.readAllBytes(kept.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void onlyTheSaveFilesOfAGameFolder() throws Exception {
        // RPG Maker XP/VX/Ace keep Save*.rvdata2 next to the game's own files
        write("old/Save01.rvdata2", "s");
        write("old/Game.ini", "game file");
        new File(tmp.getRoot(), "new").mkdirs();
        List<AgvnSaveLocations.Location> from = places("old", "RPGMAKER"), to = places("new", "RPGMAKER");
        assertEquals(1, AgvnGameVersions.copyFolderSaves(from, to));
        assertTrue(new File(tmp.getRoot(), "new/Save01.rvdata2").isFile());
        assertFalse(new File(tmp.getRoot(), "new/Game.ini").exists());
        // profile places are the container's, shared already: never copied
        AgvnSaveLocations.Location profile = new AgvnSaveLocations.Location(
                new File(tmp.getRoot(), "old"), "profile/AppData/LocalLow/a/b", null);
        assertEquals(0, AgvnGameVersions.copyFolderSaves(Collections.singletonList(profile), to));
    }
}
