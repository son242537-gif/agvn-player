/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.SystemClock;

import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * A camera that turns with the mouse, turned by dragging a finger. A 3D game that holds the cursor (Unity's
 * CursorLockMode.Locked, Unreal capturing the mouse) reads how far the mouse moved, not where it is. A touch was sent
 * where the finger is, so the game got, at every move, the distance from the held point to the finger: Romantic
 * Escape (Unity 2022.3, Adreno 830, 27/10/2026) clipped the cursor to (640,360) and its camera did not follow the
 * finger. While the game holds the cursor, a finger's move goes to the game as that movement instead: as SendInput
 * by agvn-winhandler.exe (raw input, DirectInput and the cursor's messages alike), else from where the game put the X
 * pointer. A game holds the cursor when agvn-winhandler.exe says so (the cursor hidden and clipped,
 * {@link #reported}), or when it puts the pointer back on one point again and again ({@link #warped}), as games that
 * read the mouse through the cursor do; such a game holds it until it shows its cursor again, not only while it warps:
 * it puts the pointer back only after a move, so the first touch after a pause jumped to the finger and the camera
 * turned toward it. While the game holds the cursor, a finger's press waits ({@link #pressLater}): a drag turns the
 * camera and clicks nothing, a finger lifted where it went down clicks. Pure Java but the sending.
 */
public final class AgvnMouseLook {
    /**
     * RC_AGVN_STATE's bits: the game holds the cursor, a text caret of Windows shows (agvn-winhandler.exe 2), the game
     * turned on its own text input (5: SDL's and Ren'Py's, Unity's, Unreal's, Godot's fields).
     */
    public static final int LOCKED = 1, TEXT = 2, TYPING = 4;
    /** A move after this long without one starts a new drag: the finger went down somewhere else. */
    static final long NEW_DRAG_MS = 250;
    /** This many warps to one point, each within {@link #WARP_WINDOW_MS} of the last: the game holds the pointer there. */
    static final int WARPS = 3;
    static final long WARP_WINDOW_MS = 500, WARP_HOLD_MS = 1000;

    private final XServer xServer;
    private final BooleanSupplier cursorHidden;
    private final LongSupplier clock;
    private volatile int reported;
    private volatile IntConsumer onText;
    private int lastX, lastY, warpX, warpY, warpCount;
    private boolean dragging;
    private long lastMoveMs, warpLastMs = -WARP_HOLD_MS - 1;
    private boolean told;
    /** The game warped the pointer to one point as it holds it, and has not shown its cursor since. */
    private boolean warpHeld;
    /** A finger's press waiting to know whether it is a tap or a drag ({@link #pressLater}), and how far it went. */
    private Pointer.Button heldPress;
    private int travel;

    public AgvnMouseLook(XServer xServer) {
        this.xServer = xServer;
        cursorHidden = this::gameCursorHidden;
        clock = SystemClock::uptimeMillis;
    }

    /** For the tests: {@code cursorHidden} says whether the game hides its cursor, {@code clock} the time. */
    AgvnMouseLook(BooleanSupplier cursorHidden, LongSupplier clock) {
        this.xServer = null;
        this.cursorHidden = cursorHidden;
        this.clock = clock;
    }

    /** What agvn-winhandler.exe reports (RC_AGVN_STATE); a text field coming or going is told to {@link #onText}. */
    public void reported(int state) {
        int before = reported;
        reported = state;
        IntConsumer text = onText;
        if (text != null && ((before ^ state) & (TEXT | TYPING)) != 0) text.accept(state & (TEXT | TYPING));
    }

    /**
     * Told when a text field of the game takes typing (TEXT, TYPING or both) or no longer does (0): the keyboard,
     * {@link AgvnCaretKeyboard}.
     */
    public void setOnText(IntConsumer onText) {
        this.onText = onText;
    }

    /** The game put the X pointer at (x, y) (its warp, WindowRequests.warpPointer). */
    public synchronized void warped(int x, int y) {
        long now = clock.getAsLong();
        warpCount = warps(warpCount, x == warpX && y == warpY, now - warpLastMs);
        warpX = x;
        warpY = y;
        warpLastMs = now;
        if (warpCount >= WARPS) warpHeld = true;
    }

    /** True while the game holds the cursor. */
    public synchronized boolean locked() {
        if (warpHeld && !cursorHidden.getAsBoolean()) warpHeld = false; // its cursor shows: a menu, the mouse is let go
        return warpHeld || locked(reported, warpCount, clock.getAsLong() - warpLastMs);
    }

    /** Whether the game shows no cursor where the pointer is (Wine's empty cursor for a hidden one). */
    private boolean gameCursorHidden() {
        if (xServer == null) return false;
        Window window = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = window != null ? window.attributes.getCursor() : null;
        return cursor != null && !cursor.isVisible();
    }

    /**
     * A finger's press of {@code button} (TouchpadView): false when it goes now; true while the game holds the cursor,
     * when it waits for the finger to lift ({@link #takePress}). A drag of the finger drops it: the camera turns, and
     * nothing is clicked.
     */
    public synchronized boolean pressLater(Pointer.Button button) {
        heldPress = null; // one a cancelled touch left
        if (!locked()) return false;
        heldPress = button;
        travel = 0;
        return true;
    }

    /** The finger lifted: its held press if it did not drag (a tap, which clicks then), else null. */
    public synchronized Pointer.Button takePress() {
        Pointer.Button button = heldPress;
        heldPress = null;
        return button;
    }

    /** The farthest a tap moves, in the X screen's pixels: a 40th of its short side. */
    private int tapSlop() {
        if (xServer == null) return 16;
        return Math.max(8, Math.min(xServer.screenInfo.width, xServer.screenInfo.height) / 40);
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
        long now = clock.getAsLong();
        boolean start = !dragging || now - lastMoveMs > NEW_DRAG_MS;
        int dx = start ? 0 : x - lastX, dy = start ? 0 : y - lastY;
        dragging = true;
        lastX = x;
        lastY = y;
        lastMoveMs = now;
        if (heldPress != null && (travel += Math.abs(dx) + Math.abs(dy)) > tapSlop()) heldPress = null; // a drag
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
        if (xServer == null) return; // the tests
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
