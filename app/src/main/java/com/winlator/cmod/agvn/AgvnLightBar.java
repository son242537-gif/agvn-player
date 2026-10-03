/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.cmod.R;

/**
 * The bar at the top centre of a "Chạy nhẹ" game, as on Windows games ({@link AgvnControlsBar}): [⌨] the Android
 * keyboard, [✎ Sửa] moves the on-screen keys, [👁 Ẩn / 👁 Hiện] hides or shows them, and [☰] opens the game's
 * menu. It tucks itself away after 3 s without a tap ({@link AgvnBarAutoHide}).
 */
final class AgvnLightBar {
    private final LinearLayout bar;
    private final TextView eye;
    private final AgvnBarAutoHide autoHide;

    AgvnLightBar(AgvnLightTools tools) {
        float dp = tools.activity.getResources().getDisplayMetrics().density;
        bar = new LinearLayout(tools.activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(AgvnBarButton.make(tools.activity, "⌨", 18, R.string.agvn_bar_keyboard, v -> tools.host.showKeyboard()));
        bar.addView(AgvnBarButton.make(tools.activity, tools.activity.getString(R.string.agvn_bar_edit_label), 14,
                R.string.agvn_bar_edit, v -> tools.edit()));
        eye = AgvnBarButton.make(tools.activity, tools.activity.getString(R.string.agvn_bar_hide_label), 14,
                R.string.agvn_bar_toggle, v -> tools.toggleKeys());
        bar.addView(eye);
        bar.addView(AgvnBarButton.make(tools.activity, "☰", 18, R.string.agvn_light_menu, v -> tools.openMenu()));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (6 * dp);
        tools.activity.addContentView(bar, lp);
        autoHide = new AgvnBarAutoHide(tools.activity, bar);
        autoHide.reveal(); // up for the first seconds of the game, then tucked away
    }

    /** [👁] says what a tap does: hide the keys while they show, show them while hidden. */
    void update(boolean keysShown) {
        ((GradientDrawable) eye.getBackground()).setColor(keysShown ? AgvnBarButton.BG_NORMAL : AgvnBarButton.BG_HIDDEN);
        eye.setText(keysShown ? R.string.agvn_bar_hide_label : R.string.agvn_bar_show_label);
        eye.setContentDescription(eye.getContext().getString(keysShown ? R.string.agvn_bar_hide : R.string.agvn_bar_show));
    }

    /** Hidden while the menu or the key editor is on screen. */
    void suspend() {
        autoHide.suspend();
    }

    void reveal() {
        autoHide.reveal();
    }
}
