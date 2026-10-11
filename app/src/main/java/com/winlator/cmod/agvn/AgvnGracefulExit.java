/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.xconnector.XOutputStream;
import com.winlator.cmod.xconnector.XStreamLock;
import com.winlator.cmod.xserver.Atom;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.events.Event;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * "Thoát" in the game's menu: the game is asked to close as the X button of its window would on a PC (WM_DELETE_WINDOW,
 * which Wine turns into WM_CLOSE), and the app waits a little for it before ending Wine as before. Ended at once, a
 * game never saved what it keeps for its own exit: Unity writes PlayerPrefs (where many Unity games keep their
 * progress) to the registry only when it quits, so the player found an old save at the next start (ticket 08/10/2026).
 * The wait ends as soon as the game's window and process are gone, when the game opens a window of its own instead (a
 * "quit?" box), or after {@link #WAIT_MS}.
 */
public final class AgvnGracefulExit {
    private static final String TAG = "AGVN";
    static final long WAIT_MS = 5000, STEP_MS = 100;

    private AgvnGracefulExit() {}

    /** Asks the game to close, then runs {@code done} on {@code handler}'s thread when it has or the wait is over. */
    public static void close(XServer xServer, Handler handler, String exePath, Runnable done) {
        Window game = null;
        int windows = 0;
        if (xServer != null) {
            try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                game = AgvnGameWindow.find(xServer.windowManager.rootWindow);
                windows = AgvnGameWindow.count(xServer.windowManager.rootWindow);
                if (game != null) game.originClient.sendEvent(new DeleteWindow(game));
            } catch (RuntimeException e) {
                Log.w(TAG, "close request not sent", e);
                game = null;
            }
        }
        if (game == null) {
            done.run();
            return;
        }
        String exe = exeName(exePath);
        long start = SystemClock.uptimeMillis();
        int before = windows, id = game.id;
        Runnable check = new Runnable() {
            @Override
            public void run() {
                long waited = SystemClock.uptimeMillis() - start;
                boolean gone = !shown(xServer, id) && !running(exe);
                boolean asks = !gone && opened(xServer, before);
                if (!gone && !asks && waited < WAIT_MS) {
                    handler.postDelayed(this, STEP_MS);
                    return;
                }
                AgvnSessionLog.event(gone ? "Thoát: game tự đóng sau " + waited + " ms (lưu như khi tắt trên PC)"
                        : asks ? "Thoát: game mở hộp hỏi khi đóng, app tắt game" : "Thoát: game không tự đóng sau "
                        + WAIT_MS / 1000 + " giây, app tắt game");
                done.run();
            }
        };
        handler.postDelayed(check, STEP_MS);
    }

    /** "ThornSin.exe" from a Windows or Android path, lower case; "" when there is none. */
    static String exeName(String path) {
        if (path == null) return "";
        String p = path.replace("\"", "").trim();
        int dot = p.toLowerCase(Locale.ROOT).lastIndexOf(".exe");
        if (dot >= 0) p = p.substring(0, dot + 4);
        int slash = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
        return p.substring(slash + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * True while a Wine process is {@code exe}: Wine names a process after its Windows path ("D:\…\ThornSin.exe").
     * Only that first word counts, since the starter (winhandler.exe) has the game's exe among its arguments.
     */
    private static boolean running(String exe) {
        if (exe.isEmpty()) return false;
        for (String pid : ProcessHelper.listRunningWineProcesses()) {
            try {
                byte[] cmd = Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath());
                String argv0 = new String(cmd, StandardCharsets.UTF_8).split("\0", 2)[0];
                if (exeName(argv0).equals(exe)) return true;
            } catch (IOException ignored) {
                // the process ended meanwhile
            }
        }
        return false;
    }

    /** True while the game's window is still there and shown. */
    private static boolean shown(XServer xServer, int id) {
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            Window w = xServer.windowManager.getWindow(id);
            return w != null && w.attributes.isMapped();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the game shows more windows than when it was asked to close: a box asking whether to quit. */
    private static boolean opened(XServer xServer, int before) {
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            return AgvnGameWindow.count(xServer.windowManager.rootWindow) > before;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** ClientMessage WM_PROTOCOLS / WM_DELETE_WINDOW, as a window manager sends it for a window's close button. */
    static final class DeleteWindow extends Event {
        private final Window window;

        DeleteWindow(Window window) {
            super(33);
            this.window = window;
        }

        @Override
        public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
            try (XStreamLock lock = outputStream.lock()) {
                outputStream.writeByte(code);
                outputStream.writeByte((byte) 32);
                outputStream.writeShort(sequenceNumber);
                outputStream.writeInt(window.id);
                outputStream.writeInt(Atom.internAtom("WM_PROTOCOLS"));
                outputStream.writeInt(Atom.internAtom("WM_DELETE_WINDOW"));
                outputStream.writeInt(0); // CurrentTime
                outputStream.writePad(12);
            }
        }
    }
}
