/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The warning bar at the top of the game (RAM, heat): a title, a detail and buttons. A bar, not a dialog, so the game
 * keeps running under it. One at a time: a new warning replaces the one shown. Call on the UI thread.
 */
final class AgvnWarningBar {
    private static final String TAG = "agvn_warning_bar";

    /** A button; every button also closes the bar. */
    static final class Choice {
        final int label;
        final Runnable action;

        Choice(int label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    private AgvnWarningBar() {}

    static void show(Activity activity, String title, String detail, Choice... choices) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        hide(activity);
        int pad = dp(activity, 12);
        LinearLayout box = new LinearLayout(activity);
        box.setTag(TAG);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad / 2);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2332200);
        bg.setCornerRadius(dp(activity, 10));
        box.setBackground(bg);
        box.addView(text(activity, title, true));
        box.addView(text(activity, detail, false));
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END);
        for (Choice c : choices) {
            Button b = new Button(activity, null, android.R.attr.borderlessButtonStyle);
            b.setText(c.label);
            b.setTextColor(0xFFFFD27A);
            b.setOnClickListener(v -> {
                hide(activity);
                if (c.action != null) c.action.run();
            });
            buttons.addView(b);
        }
        box.addView(buttons);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(activity, 16);
        activity.addContentView(box, lp);
    }

    static void hide(Activity activity) {
        View bar = activity.getWindow().getDecorView().findViewWithTag(TAG);
        if (bar != null && bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
    }

    private static TextView text(Activity activity, String s, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(bold ? 16 : 14);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(activity, 4));
        return t;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
