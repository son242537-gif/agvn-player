/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.ActivityManager;
import android.content.Context;

/** RAM figures used by the in-game guard and the pre-launch check. */
public final class RamGuard {
    /** Wine + emulator + renderer baseline measured on device (~1 GB), plus the game's texture pool. */
    public static final long BASELINE_MB = 1024;
    public static final long DEFAULT_REQUIRED_MB = 1536;

    private RamGuard() {}

    public static long getAvailableRamMb(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem >> 20;
    }

    /** Free RAM a game needs at launch: texture pool + baseline, or 1.5 GB when the pool is unknown. */
    public static long getRequiredRamMb(int texturePoolMb) {
        return texturePoolMb > 0 ? texturePoolMb + BASELINE_MB : DEFAULT_REQUIRED_MB;
    }

    public static boolean needsCleanup(long availableMb, long requiredMb) {
        return availableMb < requiredMb;
    }
}
