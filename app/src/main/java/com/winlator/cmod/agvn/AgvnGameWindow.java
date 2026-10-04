/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.xserver.Window;

import java.util.ArrayDeque;

/**
 * The running game's own window: the largest mapped application window, Wine's explorer.exe (desktop, shell) left out.
 * In Wine's virtual desktop ("explorer /desktop=shell,WxH") the game's windows are children of the desktop's window,
 * not of the root, so every level is searched. Call with the X server's window manager locked.
 */
final class AgvnGameWindow {
    private AgvnGameWindow() {}

    static Window find(Window root) {
        Window best = null;
        long bestArea = 0;
        ArrayDeque<Window> todo = new ArrayDeque<>(root.getChildren());
        while (!todo.isEmpty()) {
            Window w = todo.pop();
            if (!w.attributes.isMapped()) continue;
            boolean explorer = w.getClassName().contains("explorer.exe");
            if (!explorer && w.isApplicationWindow()) {
                long area = (long) w.getWidth() * w.getHeight();
                if (area > bestArea) {
                    best = w;
                    bestArea = area;
                }
            } else {
                todo.addAll(w.getChildren()); // the desktop's windows, Wine's own
            }
        }
        return best;
    }

    /** The game window {@code w} is part of (itself or an ancestor, as {@link #find} counts them), or null. */
    static Window topLevel(Window w) {
        for (Window p = w; p != null; p = p.getParent())
            if (p.isApplicationWindow() && !p.getClassName().contains("explorer.exe")) return p;
        return null;
    }
}
