/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game's threads as /proc shows them: how much CPU each used over a span, its name and the core it last ran on.
 * Under FEX or Box64 the game's main thread is usually the busiest one. Pure logic over a /proc directory, so tests
 * pass their own.
 */
final class AgvnGameThreads {
    /** One thread (or a whole process) at one moment. */
    static final class Sample {
        final int id;
        final String name;
        final long ticks; // user + system CPU time so far, in clock ticks
        final int core;

        Sample(int id, String name, long ticks, int core) {
            this.id = id;
            this.name = name;
            this.ticks = ticks;
            this.core = core;
        }
    }

    /** A process's threads at one moment. */
    static final class Snapshot {
        final int pid;
        final long timeNs;
        final Map<Integer, Sample> threads;

        Snapshot(int pid, long timeNs, Map<Integer, Sample> threads) {
            this.pid = pid;
            this.timeNs = timeNs;
            this.threads = threads;
        }
    }

    /** A thread's CPU use over a span, in percent of one core. */
    static final class Busy {
        final int tid;
        final String name;
        final int core;
        final int percent;

        Busy(int tid, String name, int core, int percent) {
            this.tid = tid;
            this.name = name;
            this.core = core;
            this.percent = percent;
        }
    }

    private AgvnGameThreads() {}

    /** The process's threads now, or null when it is gone. */
    static Snapshot read(File proc, int pid, long timeNs) {
        File[] tasks = new File(proc, pid + "/task").listFiles();
        if (tasks == null) return null;
        Map<Integer, Sample> threads = new HashMap<>();
        for (File task : tasks) {
            Sample s = parse(text(new File(task, "stat")));
            if (s != null) threads.put(s.id, s);
        }
        return threads.isEmpty() ? null : new Snapshot(pid, timeNs, threads);
    }

    /** A /proc stat line: "id (name) state ppid ...", with utime and stime as fields 14 and 15, the core as 39. */
    static Sample parse(String stat) {
        if (stat == null) return null;
        int open = stat.indexOf('('), close = stat.lastIndexOf(')');
        if (open < 0 || close < open || close + 2 > stat.length()) return null;
        try {
            String[] f = stat.substring(close + 2).trim().split(" +"); // f[0] is field 3
            if (f.length < 13) return null;
            int core = f.length > 36 ? Integer.parseInt(f[36]) : -1;
            return new Sample(Integer.parseInt(stat.substring(0, open).trim()), stat.substring(open + 1, close),
                    Long.parseLong(f[11]) + Long.parseLong(f[12]), core);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The threads that ran between two snapshots of the same process, busiest first. */
    static List<Busy> busiest(Snapshot from, Snapshot to, long ticksPerSecond) {
        List<Busy> out = new ArrayList<>();
        long spanNs = to.timeNs - from.timeNs;
        if (from.pid != to.pid || spanNs <= 0 || ticksPerSecond <= 0) return out;
        for (Sample s : to.threads.values()) {
            Sample before = from.threads.get(s.id);
            long used = s.ticks - (before != null ? before.ticks : 0); // a thread started meanwhile used all of it
            if (used <= 0) continue;
            out.add(new Busy(s.id, s.name, s.core, (int) Math.round(100.0 * used * 1e9 / ticksPerSecond / spanNs)));
        }
        Collections.sort(out, (a, b) -> b.percent - a.percent);
        return out;
    }

    /** CPU time a thread used so far, in ns (schedstat, else the stat ticks); -1 when it is gone. */
    static long cpuNs(File proc, int pid, int tid, long ticksPerSecond) {
        File task = new File(proc, pid + "/task/" + tid);
        String sched = text(new File(task, "schedstat"));
        if (sched != null) {
            try {
                return Long.parseLong(sched.trim().split("\\s+")[0]);
            } catch (RuntimeException ignored) {
                // fall back to the coarser ticks
            }
        }
        Sample s = parse(text(new File(task, "stat")));
        return s != null && ticksPerSecond > 0 ? s.ticks * 1_000_000_000L / ticksPerSecond : -1;
    }

    /** The ids of the busiest threads that used at least {@code minPercent} of a core, at most {@code max}. */
    static int[] top(List<Busy> busiestFirst, int minPercent, int max) {
        int n = 0;
        while (n < Math.min(max, busiestFirst.size()) && busiestFirst.get(n).percent >= minPercent) n++;
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) ids[i] = busiestFirst.get(i).tid;
        return ids;
    }

    /** All the threads together, in percent of one core (200 = two whole cores). */
    static int total(List<Busy> threads) {
        int sum = 0;
        for (Busy b : threads) sum += b.percent;
        return sum;
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
            Sample s = parse(text(new File(proc, pid + "/stat")));
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
        String status = text(new File(proc, pid + "/status"));
        if (status == null) return false;
        for (String line : status.split("\n")) {
            if (!line.startsWith("Uid:")) continue;
            String[] ids = line.substring(4).trim().split("\\s+");
            return ids[0].equals(String.valueOf(uid));
        }
        return false;
    }

    private static String text(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0 && out.size() < 64 * 1024; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return null;
        }
    }
}
