/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The logs a game writes beside its exe under names only it knows: "*.log" and "*log*.txt". The Agnietta (cocos2d-x,
 * 17/10/2026) closed by itself as soon as it drew, and "Gửi nhật ký" had none of its action_log.txt and thread.log.
 * Also every .log and .txt of a logs/ (or log/) folder beside the exe, whatever its name ("2026-10-27.txt"): Isekai NTR
 * Inn (a Python game, 27/10/2026) drew nothing at two starts, and the logs/ folder it made at the first stayed unread.
 * They join the engine's logs ({@link AgvnEngineLogs}), so as those they are copied only when written during the
 * session, and shortened when big.
 */
final class AgvnGameDirLogs {
    /** The newest this many at most. */
    static final int MAX = 8;
    /** Folders beside the exe that hold only logs. */
    static final String[] LOG_DIRS = {"logs", "log"};

    private AgvnGameDirLogs() {}

    /** The log files right in {@code exeDir} and in its logs/ folder, newest first. */
    static List<File> beside(File exeDir) {
        File[] files = exeDir != null ? exeDir.listFiles() : null;
        if (files == null) return Collections.emptyList();
        List<File> logs = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && isLog(f.getName())) logs.add(f);
            else if (f.isDirectory() && isLogDir(f.getName())) {
                File[] inside = f.listFiles();
                if (inside != null) for (File g : inside) if (g.isFile() && inLogDir(g.getName())) logs.add(g);
            }
        }
        logs.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return logs.size() > MAX ? new ArrayList<>(logs.subList(0, MAX)) : logs;
    }

    /** "thread.log", "action_log.txt", "Log.txt"; not "changelog.txt", a game's notes. */
    static boolean isLog(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".log") || (n.endsWith(".txt") && n.contains("log") && !n.contains("changelog"));
    }

    static boolean isLogDir(String name) {
        for (String d : LOG_DIRS) if (d.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** A file of a logs/ folder: any .log or .txt. */
    static boolean inLogDir(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".log") || n.endsWith(".txt");
    }
}
