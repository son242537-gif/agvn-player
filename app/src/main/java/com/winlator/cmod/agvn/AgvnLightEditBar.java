/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.winlator.cmod.R;

/**
 * The bar while the keys of a "Chạy nhẹ" game are being moved ({@link AgvnLightTools#edit}): [－] [＋] resize the
 * selected key, [Mặc định] puts every key back, [Xong] keeps the places for every game of this type. Back is "Xong".
 */
final class AgvnLightEditBar {
    private final LinearLayout bar;

    AgvnLightEditBar(AgvnLightTools tools) {
        float dp = tools.activity.getResources().getDisplayMetrics().density;
        bar = new LinearLayout(tools.activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(AgvnBarButton.make(tools.activity, tools.activity.getString(R.string.agvn_edit_smaller), 18,
                R.string.agvn_edit_smaller_desc, v -> tools.resizeSelected(false)));
        bar.addView(AgvnBarButton.make(tools.activity, tools.activity.getString(R.string.agvn_edit_bigger), 18,
                R.string.agvn_edit_bigger_desc, v -> tools.resizeSelected(true)));
        bar.addView(AgvnBarButton.make(tools.activity, tools.activity.getString(R.string.agvn_light_reset), 14,
                R.string.agvn_light_reset_desc, v -> tools.resetKeys()));
        bar.addView(AgvnBarButton.make(tools.activity, tools.activity.getString(R.string.agvn_edit_done), 14,
                R.string.agvn_edit_done, v -> tools.finishEdit()));
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setAlpha(0.9f); // easy to see while editing
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (6 * dp);
        tools.activity.addContentView(bar, lp);
    }

    void remove() {
        if (bar.getParent() instanceof ViewGroup) ((ViewGroup) bar.getParent()).removeView(bar);
    }
}
