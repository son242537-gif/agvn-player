/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.regex.Pattern;

/**
 * A game that ended as its movie began. Demonion II (32-bit, WMV movies through wmvcore, Proton 9, FEXCore 2601;
 * Xiaomi 2510DPC44G, 0.1.63, 10/11/2026) ended four starts out of five 0.1 to 2 s after Wine began a movie, the lines
 * of Wine's GStreamer ("wg_parser.c … stream-start") its last words, with no crash said: the one that ran 2 min 40 s
 * before it was kept as a good run. "Tự sửa lỗi" now names the movie and offers Proton 10, which reads more kinds of
 * WMV than Proton 9 ({@link AgvnWine10}). Pure Java (JVM-testable).
 */
final class AgvnMovieEnd {
    /** A game that ended this soon after Wine's last line on a movie ended on it. */
    static final long WITHIN_MS = 5000;
    /** winegstreamer's lines (wg_parser.c, wg_source.c, wg_format.c...), printed as a movie opens, logs on or off. */
    private static final Pattern MOVIE = Pattern.compile("\\bWINE wg_\\w+\\.c:\\d+:");
    private static volatile long lastMs;

    private AgvnMovieEnd() {}

    /** A new game starts (AgvnSessionTrack.start). */
    static void reset() {
        lastMs = 0;
    }

    /** A line of Wine's output, read at {@code nowMs} (AgvnMovieWatch, every game's output). */
    static void line(String line, long nowMs) {
        if (line != null && MOVIE.matcher(line).find()) lastMs = nowMs;
    }

    /** Milliseconds from Wine's last movie line to {@code endMs}; -1 without one, or before the end. */
    static long msBefore(long endMs) {
        long last = lastMs;
        return last <= 0 || endMs <= 0 || last > endMs ? -1 : endMs - last;
    }
}
