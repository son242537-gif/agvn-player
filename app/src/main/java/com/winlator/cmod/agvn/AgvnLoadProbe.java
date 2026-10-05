/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Process;
import android.system.Os;
import android.system.OsConstants;

import com.winlator.cmod.core.ProcessHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How busy the GPU ({@link AgvnGpuLoad}) and the game's busiest thread are, for the slow-game checks. The threads are
 * Wine's processes (the game's main thread, DXVK's, the emulator's) for a Windows game, or this process for a "Chạy
 * nhẹ" runner (Ren'Py, mkxp-z). An HTML game runs in WebView's own process, which the app cannot read: no threads.
 * {@link #busiest} is pure Java (JVM-testable).
 */
final class AgvnLoadProbe {
    enum Threads { WINE, SELF, NONE }

    private final AgvnGpuLoad gpu = new AgvnGpuLoad();
    private final Threads threads;
    private final Map<String, Long> lastTicks = new HashMap<>();
    private long lastMs;

    AgvnLoadProbe(Threads threads) {
        this.threads = threads;
    }

    /** GPU busy percent now, or -1 when this phone does not tell apps. */
    int gpuPercent(long nowMs) {
        return gpu.percent(nowMs);
    }

    /** The share of one core the busiest thread used since the last call (1.0 = a whole core); -1 when unknown. */
    double busiestThread(long nowMs) {
        if (threads == Threads.NONE) return -1;
        Map<String, Long> ticks = threadTicks(threads == Threads.WINE
                ? ProcessHelper.listRunningWineProcesses() : Collections.singletonList(String.valueOf(Process.myPid())));
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

    /** utime + stime of each thread of {@code pids}, by "pid/tid". */
    private static Map<String, Long> threadTicks(List<String> pids) {
        Map<String, Long> ticks = new HashMap<>();
        for (String pid : pids) {
            File[] tasks = new File("/proc/" + pid + "/task").listFiles();
            if (tasks == null) continue;
            for (File task : tasks) {
                try {
                    String stat = new String(Files.readAllBytes(new File(task, "stat").toPath()), StandardCharsets.UTF_8);
                    ticks.put(pid + "/" + task.getName(), AgvnStartupProgress.cpuTicks(stat));
                } catch (IOException | RuntimeException ignored) {
                    // the thread just ended
                }
            }
        }
        return ticks;
    }
}
