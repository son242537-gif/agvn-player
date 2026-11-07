/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

/** How Wine runs a .NET exe, 32-bit or 64-bit, from its .NET header (AgvnMlangFix picks its mlang.dll by it). */
public class AgvnClrHeaderTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A PE file for {@code machine} with one section; a .NET header with {@code flags} when flags >= 0. */
    private File exe(String name, int machine, int magic, int flags) throws Exception {
        boolean plus = magic == 0x20b;
        int optSize = plus ? 240 : 224, dirs = plus ? 112 : 96, pe = 0x80, table = pe + 24 + optSize;
        byte[] b = new byte[0x400];
        b[0] = 'M';
        b[1] = 'Z';
        put(b, 0x3c, pe, 4);
        b[pe] = 'P';
        b[pe + 1] = 'E';
        put(b, pe + 4, machine, 2);
        put(b, pe + 6, 1, 2);
        put(b, pe + 20, optSize, 2);
        int opt = pe + 24;
        put(b, opt, magic, 2);
        put(b, opt + dirs - 4, 16, 4);
        if (flags >= 0) {
            put(b, opt + dirs + 14 * 8, 0x2000, 4);
            put(b, opt + dirs + 14 * 8 + 4, 72, 4);
        }
        put(b, table + 8, 0x200, 4); // virtual size
        put(b, table + 12, 0x2000, 4); // virtual address
        put(b, table + 16, 0x200, 4); // raw size
        put(b, table + 20, 0x200, 4); // raw pointer
        put(b, 0x200, 72, 4);
        if (flags >= 0) put(b, 0x200 + 16, flags, 4);
        File f = new File(tmp.getRoot(), name);
        Files.write(f.toPath(), b);
        return f;
    }

    private static void put(byte[] b, int at, long value, int bytes) {
        for (int i = 0; i < bytes; i++) b[at + i] = (byte) (value >>> (8 * i));
    }

    @Test
    public void readsTheFlagsOfTheDotNetHeader() throws Exception {
        assertEquals(AgvnClrHeader.IL_ONLY, AgvnClrHeader.flags(exe("AnyCpu.exe", 0x14c, 0x10b, 1)));
        assertEquals(3, AgvnClrHeader.flags(exe("X86.exe", 0x14c, 0x10b, 3)));
        assertEquals("no .NET header", -1, AgvnClrHeader.flags(exe("Native.exe", 0x14c, 0x10b, -1)));
        assertEquals(-1, AgvnClrHeader.flags(new File(tmp.getRoot(), "gone.exe")));
        assertTrue(AgvnWineMono.isDotNet(exe("Same.exe", 0x14c, 0x10b, 1)));
    }

    @Test
    public void wineRunsAnyCpuAsSixtyFourBit() throws Exception {
        // the report's AnyCPU PE32 exe ran 64-bit ("Could not load Mono"); with 32BITREQUIRED it ran 32-bit (07/11/2026)
        assertTrue(AgvnClrHeader.runs64(exe("AnyCpu.exe", 0x14c, 0x10b, AgvnClrHeader.IL_ONLY)));
        assertFalse(AgvnClrHeader.runs64(exe("X86.exe", 0x14c, 0x10b, AgvnClrHeader.IL_ONLY | AgvnClrHeader.REQUIRES_32)));
        assertFalse("Prefer 32-bit", AgvnClrHeader.runs64(exe("Pref.exe", 0x14c, 0x10b,
                AgvnClrHeader.IL_ONLY | AgvnClrHeader.REQUIRES_32 | AgvnClrHeader.PREFERS_32)));
        assertFalse("mixed code is 32-bit", AgvnClrHeader.runs64(exe("Mixed.exe", 0x14c, 0x10b, 0)));
        assertFalse("not .NET", AgvnClrHeader.runs64(exe("Game.exe", 0x14c, 0x10b, -1)));
        assertTrue(AgvnClrHeader.runs64(exe("Game64.exe", 0x8664, 0x20b, AgvnClrHeader.IL_ONLY)));
        assertTrue(AgvnClrHeader.runs64(exe("Native64.exe", 0x8664, 0x20b, -1)));
    }
}
