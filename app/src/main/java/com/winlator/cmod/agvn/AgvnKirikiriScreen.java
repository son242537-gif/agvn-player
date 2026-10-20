/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;

/**
 * The screen a KiriKiri game gets. KAG3 goes full screen at its start when the screen is not larger than the game,
 * an equal one too (system/MainWindow.tjs: "if(System.screenWidth &lt;= scWidth &amp;&amp; System.screenHeight
 * &lt;= scHeight)"), and its full screen (DirectDraw's exclusive mode) hangs black in Wine: a 1280x720 game on a
 * 1280x720 screen at 0.1.20; on 1280x1024 it played in its window. So the game gets a screen larger than itself
 * ({@link #larger}) at import, from "Đồ họa" and at each start, and "Vừa màn hình" draws its window over the phone's
 * screen (AgvnFitMath#verdict). Pure Java (JVM-testable) except {@link #atLaunch}.
 */
public final class AgvnKirikiriScreen {
    private static final String TAG = "AGVN";
    /** Screens taller than wide games, smallest first: a 1280x1024 one held a 1280x720 game's window (0.1.20). */
    static final String[] TALLER = {"640x480", "800x600", "1024x768", "1280x1024", "1600x1200", "1920x1440", "2560x1920"};
    /** Height for a window's title and menu bars and frame above and below the game: the screen holds it whole. */
    static final int FRAME_ROOM = 64;

    private AgvnKirikiriScreen() {}

    /**
     * {@code screen} when it is larger than the game ({@code gameSize}, "1280x720"), as KAG3 sees it, and not smaller
     * in either direction; else the first of {@link #TALLER} that holds the game and its window's bars
     * ({@link #FRAME_ROOM}). {@code screen} when the game's size is not known.
     */
    public static String larger(String screen, String gameSize) {
        int[] game = AgvnKirikiri.parse(gameSize);
        if (game == null) return screen;
        int[] now = AgvnKirikiri.parse(screen);
        if (now != null && now[0] >= game[0] && now[1] >= game[1] && (now[0] > game[0] || now[1] > game[1])) return screen;
        for (String s : TALLER) {
            int[] wh = AgvnKirikiri.parse(s);
            if (wh[0] >= game[0] && wh[1] >= game[1] + FRAME_ROOM) return s;
        }
        return AgvnFitMath.screenFor(game[0], game[1] + FRAME_ROOM);
    }

    /** The screen {@code s} gets for {@code screen}: larger than its game. */
    static String screenFor(Shortcut s, String screen) {
        return larger(screen, s.getExtra(AgvnKirikiri.EXTRA_GAME_SIZE));
    }

    /**
     * The screen a game starts on: one not larger than its KiriKiri game gets larger, and the game keeps it (a game
     * imported before 0.1.21 has its own size as its screen). Never throws.
     */
    public static String atLaunch(Shortcut s, String screen) {
        try {
            String to = s != null ? screenFor(s, screen) : screen;
            if (to == null || to.equals(screen)) return screen;
            Log.i(TAG, "KiriKiri game " + s.getExtra(AgvnKirikiri.EXTRA_GAME_SIZE) + ": screen " + screen + " -> " + to);
            s.putExtra("screenSize", to);
            s.saveData();
            return to;
        } catch (RuntimeException e) {
            Log.w(TAG, "KiriKiri screen not changed", e);
            return screen;
        }
    }
}
