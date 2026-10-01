/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.util.Log;

import java.util.Arrays;

/**
 * Android performance hints (ADPF, Android 12+) for the game's busiest threads: how long each frame took against the
 * frame time the FPS limit aims at. When frames are late, Android may move those threads to faster cores or raise their
 * clocks. On 01/10 the main thread of a game running at 35-48 FPS stayed on cores capped at 2745 MHz and never ran on
 * the two faster ones. Android checks that the threads run as this app's user; the game's process does. A phone that
 * does not support hints, or refuses them three times, is left alone.
 */
final class AgvnCpuBoost {
    private static final String TAG = "AGVN";
    private static final int GIVE_UP_AFTER = 3;

    private PerformanceHintManager.Session session; // guarded by this
    private int[] tids = new int[0];
    private long targetNs;
    private int failures;
    private boolean unsupported;

    /** Aims the session at these threads and frame time, creating it if needed. Called on the game CPU thread. */
    synchronized void aim(Context context, int[] newTids, long newTargetNs) {
        if (Build.VERSION.SDK_INT < 31 || unsupported || failures >= GIVE_UP_AFTER || newTids.length == 0) return;
        try {
            if (session != null && !Arrays.equals(tids, newTids)) {
                if (Build.VERSION.SDK_INT >= 34) session.setThreads(newTids);
                else close();
            }
            if (session == null) {
                PerformanceHintManager manager = context.getSystemService(PerformanceHintManager.class);
                session = manager != null ? manager.createHintSession(newTids, newTargetNs) : null;
                if (session == null) {
                    unsupported = true;
                    Log.i(TAG, "performance hints: this phone does not support them");
                    return;
                }
            } else if (newTargetNs != targetNs) {
                session.updateTargetWorkDuration(newTargetNs);
            }
            if (!Arrays.equals(tids, newTids) || newTargetNs != targetNs) {
                Log.i(TAG, "performance hints: game threads " + Arrays.toString(newTids) + ", "
                        + newTargetNs / 100_000 / 10.0 + " ms per frame");
            }
            tids = newTids;
            targetNs = newTargetNs;
            failures = 0;
        } catch (RuntimeException e) {
            // SecurityException: Android did not count a thread as this app's, or it ended meanwhile
            failures++;
            Log.w(TAG, "performance hints refused for threads " + Arrays.toString(newTids)
                    + (failures >= GIVE_UP_AFTER ? ", giving up" : ""), e);
            close();
        }
    }

    /** The last frame took {@code actualNs}, or the frame being waited for has taken that long so far. */
    synchronized void report(long actualNs) {
        if (Build.VERSION.SDK_INT < 31 || session == null || actualNs <= 0) return;
        try {
            session.reportActualWorkDuration(actualNs);
        } catch (RuntimeException e) {
            Log.w(TAG, "performance hint not taken", e);
            close();
        }
    }

    synchronized void close() {
        if (Build.VERSION.SDK_INT >= 31 && session != null) session.close();
        session = null;
        tids = new int[0];
    }
}
