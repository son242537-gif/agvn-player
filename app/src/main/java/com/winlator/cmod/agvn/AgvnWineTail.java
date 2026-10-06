/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.Callback;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What Wine and the programs it starts print, kept in memory for the play session summary, with "Bật debug Wine" off
 * too. Wine prints "Unhandled page fault ..." and winedbg its crash report whatever WINEDEBUG says, but nothing read
 * them, and winedbg exits with 0, so a crashed game read as a normal end. Keeps the last {@link #LINES} lines and the
 * first crash report (it can be longer than the tail: modules and threads follow the backtrace). A line that came in
 * the last {@link #RECENT} kept ones (its thread and addresses aside) is counted, not kept: a movie Wine cannot decode
 * prints two lines hundreds of times a second ({@link AgvnMovieRules}), which pushed everything else out. When no more
 * than {@link #RECENT} come between two kept lines, they are kept after all: DXVK lists Vulkan extensions with
 * "extension supported : 1" under one after another, and its list read "[AGVN] ×1 dòng lặp lại" (0.1.21). The tail goes
 * into the session folder as {@value #FILE}.
 */
public final class AgvnWineTail implements Callback<String> {
    static final int LINES = 300, CRASH_LINES = 200, LINE_CHARS = 1000, RECENT = 4;
    static final String FILE = "wine-cuoi.txt";
    private static final AgvnWineTail INSTANCE = new AgvnWineTail();

    private final ArrayDeque<String> tail = new ArrayDeque<>();
    private final List<String> crash = new ArrayList<>();
    private final ArrayDeque<String> recentKeys = new ArrayDeque<>();
    /** The first {@link #RECENT} lines counted in {@link #again}. */
    private final List<String> repeats = new ArrayList<>();
    private boolean inCrash;
    private int again;

    public static AgvnWineTail get() {
        return INSTANCE;
    }

    /** A new game: forget the last one's lines. */
    public synchronized void reset() {
        tail.clear();
        crash.clear();
        recentKeys.clear();
        repeats.clear();
        inCrash = false;
        again = 0;
    }

    @Override
    public synchronized void call(String line) {
        if (line == null) return;
        if (line.length() > LINE_CHARS) line = line.substring(0, LINE_CHARS);
        if (!inCrash && crash.isEmpty() && AgvnCrashScan.startsCrash(line)) inCrash = true;
        if (inCrash) {
            crash.add(line);
            if (crash.size() >= CRASH_LINES) inCrash = false;
        }
        String key = AgvnLogRepeats.key(line);
        if (recentKeys.contains(key)) {
            if (++again <= RECENT) repeats.add(line);
            return;
        }
        for (String counted : counted()) keep(counted);
        again = 0;
        repeats.clear();
        keep(line);
        recentKeys.addLast(key);
        if (recentKeys.size() > RECENT) recentKeys.removeFirst();
    }

    private void keep(String line) {
        if (tail.size() == LINES) tail.removeFirst();
        tail.addLast(line);
    }

    /** The lines counted since the last one kept: themselves when a few, else how many. */
    private List<String> counted() {
        return again <= RECENT ? repeats : Collections.singletonList("[AGVN] ×" + again + " dòng lặp lại");
    }

    /** The first crash report of the game, or an empty list. */
    public synchronized List<String> crash() {
        return new ArrayList<>(crash);
    }

    /** The last {@link #LINES} lines, the ones counted at the end included. */
    public synchronized List<String> lines() {
        List<String> lines = new ArrayList<>(tail);
        lines.addAll(counted());
        return lines.size() > LINES ? new ArrayList<>(lines.subList(lines.size() - LINES, lines.size())) : lines;
    }

    /**
     * The last lines to {@code dir}/{@value #FILE}, nothing when there are none: what the game said before it closed
     * (Godot's "OpenGL 3.3" error, DXVK's adapter lines and errors, Mesa's), with the game's own error dialog closed.
     */
    void save(File dir) {
        List<String> lines = lines();
        if (lines.isEmpty()) return;
        try {
            Files.write(new File(dir, FILE).toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
            // one log file less
        }
    }
}
