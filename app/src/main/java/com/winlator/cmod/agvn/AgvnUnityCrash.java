/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A crash a Unity game's own crash handler caught. Unity catches a crash in the game itself, so Wine prints nothing:
 * Unity writes "Crash!!!", the modules loaded and the stack into its log (Player.log, output_log.txt before Unity
 * 2017.2) and, since Unity 2019, "A crash has been intercepted by the crash handler..." with the folder of its report
 * (error.log names the exception). The game is dead then, though its process can stay: Become A Vtuber (Unity 6,
 * Adreno 620) crashed five times at UnityPlayer.dll+0x8d8b1a and hung on its last picture, the player swiped AGVN away,
 * and "Tự sửa lỗi" never knew. {@link AgvnUnityCrashWatch} reads it while the game plays, the doctor and the session
 * summary when it ends ({@link AgvnUnityCrashFiles}). Pure Java (JVM-testable).
 */
final class AgvnUnityCrash {
    static final String START = "Crash!!!", CAUGHT = "A crash has been intercepted by the crash handler",
            STACK = "OUTPUTTING STACK TRACE", END = "END OF STACKTRACE";
    static final int MAX_CHARS = 200;
    private static final Pattern FRAME = Pattern.compile("^0x([0-9A-Fa-f]{1,16}) \\(([^()]+)\\) (.*)$");
    private static final Pattern MODULE =
            Pattern.compile("^.*:([^:]+?) \\(([0-9A-Fa-f]{1,16})\\), size: (\\d{1,12})\\b");
    private static final Pattern FOLDER = Pattern.compile("^\\* [Cc]:[/\\\\](.+)$");
    private static final Pattern EXCEPTION = Pattern.compile("caused an? (.+?) \\((0x[0-9A-Fa-f]{1,8})\\)");
    private static final Pattern ACCESS = Pattern.compile("(Read from|Write to) location ([0-9A-Fa-f]{1,16}) caused");

    private AgvnUnityCrash() {}

    /** True when Unity's log {@code lines} hold a crash it caught. */
    static boolean crashed(List<String> lines) {
        for (String raw : lines) {
            String line = raw.trim();
            if (line.equals(START) || line.startsWith(CAUGHT)) return true;
        }
        return false;
    }

    /** True when Unity wrote all it writes of the crash: the stack's end, or the folder of its report. */
    static boolean complete(List<String> lines) {
        boolean started = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith(CAUGHT)) return true;
            if (line.equals(START)) started = true;
            else if (started && line.contains(END)) return true;
        }
        return false;
    }

    /**
     * Where the last crash was, from its stack's first frame: "UnityPlayer.dll+0x8d8b1a" (the module's base is in the
     * list Unity printed), "GameAssembly!Foo" when Unity named the function, else the module; null without a frame.
     */
    static String where(List<String> lines) {
        Map<String, long[]> bases = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        String frame = null;
        boolean crash = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.equals(START) || line.contains(STACK)) { // the last crash; a long one starts before the tail
                crash = true;
                frame = null;
                continue;
            }
            if (!crash) continue;
            Matcher m = MODULE.matcher(line);
            if (m.find()) {
                String name = m.group(1).trim();
                bases.put(key(name), new long[]{Long.parseUnsignedLong(m.group(2), 16), Long.parseLong(m.group(3))});
                names.put(key(name), name);
            } else if (frame == null && FRAME.matcher(line).matches()) {
                frame = line;
            }
        }
        if (frame == null) return null;
        Matcher m = FRAME.matcher(frame);
        if (!m.matches()) return null;
        String module = m.group(2).trim(), function = m.group(3).trim();
        if (!function.isEmpty() && !function.startsWith("(")) return cut(module + "!" + function);
        long address = Long.parseUnsignedLong(m.group(1), 16);
        long[] base = bases.get(key(module));
        if (base == null || address < base[0] || address - base[0] >= base[1]) return module;
        return names.get(key(module)) + "+0x" + Long.toHexString(address - base[0]);
    }

    /** "Unity báo crash trong UnityPlayer.dll+0x8d8b1a", or null when the lines hold no crash. */
    static String describe(List<String> lines) {
        if (!crashed(lines)) return null;
        String where = where(lines);
        return "Unity báo crash" + (where != null ? " trong " + where : "");
    }

    /** The folder on drive C: of Unity's crash reports, as its log names it ("users/xuser/AppData/..."), or null. */
    static String reportFolder(List<String> lines) {
        String folder = null;
        for (String raw : lines) {
            Matcher m = FOLDER.matcher(raw.trim());
            if (m.matches()) folder = m.group(1).replace('\\', '/');
        }
        if (folder == null) return null;
        for (String part : folder.split("/")) if (part.equals("..")) return null; // drive C only
        return folder;
    }

    /** "Access Violation 0xc0000005, đọc 0x0" from Unity's error.log, or null when it names neither. */
    static String exception(String errorLog) {
        Matcher m = EXCEPTION.matcher(errorLog);
        String what = m.find() ? m.group(1) + " " + m.group(2).toLowerCase(Locale.ROOT) : null;
        Matcher a = ACCESS.matcher(errorLog);
        if (!a.find()) return what;
        String access = (a.group(1).startsWith("Read") ? "đọc 0x" : "ghi 0x")
                + Long.toHexString(Long.parseUnsignedLong(a.group(2), 16));
        return what != null ? what + ", " + access : access;
    }

    /** {@code ev} crashed as Unity said ({@link #describe}), unless Wine named a crash already. */
    static void into(AgvnEvidence ev, String crash) {
        if (crash == null || ev.crashed) return;
        ev.crashed = true;
        ev.params.put("crash", crash);
    }

    /** "unityplayer" for UnityPlayer.dll: the stack names a module without its extension. */
    private static String key(String module) {
        String k = module.toLowerCase(Locale.ROOT);
        return k.endsWith(".dll") || k.endsWith(".exe") ? k.substring(0, k.length() - 4) : k;
    }

    private static String cut(String s) {
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) + "…" : s;
    }
}
