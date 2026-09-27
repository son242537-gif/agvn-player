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
    private static final String PREFS = "agvn_driver_probe";
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
        String myMarker = "probing:" + android.os.Process.myPid();
        if ("ok".equals(state)) return true;
        if ("bad".equals(state)) return false;
        if (state != null && state.startsWith("probing:") && !state.equals(myMarker)) {
            Log.w(TAG, "driver probe for " + driverName + " crashed last time, marking unusable");
            prefs.edit().putString(key, "bad").commit();
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
        prefs.edit().putString(key, ok ? "ok" : "bad").commit();
        return ok;
    }

    /** Returns {@code id} when usable, else Turnip (bundled wrapper), else System. */
    public static String resolveUsable(Context ctx, String id) {
        if (isUsable(ctx, id)) return SYSTEM.equalsIgnoreCase(id) ? SYSTEM : id;
        for (String candidate : Arrays.asList(DefaultVersion.WRAPPER_ADRENO, SYSTEM)) {
            if (!candidate.equalsIgnoreCase(id) && isUsable(ctx, candidate)) return candidate;
        }
        return SYSTEM;
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
