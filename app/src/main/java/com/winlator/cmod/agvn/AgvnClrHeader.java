/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * The .NET header of an exe (IMAGE_COR20_HEADER) and how Wine runs it: a PE32+ program 64-bit; a PE32 one 32-bit,
 * unless it is IL only without 32BITREQUIRED ("AnyCPU"), which Wine runs 64-bit on a 64-bit Wine as Windows does
 * (server/mapping.c: ComPlusNativeReady). Pure Java (JVM-testable).
 */
final class AgvnClrHeader {
    static final int IL_ONLY = 0x1, REQUIRES_32 = 0x2, PREFERS_32 = 0x20000;
    private static final int CLR_DIRECTORY = 14, SECTION = 40;

    private AgvnClrHeader() {}

    /** The flags of {@code exe}'s .NET header; -1 for a file that is no .NET program or cannot be read. */
    static int flags(File exe) {
        try (RandomAccessFile f = new RandomAccessFile(exe, "r")) {
            byte[] dos = AgvnPeCheck.read(f, 0, 64);
            if (dos == null || dos[0] != 'M' || dos[1] != 'Z') return -1;
            long pe = AgvnPeCheck.u32(dos, 0x3c);
            byte[] nt = AgvnPeCheck.read(f, pe, 24);
            if (nt == null || nt[0] != 'P' || nt[1] != 'E' || nt[2] != 0 || nt[3] != 0) return -1;
            int sections = AgvnPeCheck.u16(nt, 6), optSize = AgvnPeCheck.u16(nt, 20);
            byte[] opt = AgvnPeCheck.read(f, pe + 24, optSize);
            if (opt == null || optSize < 2) return -1;
            int dirs = AgvnPeCheck.u16(opt, 0) == 0x20b ? 112 : 96, entry = dirs + CLR_DIRECTORY * 8;
            if (entry + 8 > optSize || AgvnPeCheck.u32(opt, dirs - 4) <= CLR_DIRECTORY) return -1;
            long rva = AgvnPeCheck.u32(opt, entry);
            if (rva == 0 || AgvnPeCheck.u32(opt, entry + 4) < 20) return -1;
            byte[] table = AgvnPeCheck.read(f, pe + 24 + optSize, sections * SECTION);
            long at = table != null ? offset(table, sections, rva) : -1;
            byte[] cor = at >= 0 ? AgvnPeCheck.read(f, at, 20) : null;
            return cor != null ? (int) AgvnPeCheck.u32(cor, 16) : -1;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** True when Wine runs {@code exe} as a 64-bit program: PE32+, or PE32 "AnyCPU" (see the class). */
    static boolean runs64(File exe) {
        int machine = AgvnPeFacts.machine(exe);
        if (machine == 0x8664 || machine == 0xaa64) return true;
        int flags = flags(exe);
        return machine == 0x14c && flags >= 0 && runs64(flags);
    }

    /** {@link #runs64} for a PE32 .NET program with these header flags. */
    static boolean runs64(int flags) {
        return (flags & IL_ONLY) != 0 && (flags & REQUIRES_32) == 0;
    }

    /** The file offset of {@code rva} in the section table, or -1. */
    private static long offset(byte[] table, int sections, long rva) {
        for (int i = 0; i < sections; i++) {
            int s = i * SECTION;
            long va = AgvnPeCheck.u32(table, s + 12), size = Math.max(AgvnPeCheck.u32(table, s + 8), AgvnPeCheck.u32(table, s + 16));
            if (rva >= va && rva < va + size) return AgvnPeCheck.u32(table, s + 20) + (rva - va);
        }
        return -1;
    }
}
