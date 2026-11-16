/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * I will purify you (16/11/2026): args added to agvn-profile.json after the game was added were not taken ("Tham số
 * chạy: (không có)"), while the env beside them was.
 */
public class AgvnProfileArgsTest {
    private static final String WINDOWED = "-popupwindow -screen-fullscreen 0";

    @Test
    public void whatTheProfileGivesIsItsArgsInOrder() throws Exception {
        AgvnProfile p = AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"G\",\"args\":[\"-popupwindow\","
                + "\"-screen-fullscreen\",\"0\"]}");
        AgvnProfileValidator.checkArgs(p);
        assertEquals(WINDOWED, AgvnProfileArgs.given(p));
        assertEquals(WINDOWED, AgvnProfileArgs.record(WINDOWED));
        assertEquals("none is told apart from no record", AgvnProfileArgs.NONE, AgvnProfileArgs.record(""));
    }

    @Test
    public void argsAddedLaterAreTakenAndTheRestStays() {
        assertEquals(WINDOWED, AgvnProfileArgs.apply("", "", WINDOWED));
        assertEquals("what a Doctor fix or the player added stays", WINDOWED + " -force-d3d11",
                AgvnProfileArgs.apply("-force-d3d11", "", WINDOWED));
        assertEquals("there already", WINDOWED, AgvnProfileArgs.apply(WINDOWED, "", WINDOWED));
    }

    @Test
    public void argsTheProfileChangedReplaceWhatItGaveBefore() {
        assertEquals("-window-mode borderless -force-d3d11",
                AgvnProfileArgs.apply("-screen-fullscreen 0 -force-d3d11", "-screen-fullscreen 0", "-window-mode borderless"));
        assertEquals("args the profile no longer has go", "-force-d3d11",
                AgvnProfileArgs.apply("-force-d3d11 -screen-fullscreen 0", "-screen-fullscreen 0", ""));
        assertEquals("", AgvnProfileArgs.apply("-dx11", "-dx11", ""));
        assertEquals("the player changed them since: the new ones are put first, the player's stay", "-dx12 -dx11x",
                AgvnProfileArgs.apply("-dx11x", "-dx11", "-dx12"));
        assertEquals("nothing changed in the profile: the player's removal stays", "",
                AgvnProfileArgs.apply("", "-dx11", "-dx11"));
        assertEquals("only whole arguments count", -1, AgvnProfileArgs.whole("-dx11x -a", "-dx11"));
        assertEquals(3, AgvnProfileArgs.whole("-a -dx11 -b", "-dx11"));
    }

    /** A game added before the record was kept: nothing a player or a fix chose is undone on a guess. */
    @Test
    public void aGameWithNoRecordIsChangedOnlyWhereTheProfileIsKnownToHaveChanged() {
        // the profile's other file still has what the import took
        assertEquals("unchanged profile: a renderer a fix switched stays", "--rendering-driver vulkan",
                AgvnProfileArgs.unrecorded("--rendering-driver vulkan", "--rendering-driver opengl3",
                        "--rendering-driver opengl3"));
        assertEquals("edited profile: its new args replace the old ones", WINDOWED + " -force-d3d11",
                AgvnProfileArgs.unrecorded("-dx11 -force-d3d11", "-dx11", WINDOWED));
        assertEquals("edited profile, a game added with none", WINDOWED, AgvnProfileArgs.unrecorded("", "", WINDOWED));
        // no other file to tell
        assertEquals("a game with no arguments takes the profile's", WINDOWED,
                AgvnProfileArgs.unrecorded("", null, WINDOWED));
        assertEquals("a game with arguments keeps them", "-force-vulkan",
                AgvnProfileArgs.unrecorded("-force-vulkan", null, "-force-d3d11"));
    }

    @Test(expected = AgvnProfileException.class)
    public void anArgWithUnsafeCharactersIsRefusedAtAStartToo() throws Exception {
        AgvnProfileValidator.checkArgs(AgvnProfile.parse("{\"schemaVersion\":1,\"name\":\"G\",\"args\":[\"a;rm\"]}"));
    }
}
