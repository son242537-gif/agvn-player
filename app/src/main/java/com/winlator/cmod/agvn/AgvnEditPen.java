/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.winlator.cmod.R;

/**
 * A small ✎ pinned to the top-left corner of the game screen, Windows or "Chạy nhẹ": one tap edits the on-screen keys.
 * Unlike the bar at the top, it never tucks itself away (the maintainer asked for it fixed there); it only steps aside
 * while the keys are being edited or a side menu is open. The HUDs start to its right ({@link #clearX}).
 */
public final class AgvnEditPen {
    private static final int SIZE_DP = 40, MARGIN_DP = 6, GAP_DP = 6;

    private final TextView pen;

    AgvnEditPen(Activity activity, Runnable edit) {
        pen = AgvnBarButton.make(activity, "✎", 17, R.string.agvn_bar_edit, v -> edit.run());
        pen.setMinWidth(0);
        pen.setPadding(0, 0, 0, 0);
        int size = px(activity, SIZE_DP), margin = px(activity, MARGIN_DP);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
        lp.leftMargin = lp.topMargin = margin;
        activity.addContentView(pen, lp);
        pen.post(() -> clearCutout(margin)); // the insets are known once the pen is on screen
    }

    void setVisible(boolean visible) {
        pen.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** A phone whose camera hole sits in the corner (the game drawn under it): the pen moves beside the hole. */
    private void clearCutout(int margin) {
        WindowInsets insets = pen.getRootWindowInsets();
        DisplayCutout cutout = insets != null ? insets.getDisplayCutout() : null;
        if (cutout == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pen.getLayoutParams();
        lp.leftMargin = margin + cutout.getSafeInsetLeft();
        lp.topMargin = margin + cutout.getSafeInsetTop();
        pen.setLayoutParams(lp);
    }

    /** Where a HUD in the top-left corner starts so the pen stays free, in pixels from the left edge. */
    public static float clearX(Context context) {
        return px(context, MARGIN_DP + SIZE_DP + GAP_DP);
    }

    /** Moves {@code hud}, laid out at the top-left of a FrameLayout, to the right of the pen. */
    public static void clearOf(View hud) {
        ViewGroup.LayoutParams lp = hud.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).leftMargin = Math.round(clearX(hud.getContext()));
            hud.setLayoutParams(lp);
        }
    }

    private static int px(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
