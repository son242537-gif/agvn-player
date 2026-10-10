/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a game ended, read from what it left: Wine's crash report ("Unhandled exception: page fault on read access to
 * 0xfeeefeee ..." and the backtrace's first frame), a crash Unity caught ({@link AgvnUnityCrashFiles#summary}) or
 * Ren'Py's traceback.txt. Pure Java (JVM-testable).
 */
final class AgvnCrashScan {
    private static final Pattern EXCEPTION = Pattern.compile("Unhandled exception: (.+?)(?: in (?:\\w+ )?\\d+-bit code)?(?: \\(0x[0-9a-fA-F]+\\))?\\.?$");
    private static final Pattern WINE_LINE = Pattern.compile("Unhandled (.+?) at address");
    private static final Pattern FRAME = Pattern.compile("^=>0\\s+0x[0-9a-fA-F]+\\s+(?:(\\S+)\\s+)?in\\s+(\\S+)(?:\\s+\\(\\+(0x[0-9a-fA-F]+)\\))?");
    private static final Pattern PAGE_FAULT = Pattern.compile("page fault on (read|write|execute) access to (0x[0-9a-fA-F]+|[0-9a-fA-F]+)");
    static final int MAX_CHARS = 200;
    static final String CRASH_FILE = "crash.txt";

    private AgvnCrashScan() {}

    /**
     * How a game really ended: a crash Wine printed (its report goes to crash.txt in {@code dir}), else a crash Unity
     * caught (its report, if any, to unity-crash.txt), else a Ren'Py traceback.txt written in the session; else null.
     */
    static String sessionError(List<String> crash, List<File> logs, long startMs, File dir) {
        String crashed = describe(crash);
        if (crashed != null) {
            try {
                Files.write(new File(dir, CRASH_FILE).toPath(), (String.join("\n", crash) + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // the summary still says what crashed
            }
            return "Game bị lỗi (crash) – " + crashed + " (chi tiết: " + CRASH_FILE + ")";
        }
        String unity = AgvnUnityCrashFiles.summary(logs, startMs, dir);
        if (unity != null) return "Game bị lỗi (crash) – " + unity;
        for (File log : logs) {
            if (!log.getName().equalsIgnoreCase("traceback.txt") || !log.isFile()) continue;
            if (startMs > 0 && log.lastModified() < startMs - AgvnEngineLogs.OLD_SLACK_MS) continue;
            String error;
            try {
                error = renpyError(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
            } catch (IOException e) {
                error = null;
            }
            return "Game bị lỗi Ren'Py – " + (error != null ? error : "xem traceback.txt");
        }
        return null;
    }

    /** The first line of a crash: Wine's "Unhandled ..." line, or winedbg's report when that line is missing. */
    static boolean startsCrash(String line) {
        return line.contains("Unhandled exception") || line.contains("Unhandled page fault") || line.contains("starting debugger");
    }

    /** "page fault đọc 0xfeeefeee trong renpysound.pyd+0xe7a7", or null when the lines hold no crash. */
    static String describe(List<String> lines) {
        String what = null, where = null;
        for (String raw : lines) {
            String line = raw.trim();
            Matcher m = EXCEPTION.matcher(line);
            if (m.find()) what = m.group(1);
            else if (what == null && (m = WINE_LINE.matcher(line)).find()) what = m.group(1);
            if (where == null && (m = FRAME.matcher(line)).find()) {
                String module = m.group(2), offset = m.group(3), symbol = m.group(1);
                where = offset != null ? module + "+" + offset : symbol != null ? module + "!" + symbol.replace("()", "") : module;
            }
        }
        if (what == null && where == null) {
            for (String line : lines) if (startsCrash(line)) return cut(line.trim());
            return null;
        }
        String text = what != null ? words(what) : "lỗi";
        return cut(where != null ? text + " trong " + where : text);
    }

    /** "page fault on read access to 0xfeeefeee" in Vietnamese; other kinds stay as Wine wrote them. */
    static String words(String what) {
        Matcher m = PAGE_FAULT.matcher(what);
        if (!m.find()) return what;
        String access = m.group(1).equals("read") ? "đọc" : m.group(1).equals("write") ? "ghi" : "chạy mã ở";
        String address = m.group(2).toLowerCase(Locale.ROOT);
        return "page fault " + access + " " + (address.startsWith("0x") ? address : "0x" + address);
    }

    /** The error of a Ren'Py traceback.txt: its first part's last unindented line ("NameError: ..."), or null. */
    static String renpyError(String traceback) {
        if (traceback == null) return null;
        String found = null;
        for (String line : traceback.split("\r?\n")) {
            if (line.startsWith("-- Full Traceback")) break;
            if (!line.trim().isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("I'm sorry")
                    && !line.startsWith("While ")) found = line.trim();
        }
        return found != null ? cut(found) : null;
    }

    private static String cut(String s) {
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) + "…" : s;
    }
}
