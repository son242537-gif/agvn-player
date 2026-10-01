/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The phone's CPU cores as sysfs and /proc show them, as bit masks (bit n = cpu n). Phones do not all let apps read the
 * same files, so each answer has fallbacks. Pure logic over directories, so tests pass their own.
 */
final class AgvnCpuCores {
    private static final int MAX_CPUS = 32;

    private AgvnCpuCores() {}

    /**
     * The fastest cores, by top speed: from the cpufreq policies, else each core's cpufreq, else each core's scheduler
     * capacity. 0 when none of them can be read, or when every core is the same (nothing faster to move to).
     */
    static int fastest(File cpuDir) {
        int mask = fastestPolicies(new File(cpuDir, "cpufreq"));
        if (mask == 0) mask = fastestPerCpu(cpuDir, "cpufreq/cpuinfo_max_freq");
        if (mask == 0) mask = fastestPerCpu(cpuDir, "cpu_capacity");
        return mask;
    }

    static int fastestPolicies(File cpufreqDir) {
        File[] policies = cpufreqDir.listFiles((dir, name) -> name.startsWith("policy"));
        if (policies == null) return 0;
        long best = 0, slowest = Long.MAX_VALUE;
        int mask = 0;
        for (File policy : policies) {
            long khz = number(new File(policy, "cpuinfo_max_freq"));
            int cpus = mask(text(new File(policy, "related_cpus")));
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

    /** The cores whose {@code file} (under cpuN) holds the highest number; cores without it are left out. */
    static int fastestPerCpu(File cpuDir, String file) {
        long[] value = new long[MAX_CPUS];
        long best = 0, slowest = Long.MAX_VALUE;
        for (int cpu = 0; cpu < MAX_CPUS; cpu++) {
            value[cpu] = number(new File(cpuDir, "cpu" + cpu + "/" + file));
            if (value[cpu] <= 0) continue;
            best = Math.max(best, value[cpu]);
            slowest = Math.min(slowest, value[cpu]);
        }
        if (best <= slowest) return 0;
        int mask = 0;
        for (int cpu = 0; cpu < MAX_CPUS; cpu++) if (value[cpu] == best) mask |= 1 << cpu;
        return mask;
    }

    /** The cores a thread may run on, from its /proc status ("Cpus_allowed_list:"); 0 when unknown. */
    static int allowed(File proc, int tid) {
        String status = text(new File(proc, tid + "/status"));
        if (status == null) return 0;
        for (String line : status.split("\n")) {
            if (line.startsWith("Cpus_allowed_list:")) return mask(line.substring("Cpus_allowed_list:".length()));
        }
        return 0;
    }

    /** "cpu6 on, 2803 of 4320 MHz (allowed 3532)", per core of {@code mask}, for the logs; "?" for what cannot be read. */
    static String state(File cpuDir, int mask) {
        StringBuilder s = new StringBuilder();
        for (int cpu = 0; cpu < MAX_CPUS; cpu++) {
            if ((mask & 1 << cpu) == 0) continue;
            File dir = new File(cpuDir, "cpu" + cpu);
            long online = number(new File(dir, "online"));
            if (s.length() > 0) s.append("; ");
            s.append("cpu").append(cpu).append(online == 0 ? " off" : " on").append(", ")
                    .append(mhz(new File(dir, "cpufreq/scaling_cur_freq"))).append(" of ")
                    .append(mhz(new File(dir, "cpufreq/cpuinfo_max_freq"))).append(" MHz (allowed ")
                    .append(mhz(new File(dir, "cpufreq/scaling_max_freq"))).append(')');
        }
        return s.toString();
    }

    /** "/top-app (cpus 0-7)": the cpuset a thread is in and its cores, for the logs; "?" for what cannot be read. */
    static String cpuset(File proc, File cpusetRoot, int tid) {
        String path = text(new File(proc, tid + "/cpuset"));
        if (path == null) return "?";
        path = path.trim();
        int cpus = mask(text(new File(cpusetRoot, path + "/cpus")));
        return path + " (cpus " + (cpus != 0 ? list(cpus) : "?") + ")";
    }

    private static String mhz(File khzFile) {
        long khz = number(khzFile);
        return khz > 0 ? String.valueOf(khz / 1000) : "?";
    }

    /** "6 7", "6-7" or "0-3,6" as a bit mask; 0 for anything else. */
    static int mask(String list) {
        if (list == null) return 0;
        int mask = 0;
        try {
            for (String part : list.trim().split("[\\s,]+")) {
                if (part.isEmpty()) continue;
                int dash = part.indexOf('-');
                int from = Integer.parseInt(dash < 0 ? part : part.substring(0, dash));
                int to = dash < 0 ? from : Integer.parseInt(part.substring(dash + 1));
                for (int cpu = from; cpu <= to && cpu < MAX_CPUS; cpu++) mask |= 1 << cpu;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        return mask;
    }

    /** "6-7" or "0,2,4-5". */
    static String list(int mask) {
        StringBuilder s = new StringBuilder();
        for (int cpu = 0; cpu < MAX_CPUS; cpu++) {
            if ((mask & 1 << cpu) == 0) continue;
            int end = cpu;
            while (end + 1 < MAX_CPUS && (mask & 1 << end + 1) != 0) end++;
            if (s.length() > 0) s.append(',');
            s.append(cpu);
            if (end > cpu) s.append('-').append(end);
            cpu = end;
        }
        return s.toString();
    }

    private static long number(File f) {
        String t = text(f);
        try {
            return t != null ? Long.parseLong(t.trim()) : -1;
        } catch (NumberFormatException e) {
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
