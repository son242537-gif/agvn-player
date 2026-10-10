/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * Merges tier preset and game profile into the values written to the shortcut.
 * Order (later wins): tier preset < game profile < profile.weakDevice (YEU tier only).
 * On YEU without weakDevice values the profile can only go lighter than the tier preset, never heavier.
 */
public final class LaunchPresetResolver {
    /** Effective launch values; fps 0 = unlimited, texturePool 0 = game default. */
    public static final class Effective {
        public final String resolution;
        public final int fps;
        public final int texturePool;

        Effective(String resolution, int fps, int texturePool) {
            this.resolution = resolution;
            this.fps = fps;
            this.texturePool = texturePool;
        }
    }

    private LaunchPresetResolver() {}

    public static Effective resolve(AgvnProfile p, DeviceTier tier, DeviceTierRules.Preset preset) {
        String resolution = p.resolution != null ? p.resolution : preset.resolution;
        int fps = p.fpsLimit != null ? p.fpsLimit : preset.fps;
        int pool = p.texturePool != null ? p.texturePool : preset.texturePool;
        if (tier != DeviceTier.YEU) return new Effective(resolution, fps, pool);

        AgvnProfile.Preset weak = p.weakDevice;
        resolution = weak != null && weak.resolution != null ? weak.resolution : smaller(resolution, preset.resolution);
        fps = weak != null && weak.fpsLimit != null ? weak.fpsLimit : (fps == 0 ? preset.fps : Math.min(fps, preset.fps));
        pool = weak != null && weak.texturePool != null ? weak.texturePool : (pool == 0 ? preset.texturePool : Math.min(pool, preset.texturePool));
        return new Effective(resolution, fps, pool);
    }

    static String smaller(String a, String b) {
        long pa = pixels(a), pb = pixels(b);
        if (pa <= 0) return b;
        if (pb <= 0) return a;
        return pa <= pb ? a : b;
    }

    private static long pixels(String res) {
        if (res == null) return -1;
        String[] parts = res.toLowerCase().split("x");
        if (parts.length != 2) return -1;
        try {
            return Long.parseLong(parts[0].trim()) * Long.parseLong(parts[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
