/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * BCn (DXT) texture emulation in the Vulkan wrapper. Turnip reads BCn textures itself on Adreno, so emulating them
 * only adds load time and RAM: a DXT1 texture unpacked to RGBA is 8 times bigger. Bannerlator turns it off there too.
 * The Qualcomm drivers (System, v863) do not expose BCn on Android and Mali never does, so they keep the setting.
 */
public final class AgvnBcn {
    /** How many times larger an unpacked BCn texture is, at the least (BC3, BC5, BC7 to RGBA8; BC1 is 8 times). */
    static final int UNPACKED = 4;
    /** The smallest Unreal texture pool AGVN shrinks a pool to, in MB. */
    static final int MIN_POOL_MB = 64;

    private AgvnBcn() {}

    /** The bcnEmulation value to use with this Vulkan driver: "none" under Turnip, otherwise the player's setting. */
    public static String effective(String setting, String driverId) {
        return driverId != null && driverId.toLowerCase(Locale.ROOT).contains("turnip") ? "none" : setting;
    }

    /** True when BCn textures are unpacked with this driver and setting (unset means the default, "auto"). */
    static boolean unpacked(String setting, String driverId) {
        return !"none".equals(effective(setting, driverId));
    }

    /**
     * The Unreal texture pool (r.Streaming.PoolSize, MB) for {@code poolMb}: Unreal counts its textures at their BCn
     * size, so where they are unpacked a pool takes {@link #UNPACKED} times the RAM it is set to. Legend Cleaner, an
     * Unreal game, reached 4.1 GB and was ended for memory while loading on a Mali-G610 phone with 7.2 GB.
     */
    static int texturePool(int poolMb, boolean unpacked) {
        return unpacked && poolMb > 0 ? Math.min(poolMb, Math.max(MIN_POOL_MB, poolMb / UNPACKED)) : poolMb;
    }
}
