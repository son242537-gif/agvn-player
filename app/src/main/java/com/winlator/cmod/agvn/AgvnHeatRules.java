/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * When to tell the player the phone is too hot for full speed, from one sample every 5 s:
 * - the system capped the fastest CPU cores under 85% of their top speed, twice in a row. HyperOS does this at
 *   73–82 °C while Android's thermal status still reads "none" (POCO F8 Pro, 01/10/2026: 3.07 of 4.32 GHz, charging);
 * - or Android's thermal headroom reached 0.85 (1.0 = throttling), twice in a row.
 * A reading the phone does not give (NaN) keeps the count as it was. After a warning the rules wait 5 minutes.
 */
final class AgvnHeatRules {
    enum Reason { NONE, CPU_CAPPED, NEAR_THROTTLING }

    static final float CAP_WARN = 0.85f, HEADROOM_WARN = 0.85f;
    static final long COOLDOWN_MS = 5 * 60_000L;

    private int cappedCount, hotCount;
    private long lastWarn = Long.MIN_VALUE / 2;

    /** {@code cpuCap}: see {@link #fastestCap}; {@code headroom}: PowerManager.getThermalHeadroom. NaN = unknown. */
    Reason feed(long nowMs, float cpuCap, float headroom) {
        if (!Float.isNaN(cpuCap)) cappedCount = cpuCap < CAP_WARN ? cappedCount + 1 : 0;
        if (!Float.isNaN(headroom)) hotCount = headroom >= HEADROOM_WARN ? hotCount + 1 : 0;
        Reason reason = cappedCount >= 2 ? Reason.CPU_CAPPED : hotCount >= 2 ? Reason.NEAR_THROTTLING : Reason.NONE;
        if (reason == Reason.NONE || nowMs - lastWarn < COOLDOWN_MS) return Reason.NONE;
        lastWarn = nowMs;
        cappedCount = hotCount = 0;
        return reason;
    }

    /**
     * The fastest CPU cluster's allowed top speed over its hardware top speed (1 = not capped), from
     * {@code cpufreq/policy*}/scaling_max_freq and cpuinfo_max_freq; NaN when the phone does not let apps read them.
     */
    static float fastestCap(File cpufreqDir) {
        File[] policies = cpufreqDir.listFiles((dir, name) -> name.startsWith("policy"));
        if (policies == null) return Float.NaN;
        long bestHardware = 0, bestAllowed = 0;
        for (File policy : policies) {
            long hardware = khz(new File(policy, "cpuinfo_max_freq")), allowed = khz(new File(policy, "scaling_max_freq"));
            if (hardware > bestHardware && allowed > 0) {
                bestHardware = hardware;
                bestAllowed = allowed;
            }
        }
        return bestHardware > 0 ? Math.min(1f, bestAllowed / (float) bestHardware) : Float.NaN;
    }

    private static long khz(File f) {
        try {
            return Long.parseLong(new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII).trim());
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }
}
