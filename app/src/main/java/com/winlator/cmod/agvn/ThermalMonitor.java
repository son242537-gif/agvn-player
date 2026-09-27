/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

/**
 * Temperature signals an ordinary app may read without extra permissions: battery temperature (sticky
 * broadcast), PowerManager thermal status (API 29+) and thermal headroom (API 30+). dumpsys thermalservice
 * needs the DUMP permission and is therefore not used.
 */
public final class ThermalMonitor {
    private ThermalMonitor() {}

    public static SessionGuard.Sample sample(Context ctx) {
        return new SessionGuard.Sample(batteryTempC(ctx), thermalStatus(ctx), headroom(ctx), RamGuard.getAvailableRamMb(ctx));
    }

    public static float batteryTempC(Context ctx) {
        try {
            Intent battery = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int tenths = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) : Integer.MIN_VALUE;
            return tenths == Integer.MIN_VALUE ? Float.NaN : tenths / 10f;
        } catch (RuntimeException e) {
            return Float.NaN;
        }
    }

    public static int thermalStatus(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null ? pm.getCurrentThermalStatus() : -1;
    }

    public static float headroom(Context ctx) {
        if (Build.VERSION.SDK_INT < 30) return Float.NaN;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm != null ? pm.getThermalHeadroom(10) : Float.NaN;
    }
}
