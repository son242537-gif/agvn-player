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

    /** A thread's CPU use over a span, in percent of one core; {@code main} for the process's first thread. */
    static final class Busy {
        final int tid;
        final String name;
        final int core;
        final int percent;
        final boolean main;
        /** The cores it may run on, as a bit mask; 0 when not read. */
        final int allowed;

        Busy(int tid, String name, int core, int percent, boolean main) {
            this(tid, name, core, percent, main, 0);
        }

        Busy(int tid, String name, int core, int percent, boolean main, int allowed) {
            this.tid = tid;
            this.name = name;
            this.core = core;
            this.percent = percent;
            this.main = main;
            this.allowed = allowed;
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
            out.add(new Busy(s.id, s.name, s.core, (int) Math.round(100.0 * used * 1e9 / ticksPerSecond / spanNs),
                    s.id == to.pid));
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

    /**
     * The threads to hint, in id order: the main thread (under Wine and FEX it runs the game's loop; its id is the
     * process id) whenever it ran, then the busiest others from {@code minPercent} of a core, at most {@code max}.
     */
    static List<Busy> pick(List<Busy> busiestFirst, int minPercent, int max) {
        List<Busy> picked = new ArrayList<>();
        for (Busy b : busiestFirst) if (b.main && b.percent > 0) picked.add(b);
        for (Busy b : busiestFirst) {
            if (picked.size() >= max || b.percent < minPercent) break;
            if (!b.main) picked.add(b);
        }
        Collections.sort(picked, (a, b) -> Integer.compare(a.tid, b.tid));
        return picked;
    }

    static int[] ids(List<Busy> threads) {
        int[] ids = new int[threads.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = threads.get(i).tid;
        return ids;
    }

    /** "30722 Game.exe (main), 30756 dxvk-cs", for the logs. */
    static String label(List<Busy> threads) {
        StringBuilder s = new StringBuilder();
        for (Busy b : threads) {
            if (s.length() > 0) s.append(", ");
            s.append(b.tid).append(' ').append(b.name).append(b.main ? " (main)" : "");
        }
        return s.toString();
    }

    /** All the threads together, in percent of one core (200 = two whole cores). */
    static int total(List<Busy> threads) {
        int sum = 0;
        for (Busy b : threads) sum += b.percent;
        return sum;
    }

    /** A small /proc or /sys file, or null when it cannot be read. */
    static String text(File f) {
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
