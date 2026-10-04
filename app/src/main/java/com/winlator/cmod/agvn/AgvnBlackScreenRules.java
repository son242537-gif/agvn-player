/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * When {@link AgvnBlackScreen} asks, and what it offers. A game's window that stays black from its start, or a game
 * whose resolution Wine refused, past {@link #MIN_WAIT_MS} (or its last start plus {@link #AFTER_LAST_START_MS}, for a
 * game known to start slowly): the screen may be smaller than the game. A game that has shown a picture and goes black
 * later is a scene (a fade, a dark room), unless Wine refused its resolution. Pure Java (JVM-testable).
 */
final class AgvnBlackScreenRules {
    /** What a look at the game's window found. */
    enum Look { BLACK, PICTURE, UNKNOWN, NO_WINDOW }

    enum Step { WAIT, OFFER, STOP }

    static final long POLL_MS = 2000, MIN_WAIT_MS = 20_000, AFTER_LAST_START_MS = 10_000, WATCH_MS = 60_000;
    /** Looks in a row that must find black (or no window, with a refused resolution): 6 s. */
    static final int BLACK_LOOKS = 3;
    /** A pixel is dark when its red, green and blue are all at most this. */
    static final int DARK = 24;
    /** Pixels looked at across a row. */
    static final int ROW_SAMPLES = 64;
    /** Screens offered when the game's own size is not known, smallest first. */
    static final String[] SCREENS = {"1280x720", "1600x900", "1920x1080"};
    /** win32u's error for a display mode the game asked for and Wine does not list (DISP_CHANGE_BADMODE). */
    static final String REFUSED = "display settings returned -2";

    private final long waitMs;
    private boolean shown;
    private int black;

    /** {@code lastStartMs}: how long the game's last slow start took, 0 for none ({@link AgvnStartTimes}). */
    AgvnBlackScreenRules(long lastStartMs) {
        waitMs = Math.max(MIN_WAIT_MS, lastStartMs > 0 ? lastStartMs + AFTER_LAST_START_MS : 0);
    }

    long waitMs() {
        return waitMs;
    }

    Step next(Look look, long elapsedMs, boolean refused) {
        if (look == Look.PICTURE) shown = true;
        black = look == Look.BLACK || look == Look.NO_WINDOW && refused ? black + 1 : 0;
        if (elapsedMs < waitMs) return Step.WAIT;
        if (black >= BLACK_LOOKS && (!shown || refused)) return Step.OFFER;
        return elapsedMs >= waitMs + WATCH_MS ? Step.STOP : Step.WAIT;
    }

    /** True when every looked-at pixel of {@code row} (4 bytes a pixel, any 8-bit order) is dark. */
    static boolean dark(ByteBuffer row, int width) {
        int step = Math.max(1, width / ROW_SAMPLES);
        for (int x = 0; x < width; x += step) {
            int at = x * 4;
            for (int c = 0; c < 3; c++) if ((row.get(at + c) & 0xff) > DARK) return false;
        }
        return true;
    }

    /** True when Wine refused a resolution the game asked for. */
    static boolean refused(List<String> wineLines) {
        for (String line : wineLines) if (line.contains(REFUSED)) return true;
        return false;
    }

    /**
     * A screen larger than {@code screen} to offer: the game's own size when the screen is smaller ({@link AgvnKirikiri}),
     * else the window's when it is larger than the screen, else the next of {@link #SCREENS}; null when there is none.
     */
    static String bigger(String screen, String gameSize, int windowW, int windowH) {
        int[] now = size(screen);
        if (now == null) return null;
        String forGame = AgvnKirikiri.notSmaller(screen, gameSize);
        if (forGame != null && !forGame.equals(screen)) return forGame;
        if (windowW > now[0] + AgvnFitMath.SLACK || windowH > now[1] + AgvnFitMath.SLACK)
            return AgvnFitMath.screenFor(Math.max(windowW, now[0]), Math.max(windowH, now[1]));
        for (String s : SCREENS) {
            int[] wh = size(s);
            if (wh[0] >= now[0] && wh[1] >= now[1] && (wh[0] > now[0] || wh[1] > now[1])) return s;
        }
        return null;
    }

    static int[] size(String s) {
        String n = AgvnScreenSize.normalize(s);
        if (n == null) return null;
        String[] wh = n.split("x");
        return new int[]{Integer.parseInt(wh[0]), Integer.parseInt(wh[1])};
    }
}
