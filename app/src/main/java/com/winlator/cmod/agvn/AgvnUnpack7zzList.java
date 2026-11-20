/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The file list 7-Zip prints for "l -slt -ba": one block of "Name = value" lines per entry, an empty line between
 * blocks. Which lines a block has depends on the archive's format (a 7z has no "Folder", a RAR has "Hard Link"), so
 * every line is optional but "Path". Pure Java (JVM-testable).
 */
final class AgvnUnpack7zzList {
    static final class Entry {
        /** As stored; cleaned by {@link AgvnArchiveNames#clean}. */
        String path;
        long size;
        boolean dir;
        boolean encrypted;
        /** A symbolic or hard link, or an NTFS stream: never unpacked. */
        boolean link;
    }

    private AgvnUnpack7zzList() {}

    static List<Entry> parse(InputStream utf8) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(utf8, StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<>();
        for (String line; (line = reader.readLine()) != null; ) lines.add(line);
        return parse(lines);
    }

    static List<Entry> parse(List<String> lines) {
        List<Entry> out = new ArrayList<>();
        Entry current = null;
        for (String line : lines) {
            if (line.isEmpty()) {
                current = null;
                continue;
            }
            int eq = line.indexOf(" = ");
            // "CRC =" at the end of a line that lost its last space
            String key = eq >= 0 ? line.substring(0, eq) : line.endsWith(" =") ? line.substring(0, line.length() - 2) : null;
            if (key == null) continue;
            String value = eq >= 0 ? line.substring(eq + 3) : "";
            if (key.equals("Path")) {
                current = new Entry();
                current.path = value;
                out.add(current);
                continue;
            }
            if (current == null) continue; // the archive's own lines, were -ba not given
            switch (key) {
                case "Size":
                    current.size = number(value);
                    break;
                case "Folder":
                    current.dir |= value.equals("+");
                    break;
                case "Attributes": // "D", "D drwxr-xr-x", "DA"
                    current.dir |= value.startsWith("D");
                    break;
                case "Encrypted":
                    current.encrypted = value.equals("+");
                    break;
                case "Symbolic Link":
                case "Hard Link":
                    current.link |= !value.isEmpty();
                    break;
                case "Alternate Stream":
                    current.link |= value.equals("+");
                    break;
                case "Mode": // a TAR's: "lrwxrwxrwx" is a symbolic link, "hrw-r--r--" a hard link
                    current.link |= value.startsWith("l") || value.startsWith("h");
                    current.dir |= value.startsWith("d");
                    break;
                default:
            }
        }
        return out;
    }

    private static long number(String value) {
        try {
            return value.isEmpty() ? 0 : Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
