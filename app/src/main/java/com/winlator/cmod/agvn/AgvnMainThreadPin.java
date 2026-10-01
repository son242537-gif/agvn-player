/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.core.ProcessHelper;

import java.io.File;

/**
 * Keeps the game's main thread on the phone's fastest cores while "Ưu tiên nhân CPU mạnh" is on and the thread is busy.
 * On 01/10 the main thread of a game loading at 97% of a core stayed on cpu4-5 (capped at 2745 MHz) and never ran on
 * cpu6-7, even in an Android performance hint session. Only that one thread is pinned, only to the cores it was already
 * allowed (the game's CPU list still holds), and its own cores come back when the switch is turned off or the game
 * ends. Phones with one kind of core are left alone. Each reason it cannot pin is logged once.
 */
final class AgvnMainThreadPin {
    private static final String TAG = "AGVN";
    private static final File PROC = new File("/proc"), SYS_CPU = new File("/sys/devices/system/cpu");

    private final int fastest = AgvnCpuCores.fastest(SYS_CPU);
    private int tid, original, pinned, resets; // the worker thread's own
    private boolean refused, toldCores, toldUnreadable;

    /** While a thread is pinned: pins it again at once if something moved it. Called every 100 ms. */
    void recheck() {
        if (tid > 0) update(true, tid, true);
    }

    /** Pins {@code mainTid} while {@code on} and it is {@code busy}; puts it back otherwise. Called every 2 s. */
    void update(boolean on, int mainTid, boolean busy) {
        if (!on || mainTid <= 0 || tid != 0 && mainTid != tid) release();
        if (!on || mainTid <= 0 || refused) return;
        if (!toldCores) {
            toldCores = true;
            Log.i(TAG, fastest != 0 ? "fastest cores: cpus " + AgvnCpuCores.list(fastest)
                    : "fastest cores unknown (cpufreq not readable, or one kind of core): the main thread is not pinned");
        }
        if (fastest == 0) return;
        int current = allowed(mainTid);
        if (current <= 0) {
            if (!toldUnreadable) Log.w(TAG, "cannot read the cores of the game's main thread " + mainTid);
            toldUnreadable = true;
            tid = 0;
            return;
        }
        if (current == pinned && tid == mainTid) return;
        // the first pin, or Wine applied the game's CPU list again, or the kernel moved it off a parked core
        int target = current & fastest;
        if (target == 0 || target == current || tid == 0 && !busy) {
            if (tid != 0 && target == 0) { // moved where no fast core is allowed: leave it alone
                Log.w(TAG, "game main thread " + mainTid + " was moved to cpus " + AgvnCpuCores.list(current)
                        + ", none of them fast; no longer pinned");
                tid = 0;
            }
            return;
        }
        int error = set(mainTid, target);
        if (error != 0) {
            refused = true;
            Log.w(TAG, "cannot pin the game's main thread " + mainTid + ": errno " + error);
            return;
        }
        if (tid != mainTid) {
            Log.i(TAG, "game main thread " + mainTid + " pinned to cpus " + AgvnCpuCores.list(target) + " (was "
                    + AgvnCpuCores.list(current) + "); cpuset " + AgvnCpuCores.cpuset(PROC, new File("/dev/cpuset"), mainTid)
                    + "; " + AgvnCpuCores.state(SYS_CPU, fastest));
        } else if (++resets <= 3 || resets % 100 == 0) { // something keeps moving it: say how often and to where
            Log.w(TAG, "game main thread " + mainTid + " was moved to cpus " + AgvnCpuCores.list(current) + " (time "
                    + resets + "), pinned again; " + AgvnCpuCores.state(SYS_CPU, fastest));
        }
        tid = mainTid;
        original = current;
        pinned = target;
    }

    /** Gives the pinned thread back its own cores (nothing to do when it is gone). */
    void release() {
        if (tid > 0 && set(tid, original) == 0) Log.i(TAG, "game main thread " + tid + " back on cpus "
                + AgvnCpuCores.list(original));
        tid = 0;
    }

    /** From /proc, else from sched_getaffinity. */
    private static int allowed(int tid) {
        int mask = AgvnCpuCores.allowed(PROC, tid);
        if (mask > 0) return mask;
        try {
            return ProcessHelper.nativeGetProcessAffinity(tid);
        } catch (UnsatisfiedLinkError e) {
            return -1;
        }
    }

    /** 0, or the errno of sched_setaffinity (-1 when libwinlator is not loaded). */
    private static int set(int tid, int mask) {
        try {
            return ProcessHelper.nativeSetProcessAffinity(tid, mask);
        } catch (UnsatisfiedLinkError e) {
            return -1;
        }
    }
}
