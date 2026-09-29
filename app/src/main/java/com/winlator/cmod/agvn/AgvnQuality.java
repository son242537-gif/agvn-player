/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;

import java.io.File;

/**
 * "Đồ họa" slider chosen outside the game: 5 steps from Siêu nhẹ (coolest) to Rất cao (sharpest), plus AUTO = the
 * step recommended for this phone, which also honours the AGVN game profile the same way as at import
 * (LaunchPresetResolver). Writes the shortcut's resolution, FPS cap (DXVK_FRAME_RATE) and Unreal texture pool.
 * Rất cao sets no FPS cap: the game and its cheat menu keep control of FPS (an outside cap silently overrode their
 * own FPS options); the in-game "Giới hạn FPS" menu still caps it live when the phone gets hot.
 */
public final class AgvnQuality {
    public static final String EXTRA_QUALITY = "agvnQuality";

    public enum Level {
        AUTO(null, 0, 0, null),
        LOWEST("640x360", 20, 384, DeviceTier.YEU),
        LOW("854x480", 24, 512, DeviceTier.YEU),
        MEDIUM("960x544", 27, 768, DeviceTier.TRUNG_BINH),
        HIGH("1280x720", 30, 1024, DeviceTier.FLAGSHIP),
        HIGHEST("1600x900", 0, 1536, DeviceTier.FLAGSHIP);

        final String resolution;
        /** 0 = no cap. */
        final int fps;
        final int texturePool;
        final DeviceTier tier;

        Level(String resolution, int fps, int texturePool, DeviceTier tier) {
            this.resolution = resolution;
            this.fps = fps;
            this.texturePool = texturePool;
            this.tier = tier;
        }

        static Level of(String name) {
            for (Level l : values()) if (l.name().equals(name)) return l;
            return AUTO;
        }

        /** Slider position 0..4 (AUTO has none). */
        public int step() {
            return ordinal() - 1;
        }

        public static Level atStep(int step) {
            return values()[Math.max(0, Math.min(4, step)) + 1];
        }
    }

    private AgvnQuality() {}

    public static Level current(Shortcut shortcut) {
        return Level.of(shortcut.getExtra(EXTRA_QUALITY, Level.AUTO.name()));
    }

    /** The slider step suggested for a phone of this tier. */
    static Level recommended(DeviceTier tier) {
        switch (tier) {
            case FLAGSHIP: return Level.HIGH;
            case TRUNG_BINH: return Level.MEDIUM;
            default: return Level.LOW;
        }
    }

    public static Level recommended(Context ctx) {
        return recommended(DeviceTierManager.current(ctx));
    }

    public static DeviceTier tierFor(Context ctx, Level level) {
        return level.tier != null ? level.tier : DeviceTierManager.current(ctx);
    }

    /** Effective values for {@code level}: AUTO follows the phone tier and game profile, a slider step is fixed. */
    public static LaunchPresetResolver.Effective effective(Context ctx, Shortcut shortcut, Level level) {
        if (level != Level.AUTO) return new LaunchPresetResolver.Effective(level.resolution, level.fps, level.texturePool);
        DeviceTier tier = DeviceTierManager.current(ctx);
        return LaunchPresetResolver.resolve(profileOf(shortcut), tier, DeviceTierManager.getRules(ctx).preset(tier));
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

    /** FPS cap Rất cao wrote before it became uncapped (v0.1.2). */
    static final int OLD_HIGHEST_FPS = 40;

    /** Drops the old 40 FPS cap from a game saved at Rất cao, so it matches what the slider now shows. Safe to repeat. */
    public static void upgrade(Shortcut shortcut) {
        if (shortcut == null || current(shortcut) != Level.HIGHEST) return;
        String env = shortcut.getExtra("envVars");
        String cleaned = withoutOldHighestCap(env);
        if (cleaned.equals(env)) return;
        shortcut.putExtra("envVars", cleaned);
        shortcut.saveData();
    }

    static String withoutOldHighestCap(String envVars) {
        String env = envVars == null ? "" : envVars;
        EnvVars vars = new EnvVars(env);
        return String.valueOf(OLD_HIGHEST_FPS).equals(vars.get("DXVK_FRAME_RATE")) ? withFps(env, 0) : env;
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
