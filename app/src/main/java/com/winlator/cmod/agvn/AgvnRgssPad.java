/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/** The direction pad of {@link AgvnRgssKeys}: where it is and which way a touch points. Pure Java (JVM-testable). */
final class AgvnRgssPad {
    final float x, y, radius;

    AgvnRgssPad(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    /** True when a touch at (px, py) starts on the pad: inside it, with some room for a thumb. */
    boolean contains(float px, float py) {
        return Math.hypot(px - x, py - y) <= radius * 1.25f;
    }

    /**
     * 0 right, 1 down, 2 left, 3 up (screen coordinates: y grows downwards), or -1 in the small dead zone at the
     * centre. RPG Maker XP/VX/VX Ace walk in four directions, so the stronger axis wins.
     */
    int direction(float px, float py) {
        float dx = px - x, dy = py - y;
        if (Math.hypot(dx, dy) < radius * 0.22f) return -1;
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? 0 : 2;
        return dy >= 0 ? 1 : 3;
    }
}
