/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.core.FileUtils;

import java.lang.reflect.Method;

/** Detects the device tier once per process and keeps the player's manual override ("agvn_device_tier"). */
public final class DeviceTierManager {
    public static final String PREF_TIER = "agvn_device_tier";
    public static final String AUTO = "AUTO";
    private static DeviceTierRules rules;
    private static DeviceTier detected;

    private DeviceTierManager() {}

    public static synchronized DeviceTierRules getRules(Context ctx) {
        if (rules == null) rules = DeviceTierRules.parse(FileUtils.readString(ctx, "agvn/device-tiers.json"));
        return rules;
    }

    public static synchronized DeviceTier detect(Context ctx) {
        // the GPU name the app remembered: the "Chạy nhẹ" games' processes then load no Vulkan driver to tell it
        if (detected == null) detected = getRules(ctx).classify(AgvnDeviceFacts.gpu(ctx), socModel(), totalRamMb(ctx));
        return detected;
    }

    /** Manual override when set, otherwise the detected tier. */
    public static DeviceTier current(Context ctx) {
        String saved = PreferenceManager.getDefaultSharedPreferences(ctx).getString(PREF_TIER, AUTO);
        return DeviceTier.fromName(saved, detect(ctx));
    }

    public static boolean isAuto(Context ctx) {
        return AUTO.equals(PreferenceManager.getDefaultSharedPreferences(ctx).getString(PREF_TIER, AUTO));
    }

    /** {@code tier == null} returns to automatic detection. */
    public static void setOverride(Context ctx, DeviceTier tier) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(PREF_TIER, tier != null ? tier.name() : AUTO).apply();
    }

    public static String presetSummary(Context ctx, DeviceTier tier) {
        DeviceTierRules.Preset p = getRules(ctx).preset(tier);
        return "Giới hạn FPS: " + p.fps + ", Độ phân giải: " + p.resolution.replace('x', '×') + ", Texture pool: " + p.texturePool + " MB";
    }

    public static String socModel() {
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null && !Build.SOC_MODEL.equals(Build.UNKNOWN)) return Build.SOC_MODEL;
        try {
            Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            String value = (String) get.invoke(null, "ro.soc.model");
            if (value != null && !value.isEmpty()) return value;
        } catch (Exception ignored) {}
        return Build.HARDWARE;
    }

    public static long totalRamMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem >> 20;
    }
}
