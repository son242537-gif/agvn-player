/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

/**
 * Time the app keeps the game stopped: the player left the app (XServerDisplayActivity then stops Wine's processes,
 * but in picture-in-picture) or pressed the pause button (⏸) of the game's sidebar. The game shows nothing meanwhile
 * through no fault of its own, yet su-kien.txt read "Đứng hình 191669 ms" for minutes a player most likely spent away
 * from the app (Kaiju Princess, 06/10). {@link AgvnFrameStalls} and {@link AgvnBlackScreen} leave this time out, and
 * su-kien.txt says when the game stopped and when it went on. Times on System.nanoTime()'s clock. Thread-safe.
 */
public final class AgvnGamePause {
    private static final String TAG = "AGVN";
    private static boolean paused; // guarded by the class
    private static long pausedAtNs, pausedNs;

    private AgvnGamePause() {}

    /**
     * A new game (AgvnSessionTrack.start) is not stopped, though the last one may have ended stopped. The total stopped
     * time only grows, so a time measured across this (AgvnBlackScreen may start first) stays right.
     */
    static synchronized void reset(long nowNs) {
        resume(nowNs);
    }

    /** Wine's processes are stopped: {@code left}, the player left the app; else the sidebar's pause button. */
    public static void paused(boolean left) {
        if (!pause(System.nanoTime())) return;
        Log.i(TAG, "game paused: " + (left ? "the player left the app" : "the pause button"));
        AgvnSessionLog.event(left ? "Rời app (sang app khác, tắt màn hình...): game dừng tới khi quay lại"
                : "Bấm nút tạm dừng (⏸) ở thanh bên: game dừng");
    }

    /** Wine's processes go on. */
    public static void resumed() {
        long ns = resume(System.nanoTime());
        if (ns < 0) return;
        Log.i(TAG, "game resumed after " + ns / 1_000_000_000L + " s");
        AgvnSessionLog.event("Game chạy tiếp sau " + ns / 1_000_000_000L + " giây dừng (không tính là đứng hình)");
    }

    /** True when the game ran until now: a stop starts. */
    static synchronized boolean pause(long nowNs) {
        if (paused) return false;
        paused = true;
        pausedAtNs = nowNs;
        return true;
    }

    /** How long the stop that ends now lasted, or -1 when the game was not stopped. */
    static synchronized long resume(long nowNs) {
        if (!paused) return -1;
        paused = false;
        long ns = Math.max(0, nowNs - pausedAtNs);
        pausedNs += ns;
        return ns;
    }

    static synchronized boolean isPaused() {
        return paused;
    }

    /** All the time the game was stopped until {@code nowNs}, a stop going on included. */
    static synchronized long pausedNs(long nowNs) {
        return pausedNs + (paused ? Math.max(0, nowNs - pausedAtNs) : 0);
    }
}
