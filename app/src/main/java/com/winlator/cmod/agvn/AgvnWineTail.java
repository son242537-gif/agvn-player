/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.Callback;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * What Wine and the programs it starts print, kept in memory for the play session summary, with "Bật debug Wine" off
 * too. Wine prints "Unhandled page fault ..." and winedbg its crash report whatever WINEDEBUG says, but nothing read
 * them, and winedbg exits with 0, so a crashed game read as a normal end. Keeps the last {@link #LINES} lines and the
 * first crash report (it can be longer than the tail: modules and threads follow the backtrace). The tail goes into the
 * session folder as {@value #FILE}.
 */
public final class AgvnWineTail implements Callback<String> {
    static final int LINES = 300, CRASH_LINES = 200, LINE_CHARS = 1000;
    static final String FILE = "wine-cuoi.txt";
    private static final AgvnWineTail INSTANCE = new AgvnWineTail();

    private final ArrayDeque<String> tail = new ArrayDeque<>();
    private final List<String> crash = new ArrayList<>();
    private boolean inCrash;

    public static AgvnWineTail get() {
        return INSTANCE;
    }

    /** A new game: forget the last one's lines. */
    public synchronized void reset() {
        tail.clear();
        crash.clear();
        inCrash = false;
    }

    @Override
    public synchronized void call(String line) {
        if (line == null) return;
        if (line.length() > LINE_CHARS) line = line.substring(0, LINE_CHARS);
        if (tail.size() == LINES) tail.removeFirst();
        tail.addLast(line);
        if (!inCrash && crash.isEmpty() && AgvnCrashScan.startsCrash(line)) inCrash = true;
        if (inCrash) {
            crash.add(line);
            if (crash.size() >= CRASH_LINES) inCrash = false;
        }
    }

    /** The first crash report of the game, or an empty list. */
    public synchronized List<String> crash() {
        return new ArrayList<>(crash);
    }

    public synchronized List<String> lines() {
        return new ArrayList<>(tail);
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
