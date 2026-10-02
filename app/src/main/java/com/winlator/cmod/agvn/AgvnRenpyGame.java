/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Chạy nhẹ" for Ren'Py: a Ren'Py 8 game (Python 3) runs on Ren'Py 8.5.3 built for Android ({@link AgvnRenpyActivity},
 * app/agvn-renpy.gradle) instead of Wine: far lighter, cooler and kinder to the battery. Ren'Py 6 and 7 games
 * (Python 2) still run in Wine. The game's files are read where they are; nothing in the game folder is changed except
 * what Ren'Py itself writes there on a PC (saves, cache, log). Pure Java (JVM-testable) except start().
 */
public final class AgvnRenpyGame {
    private AgvnRenpyGame() {}

    /** The Python of a Ren'Py game: 3 for Ren'Py 8, 2 for Ren'Py 6 and 7, 0 when its folder does not tell. */
    static int pythonOf(File gameDir) {
        boolean py2 = false;
        for (File f : list(new File(gameDir, "lib"))) {
            if (!f.isDirectory()) continue;
            String n = f.getName().toLowerCase(Locale.ROOT);
            // Ren'Py 8: lib/py3-windows-x86_64, lib/python3.9 ... lib/python3.12
            if (n.startsWith("py3-") || n.startsWith("python3")) return 3;
            // Ren'Py 7.4+: lib/py2-*, lib/python2.7; older: lib/windows-i686, lib/pythonlib2.7, lib/darwin-x86_64
            if (n.startsWith("py2-") || n.startsWith("python2") || n.startsWith("pythonlib2") || n.startsWith("windows-")
                    || n.startsWith("linux-") || n.startsWith("darwin-") || n.startsWith("mac-")) py2 = true;
        }
        if (py2) return 2;
        File renpy = new File(gameDir, "renpy");
        if (new File(renpy, "__pycache__").isDirectory()) return 3; // Python 3 keeps compiled modules there
        for (File f : list(renpy)) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".pyo") || n.endsWith(".pyc")) return 2; // Python 2 keeps them next to the sources
        }
        return 0;
    }

    /** True when "Chạy nhẹ" can run the game: a Ren'Py 8 game with its game/ folder. */
    public static boolean canRun(File gameDir) {
        return gameDir != null && new File(gameDir, "game").isDirectory() && pythonOf(gameDir) == 3;
    }

    /** The runner at import, as AgvnHtmlGame.useHtml: "Chạy nhẹ" whenever it can run the game, unless the profile says Wine. */
    public static boolean useRenpy(AgvnProfile profile, GameExeResolver.Engine engine, File gameDir) {
        if (engine != GameExeResolver.Engine.RENPY || !canRun(gameDir)) return false;
        return profile == null || profile.runner == null || !AgvnHtmlGame.RUNNER_WINE.equals(profile.runner);
    }

    /** The game's start file name (MyGame.py next to MyGame.exe), which Ren'Py uses to find game/; else "main". */
    static String scriptName(File gameDir) {
        File[] files = list(gameDir);
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName();
            if (f.isFile() && n.toLowerCase(Locale.ROOT).endsWith(".py") && n.length() > 3) return n.substring(0, n.length() - 3);
        }
        return "main";
    }

    /** Where Ren'Py writes this game's log.txt and traceback.txt on "Chạy nhẹ": AGVN-Player/renpy/<game folder>. */
    static File publicDir(File agvnPlayerDir, File gameDir) {
        return new File(agvnPlayerDir, "renpy/" + (gameDir != null ? gameDir.getName() : "_"));
    }

    /**
     * The engine's environment: librenpython.so and Ren'Py read the ANDROID_* ones, AGVN's main.py the AGVN_RENPY_* ones.
     * Ren'Py saves in the first of OLD_PUBLIC/game/saves, PRIVATE/saves and PUBLIC/saves that is a writable folder,
     * so with game/saves made beforehand the saves stay in the game folder, where a PC and "Chạy bằng Windows" keep
     * them too. PUBLIC also gets log.txt and traceback.txt.
     */
    static Map<String, String> environment(File engine, File gameDir, File publicDir, String apk, File quitFile) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("ANDROID_PRIVATE", engine.getPath());
        env.put("ANDROID_PUBLIC", publicDir.getPath());
        env.put("ANDROID_OLD_PUBLIC", gameDir.getPath());
        env.put("ANDROID_APK", apk);
        env.put("AGVN_RENPY_BASEDIR", gameDir.getPath());
        env.put("AGVN_RENPY_NAME", scriptName(gameDir));
        env.put("AGVN_RENPY_QUIT_FILE", quitFile.getPath());
        return env;
    }

    /**
     * Called by AgvnHtmlGame.redirect for a shortcut set to "Chạy nhẹ" Ren'Py. Returns false, so the game runs in Wine,
     * when its folder moved or no longer holds a Ren'Py 8 game.
     */
    static boolean start(Activity activity, Intent from, Map<String, String> extras) {
        String dir = extras.get(AgvnGameImporter.EXTRA_GAME_DIR);
        File gameDir = dir != null && !dir.isEmpty() ? new File(dir) : null;
        if (!canRun(gameDir)) return false;
        Intent intent = new Intent(activity, AgvnRenpyActivity.class);
        if (from.getExtras() != null) intent.putExtras(from.getExtras());
        intent.putExtra(AgvnRenpyActivity.EXTRA_GAME_DIR, gameDir.getPath());
        activity.startActivity(intent);
        return true;
    }

    private static File[] list(File dir) {
        File[] files = dir != null ? dir.listFiles() : null;
        return files != null ? files : new File[0];
    }
}
