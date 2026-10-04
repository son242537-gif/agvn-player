/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.widget.VulkanXServerView;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.Arrays;

/**
 * "Vừa màn hình" for a Windows game whose own window does not suit the screen. Every second it finds the game's window
 * ({@link AgvnGameWindow}: the largest application window, Wine's explorer.exe left out, at any depth) and, once the
 * game runs and the window stays put for 3 s ({@link AgvnFitMath#verdict}):
 * <ul>
 *   <li>a small frame (a 640×480 game on a 1280×720 screen): the view draws that window over the whole screen at once
 *   (VulkanXServerView, TouchpadView), also at the next starts, and the bar can undo it;</li>
 *   <li>a window bigger than the screen (a 1280×720 game at "Thấp", 854×480) is cut off, and no pointer reaches past
 *   the screen: the bar offers a screen the window's size, then "Mở lại game ngay".</li>
 * </ul>
 * [⛶] on the top bar turns it on or off ({@link #toggle}). The game keeps the choice (extra agvnFit: 1 on, 0 off and
 * not asked again). The other renderer (the game's "native rendering") cannot redraw a part of the screen: there a
 * small frame gets the screen change too. UI thread.
 */
public final class AgvnScreenFit {
    private static final String TAG = "AGVN", EXTRA_FIT = "agvnFit";
    private static final long POLL_MS = 1000;
    private static final int STABLE_POLLS = 3;

    private final XServerDisplayActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private boolean on, asked;
    private float[] shown, last;
    private int stable;

    AgvnScreenFit(XServerDisplayActivity activity) {
        this.activity = activity;
        Shortcut s = activity.agvnShortcut();
        String saved = s != null ? s.getExtra(EXTRA_FIT) : "";
        on = "1".equals(saved);
        asked = !saved.isEmpty();
        handler.postDelayed(poll, POLL_MS);
    }

    /** [⛶]: off when on; else on, or the screen change for a window bigger than the screen. */
    void toggle() {
        if (on) {
            set(false);
            AppUtils.showToast(activity, R.string.agvn_fit_off);
            return;
        }
        float[] rect = mainWindow();
        String verdict = rect != null ? verdict(rect) : null;
        if (AgvnFitMath.OVERFLOW.equals(verdict) || verdict != null && !canDraw()) {
            offerScreen(rect, verdict);
        } else if (!canDraw()) {
            AppUtils.showToast(activity, R.string.agvn_fit_unsupported);
        } else {
            set(true);
            AppUtils.showToast(activity, R.string.agvn_fit_on);
        }
    }

    private void poll() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            float[] rect = mainWindow();
            if (on) show(rect);
            else if (!asked && AgvnSessionTrack.started()) settle(rect);
        } catch (RuntimeException e) {
            Log.w(TAG, "screen fit check failed", e);
        }
        handler.postDelayed(poll, POLL_MS);
    }

    /** The game's window has stayed put for {@link #STABLE_POLLS} checks: a small frame is fitted, a big one offered. */
    private void settle(float[] rect) {
        stable = rect != null && Arrays.equals(rect, last) ? stable + 1 : 0;
        last = rect;
        if (stable < STABLE_POLLS) return;
        String verdict = verdict(rect);
        if (verdict == null) return; // suits the screen; a window that changes later is checked again
        asked = true;
        AgvnSessionLog.event("Khung game " + size(rect) + " trên màn hình " + screenW() + "x" + screenH() + ": " + verdict);
        if (AgvnFitMath.SMALL.equals(verdict) && canDraw()) {
            set(true);
            AgvnWarningBar.show(activity, activity.getString(R.string.agvn_fit_small_title),
                    activity.getString(R.string.agvn_fit_small_detail, size(rect), size(screenW(), screenH())),
                    new AgvnWarningBar.Choice(R.string.agvn_fit_keep, null),
                    new AgvnWarningBar.Choice(R.string.agvn_fit_undo, () -> set(false)));
        } else {
            offerScreen(rect, verdict);
        }
    }

    /** A screen the window's size, so all of it shows (and gets touches), at the next start. */
    private void offerScreen(float[] rect, String verdict) {
        boolean big = AgvnFitMath.OVERFLOW.equals(verdict);
        String detail = activity.getString(big ? R.string.agvn_fit_overflow_detail : R.string.agvn_fit_small_screen_detail,
                size(rect), size(screenW(), screenH())) + "\n" + activity.getString(R.string.agvn_doctor_lead);
        String screen = AgvnFitMath.screenFor((int) rect[2], (int) rect[3]);
        String label = activity.getString(R.string.agvn_fit_resize, screen.replace('x', '×'));
        AgvnWarningBar.show(activity, activity.getString(big ? R.string.agvn_fit_overflow_title : R.string.agvn_fit_small_title),
                detail, new AgvnWarningBar.Choice(label, () -> resize(activity, screen, label)),
                new AgvnWarningBar.Choice(R.string.agvn_doctor_keep, () -> keep("0")));
    }

    /** The game gets {@code screen} at its next start, now or later ({@link AgvnSlowBar#offerRestart}). */
    static void resize(XServerDisplayActivity activity, String screen, String label) {
        Shortcut s = activity.agvnShortcut();
        if (s == null) return;
        s.putExtra("screenSize", screen);
        s.putExtra(EXTRA_FIT, null); // checked again on the new screen
        s.saveData();
        Log.i(TAG, "screen fit: screen " + screen);
        AgvnSlowBar.offerRestart(activity, s, label, activity::agvnExit);
    }

    private void set(boolean fit) {
        on = fit;
        keep(fit ? "1" : "0");
        show(fit ? mainWindow() : null);
    }

    private void keep(String value) {
        Shortcut s = activity.agvnShortcut();
        if (s == null) return;
        s.putExtra(EXTRA_FIT, value);
        s.saveData();
    }

    /** Draws and maps touches onto {@code rect} (null: the whole screen, as usual). */
    private void show(float[] rect) {
        if (Arrays.equals(rect, shown)) return;
        shown = rect;
        if (activity.getXServerView() instanceof VulkanXServerView)
            ((VulkanXServerView) activity.getXServerView()).setAgvnFitRect(rect);
        TouchpadView touch = activity.agvnTouchpadView();
        if (touch != null) touch.setAgvnFitRect(rect);
    }

    /** The game's own window ({@link AgvnGameWindow}), {x, y, width, height}, or null while it has none. */
    private float[] mainWindow() {
        XServer xServer = activity.getXServer();
        if (xServer == null) return null;
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            Window best = AgvnGameWindow.find(xServer.windowManager.rootWindow);
            return best == null ? null : new float[]{best.getRootX(), best.getRootY(), best.getWidth(), best.getHeight()};
        }
    }

    private String verdict(float[] rect) {
        return AgvnFitMath.verdict((int) rect[0], (int) rect[1], (int) rect[2], (int) rect[3], screenW(), screenH());
    }

    private boolean canDraw() {
        return activity.getXServerView() instanceof VulkanXServerView;
    }

    private int screenW() {
        return activity.getXServer().screenInfo.width;
    }

    private int screenH() {
        return activity.getXServer().screenInfo.height;
    }

    private static String size(float[] rect) {
        return size((int) rect[2], (int) rect[3]);
    }

    private static String size(int w, int h) {
        return w + "×" + h;
    }
}
