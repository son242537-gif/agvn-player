/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Toolbar of the in-game controls editor, at the top (or bottom) centre of the game screen. The tools sit in a row that
 * scrolls sideways, with faded edges when it does not fit (narrow screen, large font); [Xong] is pinned outside that
 * row on the right, so the way out of editing is always visible.
 */
final class AgvnEditToolbar {
    private static final int BUTTON_BG = 0xff2a3038, DONE_BG = 0xff2184ff, BAR_BG = 0xcc101418;

    private final Activity activity;
    private final float dp;
    private final LinearLayout tools;
    private final LinearLayout root;

    AgvnEditToolbar(Activity activity, int doneRes, View.OnClickListener onDone) {
        this.activity = activity;
        dp = activity.getResources().getDisplayMetrics().density;
        tools = new LinearLayout(activity);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(true); // a faded edge shows that more tools are there
        scroll.setFadingEdgeLength((int) (32 * dp));
        scroll.addView(tools);
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding((int) (4 * dp), (int) (4 * dp), (int) (4 * dp), (int) (4 * dp));
        // wrap_content + weight: the tools row shrinks (and scrolls) when the screen is too narrow, [Xong] keeps its size
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView done = button(doneRes, 0, onDone);
        ((GradientDrawable) done.getBackground()).setColor(DONE_BG);
        root.addView(done);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BAR_BG);
        bg.setCornerRadius(12 * dp);
        root.setBackground(bg);
        root.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = lp.bottomMargin = (int) (4 * dp);
        activity.addContentView(root, lp);
    }

    /** Adds a tool button to the scrolling row. */
    AgvnEditToolbar tool(int textRes, int descRes, View.OnClickListener onClick) {
        tools.addView(button(textRes, descRes, onClick));
        return this;
    }

    void setVisible(boolean visible) {
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Moves the toolbar between the top and the bottom edge, to reach controls under it. */
    void flip() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) root.getLayoutParams();
        boolean top = (lp.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP;
        lp.gravity = (top ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
        root.setLayoutParams(lp);
    }

    private TextView button(int textRes, int descRes, View.OnClickListener onClick) {
        TextView b = new TextView(activity);
        b.setText(textRes);
        if (descRes != 0) b.setContentDescription(activity.getString(descRes));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinWidth((int) (48 * dp));
        b.setMinHeight((int) (44 * dp));
        b.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BUTTON_BG);
        bg.setCornerRadius(8 * dp);
        b.setBackground(bg);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (44 * dp));
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        b.setLayoutParams(lp);
        return b;
    }
}
