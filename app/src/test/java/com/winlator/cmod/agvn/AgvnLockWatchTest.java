/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.EnvVars;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Ticket of 21/10/2026: a Unity IL2CPP game stuck for good on a lock while it loaded a scene (Proton 9, FEX). */
public class AgvnLockWatchTest {
    private static final String MAIN = "00e0:err:sync:RtlpWaitForCriticalSection section 0000004FFAB10280 (null) wait"
            + " timed out in thread 00e0, blocked by 0224, retrying (60 sec)";
    private static final String OTHER = "0214:err:sync:RtlpWaitForCriticalSection section 0000004FFAB10280 (null) wait"
            + " timed out in thread 0214, blocked by 0224, retrying (60 sec)";
    private static final String SECOND_LOCK = "0210:err:sync:RtlpWaitForCriticalSection section 0000004FFDA32118"
            + " (null) wait timed out in thread 0210, blocked by 0208, retrying (60 sec)";

    @Test
    public void aThreadReportedTwiceIsStuck() {
        AgvnLockWatch watch = new AgvnLockWatch();
        assertNull("5 s: maybe a long load", watch.line(MAIN));
        assertNull("another thread's first wait", watch.line(OTHER));
        assertNull(watch.line("00e0:warn:seh:dispatch_exception EXCEPTION_WINE_CXX_EXCEPTION exception"
                + " (code=e06d7363)"));
        assertEquals(AgvnLockWatch.NOTE + " luồng 00e0 chờ khoá 0000004FFAB10280 hơn 1 phút, khoá do luồng 0224 giữ",
                watch.line(MAIN));
        assertNull("one note per lock", watch.line(OTHER));
        assertNull(watch.line(MAIN));
        assertNull(watch.line(SECOND_LOCK));
        assertTrue(watch.line(SECOND_LOCK).endsWith("khoá do luồng 0208 giữ"));
        watch.reset();
        assertNull("a new game", watch.line(MAIN));
    }

    @Test
    public void theNoteStaysInWinesTailThroughRepeats() {
        AgvnWineTail tail = AgvnWineTail.get();
        tail.reset();
        for (String line : Arrays.asList(MAIN, OTHER, MAIN, OTHER, MAIN, OTHER)) tail.call(line);
        List<String> lines = tail.lines();
        assertEquals(1, lines.stream().filter(l -> l.startsWith(AgvnLockWatch.NOTE)).count());
        assertTrue(lines.contains(MAIN));
        tail.reset();
        assertFalse(tail.lines().stream().anyMatch(l -> l.startsWith(AgvnLockWatch.NOTE)));
    }

    @Test
    public void theDoctorOffersTheLockFixes() throws Exception {
        AgvnDoctorTest.load();
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.playerQuit = true;
        ev.seconds = 240;
        ev.lines.addAll(Arrays.asList(MAIN, "[AGVN] ×3 dòng lặp lại",
                AgvnLockWatch.NOTE + " luồng 00e0 chờ khoá 0000004FFAB10280 hơn 1 phút, khoá do luồng 0224 giữ"));
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(ev);
        assertEquals("deadlock", f.id());
        assertEquals(Arrays.asList("emulator-order", "esync-off", "emulator-stable", "wine-old", "mods-off",
                "send-logs"), f.fixes());
        AgvnEvidence slow = new AgvnEvidence();
        slow.started = slow.playerQuit = true;
        slow.seconds = 240;
        slow.lines.add(MAIN);
        assertNull("one wait of 5 s is no deadlock", AgvnDoctorTest.catalog.find(slow));
    }

    @Test
    public void theFixesSetOneVariableOfTheGame() {
        assertEquals("WINEESYNC=0", AgvnLockFixes.withVar("", AgvnLockFixes.WINEESYNC, "0"));
        assertEquals("DXVK_ASYNC=1 FEX_HALFBARRIERTSOENABLED=0",
                AgvnLockFixes.withVar("DXVK_ASYNC=1 FEX_HALFBARRIERTSOENABLED=1", AgvnLockFixes.HALF_BARRIER, "0"));
        assertTrue(AgvnLockFixes.runsOnFex("proton-9.0-arm64ec", "Box64"));
        assertTrue("32-bit code on FEX", AgvnLockFixes.runsOnFex("proton-9.0-x86_64", "FEXCore"));
        assertFalse(AgvnLockFixes.runsOnFex("proton-9.0-x86_64", "Box64"));
        assertTrue("FEX's default", AgvnLockFixes.halfBarriers(new EnvVars("")));
        assertTrue(AgvnLockFixes.halfBarriers(new EnvVars("FEX_TSOENABLED=1 FEX_HALFBARRIERTSOENABLED=1")));
        assertFalse("already off", AgvnLockFixes.halfBarriers(new EnvVars("FEX_HALFBARRIERTSOENABLED=0")));
        assertFalse("no TSO: emulator-stable's turn", AgvnLockFixes.halfBarriers(new EnvVars("FEX_TSOENABLED=0")));
    }
}
