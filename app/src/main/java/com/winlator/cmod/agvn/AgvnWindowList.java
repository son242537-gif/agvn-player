/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The windows on the screen, bottom to top, for su-kien.txt when the player reports a problem from the game's ⋮ menu.
 * "To Be or Not to Be" (Unity; its window run by UniWindowController, whose see-through background a Vietnamese mod
 * turns off) showed black on a Xiaomi with an Adreno 825 while it presented 30 frames a second into its 1272×694
 * window, and a window the size of the screen took new content every 5 s; no log said which of them was on top
 * (21/10/2026). The view draws every window opaque (window.frag, useTexAlpha 0), so the see-through pixels of a window
 * of depth 32 show black over what lies under it.
 */
final class AgvnWindowList {
    static final int MAX_LINES = 16;
    private static final String TAG = "AGVN";

    private AgvnWindowList() {}

    /** Writes the list into su-kien.txt; never in the game's way. */
    static void note(XServer xServer) {
        if (xServer == null) return;
        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            List<String> lines = new ArrayList<>();
            walk(xServer.windowManager.rootWindow, 0, lines);
            AgvnSessionLog.event("Cửa sổ trên màn hình, dưới lên trên (" + lines.size() + "):"
                    + (lines.isEmpty() ? " không có" : "\n" + String.join("\n", lines)));
        } catch (RuntimeException e) {
            Log.w(TAG, "window list not written", e);
        }
    }

    /** The mapped windows under {@code parent}, depth first, each drawn over the ones before it. */
    private static void walk(Window parent, int level, List<String> out) {
        for (Window w : parent.getChildren()) {
            if (out.size() >= MAX_LINES) return;
            if (!w.attributes.isMapped()) continue;
            Drawable content = w.getContent();
            int depth = content != null && content.visual != null ? content.visual.depth : 0;
            out.add(line(level, w.id, w.getClassName(), w.getName(), w.getRootX(), w.getRootY(), w.getWidth(),
                    w.getHeight(), depth));
            walk(w, level + 1, out);
        }
    }

    /** "    0x1000038 \"game.exe\" \"Game\" 8,26 1272×694 sâu 32": indented by its level under the root. */
    static String line(int level, int id, String className, String name, int x, int y, int width, int height,
            int depth) {
        StringBuilder b = new StringBuilder("  ");
        for (int i = 0; i < level; i++) b.append("  ");
        b.append(String.format(Locale.ROOT, "0x%x", id));
        if (className != null && !className.isEmpty()) b.append(" \"").append(cut(className)).append('"');
        if (name != null && !name.isEmpty()) b.append(" \"").append(cut(name)).append('"');
        b.append(' ').append(x).append(',').append(y).append(' ').append(width).append('×').append(height);
        return depth > 0 ? b.append(" sâu ").append(depth).toString() : b.append(" không vẽ").toString();
    }

    private static String cut(String s) {
        String one = s.replace('\0', ' ').replace('\n', ' ').trim();
        return one.length() > 40 ? one.substring(0, 40) + "…" : one;
    }
}
