/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Process;
import android.util.Log;

/**
 * Gives the game's main thread nice -2 while "Ưu tiên nhân CPU mạnh" is on and the thread is busy. On 01/10 GameHub ran
 * the same Unity game's main thread at nice -2 and the phone (HyperOS) put it on cpu6-7 in 35 of 40 samples, while
 * AGVN's at nice 0 stayed on cpu5; affinity, cpuset and the other threads were the same in both. Android lets an app
 * raise its own threads to this level, and the game's process runs as the app's user.
 *
 * <p>The priority is set once per main thread, so a priority the game sets later is kept. Turning the switch off or the
 * game ending gives the thread its own priority back.
 */
final class AgvnMainThreadPriority {
    private static final String TAG = "AGVN";
    static final int NICE = -2;

    private int tid, original; // the worker thread's own
    private boolean refused;

    /** Raises {@code mainTid} once while {@code on} and it is {@code busy}; gives it back otherwise. */
    void update(boolean on, int mainTid, boolean busy) {
        if (!on || mainTid <= 0 || tid != 0 && mainTid != tid) release();
        if (!on || mainTid <= 0 || mainTid == tid || refused || !busy) return;
        try {
            int current = Process.getThreadPriority(mainTid);
            if (current <= NICE) return; // already as high, or the game raised it itself
            Process.setThreadPriority(mainTid, NICE);
            int now = Process.getThreadPriority(mainTid);
            Log.i(TAG, "game main thread " + mainTid + " priority nice " + current + " -> " + now
                    + (now == NICE ? "" : " (the system did not keep it)"));
            tid = mainTid;
            original = current;
        } catch (IllegalArgumentException | SecurityException e) {
            refused = true;
            Log.w(TAG, "cannot raise the game's main thread " + mainTid, e);
        }
    }

    /** Gives the thread back its own priority (nothing to do when it is gone). */
    void release() {
        if (tid <= 0) return;
        try {
            Process.setThreadPriority(tid, original);
            Log.i(TAG, "game main thread " + tid + " back at nice " + original);
        } catch (IllegalArgumentException | SecurityException ignored) {
            // the game ended
        }
        tid = 0;
    }
}
