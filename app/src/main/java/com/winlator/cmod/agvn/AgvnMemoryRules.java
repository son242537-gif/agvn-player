/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.ArrayDeque;

/**
 * When to tell the player a game takes too much memory, from one sample every 5 s. Every game and phone, so no single
 * game's numbers are thresholds here:
 * - free RAM (MemAvailable) under a tenth of the phone's RAM (600 MB to 1.2 GB), twice in a row. Android's low-memory
 *   killer acts long before free RAM reaches the old guard's 300 MB on phones with swap, and it kills the app on screen
 *   too; a fixed 1.2 GB would nag on a 6 GB phone that runs a big game normally.
 * - or the app's memory (RSS + DMA-BUF) up 768 MB in each of the last two minutes while nobody touched anything: a
 *   leak, like a video that fails and retries every 15 s (+288 MB a try). A level loading 1–2 GB and then staying flat
 *   is not one. The first 90 s of a session are skipped, since games load then.
 * After a warning the rules wait 5 minutes, unless free RAM drops under half the threshold.
 */
final class AgvnMemoryRules {
    enum Reason { NONE, LOW_FREE, GROWING }

    static final long GROWTH_MB = 768;
    static final long WINDOW_MS = 60_000, GRACE_MS = 90_000, COOLDOWN_MS = 300_000;

    final long lowFreeMb, criticalFreeMb;
    /** {time, used MB} of the last two minutes. */
    private final ArrayDeque<long[]> history = new ArrayDeque<>();
    private int lowCount;
    private long lastWarn = Long.MIN_VALUE / 2;

    /** {@code totalMb}: the phone's RAM (MemTotal), or 0 when unknown. */
    AgvnMemoryRules(long totalMb) {
        lowFreeMb = totalMb > 0 ? Math.max(600, Math.min(1200, totalMb / 10)) : 1200;
        criticalFreeMb = lowFreeMb / 2;
    }

    /** Times are one clock (uptime ms); {@code sessionStartMs} = when the game started. */
    Reason feed(long nowMs, long sessionStartMs, long freeMb, long usedMb, long lastInputMs) {
        history.addLast(new long[]{nowMs, usedMb});
        while (nowMs - history.peekFirst()[0] > 2 * WINDOW_MS) history.removeFirst();
        lowCount = freeMb >= 0 && freeMb < lowFreeMb ? lowCount + 1 : 0;

        boolean idle = nowMs - lastInputMs >= WINDOW_MS;
        boolean growing = nowMs - sessionStartMs >= GRACE_MS && idle && grewEachMinute(nowMs);

        Reason reason = lowCount >= 2 ? Reason.LOW_FREE : growing ? Reason.GROWING : Reason.NONE;
        if (reason == Reason.NONE) return reason;
        boolean critical = freeMb >= 0 && freeMb < criticalFreeMb;
        if (nowMs - lastWarn < COOLDOWN_MS && !critical) return Reason.NONE;
        lastWarn = nowMs;
        lowCount = 0;
        return reason;
    }

    /** Memory rose by {@link #GROWTH_MB} in the last minute and in the minute before it. */
    private boolean grewEachMinute(long nowMs) {
        if (nowMs - history.peekFirst()[0] < 2 * WINDOW_MS - 5_000) return false; // not two minutes of samples yet
        long lastMinuteLeast = Long.MAX_VALUE, minuteBeforeLeast = Long.MAX_VALUE, now = history.peekLast()[1];
        for (long[] s : history) {
            if (nowMs - s[0] <= WINDOW_MS) lastMinuteLeast = Math.min(lastMinuteLeast, s[1]);
            else minuteBeforeLeast = Math.min(minuteBeforeLeast, s[1]);
        }
        return now - lastMinuteLeast >= GROWTH_MB && lastMinuteLeast - minuteBeforeLeast >= GROWTH_MB;
    }
}
