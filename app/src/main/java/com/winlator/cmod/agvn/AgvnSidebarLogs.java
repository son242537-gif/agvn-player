/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;

/**
 * "Gửi nhật ký" in a Windows game's sidebar, under "Xem log" (HUD panel), with or without the log settings on: the
 * game's logs as they are now, while it keeps running ({@link AgvnLogShare}). Styled as the sidebar's own buttons.
 */
public final class AgvnSidebarLogs {
    private AgvnSidebarLogs() {}

    /** Called once after the sidebar is set up. */
    public static void attach(XServerDisplayActivity activity) {
        View logs = activity.findViewById(R.id.BTItemLogs);
        Shortcut shortcut = activity.agvnShortcut();
        if (logs == null || shortcut == null || !(logs.getParent() instanceof ViewGroup)) return;
        float dp = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundResource(R.drawable.sidebar_action);
        row.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_sidebar_logs);
        icon.setColorFilter(0xFFDDF6FF);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) (24 * dp), (int) (24 * dp));
        iconLp.setMarginEnd((int) (8 * dp));
        row.addView(icon, iconLp);
        TextView text = new TextView(activity);
        text.setText(R.string.agvn_logs_send);
        text.setTextColor(0xFFDDF6FF);
        text.setTextSize(14);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(text);
        row.setOnClickListener(v -> {
            View drawer = activity.findViewById(R.id.DrawerLayout);
            if (drawer instanceof DrawerLayout) ((DrawerLayout) drawer).closeDrawers();
            AgvnLogShare.share(activity, shortcut);
        });
        ViewGroup parent = (ViewGroup) logs.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * dp));
        lp.topMargin = (int) (12 * dp);
        parent.addView(row, parent.indexOfChild(logs) + 1, lp);
    }
}
