/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the running Windows game did, for {@link AgvnDoctor} when it ends and {@link AgvnSlowWatch} while it runs:
 * <ul>
 *   <li>whether it drew its own window: one of at least a quarter of the screen, updated {@link #STARTED_UPDATES}
 *   times. An error box ("The current resolution is too low...") is smaller, so a game that only showed one never
 *   started;</li>
 *   <li>who ended it: Wine by itself (the game closed, crashed or showed its error and quit), or the player;</li>
 *   <li>how many frames its window drew;</li>
 *   <li>when free RAM last fell under the RAM bar's level ({@link AgvnMemoryWatch}): a game that ends just after it
 *   most likely ran out of memory, since Android or the kernel ends it without a word in any log.</li>
 * </ul>
 * One game at a time, as XServerDisplayActivity runs one. Pure Java (JVM-testable).
 */
public final class AgvnSessionTrack {
    static final int STARTED_UPDATES = 20;
    static final double BIG_AREA = 0.25;
    private static final AtomicInteger frames = new AtomicInteger();
    private static final AtomicInteger bigUpdates = new AtomicInteger();
    private static volatile long startMs;
    private static volatile Map<String, String> settings = Collections.emptyMap();
    private static volatile boolean bigSeen, wineEnded, playerQuit, exiting;
    private static long lowRamAtMs, lowRamFreeMb = -1;

    private AgvnSessionTrack() {}

    /** A new game starts (AgvnSessionLog.start) with {@code ranWith}: its settings as it starts (AgvnGoodConfig). */
    static synchronized void start(long nowMs, Map<String, String> ranWith) {
        start(nowMs);
        settings = Collections.unmodifiableMap(ranWith);
    }

    static synchronized void start(long nowMs) {
        startMs = nowMs;
        settings = Collections.emptyMap();
        frames.set(0);
        bigUpdates.set(0);
        bigSeen = wineEnded = playerQuit = exiting = false;
        lowRamAtMs = 0;
        lowRamFreeMb = -1;
    }

    /** A window's content changed: X server thread, every frame, so kept cheap. */
    public static void onWindowUpdate(int width, int height, int screenWidth, int screenHeight, boolean application) {
        if (!application || !big(width, height, screenWidth, screenHeight)) return;
        bigSeen = true;
        if (bigUpdates.get() < STARTED_UPDATES) bigUpdates.incrementAndGet();
        frames.incrementAndGet();
    }

    static boolean big(int width, int height, int screenWidth, int screenHeight) {
        return screenWidth > 0 && screenHeight > 0 && (double) width * height >= BIG_AREA * screenWidth * screenHeight;
    }

    /** Wine's process ended (its termination callback). Before the player's exit, the game ended by itself. */
    public static void wineEnded() {
        if (!exiting) wineEnded = true;
    }

    /** The game is being closed: by the player when Wine had not ended first. */
    public static synchronized void exitStarted() {
        if (exiting) return;
        exiting = true;
        playerQuit = !wineEnded;
    }

    /** A memory sample while the game plays: {@code freeMb} under {@code lowFreeMb}, the RAM bar's level, is kept. */
    static synchronized void memory(long nowMs, long freeMb, long lowFreeMb) {
        if (freeMb < 0 || freeMb >= lowFreeMb) return;
        lowRamAtMs = nowMs;
        lowRamFreeMb = freeMb;
    }

    /** The free RAM (MB) of the last sample under the RAM bar's level, if within {@code withinMs}; else -1. */
    static synchronized long lowRamFreeMb(long nowMs, long withinMs) {
        return lowRamFreeMb >= 0 && nowMs - lowRamAtMs <= withinMs ? lowRamFreeMb : -1;
    }

    static boolean started() {
        return bigUpdates.get() >= STARTED_UPDATES;
    }

    static boolean bigWindowSeen() {
        return bigSeen;
    }

    static boolean endedByGame() {
        return wineEnded;
    }

    static boolean playerQuit() {
        return playerQuit;
    }

    /** The settings the game started with; a fix chosen while it plays changes the next start, not these. */
    static Map<String, String> settings() {
        return settings;
    }

    static long startMs() {
        return startMs;
    }

    static long seconds(long nowMs) {
        return startMs > 0 ? Math.max(0, nowMs - startMs) / 1000 : 0;
    }

    /** Frames the game's window drew since the last call. */
    static int takeFrames() {
        return frames.getAndSet(0);
    }
}
