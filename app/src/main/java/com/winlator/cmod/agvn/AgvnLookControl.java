/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;

import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.xserver.XServer;

/**
 * An on-screen control set to turn the camera as it is dragged ("Xoay camera khi kéo", the controls profile's
 * "agvnLook"). A button so set presses its keys and, held, turns the camera with the finger, as the fire button of a
 * phone shooter does: in the 3D layout Bắn and Ngắm, which held the finger and turned nothing. A camera zone (a
 * trackpad so set) turns it whether or not the game is seen to hold the cursor: a game that turns its camera with
 * the mouse but neither hides nor holds the cursor got the finger's place, and its camera jumped; a tap on the zone
 * clicks where it went down, as a tap off the controls does. One per control; on the UI thread.
 */
public final class AgvnLookControl {
    /** A tap: the finger lifted this soon (TouchpadView.MAX_TAP_MILLISECONDS)... */
    static final long TAP_MS = 200;

    private float startX, startY, lastX, lastY, travel;
    private long downMs;

    /** The finger went down at (x, y) of the controls' view. */
    public void down(float x, float y) {
        startX = lastX = x;
        startY = lastY = y;
        travel = 0;
        downMs = SystemClock.uptimeMillis();
    }

    /** The finger moved to (x, y): the camera turns by as much ({@link AgvnLook#zone}). */
    public void move(InputControlsView view, float x, float y) {
        float dx = x - lastX, dy = y - lastY;
        lastX = x;
        lastY = y;
        travel += Math.abs(dx) + Math.abs(dy);
        XServer xServer = view.getXServer();
        if (xServer != null && (dx != 0 || dy != 0)) xServer.agvnLook.zone(dx, dy, view.getWidth(), view.getHeight());
    }

    /** The finger of a camera zone lifted: a tap clicks where it went down. */
    public void tapUp(InputControlsView view) {
        TouchpadView touchpad = view.getTouchpadView();
        if (touchpad != null && isTap(SystemClock.uptimeMillis() - downMs, travel, slop(view))) {
            touchpad.agvnTapAt(startX, startY);
        }
    }

    /** ...and moved no more than {@code slop} pixels. */
    static boolean isTap(long heldMs, float travel, float slop) {
        return heldMs < TAP_MS && travel <= slop;
    }

    /** A 40th of the screen's short side, as the camera's taps ({@link AgvnLook#finger}). */
    private static float slop(InputControlsView view) {
        return Math.max(8, Math.min(view.getWidth(), view.getHeight()) / 40f);
    }
}
