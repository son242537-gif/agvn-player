/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void aStackTooDeepShowsTheFirstAndLastFrames() {
        // report of 04/11/2026: "stack level too deep" at "(eval) line 159" said nothing of the script that began it
        StringBuilder trace = new StringBuilder("Script '(eval)' line 159: SystemStackError occurred.\n\n"
                + "stack level too deep\n\n(eval):159:in `update': stack level too deep (SystemStackError)");
        for (int i = 0; i < 9000; i++) trace.append("\n\tfrom (eval):159:in `update'");
        trace.append("\n\tfrom Scene_Map:12:in `update'\n\tfrom Main:3:in `<main>'");
        String brief = AgvnRgssFailure.brief(trace.toString(), "(%d left out)");
        String[] lines = brief.split("\n");
        assertEquals("the message, then 10 frames, the gap, the last 10", 4 + 10 + 1 + 10, lines.length);
        assertEquals("(eval):159:in `update': stack level too deep (SystemStackError)", lines[4]);
        assertEquals("\t(" + (9002 - 19) + " left out)", lines[14]);
        assertEquals("\tfrom Scene_Map:12:in `update'", lines[23]);
        assertEquals("\tfrom Main:3:in `<main>'", lines[24]);
    }

    @Test
    public void aShortBacktraceStaysWhole() {
        StringBuilder trace = new StringBuilder("Script 'Window_Base' line 40: NoMethodError occurred.\n\nundefined\n\n"
                + "Window_Base:40:in `x': undefined (NoMethodError)");
        for (int i = 0; i < 19; i++) trace.append("\n\tfrom Scene_").append(i).append(":1:in `y'");
        assertEquals(trace.toString(), AgvnRgssFailure.brief(trace.toString(), "(%d left out)"));
        assertEquals("no backtrace", "Unable to find Game.ini", AgvnRgssFailure.brief("Unable to find Game.ini", "%d"));
    }
}
