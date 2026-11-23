/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A game's movie that Wine cannot decode, read from Wine's output. The app's Wine (proton-9.0-arm64ec) gives GStreamer's
 * decoder a WMV3 (Windows Media Video 9) movie without its sequence header (codec_data), so the decoder refuses every
 * frame: "Subclass refused caps" and "Failed to push transform input", hundreds of times a second. Wine tells the game
 * nothing, and a KiriKiri game waited 4.5 minutes for its opening movie to end (0.1.20). {@link #LINES} such lines
 * within {@link #WINDOW_MS}, for {@link #MIN_SPAN_MS} at least, are a movie that does not play; so are
 * {@link #PUSHES} frames the decoder was handed and dropped ("Failed to push transform input") within
 * {@link #PUSH_WINDOW_MS}, however close together: a 32-bit game reads its movies Wine's old way
 * ({@link AgvnMedia32}), which can hand the decoder the whole movie at once, and Sickly Days (KiriKiri Z, WMV3
 * 1920x1080, 24/11/2026) stayed black with sound for 94 s, 6905 such lines in its log and no word from the app. One
 * that stops for {@link #REARM_MS} lets the next one be found. Thread-safe. Pure Java (JVM-testable).
 */
final class AgvnMovieRules {
    /** A frame the decoder refused: GStreamer's gstvideodecoder.c, and Wine's wg_transform.c. */
    static final String[] FAILED = {"Subclass refused caps", "Failed to push transform input"};
    static final int LINES = 100;
    static final long WINDOW_MS = 5000, MIN_SPAN_MS = 1000, QUIET_MS = 2000, REARM_MS = 10_000;
    /** Frames dropped within {@link #PUSH_WINDOW_MS} that make a movie that does not play (about 7 s of video). */
    static final int PUSHES = 200;
    static final long PUSH_WINDOW_MS = 10_000;
    private static final Pattern CAPS = Pattern.compile("caps video/(?:x-)?([\\w-]+)");
    private static final Pattern FORMAT = Pattern.compile("format=\\(string\\)(\\w+)");
    private static final Pattern SIZE = Pattern.compile("width=\\(int\\)(\\d+), height=\\(int\\)(\\d+)");

    private long windowStart = -1, lastFailMs = -1, pushStart = -1;
    private int count, pushes;
    private boolean found;
    private String movie;

    /** True once for a movie: when {@code line} is a refused frame that makes it one that does not play. */
    synchronized boolean add(String line, long nowMs) {
        String m = movie(line);
        if (m != null) movie = m;
        if (!failed(line)) return false;
        if (found && nowMs - lastFailMs > REARM_MS) found = false; // that movie ended: this is another
        lastFailMs = nowMs;
        if (windowStart < 0 || nowMs - windowStart > WINDOW_MS) {
            windowStart = nowMs;
            count = 0;
        }
        if (line.contains(FAILED[1])) { // a frame the decoder dropped
            if (pushStart < 0 || nowMs - pushStart > PUSH_WINDOW_MS) {
                pushStart = nowMs;
                pushes = 0;
            }
            pushes++;
        }
        boolean flood = ++count >= LINES && nowMs - windowStart >= MIN_SPAN_MS;
        if (found || !(flood || pushes >= PUSHES)) return false;
        found = true;
        return true;
    }

    /** True while the decoder still refuses frames: one in the last {@link #QUIET_MS}. */
    synchronized boolean stillFailing(long nowMs) {
        return lastFailMs >= 0 && nowMs - lastFailMs <= QUIET_MS;
    }

    /** The movie's format, "WMV3 1280x720", from the last caps Wine printed, or null. */
    synchronized String movie() {
        return movie;
    }

    static boolean failed(String line) {
        for (String f : FAILED) if (line.contains(f)) return true;
        return false;
    }

    /** "WMV3 1280x720" from a line with a compressed video's caps ("caps video/x-wmv, width=(int)1280"), or null. */
    static String movie(String line) {
        Matcher caps = CAPS.matcher(line);
        if (!caps.find() || caps.group(1).equals("raw")) return null;
        String rest = line.substring(caps.start());
        Matcher format = FORMAT.matcher(rest), size = SIZE.matcher(rest);
        String name = format.find() ? format.group(1) : caps.group(1).toUpperCase(Locale.ROOT);
        return size.find() ? name + " " + size.group(1) + "x" + size.group(2) : name;
    }
}
