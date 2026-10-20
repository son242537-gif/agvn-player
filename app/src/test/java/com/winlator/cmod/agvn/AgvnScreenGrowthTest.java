/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnScreenGrowthTest {
    @Test
    public void aWindowThatGrowsWithItsScreenGetsNoLargerOne() {
        // Party Me on a Mali-G615: its window reached 12 px past every screen it got, 1080x664, 1092x676, 1104x688
        assertEquals("12x12", AgvnScreenGrowth.past(1092, 676, 1080, 664));
        assertTrue(AgvnScreenGrowth.followed("12x12", "1092x676", 1104, 688, 1092, 676));
        assertTrue("an odd size made even", AgvnScreenGrowth.followed("12x12", "1092x676", 1105, 689, 1092, 676));
        assertFalse("the screen changed since", AgvnScreenGrowth.followed("12x12", "1092x676", 1292, 732, 1280, 720));
        // Monster Black Market (Unity, a 1280x720 window with its frame): 1288x746 on 1280x720, then whole on 1288x746
        assertEquals("8x26", AgvnScreenGrowth.past(1288, 746, 1280, 720));
        assertFalse(AgvnScreenGrowth.followed("8x26", "1288x746", 1288, 746, 1288, 746));
        assertFalse("no note", AgvnScreenGrowth.followed("", "", 1104, 688, 1092, 676));
        assertFalse(AgvnScreenGrowth.followed(null, null, 1104, 688, 1092, 676));
        assertFalse(AgvnScreenGrowth.followed("axb", "1092x676", 1104, 688, 1092, 676));
        assertFalse("never past it", AgvnScreenGrowth.followed("0x0", "1092x676", 1092, 676, 1092, 676));
    }

    @Test
    public void theProgramsThatStartAGameAreNotIt() {
        // Monster Black Market's exe was gone: winhandler.exe's 144x106 "File not found." box was zoomed as the game
        assertTrue(AgvnGameWindow.starter("explorer.exe"));
        assertTrue(AgvnGameWindow.starter("winhandler.exe"));
        assertTrue(AgvnGameWindow.starter("START.EXE"));
        assertFalse(AgvnGameWindow.starter("GameStart.exe"));
        assertFalse(AgvnGameWindow.starter("MonsterBlackMarket.exe"));
        assertFalse(AgvnGameWindow.starter(""));
    }
}
