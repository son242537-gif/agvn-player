/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.Callback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * What Wine and the programs it starts print, kept in memory for the play session summary, with "Bật debug Wine" off
 * too. Wine prints "Unhandled page fault ..." and winedbg its crash report whatever WINEDEBUG says, but nothing read
 * them, and winedbg exits with 0, so a crashed game read as a normal end. Keeps the last {@link #LINES} lines and the
 * first crash report (it can be longer than the tail: modules and threads follow the backtrace).
 */
public final class AgvnWineTail implements Callback<String> {
    static final int LINES = 300, CRASH_LINES = 200, LINE_CHARS = 1000;
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
}
