/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * When {@link AgvnBlackScreen} asks, and what it offers. A game's window that stays black from its start, or a game
 * whose resolution Wine refused, past {@link #MIN_WAIT_MS} (or its last start plus {@link #AFTER_LAST_START_MS}, for a
 * game known to start slowly): the screen may be smaller than the game. A game that has shown a picture and goes black
 * later is a scene (a fade, a dark room), and one that showed a message box waits on it or crashed into it ("Assertion
 * failed!", read by "Tự sửa lỗi" when the game ends), unless Wine refused its resolution. A window all of one lit
 * colour is not a picture: it is the window's own background before the game draws (a KiriKiri game's grey window
 * then hung black going full screen, 0.1.20, and was never asked). Black while the game's memory still grows fast
 * ({@link #LOADING_MB}) is a loading screen and does not count: Lg Light (Unity) took 150-300 MB every 10 s, black,
 * until it ran out of RAM, and was offered a larger screen at 22 s, which only takes more. Pure Java (JVM-testable).
 */
final class AgvnBlackScreenRules {
    /** What a look at the game's window found: PLAIN is one lit colour all over, the window's own background. */
    enum Look { BLACK, PLAIN, PICTURE, UNKNOWN, NO_WINDOW }

    enum Step { WAIT, OFFER, STOP }

    static final long POLL_MS = 2000, MIN_WAIT_MS = 20_000, AFTER_LAST_START_MS = 10_000, WATCH_MS = 60_000;
    /** Looks in a row that must find black (or no window, with a refused resolution): 6 s. */
    static final int BLACK_LOOKS = 3;
    /** A pixel is dark when its red, green and blue are all at most this. */
    static final int DARK = 24;
    /** Pixels looked at across a row. */
    static final int ROW_SAMPLES = 64;
    /** How far a pixel's red, green or blue may be from the first one's in a window of one colour. */
    static final int PLAIN_SPREAD = 12;
    /** Screens offered when the game's own size is not known, smallest first. */
    static final String[] SCREENS = {"1280x720", "1600x900", "1920x1080"};
    /**
     * MB the app and the game take between two memory samples (5 s apart) while the game loads
     * ({@link AgvnSessionTrack#grewMb}); a sample older than {@link #LOADING_SAMPLE_MS} tells nothing.
     */
    static final long LOADING_MB = 64, LOADING_SAMPLE_MS = 7_000;
    /** win32u's error for a display mode the game asked for and Wine does not list (DISP_CHANGE_BADMODE). */
    static final String REFUSED = "display settings returned -2";
    /** Wine's line for each message box a game shows ({@link AgvnWineDebug#MESSAGE_BOXES}). */
    static final String BOX = "trace:msgbox:";

    private final long waitMs;
    private boolean shown;
    private int black;
    /** When the game was last seen loading, from its start. */
    private long loadingMs;

    /** {@code lastStartMs}: how long the game's last slow start took, 0 for none ({@link AgvnStartTimes}). */
    AgvnBlackScreenRules(long lastStartMs) {
        waitMs = Math.max(MIN_WAIT_MS, lastStartMs > 0 ? lastStartMs + AFTER_LAST_START_MS : 0);
    }

    long waitMs() {
        return waitMs;
    }

    /**
     * {@code refused}: Wine refused the game's resolution ({@link #refused}); {@code box}: it showed a message box;
     * {@code loading}: its memory grew by {@link #LOADING_MB} or more at the last sample. The watch lasts
     * {@link #WATCH_MS} past the wait, or past the game's loading when that ends later.
     */
    Step next(Look look, long elapsedMs, boolean refused, boolean box, boolean loading) {
        if (look == Look.PICTURE) shown = true;
        if (loading) loadingMs = elapsedMs;
        black = !loading && (look == Look.BLACK || look == Look.NO_WINDOW && refused) ? black + 1 : 0;
        if (elapsedMs < waitMs) return Step.WAIT;
        if (black >= BLACK_LOOKS && (refused || !shown && !box)) return Step.OFFER;
        return elapsedMs >= Math.max(waitMs, loadingMs) + WATCH_MS ? Step.STOP : Step.WAIT;
    }

    /** True when the last {@link #BLACK_LOOKS} looks or more found black. */
    boolean blackNow() {
        return black >= BLACK_LOOKS;
    }

    /** True when the game has shown a picture. */
    boolean shown() {
        return shown;
    }

    /** True when every looked-at pixel of {@code row} (4 bytes a pixel, any 8-bit order) is dark. */
    static boolean dark(ByteBuffer row, int width) {
        return look(new ByteBuffer[]{row}, width) == Look.BLACK;
    }

    /** BLACK when every looked-at pixel of {@code rows} is dark, PLAIN when they are all one lit colour, else PICTURE. */
    static Look look(ByteBuffer[] rows, int width) {
        int step = Math.max(1, width / ROW_SAMPLES);
        boolean lit = false, plain = true;
        int[] first = null;
        for (ByteBuffer row : rows) {
            for (int x = 0; x < width; x += step) {
                int at = x * 4;
                int[] rgb = {row.get(at) & 0xff, row.get(at + 1) & 0xff, row.get(at + 2) & 0xff};
                if (rgb[0] > DARK || rgb[1] > DARK || rgb[2] > DARK) lit = true;
                if (first == null) first = rgb;
                for (int c = 0; c < 3 && plain; c++) if (Math.abs(rgb[c] - first[c]) > PLAIN_SPREAD) plain = false;
            }
        }
        return !lit ? Look.BLACK : plain ? Look.PLAIN : Look.PICTURE;
    }

    /** True when Wine refused a resolution the game asked for. */
    static boolean refused(List<String> wineLines) {
        for (String line : wineLines) if (line.contains(REFUSED)) return true;
        return false;
    }

    /** True when the game showed a message box. */
    static boolean boxShown(List<String> wineLines) {
        for (String line : wineLines) if (line.contains(BOX)) return true;
        return false;
    }

    /**
     * A screen larger than {@code screen} to offer: the game's own size when the screen is smaller ({@link AgvnKirikiri}),
     * else the window's ({x, y, width, height}, or null) when the screen cuts it off ({@link AgvnFitMath#cutOff}), else
     * the next of {@link #SCREENS} when Wine refused the game's resolution. Black without one of these is a game still
     * loading, or one whose frames do not reach the screen, and a larger screen lights neither: Legend Cleaner opened
     * 711x400 on 854x480 while it loaded, and Support Pregnancy School stayed black full screen (1280x720) on a
     * Mali-G615 until "Đồng bộ khung hình" and "Tắt Present Wait" were on ({@link AgvnPresentSync}, which the bar then
     * offers a DirectX game). {@code window} is null for one that followed its screen ({@link AgvnScreenGrowth}). A
     * KiriKiri game whose size could not be read ({@link AgvnKirikiri#UNKNOWN}) and stays black has gone full screen,
     * so it is no smaller than the screen: it gets one larger ({@link AgvnKirikiriScreen#larger}). Null when there is
     * none.
     */
    static String bigger(String screen, String gameSize, int[] window, boolean refused) {
        int[] now = size(screen);
        if (now == null) return null;
        String forGame = AgvnKirikiriScreen.larger(screen, gameSize);
        if (forGame != null && !forGame.equals(screen)) return forGame;
        if (window != null && AgvnFitMath.cutOff(window[0], window[1], window[2], window[3], now[0], now[1]))
            return AgvnFitMath.screenFor(Math.max(window[2], now[0]), Math.max(window[3], now[1]));
        if (AgvnKirikiri.UNKNOWN.equals(gameSize)) {
            String kirikiri = AgvnKirikiriScreen.larger(screen, screen);
            if (!screen.equals(kirikiri)) return kirikiri;
        }
        if (!refused) return null;
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
