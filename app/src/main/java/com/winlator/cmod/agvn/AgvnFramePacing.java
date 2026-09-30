/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;

import java.util.Locale;

/**
 * FPS limits the screen can show evenly. A frame can only stay on screen a whole number of vsyncs, so on a 60 Hz screen
 * the even rates are 60, 30, 20 and 15 FPS: 24 FPS would mix 33 ms and 50 ms frames, which looks like stutter.
 */
public final class AgvnFramePacing {
    private AgvnFramePacing() {}

    /**
     * How many vsyncs each frame stays on screen for a limit: the nearest whole number, but never more than 10% above
     * the limit. On 60 Hz: 30 FPS = 2 vsyncs, 24 and 27 FPS = 3 (20 FPS), 45 FPS = 2 (30 FPS). On 120 Hz: 24 FPS = 5.
     */
    public static int vsyncsPerFrame(double refreshHz, int fps) {
        if (fps <= 0 || refreshHz <= 0) return 1;
        int n = Math.max(1, (int) Math.floor(refreshHz / fps + 0.5));
        if (refreshHz / n > fps * 1.10) n++;
        return n;
    }

    /** The FPS a limit really gives on this screen. */
    public static double effectiveFps(double refreshHz, int fps) {
        return fps <= 0 ? 0 : refreshHz / vsyncsPerFrame(refreshHz, fps);
    }

    /** "30 FPS", or "24 → 20 FPS" when the screen cannot show the limit evenly. */
    public static String label(int fps, double refreshHz) {
        double real = effectiveFps(refreshHz, fps);
        if (refreshHz <= 0 || Math.abs(real - fps) < 0.5) return fps + " FPS";
        String shown = Math.abs(real - Math.round(real)) < 0.05
                ? String.valueOf(Math.round(real)) : String.format(new Locale("vi", "VN"), "%.1f", real);
        return fps + " → " + shown + " FPS";
    }

    /** The screen's current refresh rate, or 60 when the view is not on a screen. */
    public static float refreshHz(View view) {
        Display display = view != null ? view.getDisplay() : null;
        float hz = display != null ? display.getRefreshRate() : 0;
        return hz >= 20 ? hz : 60;
    }

    /**
     * Tells Android the game's frame rate (0 = no preference), so it picks a refresh rate that is a multiple of it,
     * e.g. 120 Hz rather than 90 Hz for 24 or 30 FPS. Android 11+; the player's peak refresh setting still wins.
     */
    public static void hintFrameRate(SurfaceView view, int fps) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
            Surface surface = view.getHolder().getSurface();
            if (surface != null && surface.isValid())
                surface.setFrameRate(Math.max(0, fps), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        } catch (RuntimeException ignored) {
            // the surface went away meanwhile; the next hint sets it again
        }
    }
}
