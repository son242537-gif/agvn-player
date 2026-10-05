/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds Windows exes and DLLs that were copied or unpacked only partly. Wine does not load a PE file whose section
 * data runs past the end of the file ("Could not map ... file probably truncated"), and the game then closes at once
 * on a black screen. The rule is Wine's own (map_image_into_view in dlls/ntdll/unix/virtual.c), so a file Wine can
 * load is never reported. Pure Java (JVM-testable).
 */
public final class AgvnPeCheck {
    /** A PE file shorter than its own headers say; {@code needed} is 0 for an empty file. */
    public static final class Broken {
        public final File file;
        public final long size, needed;

        Broken(File file, long size, long needed) {
            this.file = file;
            this.size = size;
            this.needed = needed;
        }
    }

    /** Files checked per launch at most: only headers are read, but storage can be slow. */
    static final int MAX_FILES = 200;
    private static final long SECTOR_MASK = 0x1ff, PAGE_MASK = 0xfff, SCN_MEM_SHARED = 0x10000000L, SCN_MEM_WRITE = 0x80000000L;
    private static final int SECTION_HEADER = 40;

    private AgvnPeCheck() {}

    /** Null when {@code file} is not a PE file, cannot be read, or Wine can map it. */
    public static Broken check(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long size = raf.length();
            if (size == 0) return new Broken(file, 0, 0);
            byte[] dos = read(raf, 0, 64);
            if (dos == null || dos[0] != 'M' || dos[1] != 'Z') return null;
            long pe = u32(dos, 0x3c);
            byte[] nt = read(raf, pe, 24);
            // a DOS exe, or a file cut before its PE header: nothing tells what it should hold
            if (nt == null || nt[0] != 'P' || nt[1] != 'E' || nt[2] != 0 || nt[3] != 0) return null;
            int sections = u16(nt, 6), optionalSize = u16(nt, 20);
            long table = pe + 24 + optionalSize, tableEnd = table + (long) sections * SECTION_HEADER;
            if (tableEnd > size) return new Broken(file, size, tableEnd);
            byte[] optional = read(raf, pe + 24, Math.min(optionalSize, 40));
            // section alignment under a page: Wine maps the whole file as it is, without this check
            if (optional == null || optional.length < 36 || (u32(optional, 32) & PAGE_MASK) != 0) return null;
            byte[] headers = read(raf, table, sections * SECTION_HEADER);
            return headers != null ? sections(file, size, headers, sections) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Broken sections(File file, long size, byte[] headers, int count) {
        long needed = 0;
        boolean cut = false;
        for (int i = 0; i < count; i++) {
            int at = i * SECTION_HEADER;
            long virtualSize = u32(headers, at + 8), rawSize = u32(headers, at + 16), rawStart = u32(headers, at + 20);
            long flags = u32(headers, at + 36);
            if ((flags & SCN_MEM_SHARED) != 0 && (flags & SCN_MEM_WRITE) != 0) continue; // mapped another way
            long mapSize = ((virtualSize != 0 ? virtualSize : rawSize) + PAGE_MASK) & ~PAGE_MASK;
            long fileStart = rawStart & ~SECTOR_MASK;
            long fileSize = Math.min((rawSize + (rawStart & SECTOR_MASK) + SECTOR_MASK) & ~SECTOR_MASK, mapSize);
            if (rawStart == 0 || fileSize == 0) continue;
            long end = fileStart + fileSize;
            needed = Math.max(needed, end);
            if (rawStart >= size || end > ((size + SECTOR_MASK) & ~SECTOR_MASK)) cut = true;
        }
        return cut ? new Broken(file, size, needed) : null;
    }

    /**
     * Cut-short files among {@code exe} and the .exe and .dll files right inside each of {@code folders} (null
     * folders are skipped; a file is checked once even when two folders are the same).
     */
    public static List<Broken> scan(File exe, File... folders) {
        List<File> files = new ArrayList<>();
        if (exe != null) files.add(exe);
        for (File dir : folders) {
            File[] list = dir != null ? dir.listFiles() : null;
            if (list == null) continue;
            Arrays.sort(list);
            for (File f : list) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".exe") || name.endsWith(".dll")) files.add(f);
            }
        }
        Set<String> seen = new HashSet<>();
        List<Broken> broken = new ArrayList<>();
        for (File f : files) {
            if (seen.size() >= MAX_FILES) break;
            if (!seen.add(AgvnGameScanner.canonical(f)) || !f.isFile()) continue;
            Broken b = check(f);
            if (b != null) broken.add(b);
        }
        return broken;
    }

    /** {@code length} bytes at {@code offset}, or null when the file ends first. */
    static byte[] read(RandomAccessFile raf, long offset, int length) throws IOException {
        if (offset < 0 || offset + length > raf.length()) return null;
        byte[] out = new byte[length];
        raf.seek(offset);
        raf.readFully(out);
        return out;
    }

    static int u16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    static long u32(byte[] b, int at) {
        return ((long) u16(b, at + 2) << 16) | u16(b, at);
    }
}
