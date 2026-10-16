/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xserver.XServer;

import java.io.File;

/**
 * The program that starts a Windows game in Wine. Winlator's winhandler.exe reads its command line in the Windows code
 * page (__getmainargs, ShellExecuteExA), which loses the letters beyond it: GAMEHUB, a game in a folder named with
 * Vietnamese accents, got "File not found." (Xiaomi 23090RA98G, 10/10/2026) while GameHub opened it. AGVN's
 * agvn-winhandler.exe reads it as Unicode (CommandLineToArgvW, ShellExecuteExW), and Wine for Android (Proton 9 and 10)
 * names files in UTF-8 as the phone does, so a game whose start has letters beyond ASCII starts through it, as does a
 * game that takes touches as a real mouse ({@link AgvnRawMouse}). The others keep winhandler.exe, as before.
 */
public final class AgvnStarter {
    private static volatile String note;

    private AgvnStarter() {}

    /**
     * Before Wine starts: the program that starts {@code shortcut}'s game from {@code launch} (its exe, as the launch
     * command has it), with the game's own arguments; it goes into the container when it is AGVN's.
     */
    public static String prepare(Context context, XServer xServer, Shortcut shortcut, String launch) {
        String starter = AgvnRawMouse.prepare(context, xServer, shortcut);
        note = null;
        if (shortcut == null || AgvnRawMouse.EXE.equals(starter)) return starter;
        String beyond = beyondAscii(launch, shortcut.getExtra("execArgs"));
        if (beyond.isEmpty()) return starter;
        File windows = new File(shortcut.container.getRootDir(), ".wine/drive_c/windows");
        byte[] exe = FileUtils.read(context, "agvn/" + AgvnRawMouse.EXE);
        if (!AgvnRawMouse.install(exe, new File(windows, AgvnRawMouse.EXE))) return starter;
        note = "Đường dẫn game có chữ ngoài ASCII (" + beyond + "): mở game bằng " + AgvnRawMouse.EXE
                + ", đọc đường dẫn bằng Unicode";
        return AgvnRawMouse.EXE;
    }

    /** The letters beyond ASCII in the start of a game ({@code launch} and {@code args}); empty when there are none. */
    static String beyondAscii(String launch, String args) {
        return AgvnGameFacts.beyondAscii((launch != null ? launch : "") + " " + (args != null ? args : ""));
    }

    /** The line for the session's events, once; null when the game starts as before. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
