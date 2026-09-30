/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayDeque;

/**
 * When to tell the player a game takes too much memory, from one sample every 5 s:
 * - free RAM (MemAvailable) under 1.2 GB twice in a row;
 * - or the app's memory (RSS + DMA-BUF) up 1 GB within a minute while nobody touched anything: a leak, like a video
 *   that fails and retries every 15 s. The first 90 s of a session are skipped, since games load then.
 * Android's low-memory killer acts long before free RAM reaches the old guard's 300 MB on phones with swap, and it
 * kills the app on screen too. After a warning the rules wait 3 minutes, unless free RAM drops under 600 MB.
 */
final class AgvnMemoryRules {
    enum Reason { NONE, LOW_FREE, GROWING }

    static final long LOW_FREE_MB = 1200, CRITICAL_FREE_MB = 600, GROWTH_MB = 1024;
    static final long WINDOW_MS = 60_000, GRACE_MS = 90_000, COOLDOWN_MS = 180_000;

    /** {time, used MB} of the last minute. */
    private final ArrayDeque<long[]> history = new ArrayDeque<>();
    private int lowCount;
    private long lastWarn = Long.MIN_VALUE / 2;

    /** Times are one clock (uptime ms); {@code sessionStartMs} = when the game started. */
    Reason feed(long nowMs, long sessionStartMs, long freeMb, long usedMb, long lastInputMs) {
        history.addLast(new long[]{nowMs, usedMb});
        while (nowMs - history.peekFirst()[0] > WINDOW_MS) history.removeFirst();
        lowCount = freeMb >= 0 && freeMb < LOW_FREE_MB ? lowCount + 1 : 0;

        long least = Long.MAX_VALUE;
        for (long[] s : history) least = Math.min(least, s[1]);
        boolean idle = nowMs - lastInputMs >= WINDOW_MS;
        boolean growing = nowMs - sessionStartMs >= GRACE_MS && idle && usedMb - least >= GROWTH_MB;

        Reason reason = lowCount >= 2 ? Reason.LOW_FREE : growing ? Reason.GROWING : Reason.NONE;
        if (reason == Reason.NONE) return reason;
        boolean critical = freeMb >= 0 && freeMb < CRITICAL_FREE_MB;
        if (nowMs - lastWarn < COOLDOWN_MS && !critical) return Reason.NONE;
        lastWarn = nowMs;
        lowCount = 0;
        return reason;
    }
}
