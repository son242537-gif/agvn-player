/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.R;

/**
 * Tucks the ⌨ ✎ 👁 bar away after {@link #IDLE_MS} without a tap, leaving a thin faint line at the top edge; tapping
 * the line brings the bar back for another {@link #IDLE_MS}. A tucked bar is View.GONE, so touches where it was reach
 * the game; only the small line (about 80×18 dp at the very top) still takes touches.
 */
final class AgvnBarAutoHide {
    static final long IDLE_MS = 3000;
    private static final long ANIM_MS = 180;

    private final View bar;
    private final View handle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tuck = this::tuck;
    /** False while the controls editor or the sidebar menu owns the screen: neither the bar nor the line shows. */
    private boolean enabled = true;

    @SuppressLint("ClickableViewAccessibility") // the touch listener only restarts the countdown; clicks still happen
    AgvnBarAutoHide(Activity activity, ViewGroup bar) {
        this.bar = bar;
        float dp = activity.getResources().getDisplayMetrics().density;
        handle = makeHandle(activity, dp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (80 * dp), (int) (18 * dp),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        activity.addContentView(handle, lp);
        handle.setVisibility(View.GONE);
        handle.setOnClickListener(v -> reveal());
        // any touch on a bar button keeps the bar up for another IDLE_MS
        for (int i = 0; i < bar.getChildCount(); i++) {
            bar.getChildAt(i).setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_UP) poke();
                return false;
            });
        }
    }

    /** Shows the bar (hides the line) and tucks it again after {@link #IDLE_MS} without a tap. */
    void reveal() {
        enabled = true;
        handler.removeCallbacks(tuck);
        handle.setVisibility(View.GONE);
        bar.animate().cancel();
        if (bar.getVisibility() != View.VISIBLE) {
            bar.setAlpha(0f);
            bar.setTranslationY(-bar.getHeight() / 2f);
            bar.setVisibility(View.VISIBLE);
        }
        bar.animate().alpha(1f).translationY(0f).setDuration(ANIM_MS).start();
        handler.postDelayed(tuck, IDLE_MS);
    }

    /** Restarts the idle countdown while the bar is up. */
    void poke() {
        if (!enabled || bar.getVisibility() != View.VISIBLE) return;
        handler.removeCallbacks(tuck);
        handler.postDelayed(tuck, IDLE_MS);
    }

    /** Hides the bar and the line at once (controls editor, sidebar menu); {@link #reveal()} undoes it. */
    void suspend() {
        enabled = false;
        handler.removeCallbacks(tuck);
        bar.animate().cancel();
        bar.setAlpha(1f);
        bar.setTranslationY(0f);
        bar.setVisibility(View.GONE);
        handle.setVisibility(View.GONE);
    }

    private void tuck() {
        if (!enabled || bar.getVisibility() != View.VISIBLE) return;
        bar.animate().cancel();
        bar.animate().alpha(0f).translationY(-bar.getHeight() / 2f).setDuration(ANIM_MS).withEndAction(() -> {
            if (!enabled) return;
            bar.setVisibility(View.GONE); // GONE: touches where the bar was go to the game
            bar.setAlpha(1f);
            bar.setTranslationY(0f);
            handle.setAlpha(0f);
            handle.setVisibility(View.VISIBLE);
            handle.animate().alpha(1f).setDuration(ANIM_MS).start();
        }).start();
    }

    /** A transparent 80×18 dp touch area with a faint 40×4 dp pill near the top edge. */
    private static View makeHandle(Activity activity, float dp) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(2 * dp);
        pill.setColor(0x80FFFFFF);
        pill.setStroke(Math.max(1, (int) dp), 0x40000000);
        View line = new View(activity);
        line.setBackground(pill);
        FrameLayout handle = new FrameLayout(activity);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (40 * dp), (int) (4 * dp),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (5 * dp);
        handle.addView(line, lp);
        handle.setContentDescription(activity.getString(R.string.agvn_bar_reveal));
        return handle;
    }
}
