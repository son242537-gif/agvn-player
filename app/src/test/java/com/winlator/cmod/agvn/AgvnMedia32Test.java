/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.EnvVars;

import org.junit.Test;

import java.util.Arrays;

/**
 * I will purify you (Unity 2022.3 IL2CPP x86, POCO F8 Pro, 16/11/2026): Proton 9's new media source got no data of the
 * game's logo movie ("data 0x0, size 658352"), as for every 32-bit program, and the game stayed black.
 */
public class AgvnMedia32Test {
    private static final String OLD = AgvnMediaFoundation.OLD_MEDIA_SOURCE;

    @Test
    public void aThirtyTwoBitGameReadsMoviesTheOldWayFromTheStart() {
        AgvnMedia32.takeNote();
        EnvVars env = new EnvVars("WINEESYNC=1");
        AgvnMedia32.apply(env, true);
        assertEquals("1", env.get(OLD));
        assertTrue(AgvnMediaFoundation.oldSource(env.get(OLD)));
        String note = AgvnMedia32.takeNote();
        assertTrue(note, note.contains("32-bit") && note.contains(OLD + "=1"));
        assertNull("told once", AgvnMedia32.takeNote());
    }

    @Test
    public void aSixtyFourBitGameKeepsTheNewWay() {
        EnvVars env = new EnvVars("WINEESYNC=1");
        AgvnMedia32.apply(env, false);
        assertFalse(env.has(OLD));
        assertNull(AgvnMedia32.takeNote());
    }

    @Test
    public void whatThePlayerSetStays() {
        EnvVars off = new EnvVars(OLD + "=0");
        AgvnMedia32.apply(off, true);
        assertEquals("the player wants the new way", "0", off.get(OLD));
        assertNull(AgvnMedia32.takeNote());
        EnvVars on = new EnvVars(OLD + "=1");
        AgvnMedia32.apply(on, true);
        assertEquals("1", on.get(OLD));
    }

    @Test
    public void theFaultIsToldByItsLineAndOfferedTheOldWay() throws Exception {
        String wine = "(wine:19523): GStreamer-Base-CRITICAL **: 18:31:30.598: gst_type_find_helper_for_data_with_extension:"
                + " assertion 'data != NULL' failed";
        assertTrue(wine.contains(AgvnMedia32.NO_DATA));
        AgvnProblemCatalog catalog;
        try (java.io.Reader r = java.nio.file.Files.newBufferedReader(
                new java.io.File("src/main/assets/" + AgvnProblemCatalog.ASSET).toPath())) {
            catalog = AgvnProblemCatalog.parse(r);
        }
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = true;
        ev.playerQuit = true; // black after the splash: the player left
        ev.seconds = 120;
        ev.newMediaSource = true; // a 64-bit launcher's 32-bit game, or the variable set to 0
        ev.lines.addAll(Arrays.asList("info:  DXVK: Using 8 compiler threads", wine));
        AgvnProblemCatalog.Finding found = catalog.find(ev);
        assertEquals("movie-no-data", found.id());
        assertEquals(Arrays.asList("media-old", "send-logs"), found.fixes());
        ev.newMediaSource = false; // reads the old way already: nothing to offer
        AgvnProblemCatalog.Finding none = catalog.find(ev);
        assertTrue(none == null || !"movie-no-data".equals(none.id()));
    }
}
