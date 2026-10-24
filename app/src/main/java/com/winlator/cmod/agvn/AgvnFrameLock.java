/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.ui.FpsLimiterControl;

import java.util.Arrays;
import java.util.List;

/**
 * Engines that run a game's time one step per frame: GameMaker (a step per frame at the game's speed, 60 in most
 * games), Wolf RPG Editor (60) and RPG Maker 2000 to VX Ace on Wine (60, XP 40). Under an FPS limit below that speed
 * the whole game slows with it, it does not get lighter: a GameMaker game (game_get_speed 60, POCO, 24/10/2026) ran at
 * half speed under the 30 FPS start limit, its clocks, walking and timers all at half speed, and nothing said so. Such
 * a game starts with no limit below {@link #FPS}, whatever the phone or the Đồ họa step; the player can still set a
 * lower one in game ("Giới hạn FPS").
 */
public final class AgvnFrameLock {
    static final int FPS = 60;
    /** Shortcut extra "1": a game added before this rule had its start limit raised once ({@link #upgrade}). */
    static final String EXTRA_RAISED = "agvnFrameLockFps";
    private static final List<String> ENGINES = Arrays.asList(GameExeResolver.Engine.GAMEMAKER.name(),
            GameExeResolver.Engine.WOLFRPG.name(), GameExeResolver.Engine.RPGMAKER.name());
    private static volatile String note;

    private AgvnFrameLock() {}

    /** True for an engine whose game runs one step per frame ({@code engine}: an Engine name, as shortcuts keep it). */
    public static boolean frameLocked(String engine) {
        return engine != null && ENGINES.contains(engine);
    }

    /** The start limit for a game of {@code engine}: {@code fps} (0 = none), or {@link #FPS} where it would slow the game. */
    static int fps(String engine, int fps) {
        return fps > 0 && fps < FPS && frameLocked(engine) ? FPS : fps;
    }

    /**
     * A game added before this rule: its start limit, when lower than {@link #FPS}, goes to it, once; a limit set in
     * game afterwards stays. True when the shortcut changed (the caller saves). Before the session log starts.
     */
    static boolean upgrade(Shortcut s) {
        note = null;
        if (!frameLocked(s.getExtra(AgvnGameImporter.EXTRA_ENGINE)) || "1".equals(s.getExtra(EXTRA_RAISED))) return false;
        s.putExtra(EXTRA_RAISED, "1");
        int limit = parse(s.getExtra(FpsLimiterControl.EXTRA_LIMIT));
        if (fps(s.getExtra(AgvnGameImporter.EXTRA_ENGINE), limit) != limit) {
            s.putExtra(FpsLimiterControl.EXTRA_LIMIT, String.valueOf(FPS));
            note = "Giới hạn FPS lúc mở game: " + limit + " → " + FPS + " FPS (game chạy theo khung hình: giới hạn thấp hơn"
                    + " làm cả game chậm lại)";
        }
        return true;
    }

    /** The line for the session's events, once; null when the limit stayed. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }

    private static int parse(String value) {
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
