/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;

/**
 * A game uploaded as several archives of their own: name.part1.zip, name.part2.zip... (also .7z, and .rar files that are
 * no volumes of one RAR). Each can be opened alone and holds a share of the game's files; 7-Zip does not join them. All
 * of them go into one game folder ({@link AgvnUnpackSeries}). A real RAR set (name.part1.rar whose header says
 * "volume") is one archive and stays with {@link AgvnArchiveRarSet}. Pure Java (JVM-testable).
 */
final class AgvnArchiveSeries {
    /** The archives that are there, in the order of their numbers. */
    final List<File> archives = new ArrayList<>();
    /** The name of the first number that is not there, or null. */
    String missing;
    /** The name without ".partN.zip": the game folder is named after it. */
    String baseName;

    private AgvnArchiveSeries() {}

    /** The series {@code picked} is one of; null for any other file, a lone name.part1.zip and a RAR volume. */
    static AgvnArchiveSeries find(File picked) {
        String name = picked.getName();
        Matcher m = AgvnArchiveKind.PART_N.matcher(name);
        if (!m.matches() || m.group(2).length() > 4) return null;
        boolean rar = m.group(3).equalsIgnoreCase("rar");
        if (rar && !wholeRar(picked)) return null;
        File dir = picked.getAbsoluteFile().getParentFile();
        String[] names = dir != null ? dir.list() : null;
        TreeMap<Integer, String> byNumber = new TreeMap<>();
        if (names != null) {
            for (String n : names) {
                Matcher p = AgvnArchiveKind.PART_N.matcher(n);
                if (!p.matches() || p.group(2).length() > 4 || !p.group(1).equalsIgnoreCase(m.group(1))
                        || !p.group(3).equalsIgnoreCase(m.group(3))) continue;
                int number = Integer.parseInt(p.group(2));
                // part1 and part01 side by side: the one numbered like the picked file
                if (!byNumber.containsKey(number) || p.group(2).length() == m.group(2).length()) byNumber.put(number, n);
            }
        }
        byNumber.put(Integer.parseInt(m.group(2)), name);
        int first = byNumber.firstKey() == 0 ? 0 : 1, last = byNumber.lastKey();
        if (byNumber.size() == 1 && last == first) return null; // one archive, whatever its name
        AgvnArchiveSeries series = new AgvnArchiveSeries();
        series.baseName = m.group(1);
        for (int i = first; i <= last; i++) {
            String part = byNumber.get(i);
            if (part != null) series.archives.add(new File(dir, part));
            else if (series.missing == null) series.missing = AgvnArchiveRarSet.partName(name, m, i);
        }
        return series;
    }

    /** One archive of a series as {@link AgvnUnpack} reads it: this file alone, no parts looked for beside it. */
    static AgvnArchiveKind.Volumes single(File archive, String baseName) {
        AgvnArchiveKind.Volumes v = new AgvnArchiveKind.Volumes();
        v.first = archive;
        v.files.add(archive);
        v.baseName = baseName;
        String name = archive.getName();
        AgvnArchiveKind.Type seen = AgvnArchiveKind.sniff(archive);
        v.type = seen != AgvnArchiveKind.Type.UNKNOWN ? seen : AgvnArchiveKind.byExtension(name.substring(name.lastIndexOf('.') + 1));
        return v;
    }

    /**
     * True when a .rar says it is a whole archive, not a volume of a set (RAR 4: the main header's flag 1; RAR 5: the
     * archive flag 1). False when it is a volume or cannot be told (headers locked, not a RAR): then it is left to
     * 7-Zip as a set, as before.
     */
    static boolean wholeRar(File file) {
        byte[] b = new byte[32];
        int n = 0;
        try (InputStream in = new FileInputStream(file)) {
            int r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
        } catch (IOException e) {
            return false;
        }
        return wholeRar(b, n);
    }

    static boolean wholeRar(byte[] b, int n) {
        if (n < 13 || b[0] != 'R' || b[1] != 'a' || b[2] != 'r' || b[3] != '!' || b[4] != 0x1A || b[5] != 7) return false;
        if (b[6] == 0) return b[9] == 0x73 && (b[10] & 1) == 0; // RAR 4: CRC (2), type, flags
        if (b[6] != 1 || b[7] != 0) return false;
        int[] at = {12}; // RAR 5: after the signature and the header's CRC
        vint(b, n, at); // header size
        if (vint(b, n, at) != 1) return false; // 1: the main header; 4: headers locked
        long flags = vint(b, n, at);
        if ((flags & 1) != 0) vint(b, n, at); // extra area size
        if ((flags & 2) != 0) vint(b, n, at); // data size
        long archive = vint(b, n, at);
        return archive >= 0 && (archive & 1) == 0;
    }

    /** RAR 5's number of variable length at {@code at[0]}, which moves past it; -1 when the bytes end. */
    private static long vint(byte[] b, int n, int[] at) {
        long value = 0;
        for (int shift = 0; shift < 63; shift += 7) {
            if (at[0] >= n) return -1;
            int c = b[at[0]++] & 0xFF;
            value |= (long) (c & 0x7F) << shift;
            if ((c & 0x80) == 0) return value;
        }
        return -1;
    }
}
