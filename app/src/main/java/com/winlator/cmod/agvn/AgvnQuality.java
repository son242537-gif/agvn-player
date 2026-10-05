/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.ui.FpsLimiterControl;

import java.io.File;

/**
 * "Đồ họa" slider chosen outside the game: 5 steps from Siêu nhẹ (coolest) to Rất cao (sharpest), plus AUTO = the
 * step recommended for this phone, which also honours the AGVN game profile the same way as at import
 * (LaunchPresetResolver). Writes the shortcut's resolution, the FPS the in-game "Giới hạn FPS" starts from, and the
 * Unreal texture pool. The FPS is only a starting point: the X server paces every renderer at it, and the player can
 * raise, lower or turn it off in game. Rất cao starts with no cap, so the game and its cheat menu control FPS.
 */
public final class AgvnQuality {
    public static final String EXTRA_QUALITY = "agvnQuality";

    public enum Level {
        AUTO(null, 0, 0, null),
        LOWEST("640x360", 20, 384, DeviceTier.YEU),
        LOW("854x480", 24, 512, DeviceTier.YEU),
        // FPS a 60 Hz screen shows evenly: 20 or 30 (24 runs as 20 there, as 24 on 120 Hz; see AgvnFramePacing)
        MEDIUM("960x544", 30, 768, DeviceTier.TRUNG_BINH),
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

    /**
     * Effective values for {@code level}: AUTO follows the phone tier and game profile, a slider step is fixed. The
     * screen is never smaller than a KiriKiri game ({@link AgvnKirikiri#screenFor}).
     */
    public static LaunchPresetResolver.Effective effective(Context ctx, Shortcut shortcut, Level level) {
        LaunchPresetResolver.Effective eff;
        if (level != Level.AUTO) eff = new LaunchPresetResolver.Effective(level.resolution, level.fps, level.texturePool);
        else {
            DeviceTier tier = DeviceTierManager.current(ctx);
            eff = LaunchPresetResolver.resolve(profileOf(shortcut), tier, DeviceTierManager.getRules(ctx).preset(tier));
        }
        return new LaunchPresetResolver.Effective(AgvnKirikiri.screenFor(shortcut, eff.resolution), eff.fps, eff.texturePool);
    }

    public static void apply(Context ctx, Shortcut shortcut, Level level) {
        LaunchPresetResolver.Effective eff = effective(ctx, shortcut, level);
        if (eff.resolution != null) shortcut.putExtra("screenSize", eff.resolution);
        shortcut.putExtra("envVars", withoutFpsCap(shortcut.getExtra("envVars")));
        setStartFps(shortcut, eff.fps);
        if (GameExeResolver.Engine.UNREAL.name().equals(shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE)))
            shortcut.putExtra(AgvnGameImporter.EXTRA_TEXTURE_POOL, String.valueOf(eff.texturePool));
        shortcut.putExtra(AgvnGameImporter.EXTRA_TIER, tierFor(ctx, level).name());
        shortcut.putExtra(EXTRA_QUALITY, level.name());
        shortcut.saveData();
    }

    /**
     * Sets where the in-game "Giới hạn FPS" starts (0 = off) and drops an older in-game choice. Nothing else caps FPS:
     * DXVK_FRAME_RATE is not written, because DXVK enforces it and nothing in game (its own options, a cheat menu,
     * "Giới hạn FPS") can lift it.
     */
    static void setStartFps(Shortcut shortcut, int fps) {
        shortcut.putExtra(FpsLimiterControl.EXTRA_LIMIT, String.valueOf(Math.max(0, fps)));
        shortcut.putExtra(FpsLimiterControl.EXTRA_ENABLED, null);
        shortcut.putExtra("graphicsFpsPreset", null);
    }

    /** FPS cap Rất cao wrote before it became uncapped (v0.1.2). */
    static final int OLD_HIGHEST_FPS = 40;

    /**
     * Games saved by v0.1.2 carry their step's cap as DXVK_FRAME_RATE. Moves it to where "Giới hạn FPS" starts, so it
     * can be changed in game, unless the player already chose a limit there. Rất cao's old 40 FPS becomes off. Safe to
     * repeat.
     */
    public static void upgrade(Shortcut shortcut) {
        if (shortcut == null) return;
        String env = shortcut.getExtra("envVars");
        String start = startFpsFromOldCap(env, current(shortcut) == Level.HIGHEST);
        if (start.isEmpty()) return;
        boolean chosenInGame = !shortcut.getExtra(FpsLimiterControl.EXTRA_LIMIT).isEmpty()
                || !shortcut.getExtra(FpsLimiterControl.EXTRA_ENABLED).isEmpty()
                || !shortcut.getExtra("graphicsFpsPreset").isEmpty();
        if (!chosenInGame) setStartFps(shortcut, Integer.parseInt(start));
        shortcut.putExtra("envVars", withoutFpsCap(env));
        shortcut.saveData();
    }

    /** Where "Giới hạn FPS" should start for an old DXVK_FRAME_RATE ("0" = off), or "" when there is none. */
    static String startFpsFromOldCap(String envVars, boolean highest) {
        String fps = new EnvVars(envVars == null ? "" : envVars).get("DXVK_FRAME_RATE");
        if (!fps.matches("[0-9]{1,4}")) return "";
        int value = Integer.parseInt(fps);
        return String.valueOf(highest && value == OLD_HIGHEST_FPS ? 0 : value);
    }

    /** Removes DXVK_FRAME_RATE, keeping every other variable. */
    static String withoutFpsCap(String envVars) {
        EnvVars vars = new EnvVars(envVars == null ? "" : envVars);
        vars.remove("DXVK_FRAME_RATE");
        return vars.toString();
    }

    static AgvnProfile profileOf(Shortcut shortcut) {
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
