/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** "Toàn màn hình cho mọi game": the game's own choice first, then its environment's, then the setting. */
public class AgvnFullscreenTest {
    @Test
    public void theGameChoosesFirst() {
        assertTrue(AgvnFullscreen.stretch("1", false, false));
        assertFalse("a game kept in proportion stays so", AgvnFullscreen.stretch("0", true, true));
        assertTrue(AgvnFullscreen.stretch("", false, true));
        assertTrue(AgvnFullscreen.stretch(null, true, false));
        assertFalse("off by default", AgvnFullscreen.stretch("", false, false));
    }

    @Test
    public void anEditorLeavesTheGameToTheSetting() {
        // saved as it was shown: no choice of its own, so the setting still applies to it later
        assertNull(AgvnFullscreen.toStore(true, "", "1"));
        assertNull(AgvnFullscreen.toStore(false, null, "0"));
        // changed: its own choice from now on
        assertEquals("0", AgvnFullscreen.toStore(false, "", "1"));
        assertEquals("1", AgvnFullscreen.toStore(true, "", "0"));
        assertEquals("1", AgvnFullscreen.toStore(true, "1", "1"));
    }
}
