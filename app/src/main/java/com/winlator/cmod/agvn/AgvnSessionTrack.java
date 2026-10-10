/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
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
 *   <li>how many frames its window drew, when it last showed one (drawn or presented, Vulkan and OpenGL included),
 *   and how often the player pressed something since: a game that shows nothing while the player keeps pressing has
 *   frozen;</li>
 *   <li>when free RAM last fell under the RAM bar's level ({@link AgvnMemoryWatch}): a game that ends just after it
 *   most likely ran out of memory, since Android or the kernel ends it without a word in any log;</li>
 *   <li>how much memory the app and the game took at the memory watch's last two samples: a game whose memory still
 *   grows fast is still loading ({@link AgvnBlackScreen});</li>
 *   <li>a crash the game's engine caught itself, as Unity does ({@link AgvnUnityCrashWatch}): Wine goes on, the game
 *   does not.</li>
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
    private static volatile String engineCrash;
    private static volatile boolean bigSeen, wineEnded, playerQuit, exiting;
    private static volatile long lastFrameMs, endMs;
    private static final AtomicInteger pressesSinceFrame = new AtomicInteger(), framesSeen = new AtomicInteger();
    private static long lowRamAtMs, lowRamFreeMb = -1;
    private static long usedAtMs, usedMb = -1, usedBeforeMb = -1;

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
        lastFrameMs = endMs = 0;
        pressesSinceFrame.set(0);
        framesSeen.set(0);
        lowRamAtMs = 0;
        lowRamFreeMb = -1;
        usedAtMs = 0;
        usedMb = usedBeforeMb = -1;
        engineCrash = null;
        AgvnGamePause.reset(System.nanoTime());
    }

    /** A window's content changed: X server thread, every frame, so kept cheap. */
    public static void onWindowUpdate(int width, int height, int screenWidth, int screenHeight, boolean application) {
        if (!application || !big(width, height, screenWidth, screenHeight)) return;
        bigSeen = true;
        if (bigUpdates.get() < STARTED_UPDATES) bigUpdates.incrementAndGet();
        frames.incrementAndGet();
        onFrame();
    }

    /** The game showed a frame: drawn into its window, or presented (AgvnInputHold.onGameFrame). Kept cheap. */
    static void onFrame() {
        onFrame(System.currentTimeMillis());
    }

    static void onFrame(long nowMs) {
        lastFrameMs = nowMs;
        if (pressesSinceFrame.get() != 0) pressesSinceFrame.set(0);
        if (framesSeen.get() < STARTED_UPDATES) framesSeen.incrementAndGet();
    }

    /** The player pressed a key, a mouse button or the screen (AgvnInputHold.pressing). */
    static void onPress() {
        pressesSinceFrame.incrementAndGet();
    }

    static boolean big(int width, int height, int screenWidth, int screenHeight) {
        return screenWidth > 0 && screenHeight > 0 && (double) width * height >= BIG_AREA * screenWidth * screenHeight;
    }

    /** Wine's process ended (its termination callback). Before the player's exit, the game ended by itself. */
    public static void wineEnded() {
        if (!exiting) wineEnded = true;
        noteEnd(System.currentTimeMillis());
    }

    /** The game is being closed: by the player when Wine had not ended first. */
    public static synchronized void exitStarted() {
        if (exiting) return;
        exiting = true;
        playerQuit = !wineEnded;
        noteEnd(System.currentTimeMillis());
    }

    /** The game's end: Wine's, or the player's exit, whichever came first. */
    static void noteEnd(long nowMs) {
        if (endMs == 0) endMs = nowMs;
    }

    /**
     * Seconds from the game's last frame to its end; -1 before the end, or when it showed fewer than
     * {@link #STARTED_UPDATES} frames this way (a game drawn through EGL or DisplayX is not judged).
     */
    static long frozenSecondsAtEnd() {
        long last = lastFrameMs, end = endMs;
        return framesSeen.get() >= STARTED_UPDATES && end >= last ? (end - last) / 1000 : -1;
    }

    /** Presses since the game's last frame. */
    static int pressesWithoutFrame() {
        return pressesSinceFrame.get();
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

    /** A memory watch sample: {@code mb} is what the app and the game take (RSS and DMA-BUF). */
    static synchronized void used(long nowMs, long mb) {
        usedBeforeMb = usedMb;
        usedMb = mb;
        usedAtMs = nowMs;
    }

    /** MB the app and the game grew by between the last two samples, if the last is within {@code withinMs}; else 0. */
    static synchronized long grewMb(long nowMs, long withinMs) {
        return usedBeforeMb >= 0 && nowMs - usedAtMs <= withinMs ? usedMb - usedBeforeMb : 0;
    }

    /** The game's engine caught a crash and said so in its log ({@link AgvnUnityCrash#describe}'s words). */
    static void engineCrashed(String crash) {
        engineCrash = crash;
    }

    /** The crash the game's engine caught this session, or null. */
    static String engineCrash() {
        return engineCrash;
    }

    static boolean started() {
        return bigUpdates.get() >= STARTED_UPDATES;
    }

    /** The game showed {@link #STARTED_UPDATES} frames, drawn into its window or presented (Vulkan, OpenGL). */
    static boolean showedFrames() {
        return started() || framesSeen.get() >= STARTED_UPDATES;
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
