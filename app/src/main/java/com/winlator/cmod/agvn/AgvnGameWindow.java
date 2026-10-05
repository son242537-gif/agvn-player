/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.xserver.Window;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * The running game's own window: the largest mapped application window, the programs that start it left out (Wine's
 * explorer.exe, its desktop and shell; winhandler.exe and start.exe, which open the game's exe and show Wine's
 * "File not found." box when it is gone: "Vừa màn hình" took a 144x106 one for a game's small frame). In Wine's
 * virtual desktop ("explorer /desktop=shell,WxH") the game's windows are children of the desktop's window, not of the
 * root, so every level is searched. Call with the X server's window manager locked.
 */
final class AgvnGameWindow {
    private static final String[] STARTERS = {"explorer.exe", "winhandler.exe", "start.exe"};

    private AgvnGameWindow() {}

    /** True for a window of a program that starts games ({@link #STARTERS}), never the game's ("GameStart.exe" is). */
    static boolean starter(String className) {
        for (String part : className.toLowerCase(Locale.ROOT).split("[^a-z0-9._-]+"))
            for (String name : STARTERS) if (part.equals(name)) return true;
        return false;
    }

    static Window find(Window root) {
        Window best = null;
        long bestArea = 0;
        ArrayDeque<Window> todo = new ArrayDeque<>(root.getChildren());
        while (!todo.isEmpty()) {
            Window w = todo.pop();
            if (!w.attributes.isMapped()) continue;
            if (!starter(w.getClassName()) && w.isApplicationWindow()) {
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
            if (p.isApplicationWindow() && !starter(p.getClassName())) return p;
        return null;
    }
}
