/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.Arrays;

/** Housekeeping of AGVN-Player/logs: a folder per game, the newest sessions kept, loose logs dropped after a while. */
final class AgvnLogFolders {
    static final int KEEP = 5;
    /** Loose logs in the logs/ root (Wine's own from older versions, logcat) go once this old. */
    static final long LOOSE_MS = 3L * 24 * 3600 * 1000;

    private AgvnLogFolders() {}

    /** Keeps the {@link #KEEP} newest session folders (their names sort by time). */
    static void prune(File gameLogs) {
        File[] sessions = gameLogs.listFiles(File::isDirectory);
        if (sessions == null || sessions.length <= KEEP) return;
        Arrays.sort(sessions, (a, b) -> b.getName().compareTo(a.getName()));
        for (int i = KEEP; i < sessions.length; i++) deleteTree(sessions[i]);
    }

    /** Deletes the .txt files right in {@code root} that are {@link #LOOSE_MS} old; session folders stay. */
    static void pruneLoose(File root, long now) {
        File[] files = root.listFiles(f -> f.isFile() && f.getName().endsWith(".txt"));
        if (files != null) for (File f : files) if (f.lastModified() < now - LOOSE_MS) f.delete();
    }

    /** A game name as a folder name. */
    static String safeName(String name) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return s.isEmpty() ? "game" : s;
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        f.delete();
    }
}
