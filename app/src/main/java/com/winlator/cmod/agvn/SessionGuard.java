/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * Pure decision logic for the in-game guard. A warning fires only after several consecutive bad samples
 * (thermal: 3 x 2 s, RAM: 2 x 2 s) and then stays quiet for a cooldown so it never nags.
 * Thermal uses the OS throttling signals and battery temperature, not raw CPU/GPU zones: the device findings
 * show CPU 50-53 °C / GPU 45-47 °C as the normal steady state on a healthy flagship.
 */
public final class SessionGuard {
    public enum Decision { OK, WARN_THERMAL, WARN_LOW_RAM }

    /** One poll; use NaN / -1 for readings that are not available on this device. */
    public static final class Sample {
        public final float batteryTempC;
        /** PowerManager.getCurrentThermalStatus(), -1 if unknown. */
        public final int thermalStatus;
        /** PowerManager.getThermalHeadroom(), NaN if unknown; 1.0 = throttling. */
        public final float headroom;
        public final long availRamMb;

        public Sample(float batteryTempC, int thermalStatus, float headroom, long availRamMb) {
            this.batteryTempC = batteryTempC;
            this.thermalStatus = thermalStatus;
            this.headroom = headroom;
            this.availRamMb = availRamMb;
        }
    }

    public static final float BATTERY_WARN_C = 45f;
    public static final int THERMAL_STATUS_SEVERE = 3;
    public static final float HEADROOM_WARN = 1.0f;
    public static final long LOW_RAM_MB = 300;
    static final int THERMAL_SAMPLES = 3;
    static final int RAM_SAMPLES = 2;
    static final long COOLDOWN_MS = 5 * 60 * 1000L;

    private int hotCount;
    private int lowRamCount;
    private long lastThermalWarn = Long.MIN_VALUE / 2;
    private long lastRamWarn = Long.MIN_VALUE / 2;

    public static boolean isHot(Sample s) {
        return (!Float.isNaN(s.batteryTempC) && s.batteryTempC >= BATTERY_WARN_C)
                || s.thermalStatus >= THERMAL_STATUS_SEVERE
                || (!Float.isNaN(s.headroom) && s.headroom >= HEADROOM_WARN);
    }

    public static boolean isLowRam(Sample s) {
        return s.availRamMb >= 0 && s.availRamMb < LOW_RAM_MB;
    }

    /** Feed one sample; returns a warning at most once per cooldown per kind. Thermal wins over RAM. */
    public Decision feed(Sample s, long nowMs) {
        hotCount = isHot(s) ? hotCount + 1 : 0;
        lowRamCount = isLowRam(s) ? lowRamCount + 1 : 0;
        if (hotCount >= THERMAL_SAMPLES && nowMs - lastThermalWarn >= COOLDOWN_MS) {
            lastThermalWarn = nowMs;
            hotCount = 0;
            return Decision.WARN_THERMAL;
        }
        if (lowRamCount >= RAM_SAMPLES && nowMs - lastRamWarn >= COOLDOWN_MS) {
            lastRamWarn = nowMs;
            lowRamCount = 0;
            return Decision.WARN_LOW_RAM;
        }
        return Decision.OK;
    }
}
