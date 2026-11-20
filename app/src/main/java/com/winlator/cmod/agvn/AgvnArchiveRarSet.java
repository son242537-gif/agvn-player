/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The volumes of a RAR set, found beside the file the player picked ({@link AgvnArchiveKind#resolve}): WinRAR's
 * name.part1.rar, name.part2.rar... (also part01, part001) and the old name.rar, name.r00, name.r01... The first volume
 * is the one to open; a volume missing in the middle is named here, one missing at the end only by 7-Zip as it reads.
 * Pure Java (JVM-testable).
 */
final class AgvnArchiveRarSet {
    private static final Pattern OLD_PART = Pattern.compile("(?i)^(.+)\\.r(\\d{2,3})$");

    private AgvnArchiveRarSet() {}

    /** {@code name} (matched by PART_N as {@code m}) with another part number, padded like its own. */
    static String partName(String name, Matcher m, int number) {
        String digits = String.format(Locale.ROOT, "%0" + m.group(2).length() + "d", number);
        return name.substring(0, m.start(2)) + digits + name.substring(m.end(2));
    }

    /** name.partN.rar: parts 1 to the highest number beside {@code name}. */
    static void newStyle(AgvnArchiveKind.Volumes v, File dir, String[] names, String name, Matcher m) {
        int width = m.group(2).length(), last = Integer.parseInt(m.group(2));
        for (String n : names) {
            Matcher p = AgvnArchiveKind.PART_N.matcher(n);
            if (p.matches() && p.group(3).equalsIgnoreCase("rar") && p.group(1).equalsIgnoreCase(m.group(1)) && p.group(2).length() == width)
                last = Math.max(last, Integer.parseInt(p.group(2)));
        }
        for (int i = 1; i <= last; i++) {
            String wanted = partName(name, m, i), found = AgvnArchiveKind.find(names, wanted);
            if (found != null) v.files.add(new File(dir, found));
            else if (v.missing == null) v.missing = wanted;
        }
        if (!v.files.isEmpty() && v.missing == null) v.first = v.files.get(0);
    }

    /** name.rar with name.r00, name.r01... ({@code v.baseName}: "name"); a lone name.rar is a whole archive. */
    static void oldStyle(AgvnArchiveKind.Volumes v, File dir, String[] names) {
        int last = -1;
        for (String n : names) {
            Matcher p = OLD_PART.matcher(n);
            if (p.matches() && p.group(1).equalsIgnoreCase(v.baseName)) last = Math.max(last, Integer.parseInt(p.group(2)));
        }
        String rar = AgvnArchiveKind.find(names, v.baseName + ".rar");
        if (rar != null) {
            v.first = new File(dir, rar);
            v.files.add(v.first);
        } else {
            v.missing = v.baseName + ".rar"; // the first volume
        }
        for (int i = 0; i <= last; i++) {
            String wanted = v.baseName + String.format(Locale.ROOT, ".r%02d", i), found = AgvnArchiveKind.find(names, wanted);
            if (found != null) v.files.add(new File(dir, found));
            else if (v.missing == null) v.missing = wanted;
        }
    }
}
