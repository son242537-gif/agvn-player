/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Which games start through agvn-winhandler.exe because of the letters in their start (GAMEHUB, 10/10/2026). */
public class AgvnStarterTest {
    @Test
    public void lettersBeyondAsciiAnywhereInTheStart() {
        assertEquals("plain: Winlator's winhandler.exe as before", "",
                AgvnStarter.beyondAscii("/storage/emulated/0/Download/New Folder 1/GAMEHUB/Lifeguard Holic.exe", ""));
        assertEquals("ệ, ó", AgvnStarter.beyondAscii(
                "/storage/emulated/0/Download/Lifeguard Holic Việt Hóa/GAMEHUB/Lifeguard Holic.exe", ""));
        assertEquals("ゲ, ー, ム", AgvnStarter.beyondAscii("D:\\ゲーム\\Game.exe", null));
        assertEquals("the game's own arguments go on the same command line", "ê",
                AgvnStarter.beyondAscii("D:\\Game\\Game.exe", "-name Tên"));
        assertEquals("no shortcut: the container's own programs", "", AgvnStarter.beyondAscii(null, null));
    }
}
