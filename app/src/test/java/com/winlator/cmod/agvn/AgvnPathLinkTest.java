/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

/** A game in a folder named "Game " (Lifeguard Holic, 14/10/2026) starts through a link Windows can hold. */
public class AgvnPathLinkTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void findsTheLastFolderWindowsCannotHold() {
        assertEquals("/storage/emulated/0/Game ".length(), AgvnPathLink.lastBadFolderEnd("/storage/emulated/0/Game /LG/a.exe"));
        assertEquals("/s/Game /v1.".length(), AgvnPathLink.lastBadFolderEnd("/s/Game /v1./a.exe"));
        assertEquals("the exe's own name does not count", -1, AgvnPathLink.lastBadFolderEnd("/s/Game/a .exe"));
        assertEquals(-1, AgvnPathLink.lastBadFolderEnd("/s/./Game/../x/a.exe"));
        assertEquals(-1, AgvnPathLink.lastBadFolderEnd("/storage/emulated/0/Download/AGVN/Game/a.exe"));
    }

    @Test
    public void namesTheLinkWithoutTheSpace() {
        String name = AgvnPathLink.linkName(new File("/storage/emulated/0/Game "));
        assertTrue(name, name.matches("Game-[0-9a-f]{8}"));
        assertTrue(AgvnPathLink.linkName(new File("/s/ . ")).startsWith("game-"));
        assertTrue("another folder of the same name gets its own link",
                !name.equals(AgvnPathLink.linkName(new File("/storage/emulated/0/Download/Game "))));
    }

    @Test
    public void startsTheGameThroughALinkToItsOwnFolder() throws Exception {
        // Windows, where the release is built, trims "Game " itself and links need rights: this runs on a phone-like disk
        assumeTrue(File.separatorChar == '/');
        File storage = tmp.newFolder("storage"), driveC = tmp.newFolder("drive_c");
        File game = new File(storage, "Game /LifeguardHolic-GAMEHUB-AGVN");
        assertTrue(new File(game, "Lifeguard Holic_Data/StreamingAssets/aa").mkdirs());
        Files.write(new File(game, "Lifeguard Holic_Data/StreamingAssets/aa/settings.json").toPath(), new byte[]{'{', '}'});
        Map<String, String> drives = Collections.singletonMap("F", storage.getPath());

        String launch = "F:\\Game \\LifeguardHolic-GAMEHUB-AGVN\\Lifeguard Holic.exe -screen-fullscreen 1";
        String to = AgvnPathLink.apply(launch, drives, driveC);
        String name = AgvnPathLink.linkName(new File(storage, "Game "));
        assertEquals("\"C:\\agvn-links\\" + name + "\\LifeguardHolic-GAMEHUB-AGVN\\Lifeguard Holic.exe\" -screen-fullscreen 1", to);
        File through = new File(driveC, "agvn-links/" + name + "/LifeguardHolic-GAMEHUB-AGVN/Lifeguard Holic_Data/StreamingAssets/aa/settings.json");
        assertTrue("the game's own file, through the link", through.isFile());
        assertTrue(AgvnPathLink.takeNote().contains("\"Game \""));

        assertEquals("the link already there is used again", to, AgvnPathLink.apply(launch, drives, driveC));
        String unix = "\"" + game.getPath() + "/Lifeguard Holic.exe\"";
        assertEquals("a launch written as a phone path", to.replace(" -screen-fullscreen 1", ""),
                AgvnPathLink.apply(unix, drives, driveC));
    }

    @Test
    public void otherGamesStartAsBefore() throws Exception {
        File driveC = tmp.newFolder("drive_c");
        Map<String, String> drives = Collections.singletonMap("D", tmp.newFolder("Download").getPath());
        String launch = "\"D:\\AGVN\\Game\\Game.exe\"";
        assertSame(launch, AgvnPathLink.apply(launch, drives, driveC));
        String unmapped = "E:\\Game \\a.exe";
        assertSame("a drive the app does not know", unmapped, AgvnPathLink.apply(unmapped, drives, driveC));
        assertEquals(null, AgvnPathLink.takeNote());
    }
}
