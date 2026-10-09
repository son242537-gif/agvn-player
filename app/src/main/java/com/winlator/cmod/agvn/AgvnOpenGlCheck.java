/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.util.Map;
import java.util.Properties;

/**
 * Whether OpenGL starts on this phone with a Vulkan driver. AGVN draws OpenGL with Mesa's Zink on the game's Vulkan
 * driver, and WineD3D draws DirectX with OpenGL, so when Zink does not start ("failed to load driver: zink", the
 * problem {@value #PROBLEM}) WineD3D cannot help a game DXVK fails on. Thorn Sin (06/10, PowerVR BXM-8-256, Vulkan 1.1)
 * ran on WineD3D five times and failed each time, with nothing left to try: Zink did not start on that driver, and
 * DXVK's DirectX 11 needs features it lacks. Once Zink failed, no game is offered WineD3D with that driver
 * ({@link AgvnFixes}), and a game on WineD3D is offered DXVK back. Kept per driver and app version, as
 * {@link DriverSafety} keeps its probes: another driver or a newer Mesa may start. A game whose DirectX 11 level
 * WineD3D did not reach with a driver (Zink started, without features the game needs) is not offered WineD3D again
 * with it.
 */
final class AgvnOpenGlCheck {
    static final String PROBLEM = "opengl-unavailable", PREFS = "agvn_opengl", ZINK_FAILED = "failed to load driver: zink";
    /** A game's DirectX 11 level was not reached ("d3d11-level"); the doctor-state key of a WineD3D that fell short. */
    static final String LEVEL_PROBLEM = "d3d11-level", SHORT = "wined3dShort";
    private static final String TAG = "AGVN";

    private AgvnOpenGlCheck() {}

    /** The Vulkan driver {@code s} starts with, as DriverSafety resolves it ("System", "turnip26.2.0"). */
    static String driver(Context ctx, Shortcut s) {
        String chosen = AgvnFixEdits.configValue(AgvnFixes.driverConfig(s), "version", ';');
        return DriverSafety.resolveUsable(ctx, chosen.isEmpty() ? AgvnFixes.SYSTEM : chosen);
    }

    static String key(String driver, long appVersionCode) {
        return driver + "@" + appVersionCode;
    }

    /** True when Wine's lines say Zink did not start ("failed to load driver: zink"), however the game ended. */
    static boolean zinkFailed(java.util.List<String> lines) {
        for (String line : lines) if (line != null && line.contains(ZINK_FAILED)) return true;
        return false;
    }

    /** {@code s} ended with Zink not starting: OpenGL does not work with its driver. */
    static void failed(Context ctx, Shortcut s) {
        String driver = driver(ctx, s);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(key(driver, DriverSafety.appVersionCode(ctx)), true).apply();
        Log.i(TAG, "OpenGL (Zink) does not start with the " + driver + " driver: WineD3D is not offered with it");
    }

    /** True when Zink did not start with {@code s}'s driver, in this app version. */
    static boolean failedBefore(Context ctx, Shortcut s) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key(driver(ctx, s), DriverSafety.appVersionCode(ctx)), false);
    }

    /** True when {@code s} draws DirectX with WineD3D (the fix "wined3d" sets it). */
    static boolean wined3d(Shortcut s) {
        return "wined3d".equals(s.getExtra("dxwrapper", s.container.getDXWrapper()));
    }

    /** The DXVK {@code s} goes back to: its container's, else the default one. */
    static String dxvkBack(Shortcut s) {
        String own = s.container.getDXWrapper();
        return own != null && own.contains("dxvk") ? own : Container.DEFAULT_DXWRAPPER;
    }

    /**
     * True when a game that ended with {@code problem} while drawing with {@code dxwrapper} needs more DirectX 11 than
     * WineD3D gives: Zink started, but without features the game's level needs (MK Days, Mali-G610, 10/10/2026).
     */
    static boolean fellShort(String problem, String dxwrapper) {
        return LEVEL_PROBLEM.equals(problem) && "wined3d".equals(dxwrapper);
    }

    /**
     * {@code s} ended with {@code problem}, drawing as {@code ran} says (its container's DirectX when not its own):
     * when WineD3D fell short of it ({@link #fellShort}), WineD3D is not offered to it again with its driver, in this
     * app version. Kept in its doctor {@code state}; true when kept.
     */
    static boolean keepShort(Context ctx, Shortcut s, Properties state, String problem, Map<String, String> ran) {
        String dx = ran.containsKey("dxwrapper") ? ran.get("dxwrapper") : s.container.getDXWrapper();
        if (!fellShort(problem, dx)) return false;
        state.setProperty(SHORT, key(driver(ctx, s), DriverSafety.appVersionCode(ctx)));
        Log.i(TAG, s.name + " needs more DirectX 11 than WineD3D gives: no WineD3D for it with this driver");
        return true;
    }

    /** True when WineD3D fell short of {@code s} with its driver, in this app version ({@link #keepShort}). */
    static boolean shortBefore(Context ctx, Shortcut s, Properties state) {
        return state != null && key(driver(ctx, s), DriverSafety.appVersionCode(ctx)).equals(state.getProperty(SHORT));
    }
}
