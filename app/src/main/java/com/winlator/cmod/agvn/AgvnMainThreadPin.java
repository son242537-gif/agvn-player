/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.core.ProcessHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Keeps the game's main thread on the phone's fastest cores while "Ưu tiên nhân CPU mạnh" is on and the thread is busy.
 * On 01/10 the main thread of a game loading at 97% of a core stayed on cpu4-5 (capped at 2745 MHz) and never ran on
 * cpu6-7, even in an Android performance hint session. Only that one thread is pinned, only to the cores it was already
 * allowed (the game's CPU list still holds), and its own CPU set comes back when the switch is turned off or the game
 * ends. Phones with one kind of core are left alone.
 */
final class AgvnMainThreadPin {
    private static final String TAG = "AGVN";
    private static final File CPUFREQ = new File("/sys/devices/system/cpu/cpufreq");

    private final int fastest = fastestMask(CPUFREQ);
    private int tid, original, pinned; // the worker thread's own
    private boolean refused;

    /** Pins {@code mainTid} while {@code on} and it is {@code busy}; puts it back otherwise. Called every 2 s. */
    void update(boolean on, int mainTid, boolean busy) {
        if (!on || mainTid <= 0 || tid != 0 && mainTid != tid) release();
        if (!on || mainTid <= 0 || fastest == 0 || refused) return;
        int current = get(mainTid);
        if (current <= 0) {
            tid = 0; // gone
            return;
        }
        if (current == pinned && tid == mainTid) return;
        // first pin, or Wine applied the game's CPU list again, or the kernel moved it off a parked core
        int target = current & fastest;
        if (target == 0 || target == current || tid == 0 && !busy) {
            if (tid != 0 && target == 0) tid = 0; // its CPU list no longer has fast cores: leave it alone
            return;
        }
        int error = set(mainTid, target);
        if (error != 0) {
            refused = true;
            Log.w(TAG, "cannot pin the game's main thread " + mainTid + ": errno " + error);
            return;
        }
        if (tid != mainTid) Log.i(TAG, "game main thread " + mainTid + " pinned to cpus " + list(target) + " (was "
                + list(current) + ")");
        tid = mainTid;
        original = current;
        pinned = target;
    }

    /** Gives the pinned thread back its own CPU set (nothing to do when it is gone). */
    void release() {
        if (tid > 0 && set(tid, original) == 0) Log.i(TAG, "game main thread " + tid + " back on cpus " + list(original));
        tid = 0;
    }

    private static int get(int tid) {
        try {
            return ProcessHelper.nativeGetProcessAffinity(tid);
        } catch (UnsatisfiedLinkError e) {
            return -1;
        }
    }

    /** 0, or the errno of sched_setaffinity. */
    private static int set(int tid, int mask) {
        try {
            return ProcessHelper.nativeSetProcessAffinity(tid, mask);
        } catch (UnsatisfiedLinkError e) {
            return -1;
        }
    }

    /**
     * The CPUs of the cpufreq policy with the highest top speed, as a bit mask; 0 when unknown or when every policy has
     * the same top speed (nothing faster to move to).
     */
    static int fastestMask(File cpufreqDir) {
        File[] policies = cpufreqDir.listFiles((dir, name) -> name.startsWith("policy"));
        if (policies == null) return 0;
        long best = 0, slowest = Long.MAX_VALUE;
        int mask = 0;
        for (File policy : policies) {
            long khz = number(new File(policy, "cpuinfo_max_freq"));
            int cpus = cpuMask(text(new File(policy, "related_cpus")));
            if (khz <= 0 || cpus == 0) continue;
            slowest = Math.min(slowest, khz);
            if (khz > best) {
                best = khz;
                mask = cpus;
            } else if (khz == best) {
                mask |= cpus;
            }
        }
        return best > slowest ? mask : 0;
    }

    /** "6 7", "6-7" or "0-3,6" as a bit mask; 0 for anything else. */
    static int cpuMask(String list) {
        if (list == null) return 0;
        int mask = 0;
        try {
            for (String part : list.trim().split("[\\s,]+")) {
                if (part.isEmpty()) continue;
                int dash = part.indexOf('-');
                int from = Integer.parseInt(dash < 0 ? part : part.substring(0, dash));
                int to = dash < 0 ? from : Integer.parseInt(part.substring(dash + 1));
                for (int cpu = from; cpu <= to && cpu < 32; cpu++) mask |= 1 << cpu;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        return mask;
    }

    /** "6-7" or "0,2,4-5". */
    static String list(int mask) {
        StringBuilder s = new StringBuilder();
        for (int cpu = 0; cpu < 32; cpu++) {
            if ((mask & 1 << cpu) == 0) continue;
            int end = cpu;
            while (end + 1 < 32 && (mask & 1 << end + 1) != 0) end++;
            if (s.length() > 0) s.append(',');
            s.append(cpu);
            if (end > cpu) s.append('-').append(end);
            cpu = end;
        }
        return s.toString();
    }

    private static long number(File f) {
        try {
            return Long.parseLong(text(f).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String text(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
