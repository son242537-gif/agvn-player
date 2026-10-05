/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.List;

/**
 * Where the keys of {@link AgvnLightLayout} sit on a w x h screen and which one a touch hits, sized as the Windows
 * on-screen controls are (ControlElement.getBoundingBox): the unit is the screen width / 100, a round button is 6
 * units across, a pill 8 x 4 and a pad 16, each times its scale. Pure Java (JVM-testable).
 */
final class AgvnLightGeometry {
    /** A touch this much outside a key still hits it (fingers are wider than the drawing). */
    static final float SLACK = 1.15f;
    /** The pad's centre where no direction is pressed, as a share of its radius. */
    static final float DEAD_ZONE = 0.22f;

    private AgvnLightGeometry() {}

    static float unit(int width) {
        return Math.max(1f, width / 100f);
    }

    static float halfWidth(AgvnLightLayout.Element e, float unit) {
        return (e.pad ? 8 : e.round ? 3 : 4) * unit * e.scale;
    }

    static float halfHeight(AgvnLightLayout.Element e, float unit) {
        return (e.pad ? 8 : e.round ? 3 : 2) * unit * e.scale;
    }

    /** The key under (x, y), the last drawn (topmost) first, or -1. */
    static int hit(List<AgvnLightLayout.Element> elements, float x, float y, int w, int h) {
        float unit = unit(w);
        for (int i = elements.size() - 1; i >= 0; i--) {
            AgvnLightLayout.Element e = elements.get(i);
            float dx = x - e.x * w, dy = y - e.y * h, hw = halfWidth(e, unit), hh = halfHeight(e, unit);
            boolean in = e.pad || e.round ? Math.hypot(dx, dy) <= hw * SLACK
                    : Math.abs(dx) <= hw * SLACK && Math.abs(dy) <= hh * SLACK;
            if (in) return i;
        }
        return -1;
    }

    /**
     * The pad binding a touch at (x, y) presses: 0 up, 1 right, 2 down, 3 left (the .icp's order), or -1 in the dead
     * zone at the centre. RPG Maker walks in four directions, so the stronger axis wins; a finger that slides off the
     * pad keeps steering.
     */
    static int padPart(AgvnLightLayout.Element pad, float x, float y, int w, int h) {
        float dx = x - pad.x * w, dy = y - pad.y * h;
        if (Math.hypot(dx, dy) < halfWidth(pad, unit(w)) * DEAD_ZONE) return -1;
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? 1 : 3;
        return dy >= 0 ? 2 : 0;
    }

    /** A key's new centre, as fractions of the screen, after a drag to (x, y) that grabbed it (dx, dy) off-centre. */
    static float[] moved(float x, float y, float grabDx, float grabDy, int w, int h) {
        return new float[]{AgvnLightLayout.clamp((x - grabDx) / Math.max(1, w), 0, 1),
                AgvnLightLayout.clamp((y - grabDy) / Math.max(1, h), 0, 1)};
    }
}
