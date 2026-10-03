/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;

/**
 * Games "Chạy nhẹ" runs on Android itself, without Windows: RPG Maker MV/MZ and Tyrano (index.html), Ren'Py 8, and RPG
 * Maker XP/VX/VX Ace. Copies made for phones (JoiPlay packs) often come without the Windows .exe. Such a folder is still
 * a game: it is imported with a placeholder exe that is never started, and "Chạy bằng Windows" is not offered for it.
 * Pure Java (JVM-testable).
 */
final class AgvnLightGame {
    private AgvnLightGame() {}

    /** True when "Chạy nhẹ" can run the folder as a game of {@code engine}, with or without an .exe. */
    static boolean canRun(File dir, GameExeResolver.Engine engine) {
        switch (engine) {
            case RPGMAKER_MV:
            case TYRANO:
                return AgvnHtmlGame.indexFor(dir, engine) != null;
            case RENPY:
                return AgvnRenpyGame.canRun(dir);
            case RPGMAKER:
                return AgvnRgssGame.canRun(dir);
            default:
                return false;
        }
    }

    /** The exe a game without one gets in its shortcut: what the PC copy would have (Game.exe, or MyGame.exe next to MyGame.ini). */
    static String placeholderExe(File dir, GameExeResolver.Engine engine) {
        AgvnRgssGame.Ini ini = engine == GameExeResolver.Engine.RPGMAKER ? AgvnRgssGame.readIni(dir) : null;
        return (ini != null ? ini.execName() : "Game") + ".exe";
    }

    /**
     * The game folder of a file picked in "Chọn thư mục khác" that is not an exe: index.html, Game.ini or an RPG Maker
     * archive. MV keeps index.html in www/, so a pick there means the folder above it.
     */
    static File folderOf(File picked) {
        File dir = picked.getParentFile();
        if (dir != null && dir.getName().equalsIgnoreCase("www") && dir.getParentFile() != null) return dir.getParentFile();
        return dir;
    }

    /** True for the non-exe files the folder browser offers: index.html, Game.ini and RPG Maker archives. */
    static boolean isGameFile(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.equals("index.html") || n.equals("game.ini") || n.endsWith(".rgssad") || n.endsWith(".rgss2a")
                || n.endsWith(".rgss3a");
    }
}
