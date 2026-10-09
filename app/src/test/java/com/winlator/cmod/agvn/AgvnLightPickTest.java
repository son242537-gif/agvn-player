/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** A "Chạy nhẹ" game starts from the key profile picked for it in its settings ("Cấu hình phím ảo"). */
public class AgvnLightPickTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void whatThePlayerPickedForTheGame() {
        assertEquals(12, AgvnLightPick.picked("12", null)); // "Nym", made in "Điều khiển" (07/10/2026)
        assertEquals("an AGVN layout the player picked", 9002, AgvnLightPick.picked("9002", null));
        assertEquals("the layout AGVN picked at import", AgvnLightPick.NONE, AgvnLightPick.picked("9002", "1"));
        assertEquals(AgvnLightPick.OFF, AgvnLightPick.picked("0", null));
        assertEquals(AgvnLightPick.NONE, AgvnLightPick.picked("", null));
        assertEquals(AgvnLightPick.NONE, AgvnLightPick.picked(null, null));
        assertEquals("the old single AGVN profile", AgvnLightPick.NONE, AgvnLightPick.picked("900", null));
        assertEquals(AgvnLightPick.NONE, AgvnLightPick.picked("abc", null));
    }

    @Test
    public void aNewPickShowsItsKeysAndAnOwnSetKeepsItsBase() throws Exception {
        File dir = tmp.newFolder("files");
        String game = "/sdcard/Games/Nym";
        AgvnLightPrefs prefs = new AgvnLightPrefs(dir);
        prefs.setKeysHidden(game, true);
        prefs.setLayout(game, "{\"elements\":[]}", 0); // edited on the game, from AGVN's keys
        assertEquals("before any pick", 0, prefs.lastPick(game));
        prefs.picked(game, 12);
        AgvnLightPrefs later = new AgvnLightPrefs(dir);
        assertFalse("a profile picked for the game shows", later.keysHidden(game));
        assertEquals(12, later.lastPick(game));
        assertEquals("the own set was made from AGVN's keys, not from the pick", 0, later.layoutBase(game));
        later.setLayout(game, "{\"elements\":[]}", 12);
        assertEquals(12, new AgvnLightPrefs(dir).layoutBase(game));
        later.setKeysHidden(game, true);
        later.picked(game, AgvnLightPick.OFF);
        assertTrue("\"Tắt\" leaves the keys as they were", new AgvnLightPrefs(dir).keysHidden(game));
    }

    @Test
    public void aProfileSavedOutsideAfterTheGamesOwnKeysWins() throws Exception {
        assertTrue("the profile was saved before the game's own set", AgvnLightPick.ownIsNewest(2_000, 1_000));
        assertFalse("saved in \"Điều khiển\" after it (09/10/2026)", AgvnLightPick.ownIsNewest(1_000, 2_000));
        assertTrue("AGVN's layout as installed: an update rewrites it", AgvnLightPick.ownIsNewest(1_000, 0));

        File dir = tmp.newFolder("light");
        String game = "/sdcard/Games/Nym";
        AgvnLightPrefs prefs = new AgvnLightPrefs(dir);
        long before = System.currentTimeMillis();
        prefs.setLayout(game, "{\"elements\":[]}", 12);
        long at = new AgvnLightPrefs(dir).layoutAt(game);
        assertTrue("kept with the time it was made", at >= before && at <= System.currentTimeMillis());

        File file = new File(dir, AgvnLightPrefs.FILE_NAME); // a set kept before 0.1.33 has no time
        String old = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(file.toPath(), old.replaceAll("(?m)^layoutAt\\..*$", "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(123_000));
        assertEquals("then the file's last write, at or after it", 123_000, new AgvnLightPrefs(dir).layoutAt(game));
    }
}
