/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The notes of a running session (dang-chay.txt): "key=value" lines such as start=, game=, shortcut=, container=,
 * log=, logscan= (a folder searched for Godot's log at the end), wine= and removed= (the player swiped AGVN away).
 * Pure Java (JVM-testable).
 */
final class AgvnSessionNotes {
    static final String REMOVED = "removed";
    /** How a session ended that the player closed by swiping AGVN away from the recent apps. */
    static final String REMOVED_HOW = "Người chơi vuốt tắt AGVN khỏi danh sách app gần đây (không phải máy tắt game)";

    private AgvnSessionNotes() {}

    /** True when the player swiped AGVN away during the session: Android's SIGKILL then is AGVN's own. */
    static boolean removedByPlayer(String notes) {
        return !text(notes, REMOVED).isEmpty();
    }

    /** The engine logs the notes name (log=), and Godot's written since {@code start} in the folders they name (logscan=). */
    static List<File> engineLogs(String notes, long start) {
        List<File> logs = files(notes, "log=");
        for (File roaming : files(notes, "logscan=")) logs.addAll(AgvnGodotFiles.logs(roaming, start));
        return logs;
    }

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
