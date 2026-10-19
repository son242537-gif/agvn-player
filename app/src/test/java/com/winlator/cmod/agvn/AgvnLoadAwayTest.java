/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/** A game still starting loads on while the player is away, and stops once it is up; one that is up stops at once. */
public class AgvnLoadAwayTest {
    @After
    public void clear() {
        AgvnLoadAway.back();
    }

    @Test
    public void aGameStillStartingStopsOnlyOnceItIsUp() {
        AtomicInteger stopped = new AtomicInteger();
        assertTrue(AgvnLoadAway.keepLoading(true, stopped::incrementAndGet)); // Wine goes on
        assertEquals(0, stopped.get());
        AgvnLoadAway.up(true);
        assertEquals("up while the player is away: stopped now", 1, stopped.get());
        AgvnLoadAway.up(true);
        assertEquals("once", 1, stopped.get());
    }

    @Test
    public void aPlayerBackFirstHasNothingStopped() {
        AtomicInteger stopped = new AtomicInteger();
        assertTrue(AgvnLoadAway.keepLoading(true, stopped::incrementAndGet));
        AgvnLoadAway.back();
        AgvnLoadAway.up(true);
        assertEquals(0, stopped.get());
        assertTrue("away again while it still starts", AgvnLoadAway.keepLoading(true, stopped::incrementAndGet));
        AgvnLoadAway.up(false); // the start watch gave up (15 min): stopped all the same
        assertEquals(1, stopped.get());
    }

    @Test
    public void aGameThatIsUpStopsAtOnceAsBefore() {
        AtomicInteger stopped = new AtomicInteger();
        assertFalse(AgvnLoadAway.keepLoading(false, stopped::incrementAndGet));
        AgvnLoadAway.up(true);
        assertEquals("nothing kept for later", 0, stopped.get());
    }

    @Test
    public void theEmulatorOfA32BitGameIsNamed() {
        assertEquals("Giả lập 32-bit: FEXCore 2601 (libwow64fex.dll)", AgvnEmulatorNote.line(true, "FEXCore", "2601", "0.4.2"));
        assertEquals("Giả lập 32-bit: FEXCore 2601 (libwow64fex.dll)", AgvnEmulatorNote.line(true, "fexcore", "2601", "0.4.2"));
        assertEquals("Giả lập 32-bit: WOWBox64 0.4.2 (wowbox64.dll)", AgvnEmulatorNote.line(true, "Box64", "2601", "0.4.2"));
        assertEquals("Giả lập x86: Box64 0.3.6", AgvnEmulatorNote.line(false, "Box64", "2601", "0.3.6"));
    }
}
