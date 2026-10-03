/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.system.Os;
import android.system.OsConstants;

import com.winlator.cmod.core.ProcessHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How busy the GPU and the game's busiest thread are, for {@link AgvnSlowWatch}. The GPU from the kernel's load files
 * (Adreno's kgsl, Mali's utilisation, MediaTek's ged), which some phones do not let apps read (-1 then); the CPU from
 * Wine's threads in /proc (the game's main thread, DXVK's, the emulator's). The parsing is pure Java (JVM-testable).
 */
final class AgvnLoadProbe {
    private static final String[] GPU_FILES = {
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/module/ged/parameters/gpu_loading"};
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private final Map<String, Long> lastTicks = new HashMap<>();
    private String gpuFile;
    private boolean gpuUnreadable;
    private long lastMs;

    /** GPU busy percent now, or -1 when this phone does not tell apps. */
    int gpuPercent() {
        if (gpuUnreadable) return -1;
        if (gpuFile != null) return parseGpu(gpuFile, read(gpuFile));
        for (String f : GPU_FILES) {
            int value = parseGpu(f, read(f));
            if (value >= 0) {
                gpuFile = f;
                return value;
            }
        }
        gpuUnreadable = true;
        return -1;
    }

    /** "45 %" or "45" → 45; kgsl's gpubusy "busy total" → busy × 100 / total; -1 when unreadable. */
    static int parseGpu(String path, String text) {
        if (text == null) return -1;
        String t = text.trim();
        if (path.endsWith("/gpubusy")) {
            String[] parts = t.split("\\s+");
            try {
                long busy = Long.parseLong(parts[0]), total = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                return total > 0 ? (int) Math.min(100, busy * 100 / total) : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        Matcher m = NUMBER.matcher(t);
        return m.find() ? (int) Math.min(100, Long.parseLong(m.group())) : -1;
    }

    /** The share of one core the busiest game thread used since the last call (1.0 = a whole core); -1 the first time. */
    double busiestThread(long nowMs) {
        Map<String, Long> ticks = threadTicks();
        double busiest = busiest(lastTicks, ticks, nowMs - lastMs, Os.sysconf(OsConstants._SC_CLK_TCK));
        lastTicks.clear();
        lastTicks.putAll(ticks);
        lastMs = nowMs;
        return busiest;
    }

    static double busiest(Map<String, Long> before, Map<String, Long> after, long ms, long ticksPerSecond) {
        if (before.isEmpty() || ms <= 0 || ticksPerSecond <= 0) return -1;
        long most = 0;
        for (Map.Entry<String, Long> e : after.entrySet()) {
            Long was = before.get(e.getKey());
            if (was != null) most = Math.max(most, e.getValue() - was);
        }
        return most * 1000.0 / ticksPerSecond / ms;
    }

    /** utime + stime of each thread of Wine's processes, by "pid/tid". */
    private static Map<String, Long> threadTicks() {
        Map<String, Long> ticks = new HashMap<>();
        for (String pid : ProcessHelper.listRunningWineProcesses()) {
            File[] tasks = new File("/proc/" + pid + "/task").listFiles();
            if (tasks == null) continue;
            for (File task : tasks) {
                String stat = read(task.getPath() + "/stat");
                if (stat == null) continue;
                try {
                    ticks.put(pid + "/" + task.getName(), AgvnStartupProgress.cpuTicks(stat));
                } catch (RuntimeException ignored) {
                    // the thread just ended
                }
            }
        }
        return ticks;
    }

    private static String read(String path) {
        try {
            return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
