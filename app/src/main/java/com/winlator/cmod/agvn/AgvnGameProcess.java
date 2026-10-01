/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.system.Os;
import android.system.OsConstants;

import com.winlator.cmod.xserver.Window;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Which process runs the game, from the X window that shows its frames or else from /proc. Pure logic but the window. */
final class AgvnGameProcess {
    private AgvnGameProcess() {}

    /** The process that owns the window or one of its parents (Wine sets _NET_WM_PID on its top-level windows). */
    static int pidOf(Window window) {
        try {
            for (Window w = window; w != null; w = w.getParent()) {
                int pid = w.getProcessId();
                if (pid > 0) return pid;
            }
        } catch (RuntimeException ignored) {
            // a malformed property: find the game by its CPU instead
        }
        return 0;
    }

    /**
     * The process that runs the game: {@code windowPid} when it is one of this user's processes, otherwise the one of
     * them that used the most CPU since the last call ({@code seen} keeps their CPU times; the first call compares
     * with zero), leaving out this app's own process and Wine's server. 0 when none is found.
     */
    static int findGame(File proc, int windowPid, int uid, int selfPid, Map<Integer, Long> seen) {
        if (windowPid > 0 && windowPid != selfPid && ownedBy(proc, windowPid, uid)) return windowPid;
        String[] pids = proc.list();
        if (pids == null) return 0;
        Map<Integer, Long> now = new HashMap<>();
        int best = 0;
        long bestUsed = -1;
        for (String name : pids) {
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
            int pid;
            try {
                pid = Integer.parseInt(name);
            } catch (NumberFormatException e) {
                continue;
            }
            if (pid == selfPid || !ownedBy(proc, pid, uid)) continue;
            AgvnGameThreads.Sample s = AgvnGameThreads.parse(AgvnGameThreads.text(new File(proc, pid + "/stat")));
            if (s == null || s.name.equals("wineserver")) continue;
            now.put(pid, s.ticks);
            Long before = seen.get(pid);
            long used = s.ticks - (before != null ? before : 0);
            if (used > bestUsed) {
                best = pid;
                bestUsed = used;
            }
        }
        seen.clear();
        seen.putAll(now);
        return best;
    }

    /** True when the process exists and runs as {@code uid}. */
    static boolean ownedBy(File proc, int pid, int uid) {
        String status = AgvnGameThreads.text(new File(proc, pid + "/status"));
        if (status == null) return false;
        for (String line : status.split("\n")) {
            if (!line.startsWith("Uid:")) continue;
            String[] ids = line.substring(4).trim().split("\\s+");
            return ids[0].equals(String.valueOf(uid));
        }
        return false;
    }

    /** Clock ticks per second of the CPU times in /proc (100 on Android). */
    static long clockTicks() {
        try {
            long ticks = Os.sysconf(OsConstants._SC_CLK_TCK);
            return ticks > 0 ? ticks : 100;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return 100;
        }
    }
}
