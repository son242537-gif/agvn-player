/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * An Electron game whose page can run in "Chạy nhẹ" ({@link AgvnHtmlActivity}, with html-electron.js standing in for
 * require('electron') and Steam's greenworks). Through Wine, Chromium can stand still on its first page: an Electron 18
 * game (Cocos Creator 2.4) on a Snapdragon 8 Elite drew its page and its loading bar at 0%, then nothing for minutes,
 * on 0.1.64 and 0.1.68 (26/10/2026). Its app is resources/app/ or resources/app.asar ({@link AgvnAsar}); package.json
 * names the main script, and the page is the one that script opens (win.loadFile / win.loadURL), else index.html.
 * Pure Java (JVM-testable).
 */
public final class AgvnElectronGame {
    static final int MAX_SCRIPT = 4 << 20;
    static final String LOGGING = "ELECTRON_ENABLE_LOGGING";

    private AgvnElectronGame() {}

    /**
     * An Electron or NW.js game ({@link GameExeResolver#chromiumShell}) prints Chromium's log, which the session keeps
     * with Wine's output: an Electron 44 game whose processes all ended 6 s after it started left no reason
     * (31/10/2026). A value the player set stays.
     */
    public static void logging(EnvVars env, Shortcut s) {
        if (s != null && !env.has(LOGGING) && GameExeResolver.chromiumShell(AgvnEngineLogs.gameDir(s))) env.put(LOGGING, "1");
    }

    /** The page {@code gameDir}'s Electron app opens, as a file (inside its app.asar for a packed one); null for none. */
    static File page(File gameDir) {
        File resources = child(gameDir, "resources");
        File app = resources != null ? child(resources, "app") : null;
        if (app != null && app.isDirectory()) {
            String json = read(child(app, "package.json"), AgvnNwjsGame.MAX_PACKAGE);
            if (json == null) return null;
            String main = AgvnElectronMain.mainOf(json);
            for (String page : AgvnElectronMain.pages(main, read(AgvnHtmlFiles.resolve(app, "/" + main), MAX_SCRIPT))) {
                File f = AgvnHtmlFiles.resolve(app, "/" + page);
                if (f != null) return f;
            }
            return null;
        }
        File packed = resources != null ? child(resources, "app.asar") : null;
        AgvnAsar asar = packed != null && packed.isFile() ? AgvnAsar.of(packed) : null;
        String json = asar != null ? asar.text("package.json", AgvnNwjsGame.MAX_PACKAGE) : null;
        if (json == null) return null;
        String main = AgvnElectronMain.mainOf(json);
        for (String page : AgvnElectronMain.pages(main, asar.text(main, MAX_SCRIPT))) {
            String real = asar.find(page);
            if (real != null) return new File(packed, real);
        }
        return null;
    }

    /** The page {@code gameDir} plays in "Chạy nhẹ": its Electron app's, else its NW.js one's ({@link AgvnNwjsGame}). */
    static File pageOf(File gameDir) {
        if (gameDir == null) return null;
        File page = page(gameDir);
        return page != null ? page : AgvnNwjsGame.index(gameDir);
    }

    /** True when {@code page} is there: a file, or a file in an app.asar. */
    static boolean exists(File page) {
        if (page.isFile()) return true;
        File archive = AgvnAsar.archiveOf(page);
        AgvnAsar asar = archive != null ? AgvnAsar.of(archive) : null;
        return asar != null && asar.find(AgvnAsar.pathIn(archive, page)) != null;
    }

    /**
     * The Electron game folder {@code inside} (a page, a folder) is in: the one above resources/app or
     * resources/app.asar, with Electron's files ({@link GameExeResolver#chromiumShell}); null outside one.
     */
    static File gameDirOf(File inside) {
        for (File f = inside; f != null; f = f.getParentFile()) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            File resources = f.getParentFile();
            if (!(name.equals("app") || name.equals("app.asar")) || resources == null) continue;
            File game = resources.getParentFile();
            if (resources.getName().equalsIgnoreCase("resources") && game != null && GameExeResolver.chromiumShell(game)) return game;
        }
        return null;
    }

    private static File child(File dir, String name) {
        File exact = new File(dir, name);
        if (exact.exists()) return exact;
        File[] children = dir.listFiles();
        if (children != null) for (File c : children) if (c.getName().equalsIgnoreCase(name)) return c;
        return null;
    }

    /** At most {@code max} bytes of {@code f} as UTF-8 text, or null when it cannot be read. */
    private static String read(File f, int max) {
        if (f == null || !f.isFile()) return null;
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[(int) Math.min(in.length(), max)];
            in.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
