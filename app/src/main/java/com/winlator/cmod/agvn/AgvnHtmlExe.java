/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.Locale;

/**
 * The exe an HTML game runs as on a PC, for html-compat.js's process.execPath ("/Game.exe", in the game folder): a game
 * may pick its language by its name, path.parse(process.execPath).name ("Game_E" for English) (report of 20/10/2026).
 * It is the exe the game's shortcut starts on Windows ("Chạy bằng Windows"), else the folder's main exe as the import
 * picks it ({@link GameExeResolver#resolveExe}: Game.exe before Game_E.exe), never a tool that only serves the game (a
 * launcher, a setup), else Game.exe. Pure Java (JVM-testable).
 */
final class AgvnHtmlExe {
    static final String DEFAULT = "Game.exe";

    private AgvnHtmlExe() {}

    /** The line html-compat.js takes it from, put before it: window.__agvnExe = 'Game.exe'; */
    static String line(String shortcutPath, File gameDir) {
        File desktop = shortcutPath != null && !shortcutPath.isEmpty() ? new File(shortcutPath) : null;
        File exe = desktop != null && desktop.isFile() ? AgvnHtmlGame.exeIn(desktop) : null;
        return "window.__agvnExe = " + AgvnHtmlKeys.js(name(exe, gameDir)) + ";\n";
    }

    /** The exe's file name: {@code shortcutExe}'s when it is a game's, else the main one in {@code gameDir}. */
    static String name(File shortcutExe, File gameDir) {
        if (shortcutExe != null && isGame(shortcutExe.getName())) return shortcutExe.getName();
        String main = gameDir != null ? GameExeResolver.resolveExe(gameDir, GameExeResolver.Engine.UNKNOWN) : null;
        if (main != null) main = main.substring(main.lastIndexOf('/') + 1);
        return main != null && isGame(main) ? main : DEFAULT;
    }

    private static boolean isGame(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".exe") && n.length() > 4 && !n.startsWith("unins") && !GameExeResolver.isHelper(n);
    }
}
