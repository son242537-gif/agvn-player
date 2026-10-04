/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** "Bật debug Wine" must not change how a game runs: no heap checking, and no flood of file and font warnings. */
public class AgvnWineDebugTest {
    @Test
    public void warningsForEveryChannelLeaveHeapFileAndFontQuiet() {
        assertEquals("warn+all,err+all,fixme+all,warn-heap,warn-file,warn-font,trace+msgbox", AgvnWineDebug.spec(true, "warn,err,fixme"));
    }

    @Test
    public void aChannelPickedByNameKeepsItsWarnings() {
        String heap = AgvnWineDebug.spec(true, "warn,heap");
        assertFalse(heap, heap.contains("warn-heap"));
        assertTrue(heap, heap.contains("+heap"));
        assertTrue(heap, heap.endsWith("warn-file,warn-font,trace+msgbox"));
        assertEquals("warn+all,+file,warn-heap,warn-font,trace+msgbox", AgvnWineDebug.spec(true, "warn,file"));
    }

    @Test
    public void withoutWarningsForEveryChannelNothingIsAdded() {
        assertEquals("err+all,fixme+all,trace+msgbox", AgvnWineDebug.spec(true, "err,fixme"));
        assertEquals("+mfplat,trace+msgbox", AgvnWineDebug.spec(true, "mfplat"));
        assertEquals("-all,err+module,err+mscoree,trace+msgbox", AgvnWineDebug.spec(false, "warn,err,fixme"));
    }

    @Test
    public void errorBoxesAreAlwaysInTheLog() {
        // "Tự sửa lỗi" reads a game's error box: Wine traces its text (MSGBOX_OnInit), with the log on or off
        assertTrue(AgvnWineDebug.QUIET_SPEC.endsWith(",trace+msgbox"));
        assertEquals("warn+all,+msgbox,warn-heap,warn-file,warn-font", AgvnWineDebug.spec(true, "warn,msgbox"));
    }
}
