/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A game that crashed in Wine, or stopped on a Ren'Py error, is not reported as a normal end. */
public class AgvnCrashScanTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** Lust Village on 02/10/2026, with "Bật debug Wine" on. */
    private static final List<String> LUST_VILLAGE = Arrays.asList(
            "wine: Unhandled page fault on read access to FEEEFEEE at address 6F0CE7A7 (thread 00e8), starting debugger...",
            "Unhandled exception: page fault on read access to 0xfeeefeee in 32-bit code (0x6f0ce7a7).",
            "Register dump:",
            " CS:0023 SS:002b DS:002b ES:002b FS:0063 GS:006b",
            "Backtrace:",
            "=>0 0x6f0ce7a7 in renpysound.pyd (+0xe7a7) (0x0061f4c8)",
            "  1 0x6a4a89c0 in python27 (+0x989c0) (0x0061f5e0)",
            "0x6f0ce7a7 renpysound.pyd+0xe7a7: addl $0x01, (%ebx)");

    private static final String TRACEBACK = "I'm sorry, but an uncaught exception occurred.\n\nWhile running game code:\n"
            + "  File \"game/script.rpy\", line 120, in script\n    $ money = son_gs2_money_text\n"
            + "NameError: name 'son_gs2_money_text' is not defined\n\n"
            + "-- Full Traceback ------------------------------------------------------------\n\nFull traceback:\n"
            + "  File \"renpy/ast.py\", line 1, in execute\nNameError: name 'son_gs2_money_text' is not defined\n\n"
            + "Windows-10-10.0.19041\nRen'Py 7.0.0.196\n";

    @Test
    public void wineCrashReportGivesWhatAndWhere() {
        assertEquals("page fault đọc 0xfeeefeee trong renpysound.pyd+0xe7a7", AgvnCrashScan.describe(LUST_VILLAGE));
        assertEquals("page fault đọc 0xfeeefeee", AgvnCrashScan.describe(LUST_VILLAGE.subList(0, 1)));
        List<String> symbol = Arrays.asList("Unhandled exception: unimplemented function msvcp140.dll.?_Xbad called in wow64 32-bit code (0x7b012345).",
                "=>0 0x7b012345 RaiseException+0x55() in kernelbase (0x0032fd40)");
        assertEquals("unimplemented function msvcp140.dll.?_Xbad called trong kernelbase!RaiseException+0x55", AgvnCrashScan.describe(symbol));
        assertEquals("page fault ghi 0x00000000", AgvnCrashScan.words("page fault on write access to 0x00000000"));
    }

    @Test
    public void ordinaryOutputIsNoCrash() {
        assertNull(AgvnCrashScan.describe(Collections.emptyList()));
        assertNull(AgvnCrashScan.describe(Arrays.asList("0024:fixme:ntdll:NtQuerySystemInformation info_class 0x99", "wine: configuration updated")));
    }

    @Test
    public void renpyTracebackGivesItsError() {
        assertEquals("NameError: name 'son_gs2_money_text' is not defined", AgvnCrashScan.renpyError(TRACEBACK));
        assertNull(AgvnCrashScan.renpyError(""));
    }

    @Test
    public void sessionErrorWritesTheCrashReportAndSkipsAnOldTraceback() throws Exception {
        File dir = tmp.newFolder("session");
        String how = AgvnCrashScan.sessionError(LUST_VILLAGE, Collections.emptyList(), 0, dir);
        assertTrue(how, how.startsWith("Game bị lỗi (crash) – page fault đọc 0xfeeefeee trong renpysound.pyd+0xe7a7"));
        assertTrue(new String(Files.readAllBytes(new File(dir, AgvnCrashScan.CRASH_FILE).toPath()), StandardCharsets.UTF_8).contains("Backtrace:"));

        File traceback = new File(tmp.newFolder("game"), "traceback.txt");
        Files.write(traceback.toPath(), TRACEBACK.getBytes(StandardCharsets.UTF_8));
        long start = System.currentTimeMillis();
        assertTrue(traceback.setLastModified(start - 40L * 24 * 3600 * 1000)); // from last month: not this session
        assertNull(AgvnCrashScan.sessionError(Collections.emptyList(), Collections.singletonList(traceback), start, dir));
        assertTrue(traceback.setLastModified(start + 1000));
        assertEquals("Game bị lỗi Ren'Py – NameError: name 'son_gs2_money_text' is not defined",
                AgvnCrashScan.sessionError(Collections.emptyList(), Collections.singletonList(traceback), start, dir));
    }

    @Test
    public void theTailKeepsTheCrashReportPastItsOwnLength() {
        AgvnWineTail tail = AgvnWineTail.get();
        tail.reset();
        for (String line : LUST_VILLAGE) tail.call(line);
        for (int i = 0; i < AgvnWineTail.LINES + 100; i++) tail.call("module " + i);
        assertEquals(AgvnWineTail.LINES, tail.lines().size());
        assertEquals("page fault đọc 0xfeeefeee trong renpysound.pyd+0xe7a7", AgvnCrashScan.describe(tail.crash()));
        assertEquals(AgvnWineTail.CRASH_LINES, tail.crash().size());
        tail.reset();
        assertTrue(tail.crash().isEmpty());
    }
}
