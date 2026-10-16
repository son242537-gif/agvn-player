/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.regex.Pattern;

/**
 * Unreal's crash box, "The UE-<Project> Game has crashed and will close" (Unreal 4: "UE4-"), its error in a text field.
 * It is a dialog of Unreal's own, not a Windows MessageBox, so Wine's msgbox trace never has it, and OK ends the game
 * with 0: "Tự sửa lỗi" read a normal end, and Av Director Life (Mali-G925, 15/10/2026) was asked nothing at four
 * starts. Its title is its window's name (WM_NAME) on the X server: the first one of a session goes into Wine's kept
 * lines, which the doctor reads (game-problems.json: ue-crash-box), and into su-kien.txt. Pure Java (JVM-testable).
 */
public final class AgvnUnrealBox {
    static final Pattern TITLE = Pattern.compile("(?i)\\bGame has crashed and will close\\b");
    /** The line it leaves among Wine's: AGVN's own words, then the title. */
    static final String LINE = "[AGVN] Unreal crash box: ";
    private static volatile boolean seen;

    private AgvnUnrealBox() {}

    /** A new game: its box is not seen yet (AgvnSessionTrack.start). */
    static void reset() {
        seen = false;
    }

    /** A window showed or got a name (X server thread): {@code name}, its WM_NAME. */
    public static void onWindowName(String name) {
        if (!crashBox(name)) return;
        synchronized (AgvnUnrealBox.class) {
            if (seen) return;
            seen = true;
        }
        AgvnWineTail.get().call(LINE + name);
        AgvnSessionLog.event("Hộp lỗi của Unreal: \"" + name + "\"");
    }

    static boolean crashBox(String name) {
        return name != null && TITLE.matcher(name).find();
    }
}
