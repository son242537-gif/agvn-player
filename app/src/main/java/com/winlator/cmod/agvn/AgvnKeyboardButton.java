/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.core.AppUtils;

/** Small always-visible "⌨" button at the top centre of the game screen that opens the full Android keyboard. */
public final class AgvnKeyboardButton {
    private AgvnKeyboardButton() {}

    public static void attach(AppCompatActivity activity) {
        float dp = activity.getResources().getDisplayMetrics().density;
        TextView button = new TextView(activity);
        button.setText("⌨");
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setAlpha(0.6f);
        button.setContentDescription("Bàn phím");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99000000);
        bg.setCornerRadius(10 * dp);
        button.setBackground(bg);
        button.setOnClickListener(v -> AppUtils.showKeyboard(activity));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (48 * dp), (int) (36 * dp), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (6 * dp);
        activity.addContentView(button, lp);
    }
}
