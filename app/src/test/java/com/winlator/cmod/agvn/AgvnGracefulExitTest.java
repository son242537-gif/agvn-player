/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** "Thoát" waits for the game's own process: it is known by the name of its exe. */
public class AgvnGracefulExitTest {
    @Test
    public void theExeOfAPathOrACommandLine() {
        // Wine names a process after its Windows path; the launch path may be Android's, quoted, with arguments
        assertEquals("thornsin.exe",
                AgvnGracefulExit.exeName("D:\\_.normedia\\THORNSIN-v0.7.5\\ThornSin\\ThornSin.exe"));
        assertEquals("game.exe", AgvnGracefulExit.exeName("\"/storage/emulated/0/Download/Rina/Game.exe\" -windowed"));
        assertEquals("avdirectorlife-win64-shipping.exe",
                AgvnGracefulExit.exeName("D:\\AV\\AVDirectorLife\\Binaries\\Win64\\AVDirectorLife-Win64-Shipping.exe"));
        assertEquals("", AgvnGracefulExit.exeName(null));
    }
}
