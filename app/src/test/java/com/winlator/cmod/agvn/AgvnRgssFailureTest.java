/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which errors of a "Chạy nhẹ" RPG Maker game Windows may get past, and which are the game's scripts' own. */
public class AgvnRgssFailureTest {
    @Test
    public void aScriptErrorSaysWhereItStopped() {
        // report of 21/10/2026: a light-effect script's sprites outlived their viewport (mkxp-z 0.1.62)
        assertTrue(AgvnRgssFailure.scriptError("Script 'Speed test' line 12: RGSSError occurred.\n\n"
                + "disposed viewport\n\nSpeed test:12:in `transition': disposed viewport (RGSSError)\n"
                + "\tfrom Scene_Map:27:in `perform_transition'", null));
        assertTrue(AgvnRgssFailure.scriptError("Script 'Window_Base' line 40: NoMethodError occurred.\n\n"
                + "undefined method `width' for nil:NilClass", null));
    }

    @Test
    public void whatOnlyWindowsHasIsNoScriptError() {
        assertFalse("a DLL through Win32API", AgvnRgssFailure.scriptError("Script 'Mouse' line 5: RuntimeError "
                + "occurred.\n\nWin32API: cannot load user32_ex.dll", null));
        assertFalse("mkxp-z did not start the game", AgvnRgssFailure.scriptError("Unable to find Game.ini", null));
        assertFalse("nothing said", AgvnRgssFailure.scriptError("", null));
    }
}
