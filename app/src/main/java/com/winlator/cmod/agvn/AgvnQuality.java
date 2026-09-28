/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;

import java.io.File;

/**
 * "Đồ họa" presets chosen outside the game: Tự động (by phone strength), Thấp, Trung bình, Cao. Writes the shortcut's
 * resolution, FPS cap (DXVK_FRAME_RATE) and Unreal texture pool; AGVN profile values are honoured the same way as at
 * import (LaunchPresetResolver), so "Cao" uses the game's own settings.
 */
public final class AgvnQuality {
    public static final String EXTRA_QUALITY = "agvnQuality";

    public enum Level {
        AUTO(null), LOW(DeviceTier.YEU), MEDIUM(DeviceTier.TRUNG_BINH), HIGH(DeviceTier.FLAGSHIP);

        final DeviceTier tier;

        Level(DeviceTier tier) {
            this.tier = tier;
        }

        static Level of(String name) {
            for (Level l : values()) if (l.name().equals(name)) return l;
            return AUTO;
        }
    }

    private AgvnQuality() {}

    public static Level current(Shortcut shortcut) {
        return Level.of(shortcut.getExtra(EXTRA_QUALITY, Level.AUTO.name()));
    }

    public static DeviceTier tierFor(Context ctx, Level level) {
        return level.tier != null ? level.tier : DeviceTierManager.current(ctx);
    }

    /** Effective values for {@code level} (profile-aware when the game was imported from an AGVN profile). */
    public static LaunchPresetResolver.Effective effective(Context ctx, Shortcut shortcut, Level level) {
        DeviceTier tier = tierFor(ctx, level);
        DeviceTierRules.Preset preset = DeviceTierManager.getRules(ctx).preset(tier);
        LaunchPresetResolver.Effective eff = LaunchPresetResolver.resolve(profileOf(shortcut), tier, preset);
        return level == Level.MEDIUM ? capped(eff, preset) : eff;
    }

    /** "Trung bình" never goes heavier than the medium preset, even when the game profile asks for more. */
    static LaunchPresetResolver.Effective capped(LaunchPresetResolver.Effective eff, DeviceTierRules.Preset preset) {
        int fps = eff.fps == 0 ? preset.fps : Math.min(eff.fps, preset.fps);
        int pool = eff.texturePool == 0 ? preset.texturePool : Math.min(eff.texturePool, preset.texturePool);
        return new LaunchPresetResolver.Effective(LaunchPresetResolver.smaller(eff.resolution, preset.resolution), fps, pool);
    }

    public static void apply(Context ctx, Shortcut shortcut, Level level) {
        LaunchPresetResolver.Effective eff = effective(ctx, shortcut, level);
        if (eff.resolution != null) shortcut.putExtra("screenSize", eff.resolution);
        shortcut.putExtra("envVars", withFps(shortcut.getExtra("envVars"), eff.fps));
        if (GameExeResolver.Engine.UNREAL.name().equals(shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE)))
            shortcut.putExtra(AgvnGameImporter.EXTRA_TEXTURE_POOL, String.valueOf(eff.texturePool));
        shortcut.putExtra(AgvnGameImporter.EXTRA_TIER, tierFor(ctx, level).name());
        shortcut.putExtra(EXTRA_QUALITY, level.name());
        shortcut.saveData();
    }

    /** Sets or removes DXVK_FRAME_RATE, keeping every other variable. */
    static String withFps(String envVars, int fps) {
        EnvVars vars = new EnvVars(envVars == null ? "" : envVars);
        if (fps > 0) vars.put("DXVK_FRAME_RATE", fps);
        else vars.remove("DXVK_FRAME_RATE");
        return vars.toString();
    }

    private static AgvnProfile profileOf(Shortcut shortcut) {
        String path = shortcut.getExtra(AgvnGameImporter.EXTRA_PROFILE_PATH);
        if (!path.isEmpty()) {
            try {
                String json = FileUtils.readString(new File(path));
                if (json != null && !json.isEmpty()) return AgvnProfile.parse(json);
            } catch (AgvnProfileException ignored) {}
        }
        return AgvnProfile.defaultFor(shortcut.name);
    }
}
