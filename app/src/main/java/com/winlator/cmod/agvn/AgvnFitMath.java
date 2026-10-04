/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * "Vừa màn hình" in numbers ({@link AgvnScreenFit}): whether a game's own window is a small frame inside the screen
 * or bigger than it, and how the renderer and the touches map that window over the whole view. A rect is
 * {x, y, width, height} in screen pixels. Pure Java (JVM-testable).
 */
public final class AgvnFitMath {
    static final String SMALL = "small", OVERFLOW = "overflow";
    /** A window under this share of the screen, in width and in height, is a small frame. */
    static final float SMALL_SHARE = 0.85f;
    /** A window this many pixels bigger than the screen is cut off. */
    static final int SLACK = 2;
    /**
     * A maximized window's frame hangs past every edge of the screen, the game inside it whole: Wine put Party Me's
     * 1068x652 window at -6,-6 on a 1056x640 screen. Up to this many pixels past an edge is a frame, not a cut.
     */
    static final int FRAME = 16;
    /** The largest screen a game can get (some drivers allocate the whole screen at once). */
    static final int MAX_SIZE = 4096;

    private AgvnFitMath() {}

    /** {@link #SMALL}, {@link #OVERFLOW}, or null when a w x h window at x, y suits an sw x sh screen. */
    static String verdict(int x, int y, int w, int h, int sw, int sh) {
        if (w <= 0 || h <= 0 || sw <= 0 || sh <= 0) return null;
        if (cutOff(x, y, w, h, sw, sh)) return OVERFLOW;
        if (w <= sw * SMALL_SHARE && h <= sh * SMALL_SHARE) return SMALL;
        return null;
    }

    /**
     * True when a w x h window at x, y is bigger than an sw x sh screen and part of the game is off it: more than a
     * {@link #FRAME} past an edge. A screen the window's size would not help a maximized window, which grows with it.
     */
    static boolean cutOff(int x, int y, int w, int h, int sw, int sh) {
        if (w <= sw + SLACK && h <= sh + SLACK) return false;
        return x < -FRAME || y < -FRAME || x + w > sw + FRAME || y + h > sh + FRAME;
    }

    /**
     * What the renderer gets to draw {@code rect} over a surfaceW x surfaceH view: {ox, oy, sx, sy, scissor x, y,
     * width, height}. A screen point p lands at p * s + o, where 0..rootW spans the whole view (VulkanXServerView's
     * transform). Kept in proportion, centred, unless {@code stretch} (the game's "kéo giãn toàn màn hình").
     */
    public static float[] render(float[] rect, int rootW, int rootH, int surfaceW, int surfaceH, boolean stretch) {
        float fx = rect[0], fy = rect[1], fw = rect[2], fh = rect[3];
        float ax = surfaceW / fw, ay = surfaceH / fh;
        if (!stretch) ax = ay = Math.min(ax, ay);
        float drawnW = fw * ax, drawnH = fh * ay;
        float left = (surfaceW - drawnW) / 2, top = (surfaceH - drawnH) / 2;
        return new float[]{(left - fx * ax) * rootW / surfaceW, (top - fy * ay) * rootH / surfaceH,
                ax * rootW / surfaceW, ay * rootH / surfaceH,
                Math.round(left), Math.round(top), Math.round(drawnW), Math.round(drawnH)};
    }

    /** Fills {@code xform} (XForm's {n11, n12, n21, n22, dx, dy}) to map a touch on the view to its screen point. */
    public static void touch(float[] xform, float[] rect, int viewW, int viewH, boolean stretch) {
        float fx = rect[0], fy = rect[1], fw = rect[2], fh = rect[3];
        float ax = viewW / fw, ay = viewH / fh;
        if (!stretch) ax = ay = Math.min(ax, ay);
        float left = (viewW - fw * ax) / 2, top = (viewH - fh * ay) / 2;
        xform[0] = 1 / ax;
        xform[1] = 0;
        xform[2] = 0;
        xform[3] = 1 / ay;
        xform[4] = fx - left / ax;
        xform[5] = fy - top / ay;
    }

    /** The screen size that holds a w x h window whole, "1280x720": even numbers, at most {@link #MAX_SIZE}. */
    static String screenFor(int w, int h) {
        return String.format(Locale.ROOT, "%dx%d", even(w), even(h));
    }

    private static int even(int v) {
        return Math.min(MAX_SIZE, v + (v & 1));
    }
}
