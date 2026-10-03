/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * "Chạy nhẹ" Ren'Py and HTML games draw once per refresh of the screen, so a 90, 120 or 144 Hz screen makes them draw
 * two or more frames for each one the game moves (60 a second for RPG Maker MV/MZ and visual novels): heat and battery
 * for nothing, and the first RPG Maker MV versions even run twice as fast. These games ask Android for the screen's
 * 60 Hz mode at the same resolution. RPG Maker XP runs at 40 FPS, which 120 Hz shows evenly and 60 Hz does not, so
 * mkxp-z leaves the screen as it is. "Tần số quét cao" on in the settings (high_refresh_rate_mode) leaves it too.
 */
final class AgvnRefreshCap {
    private static final String TAG = "AGVN";
    static final float MAX_HZ = 60;

    static final class Mode {
        final int id, width, height;
        final float hz;

        Mode(int id, int width, int height, float hz) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.hz = hz;
        }
    }

    private AgvnRefreshCap() {}

    /**
     * The mode to ask for: the fastest one at most {@code maxHz} with the size of {@code current}, when the screen has
     * a faster one at that size; -1 when it has none (a 60 Hz screen) or nothing at most {@code maxHz}.
     */
    static int pick(List<Mode> modes, Mode current, float maxHz) {
        Mode best = null;
        boolean faster = false;
        for (Mode m : modes) {
            if (m.width != current.width || m.height != current.height) continue;
            if (m.hz > maxHz + 1) faster = true;
            else if (best == null || m.hz > best.hz) best = m;
        }
        return faster && best != null ? best.id : -1;
    }

    static void apply(Activity activity) {
        try {
            if (PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("high_refresh_rate_mode", false)) return;
            Display display = activity.getWindowManager().getDefaultDisplay();
            List<Mode> modes = new ArrayList<>();
            for (Display.Mode m : display.getSupportedModes()) modes.add(of(m));
            int id = pick(modes, of(display.getMode()), MAX_HZ);
            if (id < 0) return;
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.preferredDisplayModeId = id;
            activity.getWindow().setAttributes(lp);
            Log.i(TAG, "screen asked for its 60 Hz mode " + id + " while this game runs");
        } catch (RuntimeException e) {
            Log.w(TAG, "screen mode left as it is", e);
        }
    }

    private static Mode of(Display.Mode m) {
        return new Mode(m.getModeId(), m.getPhysicalWidth(), m.getPhysicalHeight(), m.getRefreshRate());
    }
}
