/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.winlator.cmod.R;

/**
 * A small line of text at the top right of the game: that "Bật debug Wine" is on, how a slow start is going
 * ({@link AgvnStartupProgress}), and work the app does for the game meanwhile ({@link #setWork}: installing Proton 10).
 * It takes no touches, so the game under it works as before. Call on the UI thread.
 */
public final class AgvnStatusLine {
    private final Activity activity;
    private String debug = "", progress = "", work = "";
    private TextView view;

    public AgvnStatusLine(Activity activity) {
        this.activity = activity;
    }

    public void setDebug(String text) {
        debug = text != null ? text : "";
        render();
    }

    /**
     * Back on the game: "Bật debug Wine" turned off while it ran takes the notice away. Wine keeps the log it started
     * with until the game restarts, which a toast says once.
     */
    public void followDebugSetting(boolean on) {
        if (on || debug.isEmpty()) return;
        setDebug(null);
        Toast.makeText(activity, R.string.agvn_debug_off_next_start, Toast.LENGTH_LONG).show();
    }

    public void setProgress(String text) {
        progress = text != null ? text : "";
        render();
    }

    /** Work the app does for the game ("Đang cài Wine mới… 42%"), above the rest; null when done. */
    public void setWork(String text) {
        work = text != null ? text : "";
        render();
    }

    private void render() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String text = join(work, join(progress, debug));
        if (text.isEmpty()) {
            if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            view = null;
            return;
        }
        if (view == null) {
            view = new TextView(activity);
            view.setTextSize(12);
            view.setTextColor(0xF2FFFFFF);
            view.setMaxLines(3);
            int pad = dp(6);
            view.setPadding(pad, pad / 2, pad, pad / 2);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x99000000);
            bg.setCornerRadius(dp(6));
            view.setBackground(bg);
            view.setClickable(false);
            view.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            lp.topMargin = dp(8);
            lp.rightMargin = dp(8);
            lp.setMarginEnd(dp(8));
            activity.addContentView(view, lp);
        }
        view.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String join(String first, String second) {
        return first.isEmpty() ? second : second.isEmpty() ? first : first + "\n" + second;
    }
}
