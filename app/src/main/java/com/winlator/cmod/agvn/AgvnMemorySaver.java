/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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
 * <p>
 * A game that ran out of RAM on this phone ({@link #ranOut}, found by "Tự sửa lỗi") gets Siêu nhẹ's savings at any
 * step, with its step's screen and FPS. Every other game keeps its step's savings, on any phone: the phone's RAM or GPU
 * alone never makes a game that runs well save more and look worse. 0.1.17 also gave Siêu nhẹ's savings to every
 * DirectX game on a phone under 9 GB that unpacks BCn textures, whatever step the player chose.
 */
public final class AgvnMemorySaver {
    private static final String TAG = "AGVN";
    static final String DXVK_SMALL_CHUNKS = "dxvk.maxChunkSize=16";
    static final String DXVK_FREE_PIPELINES = "dxvk.trackPipelineLifetime=True";
    /** "1" once "Tự sửa lỗi" found that the game ran out of RAM on this phone ({@link #markRamShort}). */
    static final String EXTRA_RAM_SHORT = "agvnRamShort";
    /** The problems of game-problems.json that say a game ran out of RAM (the -saved ones: with these savings on). */
    static final List<String> OUT_OF_RAM = Arrays.asList("memory", "gpu-memory", "killed-low-memory", "low-ram-end",
            "killed-low-memory-saved", "low-ram-saved");
    /** Why the last start saved the most RAM, for its session log ({@link #takeNote}); null when it did not. */
    private static volatile String note;

    private AgvnMemorySaver() {}

    /** The step whose savings apply: the slider's step, or the phone's suggested step for AUTO. */
    static AgvnQuality.Level stepOf(Context ctx, Shortcut shortcut) {
        AgvnQuality.Level level = shortcut != null ? AgvnQuality.current(shortcut) : AgvnQuality.Level.AUTO;
        return level == AgvnQuality.Level.AUTO ? AgvnQuality.recommended(ctx) : level;
    }

    /** True when the game ran out of RAM on this phone before ({@link #markRamShort}): it then saves the most RAM. */
    static boolean ranOut(Shortcut s) {
        return s != null && "1".equals(s.getExtra(EXTRA_RAM_SHORT));
    }

    /** The step whose memory savings apply: Siêu nhẹ's for a game that ran out of RAM here ({@code tight}). */
    static AgvnQuality.Level memoryStep(AgvnQuality.Level step, boolean tight) {
        return tight ? AgvnQuality.Level.LOWEST : step;
    }

    /** The Unreal texture pool (MB, 0: the game's own) for a step's {@code poolMb}: Siêu nhẹ's if tight, BCn's part. */
    static int ueTexturePool(int poolMb, boolean tight, boolean bcnUnpacked) {
        int lowest = AgvnQuality.Level.LOWEST.texturePool;
        return AgvnBcn.texturePool(tight ? (poolMb > 0 ? Math.min(poolMb, lowest) : lowest) : poolMb, bcnUnpacked);
    }

    /** True when a game that ended with {@code problem} ran out of RAM. */
    static boolean ranOutOfRam(String problem) {
        return OUT_OF_RAM.contains(problem);
    }

    /** "Tự sửa lỗi" found that the game ran out of RAM: from its next start, it saves the most RAM it can. */
    static void markRamShort(Shortcut s) {
        if (ranOut(s)) return;
        s.putExtra(EXTRA_RAM_SHORT, "1");
        s.saveData();
    }

    /** Why the game about to start saves the most RAM, once, for its session log; null when it does not. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }

    private static void noteTight() {
        note = "Tiết kiệm RAM như Siêu nhẹ: game từng bị tắt vì hết RAM trên máy này";
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
     * shortcut's {@link AgvnQuality#EXTRA_QUALITY}), or Siêu nhẹ's when it ran out of RAM before ({@code ranOut}), as
     * in Wine. Never throws: a failure must not block the launch.
     */
    static void applyRenpyLight(Context ctx, File gameDir, String quality, boolean ranOut) {
        try {
            AgvnQuality.Level level = AgvnQuality.Level.of(quality);
            level = level == AgvnQuality.Level.AUTO ? AgvnQuality.recommended(ctx) : level;
            AgvnRenpyCache.apply(gameDir, renpyCacheMb(memoryStep(level, ranOut)));
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
        boolean tight = ranOut(shortcut);
        if (tight) noteTight();
        String options = dxvkOptions(memoryStep(stepOf(ctx, shortcut), tight));
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
                if (ranOut(shortcut)) noteTight();
                AgvnRenpyCache.apply(new File(gameDir), renpyCacheMb(memoryStep(step, ranOut(shortcut))));
            } else if (GameExeResolver.Engine.UNITY.name().equals(engine)) {
                File userReg = new File(shortcut.container.getRootDir(), ".wine/user.reg");
                boolean lowest = unityLowestQuality(memoryStep(step, ranOut(shortcut)))
                        && !AgvnUnityQuality.own(shortcut); // "Để game Unity tự chọn chất lượng"
                AgvnUnityQuality.apply(shortcut, userReg, lowest);
            }
        } catch (Exception e) {
            Log.w(TAG, "memory settings not applied", e);
        }
    }
}
