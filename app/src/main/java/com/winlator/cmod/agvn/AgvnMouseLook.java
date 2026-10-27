/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;

import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.function.IntConsumer;

/**
 * A camera that turns with the mouse, turned by dragging a finger. A 3D game that holds the cursor (Unity's
 * CursorLockMode.Locked, Unreal capturing the mouse) reads how far the mouse moved, not where it is. A touch was sent
 * where the finger is, so the game got, at every move, the distance from the held point to the finger: Romantic
 * Escape (Unity 2022.3, Adreno 830, 27/10/2026) clipped the cursor to (640,360) and its camera did not follow the
 * finger. While the game holds the cursor, a finger's move goes to the game as that movement instead: as SendInput
 * by agvn-winhandler.exe (raw input, DirectInput and the cursor's messages alike), else from where the game put the X
 * pointer. A game holds the cursor when agvn-winhandler.exe says so (the cursor hidden and clipped,
 * {@link #reported}), or when it puts the pointer back on one point again and again ({@link #warped}), as games that
 * read the mouse through the cursor do. Taps and the rest stay as they were. Pure Java but the sending.
 */
public final class AgvnMouseLook {
    /** RC_AGVN_STATE's bits (agvn-winhandler.exe 2). */
    public static final int LOCKED = 1, TEXT = 2;
    /** A move after this long without one starts a new drag: the finger went down somewhere else. */
    static final long NEW_DRAG_MS = 250;
    /** This many warps to one point, each within {@link #WARP_WINDOW_MS} of the last: the game holds the pointer there. */
    static final int WARPS = 3;
    static final long WARP_WINDOW_MS = 500, WARP_HOLD_MS = 1000;

    private final XServer xServer;
    private volatile int reported;
    private volatile IntConsumer onText;
    private int lastX, lastY, warpX, warpY, warpCount;
    private boolean dragging;
    private long lastMoveMs, warpLastMs = -WARP_HOLD_MS - 1;
    private boolean told;

    public AgvnMouseLook(XServer xServer) {
        this.xServer = xServer;
    }

    /** What agvn-winhandler.exe reports (RC_AGVN_STATE); a caret coming or going is told to {@link #onText}. */
    public void reported(int state) {
        int before = reported;
        reported = state;
        IntConsumer text = onText;
        if (text != null && ((before ^ state) & TEXT) != 0) text.accept(state & TEXT);
    }

    /** Told when a text caret shows (TEXT) or goes (0) in the game (XServerDisplayActivity: the keyboard). */
    public void setOnText(IntConsumer onText) {
        this.onText = onText;
    }

    /** The game put the X pointer at (x, y) (its warp, WindowRequests.warpPointer). */
    public synchronized void warped(int x, int y) {
        long now = SystemClock.uptimeMillis();
        warpCount = warps(warpCount, x == warpX && y == warpY, now - warpLastMs);
        warpX = x;
        warpY = y;
        warpLastMs = now;
    }

    /** True while the game holds the cursor. */
    public synchronized boolean locked() {
        return locked(reported, warpCount, SystemClock.uptimeMillis() - warpLastMs);
    }

    /** The warps to one point in a row after one more ({@code same}: to the last one's point, {@code sinceMs} after it). */
    static int warps(int count, boolean same, long sinceMs) {
        return same && sinceMs <= WARP_WINDOW_MS ? count + 1 : 1;
    }

    static boolean locked(int reported, int warps, long sinceWarpMs) {
        return (reported & LOCKED) != 0 || (warps >= WARPS && sinceWarpMs <= WARP_HOLD_MS);
    }

    /**
     * A touch at (x, y) (XServer.injectPointerMove): while the game holds the cursor, the movement since the last one
     * goes to the game, the first of a drag none; true then, and the pointer is not moved there.
     */
    public synchronized boolean move(int x, int y) {
        if (!locked()) {
            dragging = false;
            return false;
        }
        long now = SystemClock.uptimeMillis();
        boolean start = !dragging || now - lastMoveMs > NEW_DRAG_MS;
        int dx = start ? 0 : x - lastX, dy = start ? 0 : y - lastY;
        dragging = true;
        lastX = x;
        lastY = y;
        lastMoveMs = now;
        if (dx != 0 || dy != 0) by(dx, dy);
        return true;
    }

    /** A movement (XServer.injectPointerMoveDelta: a stick set to the mouse): to the game as one; false when not held. */
    public synchronized boolean moveBy(int dx, int dy) {
        if (!locked()) return false;
        if (dx != 0 || dy != 0) by(dx, dy);
        return true;
    }

    /** A button let go: the next move starts a new drag. */
    public synchronized void released() {
        dragging = false;
    }

    private void by(int dx, int dy) {
        WinHandler winHandler = xServer.getWinHandler();
        boolean sendInput = winHandler != null && winHandler.agvnRelativeReady();
        if (sendInput) winHandler.agvnPointerBy(dx, dy);
        else {
            try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.INPUT_DEVICE)) {
                xServer.pointer.setPosition(xServer.pointer.getX() + dx, xServer.pointer.getY() + dy);
            }
        }
        if (!told) {
            told = true;
            AgvnSessionLog.event("Game giữ chuột (camera xoay theo chuột): kéo ngón tay gửi độ dịch chuyển của ngón "
                    + (sendInput ? "qua " + AgvnRawMouse.EXE + " (SendInput)" : "bằng sự kiện X, từ chỗ game giữ con trỏ"));
        }
    }
}
