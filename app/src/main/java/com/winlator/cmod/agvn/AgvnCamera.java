/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * The sums that turn a camera ({@link AgvnLook}): a finger's drag, a stick's tilt and the phone's turn as mouse
 * counts, the part of a count left over kept for the next move, so a slow drag or a light tilt is not lost. Pure Java
 * (JVM-testable).
 */
final class AgvnCamera {
    /**
     * Mouse counts of a drag across the screen's long side at speed 1. It was the X screen's width (854 counts on a
     * weak phone, 1280 on a strong one), so the weaker the phone the slower the camera: about a fifth of a turn for
     * the whole screen in a game that takes 2000 counts a half turn; now the same on every phone.
     */
    static final float COUNTS_PER_SCREEN = 2000f;
    /** Mouse counts a second of the camera stick at full tilt and speed 1 (600 before, in ten steps of speed). */
    static final float STICK_COUNTS_PER_S = 1500f;
    /** A stick tilted less than this turns nothing (ControlElement.STICK_DEAD_ZONE). */
    static final float STICK_DEAD_ZONE = 0.15f;
    /** Mouse counts for the phone turned by one radian at gyroscope speed 1. */
    static final float GYRO_COUNTS_PER_RAD = 700f;
    /** Slower than this (radians a second) the phone is held, not turned: the hand's tremor and the sensor's drift. */
    static final float GYRO_STILL = 0.02f;

    private float leftX, leftY;

    /** {@code x} and {@code y} more counts: the whole ones to send now; the rest waits for the next move. */
    synchronized int[] take(float x, float y) {
        leftX += x;
        leftY += y;
        int dx = (int) leftX, dy = (int) leftY;
        leftX -= dx;
        leftY -= dy;
        return new int[] {dx, dy};
    }

    /** The counts of a drag of {@code px} screen pixels on a screen whose long side is {@code longSide} pixels. */
    static float drag(float px, int longSide, float speed) {
        return longSide <= 0 ? 0 : px * COUNTS_PER_SCREEN / longSide * speed;
    }

    /**
     * The share of the stick's top speed at tilt {@code m} (0 to 1): none in its dead zone, then slow and fine just
     * past it, for aiming, and all of it at the rim, to turn around.
     */
    static float curve(float m) {
        if (!(m > STICK_DEAD_ZONE)) return 0;
        float t = Math.min(1f, (m - STICK_DEAD_ZONE) / (1f - STICK_DEAD_ZONE));
        return 0.2f * t + 0.8f * t * t;
    }

    /**
     * The phone's turn rates about its own x and y axes (the gyroscope's values[0] and [1]) about the screen's x
     * (right) and y (up) as {@code rotation} (Surface.ROTATION_0..270) shows it: landscape is 90 or 270.
     */
    static float[] screenRates(int rotation, float x, float y) {
        switch (rotation) {
            case 1: return new float[] {-y, x};
            case 2: return new float[] {-x, -y};
            case 3: return new float[] {y, -x};
            default: return new float[] {x, y};
        }
    }

    /**
     * The counts for the phone turning {@code aboutX} and {@code aboutY} radians a second about the screen's axes
     * for {@code seconds}, as through a window: turning it right looks right, tilting its top toward the player
     * looks up.
     */
    static float[] gyro(float aboutX, float aboutY, float seconds, float speed) {
        float k = GYRO_COUNTS_PER_RAD * speed * seconds;
        float dx = Math.abs(aboutY) < GYRO_STILL ? 0 : -aboutY * k;
        float dy = Math.abs(aboutX) < GYRO_STILL ? 0 : -aboutX * k;
        return new float[] {dx, dy};
    }
}
