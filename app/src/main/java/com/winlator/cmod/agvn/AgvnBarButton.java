/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.cmod.core.AppUtils;

/**
 * A button of the bars at the top of a game (⌨ ✎ 👁 on Windows games, also ☰ on "Chạy nhẹ" games, and the editors'
 * bars): a glyph, or a glyph and a short name, on a dark see-through pill; its full name is read out and shown on a
 * long press. The background is a GradientDrawable, so a bar can recolour it ({@link #BG_NORMAL}, {@link #BG_HIDDEN}).
 */
final class AgvnBarButton {
    static final int BG_NORMAL = 0x99000000, BG_HIDDEN = 0x99b71c1c;

    private AgvnBarButton() {}

    static TextView make(Activity activity, String text, int sp, int nameRes, View.OnClickListener onClick) {
        float dp = activity.getResources().getDisplayMetrics().density;
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        button.setSingleLine(true);
        button.setMinWidth((int) (48 * dp));
        button.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setAlpha(0.6f);
        button.setContentDescription(activity.getString(nameRes));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG_NORMAL);
        bg.setCornerRadius(10 * dp);
        button.setBackground(bg);
        button.setOnClickListener(onClick);
        button.setOnLongClickListener(v -> {
            AppUtils.showToast(activity, v.getContentDescription().toString());
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (36 * dp));
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        button.setLayoutParams(lp);
        return button;
    }
}
