/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps a mouse button or key down long enough for the game to see it. Games such as Unity read the mouse and keys once
 * a frame: a press and a release in the same frame can be lost. On 01/10 a 0 ms tap (adb) never advanced a Unity
 * visual novel while a 150 ms hold always did, and real taps last 7-88 ms. With the simulated touchscreen the button
 * is held as long as the finger was, and a touchpad tap held it 30 ms.
 *
 * <p>A release waits until the input has been down {@link #MIN_HOLD_MS} and the game has shown {@link #MIN_FRAMES}
 * frames since the press, never longer than {@link #MAX_HOLD_MS} after it (a longer hold could read as a long press).
 * When the game shows no frames (loading, or it does not draw through the Present extension) only the time counts.
 * A new press of the same input first sends the release still waiting. Every source goes through the X server, so
 * taps on the game, on-screen buttons and keyboards are all covered.
 */
public final class AgvnInputHold {
    static final long MIN_HOLD_MS = 60, MAX_HOLD_MS = 250, FRAMES_RECENT_MS = 500;
    static final int MIN_FRAMES = 2;

    private static final AtomicLong FRAMES = new AtomicLong();
    private static volatile long lastFrameMs = Long.MIN_VALUE / 2;
    private static ScheduledThreadPoolExecutor timer; // guarded by AgvnInputHold.class

    private final Map<Integer, long[]> pressed = new HashMap<>(); // input -> {press ms, frames at press}
    private final Map<Integer, Runnable> waiting = new HashMap<>(); // input -> its release, not sent yet

    /** A game frame was shown (X server thread). */
    public static void onGameFrame() {
        FRAMES.incrementAndGet();
        lastFrameMs = SystemClock.uptimeMillis();
        AgvnSessionTrack.onFrame(); // a Vulkan or OpenGL frame never reaches the window's own content
    }

    /**
     * How long a release must still wait, in ms: 0 to send it now, or how soon to look again. Pure: the inputs are the
     * press time, the frame count at the press, and now.
     */
    static long waitMs(long pressMs, long framesAtPress, long nowMs, long framesNow, long lastFrameMs) {
        long held = nowMs - pressMs;
        if (held >= MAX_HOLD_MS) return 0;
        boolean drawing = nowMs - lastFrameMs < FRAMES_RECENT_MS;
        boolean framesDone = !drawing || framesNow - framesAtPress >= MIN_FRAMES;
        if (held >= MIN_HOLD_MS && framesDone) return 0;
        return held < MIN_HOLD_MS ? MIN_HOLD_MS - held : 8;
    }

    /** Before a press of {@code input}: sends its waiting release first, then notes the press. */
    public void pressing(int input) {
        AgvnSessionTrack.onPress();
        Runnable release;
        synchronized (this) {
            release = waiting.remove(input);
            pressed.put(input, new long[]{SystemClock.uptimeMillis(), FRAMES.get()});
        }
        if (release != null) release.run();
    }

    /** True while a release of {@code input} is put off: the input still reads as down. */
    public synchronized boolean isWaiting(int input) {
        return waiting.containsKey(input);
    }

    /**
     * A release of {@code input}: true when it was put off and {@code releaseNow} will run later, false when the
     * caller sends it now.
     */
    public boolean deferRelease(int input, Runnable releaseNow) {
        synchronized (this) {
            long[] press = pressed.get(input);
            if (press == null) return false;
            long wait = waitMs(press[0], press[1], SystemClock.uptimeMillis(), FRAMES.get(), lastFrameMs);
            if (wait == 0) {
                pressed.remove(input);
                return false;
            }
            waiting.put(input, releaseNow);
            schedule(input, releaseNow, wait);
            return true;
        }
    }

    private void schedule(int input, Runnable release, long delayMs) {
        timer().schedule(() -> check(input, release), delayMs, TimeUnit.MILLISECONDS);
    }

    private void check(int input, Runnable release) {
        synchronized (this) {
            if (waiting.get(input) != release) return; // sent already, by a new press
            long[] press = pressed.get(input);
            long wait = press == null ? 0
                    : waitMs(press[0], press[1], SystemClock.uptimeMillis(), FRAMES.get(), lastFrameMs);
            if (wait > 0) {
                schedule(input, release, wait);
                return;
            }
            waiting.remove(input);
            pressed.remove(input);
        }
        release.run();
    }

    private static synchronized ScheduledThreadPoolExecutor timer() {
        if (timer == null) {
            timer = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "AgvnInputHold");
                t.setDaemon(true);
                return t;
            });
        }
        return timer;
    }
}
