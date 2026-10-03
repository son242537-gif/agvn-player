/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The notes of a running session (dang-chay.txt): "key=value" lines such as start=, game=, shortcut=, container=,
 * log= and wine=. Pure Java (JVM-testable).
 */
final class AgvnSessionNotes {
    private AgvnSessionNotes() {}

    static long value(String notes, String key, long fallback) {
        try {
            return Long.parseLong(text(notes, key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Every file a key names ("log=" lines can repeat). */
    static List<File> files(String notes, String key) {
        List<File> out = new ArrayList<>();
        for (String line : notes.split("\n")) if (line.startsWith(key)) out.add(new File(line.substring(key.length())));
        return out;
    }

    static String text(String notes, String key) {
        for (String line : notes.split("\n")) if (line.startsWith(key + "=")) return line.substring(key.length() + 1);
        return "";
    }
}
