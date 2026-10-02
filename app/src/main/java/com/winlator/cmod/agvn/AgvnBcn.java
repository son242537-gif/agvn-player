/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * BCn (DXT) texture emulation in the Vulkan wrapper. Turnip reads BCn textures itself on Adreno, so emulating them
 * only adds load time and RAM: a DXT1 texture unpacked to RGBA is 8 times bigger. Bannerlator turns it off there too.
 * The Qualcomm drivers (System, v863) do not expose BCn on Android and Mali never does, so they keep the setting.
 */
public final class AgvnBcn {
    private AgvnBcn() {}

    /** The bcnEmulation value to use with this Vulkan driver: "none" under Turnip, otherwise the player's setting. */
    public static String effective(String setting, String driverId) {
        return driverId != null && driverId.toLowerCase(Locale.ROOT).contains("turnip") ? "none" : setting;
    }
}
