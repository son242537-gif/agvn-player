/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An HTML game's console in logcat: a message that comes over and over is logged a few times, with its count. */
public class AgvnHtmlConsoleTest {
    private static final String ALIGN = "The provided value 'undefined' is not a valid enum value of type CanvasTextAlign."
            + " (https://g.agvn.game/js/rpg_core.js:1234)";

    @Test
    public void aFloodIsCounted() {
        Map<String, int[]> seen = new HashMap<>();
        List<String> logged = new ArrayList<>();
        for (int i = 0; i < 19_339; i++) { // the Train45 log of 07/10/2026: nine minutes of one warning
            String line = AgvnHtmlConsole.repeat(seen, ALIGN);
            if (line != null) logged.add(line);
        }
        assertEquals(5, logged.size());
        assertEquals(ALIGN, logged.get(0));
        assertEquals(ALIGN + " (×10)", logged.get(1));
        assertEquals(ALIGN + " (×10000)", logged.get(4));
        assertEquals("another message is logged at once", "Uncaught TypeError (x.js:1)",
                AgvnHtmlConsole.repeat(seen, "Uncaught TypeError (x.js:1)"));
        assertNull(AgvnHtmlConsole.repeat(seen, ALIGN));
    }
}
