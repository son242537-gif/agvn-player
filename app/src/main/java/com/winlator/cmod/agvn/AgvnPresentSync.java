/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.util.Locale;

/**
 * Frames go to the screen one at a time for a DirectX game on a phone that cannot cope ({@link AgvnMemorySaver#tight})
 * when its Vulkan driver is not Turnip: the game's "Đồng bộ khung hình" (MESA_VK_WSI_DEBUG=forcesync, the wrapper's X11
 * present waits for each frame) and "Tắt Present Wait" (the wrapper hides VK_KHR_present_wait, which DXVK 2.x waits on;
 * 1.10.3 never uses it). A Mali-G615 player's Support Pregnancy School (Unreal) stayed black on the defaults and
 * started with both on. Only this start's variables change, before the game's own (Cài đặt → Biến môi trường) are added
 * over them; the game's settings stay as they are.
 */
public final class AgvnPresentSync {
    private static final String TAG = "AGVN";
    static final String SYNC_VAR = "MESA_VK_WSI_DEBUG", SYNC = "forcesync", WAIT_VAR = "WRAPPER_DISABLE_PRESENT_WAIT";
    private static volatile boolean applied;

    private AgvnPresentSync() {}

    /**
     * At a DirectX game's start, after the wrapper's variables. {@code bcnEmulation}: the setting as this start uses it
     * ({@link AgvnBcn#effective}); {@code driverId}: the Vulkan driver it starts with.
     */
    public static void apply(Shortcut shortcut, EnvVars env, String bcnEmulation, String driverId) {
        applied = false;
        if (shortcut == null) return; // Wine's desktop, no game: as AgvnMemorySaver.applyDxvk
        try {
            boolean tight = AgvnMemorySaver.tight(AgvnMemorySaver.ranOut(shortcut), !"none".equals(bcnEmulation),
                    AgvnMemoryProbe.totalMb());
            if (!wanted(tight, driverId)) return;
            applyTo(env);
            applied = true;
            Log.i(TAG, "frames shown one at a time, present wait off: the phone cannot cope");
        } catch (RuntimeException e) {
            Log.w(TAG, "frame sync not set", e); // the game starts with its own settings
        }
    }

    /** True when this start got {@link #apply}'s variables (for the session log's note). */
    static boolean appliedThisStart() {
        return applied;
    }

    static boolean wanted(boolean tight, String driverId) {
        return tight && (driverId == null || !driverId.toLowerCase(Locale.ROOT).contains("turnip"));
    }

    static void applyTo(EnvVars env) {
        env.put(SYNC_VAR, withSync(env.get(SYNC_VAR)));
        env.put(WAIT_VAR, "1");
    }

    /** {@code current} (MESA_VK_WSI_DEBUG, "sw" when DRI3 is off) with forcesync; Mesa reads a comma-separated list. */
    static String withSync(String current) {
        if (current == null || current.trim().isEmpty()) return SYNC;
        for (String flag : current.split(",")) if (flag.trim().equals(SYNC)) return current;
        return current + "," + SYNC;
    }
}
