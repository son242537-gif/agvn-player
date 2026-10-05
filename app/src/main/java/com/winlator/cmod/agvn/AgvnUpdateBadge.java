/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

/**
 * Whether a newer AGVN Player is published, as the last check found it (when the app opens, "Kiểm tra cập nhật", or
 * in the background, {@link AgvnUpdateJob}). It puts a red dot on "Cài đặt" (the bottom bar, the landscape bar, the
 * library's top icons) and on "Cập nhật ứng dụng"; the dot goes once the phone has that version.
 */
public final class AgvnUpdateBadge {
    public static final String PREF_FOUND_CODE = "agvn_update_found_code";
    static final String PREF_FOUND_NAME = "agvn_update_found_name";
    private static final String DOT = "agvnUpdateDot";
    private static final int RED = 0xFFE53935;

    private AgvnUpdateBadge() {}

    /** True while the newest version a check found is newer than this one. */
    public static boolean shown(Context ctx) {
        return prefs(ctx).getInt(PREF_FOUND_CODE, 0) > AgvnUpdater.installedCode(ctx);
    }

    /** A check's answer: {@code info} is the published version, null when none is published. */
    static void found(Context ctx, AgvnUpdateInfo info) {
        prefs(ctx).edit()
                .putInt(PREF_FOUND_CODE, info != null ? info.versionCode : 0)
                .putString(PREF_FOUND_NAME, info != null ? info.versionName : "")
                .apply();
    }

    /**
     * The dot on the bottom bar's "Cài đặt" (the Compose screens follow the setting by themselves). A plain view on the
     * item: Material's BadgeDrawable needs a Material theme, and the app's is AppCompat's.
     */
    public static void refresh(Activity activity) {
        View item = activity.findViewById(R.id.bottom_nav_settings);
        if (!(item instanceof FrameLayout)) return;
        ViewGroup group = (ViewGroup) item;
        View dot = group.findViewWithTag(DOT);
        boolean show = shown(activity);
        if (!show && dot != null) group.removeView(dot);
        if (!show || dot != null) return;
        float dp = activity.getResources().getDisplayMetrics().density;
        dot = new View(activity);
        dot.setTag(DOT);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(RED);
        dot.setBackground(circle);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(9 * dp), Math.round(9 * dp),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.leftMargin = Math.round(14 * dp); // at the top right of the gear
        lp.topMargin = Math.round(7 * dp);
        group.addView(dot, lp);
    }

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }
}
