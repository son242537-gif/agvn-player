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
}
