/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * A tap on an overlay of the app (the HUD of FPS, CPU…) goes on to the game beneath it. The HUD took every tap to turn
 * itself sideways, so a game's buttons under it could not be pressed (a .NET game's menu at the top left, 07/11/2026).
 * The HUD still moves with a drag; it turns with a long press. UI thread.
 */
public final class AgvnTapThrough {
    /** How long the game's press lasts: a game that reads the mouse once a frame sees it down (30 FPS: 33 ms). */
    static final long PRESS_MS = 60;
    /** A press held this long on the HUD turns it, as a tap did. */
    public static final long LONG_PRESS_MS = 500;

    private AgvnTapThrough() {}

    /**
     * Gives {@code down} and {@code up} (events of {@code top}, kept by the caller: they are copied here) to the first
     * view beneath {@code top} in its parent that is visible under the finger and takes the press; the release follows
     * {@link #PRESS_MS} later. False when no view took it.
     */
    public static boolean pass(View top, MotionEvent down, MotionEvent up) {
        if (!(top.getParent() instanceof ViewGroup) || down == null || up == null) return false;
        ViewGroup parent = (ViewGroup) top.getParent();
        int[] at = new int[2], own = new int[2];
        top.getLocationOnScreen(own);
        for (int i = parent.indexOfChild(top) - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.getLocationOnScreen(at);
            float x = down.getRawX() - at[0], y = down.getRawY() - at[1];
            if (x < 0 || y < 0 || x >= child.getWidth() || y >= child.getHeight()) continue;
            MotionEvent press = MotionEvent.obtain(down);
            press.offsetLocation(own[0] - at[0], own[1] - at[1]);
            boolean took = child.dispatchTouchEvent(press);
            press.recycle();
            if (!took) continue;
            MotionEvent release = MotionEvent.obtain(up);
            release.offsetLocation(own[0] - at[0], own[1] - at[1]);
            child.postDelayed(() -> {
                child.dispatchTouchEvent(release);
                release.recycle();
            }, PRESS_MS);
            return true;
        }
        return false;
    }
}
