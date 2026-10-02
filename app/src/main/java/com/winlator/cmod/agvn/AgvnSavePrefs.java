/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.WineRegistryEditor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unity PlayerPrefs, where many Unity games keep their progress: the values under
 * {@code HKCU\Software\<company>\<product>} in the container's user.reg. They travel as one line per value, as Wine
 * writes it ({@code "name"=dword:…}, {@code "name"=hex:…} with continuation lines joined), and are written back one
 * value at a time, so other keys of user.reg are never rewritten by hand. Keys with non-ASCII names are skipped:
 * Wine escapes those characters in user.reg, and matching them is not worth the risk of a broken registry.
 */
final class AgvnSavePrefs {
    private AgvnSavePrefs() {}

    static boolean supported(String key) {
        return key != null && key.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
    }

    /** Value lines of {@code key} (relative to HKCU); empty when the key or user.reg is missing. */
    static List<String> read(File userReg, String key) throws IOException {
        if (!supported(key) || !userReg.isFile()) return Collections.emptyList();
        String header = "[" + key.replace("\\", "\\\\") + "]";
        List<String> out = new ArrayList<>();
        boolean inKey = false;
        StringBuilder pending = null;
        for (String line : Files.readAllLines(userReg.toPath(), StandardCharsets.UTF_8)) {
            if (!inKey) {
                inKey = line.regionMatches(true, 0, header, 0, header.length());
                continue;
            }
            if (pending != null) {
                pending.append(line.trim());
                if (!endContinuation(pending)) {
                    out.add(pending.toString());
                    pending = null;
                }
                continue;
            }
            if (line.isEmpty() || line.startsWith("[")) break;
            if (!line.startsWith("\"") && !line.startsWith("@=")) continue; // #time=, #class=
            pending = new StringBuilder(line);
            if (!endContinuation(pending)) {
                out.add(line);
                pending = null;
            }
        }
        if (pending != null) out.add(pending.toString());
        return out;
    }

    /** Drops a trailing continuation backslash; true when the value goes on in the next line. */
    private static boolean endContinuation(StringBuilder value) {
        int last = value.length() - 1;
        if (last < 0 || value.charAt(last) != '\\') return false;
        value.setLength(last);
        return true;
    }

    /** Writes {@code lines} (as {@link #read} returns them) under {@code key}; returns how many values were written. */
    static int write(File userReg, String key, List<String> lines) {
        if (!supported(key) || lines.isEmpty() || !userReg.isFile()) return 0;
        int written = 0;
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            for (String line : lines) {
                String[] value = split(line);
                if (value == null) continue;
                editor.setRawValue(key, value[0], value[1]);
                written++;
            }
        }
        return written;
    }

    /** {name, raw value} of a value line, name null for the default value; null when the line is malformed. */
    static String[] split(String line) {
        if (line.startsWith("@=")) return new String[]{null, line.substring(2)};
        if (!line.startsWith("\"")) return null;
        StringBuilder name = new StringBuilder();
        for (int i = 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                name.append(line.charAt(++i));
            } else if (c == '"') {
                if (i + 1 >= line.length() || line.charAt(i + 1) != '=') return null;
                String raw = line.substring(i + 2);
                return raw.isEmpty() ? null : new String[]{name.toString(), raw};
            } else {
                name.append(c);
            }
        }
        return null;
    }
}
