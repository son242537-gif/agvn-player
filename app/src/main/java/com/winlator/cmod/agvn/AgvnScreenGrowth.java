/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;

/**
 * A screen made larger for a game's window ("Đổi màn hình game thành …", {@link AgvnScreenFit#resize}) helps only when
 * the window keeps its size. Party Me's window grew with every screen it got, 12 px past it each time, so every offer
 * made its screen 12 px larger (1080x664, 1092x676, 1104x688 on one evening) wherever the window sat:
 * {@link AgvnFitMath#FRAME} guessed its place and missed. AGVN notes the screen before its first such change, the
 * screen it set, and how far past the window reached. When the window then reaches as far past the screen AGVN set, it
 * follows the screen: the game gets back the screen it had, from its next start, and is not asked again. Any game, any
 * phone, wherever its window sits. Pure Java except {@link #remember} and {@link #undo}.
 */
final class AgvnScreenGrowth {
    private static final String TAG = "AGVN";
    static final String EXTRA_FROM = "agvnFitFrom", EXTRA_TO = "agvnFitTo", EXTRA_PAST = "agvnFitPast";
    /** Pixels the reach past the screen may differ by and still be the same (screens are even-sized). */
    static final int SLACK = 2;

    private AgvnScreenGrowth() {}

    /** "12x12": how far a w x h window reaches past an sw x sh screen, each way. */
    static String past(int w, int h, int sw, int sh) {
        return (w - sw) + "x" + (h - sh);
    }

    /**
     * True when a w x h window on an sw x sh screen (the one AGVN set, "WxH") reaches past it as far as
     * {@code pastBefore} says it did past the screen before. A window with a size of its own fits its new screen.
     */
    static boolean followed(String pastBefore, String screenSet, int w, int h, int sw, int sh) {
        int[] p = pair(pastBefore);
        if (p == null || (p[0] <= 0 && p[1] <= 0) || !(sw + "x" + sh).equals(screenSet)) return false;
        return Math.abs(w - sw - p[0]) <= SLACK && Math.abs(h - sh - p[1]) <= SLACK;
    }

    /** {@link #followed} for this game's notes. */
    static boolean followed(Shortcut s, int w, int h, int sw, int sh) {
        return s != null && followed(s.getExtra(EXTRA_PAST), s.getExtra(EXTRA_TO), w, h, sw, sh);
    }

    /**
     * Before the game's screen goes to {@code screen} for a window ({x, y, width, height}, or null when unknown): the
     * screen it has, unless that is the one AGVN set last (the chain goes on), and how far past it the window reaches.
     */
    static void remember(XServerDisplayActivity activity, Shortcut s, int[] window, String screen) {
        int sw = activity.getXServer().screenInfo.width, sh = activity.getXServer().screenInfo.height;
        if (window == null || window[2] <= sw && window[3] <= sh) return;
        String now = sw + "x" + sh;
        if (s.getExtra(EXTRA_FROM).isEmpty() || !now.equals(s.getExtra(EXTRA_TO))) s.putExtra(EXTRA_FROM, now);
        s.putExtra(EXTRA_TO, screen);
        s.putExtra(EXTRA_PAST, past(window[2], window[3], sw, sh));
    }

    /** {@link #undo} when the game's window ({x, y, width, height}) followed its screen; true when it did. */
    static boolean undoIfFollowed(XServerDisplayActivity activity, float[] rect) {
        Shortcut s = activity.agvnShortcut();
        int w = (int) rect[2], h = (int) rect[3];
        int sw = activity.getXServer().screenInfo.width, sh = activity.getXServer().screenInfo.height;
        if (!followed(s, w, h, sw, sh)) return false;
        undo(activity, s, w, h, sw, sh);
        return true;
    }

    static int[] ints(float[] rect) {
        return new int[]{(int) rect[0], (int) rect[1], (int) rect[2], (int) rect[3]};
    }

    /**
     * The window followed its screen: the game gets back the screen it had before AGVN's first change, from its next
     * start, and "Vừa màn hình" asks it nothing more ({@link AgvnScreenFit}). The player is told once.
     */
    static void undo(XServerDisplayActivity activity, Shortcut s, int w, int h, int sw, int sh) {
        String from = s.getExtra(EXTRA_FROM);
        if (!from.isEmpty()) s.putExtra("screenSize", from);
        s.putExtra(EXTRA_FROM, null);
        s.putExtra(EXTRA_TO, null);
        s.putExtra(EXTRA_PAST, null);
        s.putExtra(AgvnScreenFit.EXTRA_FIT, "0");
        s.saveData();
        String back = from.isEmpty() ? sw + "x" + sh : from;
        AgvnSessionLog.event("Khung game " + w + "x" + h + " lớn theo màn hình " + sw + "x" + sh
                + ": lần mở sau trả màn hình về " + back + ", không hỏi nữa");
        Log.i(TAG, "screen fit: window follows the screen, back to " + back);
        AgvnWarningBar.show(activity, activity.getString(R.string.agvn_fit_follow_title),
                activity.getString(R.string.agvn_fit_follow_detail, w + "×" + h, sw + "×" + sh, back.replace('x', '×')),
                new AgvnWarningBar.Choice(android.R.string.ok, null));
    }

    private static int[] pair(String wh) {
        String[] p = wh == null ? new String[0] : wh.split("x");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
