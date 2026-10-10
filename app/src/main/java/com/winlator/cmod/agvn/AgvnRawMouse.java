/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.widget.XServerRendererView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XServer;

import java.io.File;
import java.util.Arrays;

/**
 * Touches as a real mouse for games that take the mouse from raw input (WM_INPUT): sent as SendInput by
 * agvn-winhandler.exe (RC_AGVN_POINTER) instead of as the X server's pointer events. Wine makes WM_INPUT from
 * SendInput, not from the X server's core events (it has no XInput2), and Unity's Input System reads the mouse only
 * from raw input: Open At Nine (Unity 2020.3, menus on InputSystemUIInputModule) showed its title screen and took no
 * tap (08/10/2026). On for a Unity game with the Input System ({@link AgvnUnityInput}), or as "Tự sửa lỗi" set
 * the game ({@link #EXTRA}). Such a game starts through agvn-winhandler.exe, as does one whose start has letters
 * beyond ASCII ({@link AgvnStarter}, with X events); the others keep Winlator's winhandler.exe and X events. A press
 * goes back the way it went. {@link #wanted} and {@link #install} are pure.
 */
public final class AgvnRawMouse {
    /** "1" or "0", set by "Tự sửa lỗi"; none: on for a Unity game with the Input System. */
    public static final String EXTRA = "agvnRawMouse";
    /** "&lt;size&gt;@&lt;time&gt;=1" or "=0": what an IL2CPP game's metadata of that size and time said. */
    static final String EXTRA_SEEN = "agvnInputSystemSeen";
    /** The program that starts such a game, in the container's C:\windows. */
    public static final String EXE = "agvn-winhandler.exe", WINLATOR_EXE = "winhandler.exe";
    static final int WHEEL_STEP = 120;
    private static volatile String note;

    private final XServer xServer;
    private volatile boolean enabled;
    private int rawHeld; // buttons pressed through agvn-winhandler.exe

    public AgvnRawMouse(XServer xServer) {
        this.xServer = xServer;
    }

    /**
     * Before Wine starts: whether the game gets touches as a real mouse; if so agvn-winhandler.exe goes into the
     * container and the session log says why. Returns the program that starts the game.
     */
    public static String prepare(Context context, XServer xServer, Shortcut shortcut) {
        boolean on = false;
        String why = "";
        if (shortcut != null) {
            String extra = shortcut.getExtra(EXTRA);
            on = wanted(extra, extra.isEmpty() && inputSystem(shortcut));
            why = "1".equals(extra) ? "Tự sửa lỗi đã chọn cho game" : "game Unity đọc chuột bằng Input System";
            File windows = new File(shortcut.container.getRootDir(), ".wine/drive_c/windows");
            if (on && !install(FileUtils.read(context, "agvn/" + EXE), new File(windows, EXE))) on = false;
        }
        xServer.agvnRawMouse.enabled = on;
        note = on ? "Chạm gửi như chuột thật, có raw input (qua " + EXE + "): " + why : null;
        return on ? EXE : WINLATOR_EXE;
    }

    /** The line for the session's events, once; null when the game gets the X server's pointer events. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }

    /**
     * Whether the game has Unity's Input System ({@link AgvnUnityInput}). An IL2CPP game's metadata, tens of MB, is
     * read once and then while it changes, not at every start.
     */
    static boolean inputSystem(Shortcut s) {
        String path = AgvnExeRedirect.toUnixPath(s.path, s.container);
        File exe = path != null ? new File(path) : null;
        File metadata = AgvnUnityInput.il2cppMetadata(exe);
        if (metadata == null) return AgvnUnityInput.usesInputSystem(exe);
        String stamp = metadata.length() + "@" + metadata.lastModified() + "=";
        String seen = s.getExtra(EXTRA_SEEN);
        if (seen.startsWith(stamp)) return seen.equals(stamp + "1");
        boolean found = AgvnUnityInput.usesInputSystem(exe);
        s.putExtra(EXTRA_SEEN, stamp + (found ? "1" : "0"));
        s.saveData();
        return found;
    }

    /** The game's own setting ("1", "0") wins; without one, on for a game with the Input System. */
    static boolean wanted(String extra, boolean inputSystem) {
        return "1".equals(extra) || (!"0".equals(extra) && inputSystem);
    }

    /** Writes {@code exe} to {@code target} unless it is there already; false when it cannot be written. */
    static boolean install(byte[] exe, File target) {
        if (exe == null || exe.length == 0) return false;
        if (target.isFile() && target.length() == exe.length && Arrays.equals(exe, FileUtils.read(target))) return true;
        boolean ok = FileUtils.write(target, exe);
        if (!ok) Log.w("AGVN", EXE + " not written to " + target);
        return ok;
    }

    /** Moves the pointer to (x, y) through agvn-winhandler.exe; false when it goes as an X event, as before. */
    public synchronized boolean move(int x, int y) {
        WinHandler winHandler = ready();
        if (winHandler == null) return false;
        Pointer pointer = xServer.pointer;
        int px = Math.max(0, Math.min(x, xServer.screenInfo.width - 1));
        int py = Math.max(0, Math.min(y, xServer.screenInfo.height - 1));
        winHandler.agvnPointer(0, px, py, 0);
        pointer.setX(px); // where X keeps the pointer, for the cursor and the touch code; no X event, so Wine gets
        pointer.setY(py); // no second, late move from its X connection (its warp to here changes nothing either)
        XServerRendererView view = xServer.getXServerView();
        if (view != null) view.onPointerMove(pointer.getX(), pointer.getY());
        return true;
    }

    /** Presses or releases {@code button} where the pointer is, through agvn-winhandler.exe; false for an X event. */
    public synchronized boolean button(Pointer.Button button, boolean pressed) {
        int bit = 1 << button.ordinal();
        boolean wheel = button == Pointer.Button.BUTTON_SCROLL_UP || button == Pointer.Button.BUTTON_SCROLL_DOWN;
        Pointer pointer = xServer.pointer;
        if (!pressed) {
            if ((rawHeld & bit) == 0) return false; // pressed as an X event: released as one
            rawHeld &= ~bit;
            pointer.getButtonMask().set(button.flag(), false);
            WinHandler winHandler = xServer.getWinHandler();
            if (!wheel && winHandler != null) {
                winHandler.agvnPointer(MouseEventFlags.getFlagFor(button, false), pointer.getX(), pointer.getY(), 0);
            }
            return true;
        }
        WinHandler winHandler = ready();
        int flags = MouseEventFlags.getFlagFor(button, true);
        if (winHandler == null || flags == 0) return false; // sideways wheel clicks stay X events
        int step = !wheel ? 0 : button == Pointer.Button.BUTTON_SCROLL_UP ? WHEEL_STEP : -WHEEL_STEP;
        winHandler.agvnPointer(flags, pointer.getX(), pointer.getY(), step);
        rawHeld |= bit;
        pointer.getButtonMask().set(button.flag(), true); // what the touch code asks: held or not
        return true;
    }

    /** agvn-winhandler.exe when this game takes touches through it now; null for X events. */
    private WinHandler ready() {
        if (!enabled || xServer.isRelativeMouseMovement() || xServer.isMouseDisabled()) return null;
        WinHandler winHandler = xServer.getWinHandler();
        return winHandler != null && winHandler.agvnPointerReady() ? winHandler : null;
    }
}
