/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The Agnietta on a Redmi Note 9 Pro (21/10/2026): FEX would not run msvcr100.dll's code, 80 s in. */
public class AgvnEmulatorNoExecTest {
    /** Wine's report as wine-cuoi.txt kept it, with {@code module} in the first frame. */
    private static List<String> report(String access, String bits, String frame) {
        return new ArrayList<>(Arrays.asList(
                "00e4:warn:seh:dispatch_exception EXCEPTION_ACCESS_VIOLATION exception (code=c0000005) raised",
                "wine: Unhandled page fault on " + access + " access to 79811E80 at address 79811E80 (thread 00e4),"
                        + " starting debugger...",
                "Unhandled exception: page fault on " + access + " access to 0x79811e80 in " + bits + " code (0x79811e80).",
                "Register dump:",
                " EIP:79811e80 ESP:0012fba0 EBP:0012fbb8 EFLAGS:00000216(   - --  I   -A-P- )",
                "Backtrace:",
                frame,
                "  1 0x7994d8ea in sqlite3 (+0x6d8ea) (0x0012fbdc)",
                "0x79811e80 msvcr100+0x1e80: push %ebp"));
    }

    private static AgvnEvidence crash(List<String> lines) {
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.crashed = ev.endedByGame = true;
        ev.seconds = 90;
        ev.lines.addAll(lines);
        ev.params.put("crash", AgvnCrashScan.describe(lines));
        return ev;
    }

    @Test
    public void codeInsideAModuleTheEmulatorWouldNotRun() throws Exception {
        List<String> lines = report("execute", "wow64 32-bit", "=>0 0x79811e80 in msvcr100 (+0x1e80) (0x0012fbb8)");
        assertTrue(AgvnEmulatorNoExec.moduleCode(lines));
        AgvnDoctorTest.load();
        AgvnEvidence ev = crash(lines);
        ev.changed = true; // the emulator, not the change: its own fixes first
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(ev);
        assertEquals("emulator-noexec-crt", f.id());
        assertEquals("msvcr100", f.params.get("1"));
        assertTrue(f.cause(), f.cause().contains("page fault chạy mã ở 0x79811e80 trong msvcr100+0x1e80"));
        assertEquals(Arrays.asList("dll-wine", "emulator-wowbox64", "emulator-stable", "send-logs"), f.fixes());

        List<String> own = report("execute", "wow64 32-bit", "=>0 0x79811e80 in sqlite3 (+0x1e80) (0x0012fbb8)");
        f = AgvnDoctorTest.catalog.find(crash(own));
        assertEquals("a DLL Wine has no copy of", "emulator-noexec", f.id());
        assertEquals(Arrays.asList("emulator-wowbox64", "emulator-stable", "send-logs"), f.fixes());

        File exeDir = Files.createTempDirectory("agvn-noexec").toFile();
        Files.write(new File(exeDir, "msvcr100.dll").toPath(), new byte[1]);
        assertEquals("the frame names it bare", "msvcr100", AgvnDllProxy.dll(exeDir, "msvcr100", ""));
        assertNull(AgvnDllProxy.dll(exeDir, "msvcr110", ""));
    }

    @Test
    public void otherCrashesAreNotTheEmulatorsRefusal() {
        assertFalse("a read", AgvnEmulatorNoExec.moduleCode(
                report("read", "wow64 32-bit", "=>0 0x79811e80 in msvcr100 (+0x1e80) (0x0012fbb8)")));
        assertFalse("64-bit code: no WOWBox64 for it", AgvnEmulatorNoExec.moduleCode(
                report("execute", "64-bit", "=>0 0x79811e80 in msvcr100 (+0x1e80) (0x0012fbb8)")));
        List<String> nowhere = report("execute", "wow64 32-bit", "=>0 0x79811e80 (0x0012fbb8)");
        assertFalse("a jump out of every module", AgvnEmulatorNoExec.moduleCode(nowhere));
        assertFalse("its first frame elsewhere", AgvnEmulatorNoExec.moduleCode(
                report("execute", "wow64 32-bit", "=>0 0x7994d8ea in sqlite3 (+0x6d8ea) (0x0012fbdc)")));
        assertFalse(crash(nowhere).holds("module-code"));
        AgvnEvidence alive = crash(report("execute", "wow64 32-bit", "=>0 0x79811e80 in msvcr100 (+0x1e80)"));
        assertTrue(alive.holds("module-code"));
        alive.crashed = false;
        assertFalse("no crash, no refusal", alive.holds("module-code"));
    }

    @Test
    public void aRunThatShowedOnlyItsLoadingScreenDidNotRunWell() {
        // The Agnietta: its loading window for 5 s, then 25 s without a frame until the player quit (0.1.62: good)
        long start = 7_000_000;
        AgvnSessionTrack.start(start);
        for (int i = 0; i < AgvnSessionTrack.STARTED_UPDATES; i++) AgvnSessionTrack.onFrame(start + 2_000 + i * 200);
        AgvnSessionTrack.noteEnd(start + 30_000);
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.playerQuit = true;
        ev.seconds = 30;
        ev.frozenS = AgvnSessionTrack.frozenSecondsAtEnd();
        ev.shownS = AgvnSessionTrack.shownSecondsAtEnd();
        assertEquals(24, ev.frozenS);
        assertEquals(3, ev.shownS);
        assertTrue(ev.loadingOnly());
        assertFalse(ev.good());
        ev.endedByGame = true; // or it closed itself, 110 s in, as at 12:06
        ev.playerQuit = false;
        ev.seconds = 110;
        ev.frozenS = 105;
        assertFalse(ev.good());

        AgvnSessionTrack.start(start + 200_000); // a game played for 40 s, then a still page read for 30 s
        for (int i = 0; i < 400; i++) AgvnSessionTrack.onFrame(start + 202_000 + i * 100);
        AgvnSessionTrack.noteEnd(start + 272_000);
        ev.frozenS = AgvnSessionTrack.frozenSecondsAtEnd();
        ev.shownS = AgvnSessionTrack.shownSecondsAtEnd();
        assertEquals(39, ev.shownS);
        assertFalse(ev.loadingOnly());
        assertTrue(ev.good());
    }

    @Test
    public void aGameThatClosedAsItCameUpFailedThoughWineSaidNothing() throws Exception {
        // Galaxy Z Fold4, Wine's log off: the loading window, 50 s of loading, first frames, and 1 s later Wine ended
        long start = 9_000_000;
        AgvnSessionTrack.start(start);
        for (int i = 0; i < AgvnSessionTrack.STARTED_UPDATES; i++) AgvnSessionTrack.onFrame(start + 2_000 + i * 150);
        AgvnSessionTrack.onPress(); // a tap while it loaded
        for (int i = 0; i < 19; i++) AgvnSessionTrack.onFrame(start + 57_000 + i * 50);
        AgvnSessionTrack.noteEnd(start + 59_000);
        AgvnEvidence ev = new AgvnEvidence();
        ev.started = ev.endedByGame = true;
        ev.seconds = 61;
        ev.frozenS = AgvnSessionTrack.frozenSecondsAtEnd();
        ev.shownS = AgvnSessionTrack.shownSecondsAtEnd();
        ev.stretchS = AgvnSessionTrack.lastStretchSecondsAtEnd();
        ev.stretchPresses = AgvnSessionTrack.pressesInLastStretch();
        assertEquals(2, ev.stretchS);
        assertEquals(0, ev.stretchPresses);
        assertTrue(ev.closedOnStart());
        assertFalse("0.1.64 kept it as a good run", ev.good());
        AgvnDoctorTest.load();
        AgvnProblemCatalog.Finding f = AgvnDoctorTest.catalog.find(ev);
        assertEquals("closed-on-start", f.id());
        assertEquals(Arrays.asList("restore-good", "emulator-wowbox64", "emulator-stable", "mods-off", "wine-old",
                "send-logs"), f.fixes());

        ev.stretchPresses = 1; // the player picked "Thoát" in a menu that had just come up
        assertFalse(ev.closedOnStart());
        ev.stretchPresses = 0;
        ev.stretchS = 40; // it ran 40 s before it closed
        assertFalse(ev.closedOnStart());
        ev.stretchS = 2;
        ev.playerQuit = true;
        assertFalse("the player's exit", ev.closedOnStart());

        AgvnSessionTrack.start(start + 100_000); // drawing all along, never 10 s without a frame: nothing to judge
        for (int i = 0; i < 300; i++) AgvnSessionTrack.onFrame(start + 101_000 + i * 100);
        AgvnSessionTrack.noteEnd(start + 131_000);
        assertEquals(-1, AgvnSessionTrack.lastStretchSecondsAtEnd());
    }

    @Test
    public void aWindowLineSaysWhereItIsAndHowDeep() {
        assertEquals("    0x1000038 \"to be or not to be.exe\" \"To Be or Not to Be\" 8,26 1272×694 sâu 32",
                AgvnWindowList.line(1, 0x1000038, "to be or not to be.exe\0", "To Be or Not to Be", 8, 26, 1272, 694,
                        32));
        assertEquals("  0x20 0,0 1×1 không vẽ", AgvnWindowList.line(0, 0x20, "", null, 0, 0, 1, 1, 0));
    }
}
