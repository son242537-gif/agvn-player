/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;

import java.util.Arrays;

/**
 * Keeps unusable graphics drivers away from the launch path and the driver pickers.
 * A driver is usable when the native Vulkan probe reports a renderer for it and the denylist does not hide
 * it for this GPU. Probe results are cached per app version; a "probing" marker left behind by a process that
 * died inside the vendor blob (native crash) counts as "bad" on the next start.
 */
public final class DriverSafety {
    private static final String TAG = "AGVN";
    static final String PREFS = "agvn_driver_probe";
    private static final String SYSTEM = "System";
    private static DriverDenylist denylist;
    private static String gpuRenderer;

    private DriverSafety() {}

    /** Patch component of a Vulkan version string ("1.3.289" -> 289); 0 when it cannot be parsed (e.g. "Unknown"). */
    public static int parseVulkanPatch(String fullVersion) {
        if (fullVersion == null) return 0;
        String[] parts = fullVersion.trim().split("\\.");
        if (parts.length < 3) return 0;
        String digits = parts[2].replaceAll("[^0-9].*$", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static synchronized String getGpuRenderer(Context ctx) {
        if (gpuRenderer == null) {
            try {
                gpuRenderer = GPUInformation.getRenderer(null, ctx);
                AgvnDeviceFacts.rememberGpu(ctx, gpuRenderer); // for the "Chạy nhẹ" games' processes
            } catch (Throwable e) {
                gpuRenderer = "";
            }
        }
        return gpuRenderer;
    }

    private static synchronized DriverDenylist getDenylist(Context ctx) {
        if (denylist == null) {
            String json = FileUtils.readString(ctx, "agvn/driver-denylist.json");
            denylist = json != null ? DriverDenylist.parse(json) : DriverDenylist.empty();
        }
        return denylist;
    }

    public static boolean isDenylisted(Context ctx, String id) {
        return getDenylist(ctx).isDenylisted(getGpuRenderer(ctx), id);
    }

    public static boolean isBundled(Context ctx, String id) {
        String[] bundled = ctx.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries);
        for (String b : bundled) if (b.equalsIgnoreCase(id)) return true;
        return false;
    }

    /** Driver id may be offered in a picker: user-installed drivers always, bundled ones only when usable. */
    public static boolean isPickable(Context ctx, String id) {
        if (isDenylisted(ctx, id)) return false;
        return !isBundled(ctx, id) || isUsable(ctx, id);
    }

    /** Probe result with poison-pill caching; never throws. */
    public static boolean isUsable(Context ctx, String id) {
        if (id == null || id.isEmpty() || isDenylisted(ctx, id)) return false;
        String driverName = SYSTEM.equalsIgnoreCase(id) ? SYSTEM : id;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = driverName + "@" + appVersionCode(ctx);
        String state = prefs.getString(key, null);
        if ("ok".equals(state)) return true;
        if ("bad".equals(state)) return false;
        int failures = previousFailures(state);
        String myMarker = "probing:" + android.os.Process.myPid() + ":" + failures;
        if (state != null && state.startsWith("probing:") && !state.equals(myMarker)) {
            // the process died inside the probe last time (native crash, or killed while probing)
            Log.w(TAG, "driver probe for " + driverName + " did not finish last time");
            prefs.edit().putString(key, failedState(failures)).commit();
            return false;
        }
        prefs.edit().putString(key, myMarker).commit();
        boolean ok;
        try {
            ok = GPUInformation.isDriverSupported(driverName, ctx);
        } catch (Throwable e) {
            Log.w(TAG, "driver probe for " + driverName + " failed", e);
            ok = false;
        }
        prefs.edit().putString(key, ok ? "ok" : failedState(failures)).commit();
        return ok;
    }

    /** Failures recorded so far: "bad1" = 1, "probing:<pid>:<n>" = n, anything else = 0. */
    static int previousFailures(String state) {
        if ("bad1".equals(state)) return 1;
        if (state != null && state.startsWith("probing:")) {
            int colon = state.lastIndexOf(':');
            try {
                return colon > 8 ? Integer.parseInt(state.substring(colon + 1)) : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** A driver is written off only after its second failed probe; one failure is retried next time. */
    static String failedState(int previousFailures) {
        return previousFailures + 1 >= 2 ? "bad" : "bad1";
    }

    /**
     * Returns {@code id} when usable, else Turnip (bundled wrapper), else System. When nothing probes as usable,
     * System is the last resort unless it is denylisted for this GPU (no 3D on Adreno 8xx); then Turnip.
     */
    public static String resolveUsable(Context ctx, String id) {
        if (isUsable(ctx, id)) return SYSTEM.equalsIgnoreCase(id) ? SYSTEM : id;
        for (String candidate : Arrays.asList(DefaultVersion.WRAPPER_ADRENO, SYSTEM)) {
            if (!candidate.equalsIgnoreCase(id) && isUsable(ctx, candidate)) return candidate;
        }
        return isDenylisted(ctx, SYSTEM) ? DefaultVersion.WRAPPER_ADRENO : SYSTEM;
    }

    public static void forgetProbes(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    static long appVersionCode(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }
}
