/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a downloaded archive is and which files belong to it: the kind by its first bytes (the extension only when the
 * bytes say nothing), and the parts of a split archive found beside the file the player picked, with the first part
 * that is not there. Pure Java (JVM-testable).
 */
public final class AgvnArchiveKind {
    public enum Type { ZIP, SEVEN_Z, RAR, UNKNOWN }

    /** name.zip.001, name.7z.002, name.001 */
    private static final Pattern NUMBERED = Pattern.compile("(?i)^(.+?)(\\.(zip|7z|rar))?\\.(\\d{3})$");
    /** name.z01 beside name.zip (the .zip is the last part and the one to open) */
    private static final Pattern ZIP_PART = Pattern.compile("(?i)^(.+)\\.z(\\d{2,3})$");
    /** name.r00 beside name.rar */
    private static final Pattern RAR_PART = Pattern.compile("(?i)^(.+)\\.r(\\d{2,3})$");
    /** name.part2.rar */
    private static final Pattern PART_N = Pattern.compile("(?i)^(.+)\\.part(\\d+)\\.(zip|7z|rar)$");
    private static final Pattern SINGLE = Pattern.compile("(?i)^(.+)\\.(zip|7z|rar)$");

    /** The archive a picked file belongs to. */
    public static final class Volumes {
        public Type type = Type.UNKNOWN;
        /** The file to open: name.zip of a split zip, part .001 of a numbered set, else the picked file. */
        public File first;
        /** A numbered set's parts in order (.001, .002, ...): one stream cut into files. Empty for other kinds. */
        public final List<File> parts = new ArrayList<>();
        /** Name of the first part that is not beside the others, or null. */
        public String missing;
        /** The archive's name without its extensions: the game folder is named after it. */
        public String baseName;

        /** A numbered set whose last part is as big as the first: the real last part is likely not downloaded. */
        public String likelyMissingNext() {
            if (parts.isEmpty()) return null;
            File last = parts.get(parts.size() - 1);
            if (last.length() != parts.get(0).length()) return null;
            String name = last.getName();
            return name.substring(0, name.length() - 3) + String.format(Locale.ROOT, "%03d", parts.size() + 1);
        }
    }

    private AgvnArchiveKind() {}

    /** A file "Thêm từ tệp nén" lists: an archive or a part of one. */
    public static boolean isArchiveName(String name) {
        return SINGLE.matcher(name).matches() || NUMBERED.matcher(name).matches() || ZIP_PART.matcher(name).matches()
                || RAR_PART.matcher(name).matches();
    }

    /** The archives of a folder, one name per split archive (its first part; a later part when the first is not there). */
    public static List<String> pickable(String[] names) {
        List<String> out = new ArrayList<>();
        if (names == null) return out;
        for (String name : names) {
            if (!isArchiveName(name)) continue;
            String head = headName(name);
            if (head == null || find(names, head) == null) out.add(name);
        }
        return out;
    }

    /** The name of the part a later part is listed under, or null for a first part or a whole archive. */
    static String headName(String name) {
        Matcher m = NUMBERED.matcher(name);
        if (m.matches()) return Integer.parseInt(m.group(4)) == 1 ? null : name.substring(0, name.length() - 3) + "001";
        m = ZIP_PART.matcher(name);
        if (m.matches()) return m.group(1) + ".zip";
        m = RAR_PART.matcher(name);
        if (m.matches()) return m.group(1) + ".rar";
        return null;
    }

    /** By the first bytes; UNKNOWN when they are no archive's (or the file cannot be read). */
    public static Type sniff(File file) {
        byte[] b = new byte[8];
        int n = 0;
        try (InputStream in = new FileInputStream(file)) {
            int r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
        } catch (IOException e) {
            return Type.UNKNOWN;
        }
        return sniff(b, n);
    }

    static Type sniff(byte[] b, int n) {
        if (n >= 4 && b[0] == 'P' && b[1] == 'K' && ((b[2] == 3 && b[3] == 4) || (b[2] == 5 && b[3] == 6) || (b[2] == 7 && b[3] == 8)))
            return Type.ZIP;
        if (n >= 6 && b[0] == '7' && b[1] == 'z' && (b[2] & 0xFF) == 0xBC && (b[3] & 0xFF) == 0xAF && b[4] == 0x27 && b[5] == 0x1C)
            return Type.SEVEN_Z;
        if (n >= 7 && b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!' && b[4] == 0x1A && b[5] == 7) return Type.RAR;
        return Type.UNKNOWN;
    }

    static Type byExtension(String ext) {
        if (ext == null) return Type.UNKNOWN;
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "zip": return Type.ZIP;
            case "7z": return Type.SEVEN_Z;
            case "rar": return Type.RAR;
            default: return Type.UNKNOWN;
        }
    }

    /** The whole archive of {@code picked}, which may be any of its parts. */
    public static Volumes resolve(File picked) {
        Volumes v = new Volumes();
        File dir = picked.getAbsoluteFile().getParentFile();
        String[] names = dir != null ? dir.list() : null;
        if (names == null) names = new String[]{picked.getName()};
        String name = picked.getName();
        Type named;
        Matcher m;
        if ((m = NUMBERED.matcher(name)).matches()) {
            named = byExtension(m.group(3));
            v.baseName = m.group(1);
            String stem = name.substring(0, name.length() - 3);
            int last = 0;
            for (String n : names) {
                if (n.length() == name.length() && n.regionMatches(true, 0, stem, 0, stem.length()) && NUMBERED.matcher(n).matches())
                    last = Math.max(last, Integer.parseInt(n.substring(n.length() - 3)));
            }
            for (int i = 1; i <= last; i++) {
                String part = find(names, stem + String.format(Locale.ROOT, "%03d", i));
                if (part == null) {
                    if (v.missing == null) v.missing = stem + String.format(Locale.ROOT, "%03d", i);
                } else {
                    v.parts.add(new File(dir, part));
                }
            }
            v.first = v.parts.isEmpty() ? picked : v.parts.get(0);
        } else if ((m = PART_N.matcher(name)).matches()) {
            named = byExtension(m.group(3));
            v.baseName = m.group(1);
            v.first = picked;
        } else if ((m = ZIP_PART.matcher(name)).matches() || ((m = SINGLE.matcher(name)).matches() && m.group(2).equalsIgnoreCase("zip"))) {
            named = Type.ZIP;
            v.baseName = m.group(1);
            String zip = find(names, v.baseName + ".zip");
            int last = 0;
            for (String n : names) {
                Matcher p = ZIP_PART.matcher(n);
                if (p.matches() && p.group(1).equalsIgnoreCase(v.baseName)) last = Math.max(last, Integer.parseInt(p.group(2)));
            }
            for (int i = 1; i <= last && v.missing == null; i++) {
                String part = v.baseName + String.format(Locale.ROOT, ".z%02d", i);
                if (find(names, part) == null) v.missing = part;
            }
            if (zip == null) v.missing = v.baseName + ".zip"; // the last part, which lists all the files
            v.first = zip != null ? new File(dir, zip) : picked;
        } else if ((m = RAR_PART.matcher(name)).matches()) {
            named = Type.RAR;
            v.baseName = m.group(1);
            v.first = picked;
        } else {
            m = SINGLE.matcher(name);
            named = m.matches() ? byExtension(m.group(2)) : Type.UNKNOWN;
            v.baseName = m.matches() ? m.group(1) : name;
            v.first = picked;
        }
        Type seen = v.first.isFile() ? sniff(v.first) : Type.UNKNOWN;
        v.type = seen != Type.UNKNOWN ? seen : named;
        return v;
    }

    /** The name in {@code names} equal to {@code wanted} ignoring case (shared storage ignores case), or null. */
    private static String find(String[] names, String wanted) {
        for (String n : names) if (n.equalsIgnoreCase(wanted)) return n;
        return null;
    }
}
