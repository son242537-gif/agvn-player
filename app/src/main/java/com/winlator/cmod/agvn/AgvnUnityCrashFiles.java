/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Where a crash Unity caught is read from ({@link AgvnUnityCrash}): the end of the game's Unity log written in the
 * session, and the error.log of the report Unity made in the container's drive C:. Pure Java (JVM-testable).
 */
final class AgvnUnityCrashFiles {
    /** Unity's error.log of the crash, copied into the session folder. */
    static final String REPORT_FILE = "unity-crash.txt";
    /** What is read of a log's end (the crash, its modules and its stack) and of error.log. */
    static final int TAIL_BYTES = 64 * 1024, REPORT_BYTES = 64 * 1024;

    private AgvnUnityCrashFiles() {}

    /** The crash a Unity log of the session (written since {@code sinceMs}) holds, as AgvnUnityCrash.describe says. */
    static String inLogs(List<File> logs, long sinceMs) {
        for (File log : logs) {
            if (!session(log, sinceMs)) continue;
            try {
                String crash = AgvnUnityCrash.describe(tail(log));
                if (crash != null) return crash;
            } catch (IOException ignored) {
                // a log less to read
            }
        }
        return null;
    }

    /**
     * For the session summary: the crash a Unity log of the session holds, with the exception Unity's error.log names;
     * the error.log is copied into {@code dir} ({@link #REPORT_FILE}). Null without a crash.
     */
    static String summary(List<File> logs, long sinceMs, File dir) {
        for (File log : logs) {
            if (!session(log, sinceMs)) continue;
            try {
                List<String> lines = tail(log);
                String crash = AgvnUnityCrash.describe(lines);
                if (crash == null) continue;
                File error = report(driveC(log), AgvnUnityCrash.reportFolder(lines), sinceMs);
                if (error == null) return crash + " (chi tiết: " + log.getName() + ")";
                byte[] text = head(error, REPORT_BYTES);
                Files.write(new File(dir, REPORT_FILE).toPath(), text);
                String exception = AgvnUnityCrash.exception(new String(text, StandardCharsets.UTF_8));
                return crash + (exception != null ? ": " + exception : "") + " (chi tiết: " + log.getName() + ", "
                        + REPORT_FILE + ")";
            } catch (IOException ignored) {
                // a log less to read
            }
        }
        return null;
    }

    /** A log Unity wrote in this session: not the one it moved aside as it started (Player-prev.log), nor older. */
    static boolean session(File log, long sinceMs) {
        if (!log.isFile() || log.getName().toLowerCase(Locale.ROOT).contains("-prev")) return false;
        return sinceMs <= 0 || log.lastModified() >= sinceMs - AgvnEngineLogs.OLD_SLACK_MS;
    }

    /** The container's drive_c above {@code log} (Player.log is in drive_c/users/xuser/AppData/LocalLow), or null. */
    static File driveC(File log) {
        File f = log.getParentFile();
        while (f != null && !f.getName().equals("drive_c")) f = f.getParentFile();
        return f;
    }

    /** error.log of the newest report in {@code folder} of {@code driveC} made since {@code sinceMs}, or null. */
    static File report(File driveC, String folder, long sinceMs) {
        if (driveC == null || folder == null) return null;
        File[] reports = new File(driveC, folder).listFiles(File::isDirectory);
        File newest = null;
        if (reports != null) for (File r : reports) {
            if (sinceMs > 0 && r.lastModified() < sinceMs - AgvnEngineLogs.OLD_SLACK_MS) continue;
            if (newest == null || r.lastModified() > newest.lastModified()) newest = r;
        }
        File error = newest != null ? new File(newest, "error.log") : null;
        return error != null && error.isFile() ? error : null;
    }

    /** The end of {@code log}, up to {@link #TAIL_BYTES}, a line each (Unity ends some lines with "\r\r\n"). */
    static List<String> tail(File log) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(log, "r")) {
            byte[] buf = new byte[(int) Math.min(in.length(), TAIL_BYTES)];
            in.seek(in.length() - buf.length);
            in.readFully(buf);
            return Arrays.asList(new String(buf, StandardCharsets.UTF_8).split("\r*\n"));
        }
    }

    private static byte[] head(File f, int max) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[(int) Math.min(in.length(), max)];
            in.readFully(buf);
            return buf;
        }
    }
}
