/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.io.File;

/**
 * Lower "Đồ họa" steps also cut RAM, each where the game spends it. Phones share RAM with the GPU, so texture and
 * buffer memory count in full. Per step:
 * <ul>
 *   <li>DXVK (DirectX 8–11): smaller memory chunks (16 MB instead of 64 MB per memory type) from Trung bình down;
 *       pipeline libraries freed once unused on Thấp and Siêu nhẹ;</li>
 *   <li>Ren'Py: image cache capped at 256 / 192 / 128 MB (the engine default is 300–400 MB, games may raise it), in
 *       Wine and on "Chạy nhẹ";</li>
 *   <li>Unity: the game starts at its lowest quality level on Thấp and Siêu nhẹ;</li>
 *   <li>Unreal: texture pool, already written by {@link AgvnQuality} and {@link UeIniWriter};</li>
 *   <li>OpenGL (Zink): freed-buffer cache capped at 256 MB in the bundled build, at every step.</li>
 * </ul>
 * Cao and Rất cao leave every game at its own defaults and undo what a lower step wrote.
 */
public final class AgvnMemorySaver {
    private static final String TAG = "AGVN";
    static final String DXVK_SMALL_CHUNKS = "dxvk.maxChunkSize=16";
    static final String DXVK_FREE_PIPELINES = "dxvk.trackPipelineLifetime=True";

    private AgvnMemorySaver() {}

    /** The step whose savings apply: the slider's step, or the phone's suggested step for AUTO. */
    static AgvnQuality.Level stepOf(Context ctx, Shortcut shortcut) {
        AgvnQuality.Level level = shortcut != null ? AgvnQuality.current(shortcut) : AgvnQuality.Level.AUTO;
        return level == AgvnQuality.Level.AUTO ? AgvnQuality.recommended(ctx) : level;
    }

    /** Ren'Py image cache in MB for {@code level}, or 0 to keep the game's own. */
    static int renpyCacheMb(AgvnQuality.Level level) {
        switch (level) {
            case LOWEST: return 128;
            case LOW: return 192;
            case MEDIUM: return 256;
            default: return 0;
        }
    }

    /**
     * "Chạy nhẹ" Ren'Py, before the game starts: the image cache cap of the game's "Đồ họa" step ({@code quality}, the
     * shortcut's {@link AgvnQuality#EXTRA_QUALITY}), as in Wine. Never throws: a failure must not block the launch.
     */
    static void applyRenpyLight(Context ctx, File gameDir, String quality) {
        try {
            AgvnQuality.Level level = AgvnQuality.Level.of(quality);
            AgvnRenpyCache.apply(gameDir, renpyCacheMb(level == AgvnQuality.Level.AUTO ? AgvnQuality.recommended(ctx) : level));
        } catch (Exception e) {
            Log.w(TAG, "Ren'Py memory setting not applied", e);
        }
    }

    static boolean unityLowestQuality(AgvnQuality.Level level) {
        return level == AgvnQuality.Level.LOWEST || level == AgvnQuality.Level.LOW;
    }

    /** DXVK options for {@code level}, empty when none. */
    static String dxvkOptions(AgvnQuality.Level level) {
        switch (level) {
            case LOWEST:
            case LOW: return DXVK_SMALL_CHUNKS + ";" + DXVK_FREE_PIPELINES;
            case MEDIUM: return DXVK_SMALL_CHUNKS;
            default: return "";
        }
    }

    /** Appends {@code options} to an existing DXVK_CONFIG value (options are separated by ';'). */
    static String mergeDxvkConfig(String current, String options) {
        if (options.isEmpty()) return current == null ? "" : current;
        if (current == null || current.trim().isEmpty()) return options;
        String trimmed = current.trim();
        return trimmed + (trimmed.endsWith(";") ? "" : ";") + options;
    }

    /** At launch, after the DXVK settings: adds this step's DXVK memory options to DXVK_CONFIG. */
    public static void applyDxvk(Context ctx, Shortcut shortcut, EnvVars envVars) {
        String options = dxvkOptions(stepOf(ctx, shortcut));
        if (options.isEmpty()) return;
        envVars.put("DXVK_CONFIG", mergeDxvkConfig(envVars.get("DXVK_CONFIG"), options));
    }

    /**
     * At launch, before Wine starts: writes or removes the Ren'Py and Unity settings of an imported game. Never
     * throws: a failure must not block the launch.
     */
    public static void applyGameSettings(Context ctx, Shortcut shortcut) {
        if (shortcut == null) return;
        try {
            String engine = shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE);
            String gameDir = shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
            if (engine.isEmpty() || gameDir.isEmpty()) return;
            AgvnQuality.Level step = stepOf(ctx, shortcut);
            if (GameExeResolver.Engine.RENPY.name().equals(engine)) {
                AgvnRenpyCache.apply(new File(gameDir), renpyCacheMb(step));
            } else if (GameExeResolver.Engine.UNITY.name().equals(engine)) {
                File userReg = new File(shortcut.container.getRootDir(), ".wine/user.reg");
                AgvnUnityQuality.apply(shortcut, userReg, unityLowestQuality(step));
            }
        } catch (Exception e) {
            Log.w(TAG, "memory settings not applied", e);
        }
    }
}
