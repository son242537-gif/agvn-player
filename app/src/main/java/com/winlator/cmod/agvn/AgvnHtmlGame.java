/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Chạy nhẹ": RPG Maker MV/MZ and TyranoBuilder games are web pages (index.html + JavaScript), so they run in an
 * Android WebView ({@link AgvnHtmlActivity}) without Wine or Box64: far lighter, cooler and kinder to the battery.
 * Ren'Py 8 games ({@link AgvnRenpyGame}) and RPG Maker XP/VX/VX Ace games ({@link AgvnRgssGame}) have their own
 * "Chạy nhẹ". The shortcut keeps its Wine Exec line; the extra
 * {@link #EXTRA_RUNNER} decides which one starts.
 */
public final class AgvnHtmlGame {
    public static final String EXTRA_RUNNER = "agvnRunner";
    public static final String EXTRA_INDEX = "agvnHtmlIndex";
    public static final String RUNNER_HTML = "html";
    public static final String RUNNER_RENPY = "renpy";
    public static final String RUNNER_RGSS = "rgss";
    public static final String RUNNER_WINE = "wine";

    private AgvnHtmlGame() {}

    /** index.html of a web-engine game, or null (other engines, or files packed into package.nw / the exe). */
    public static File indexFor(File gameDir, GameExeResolver.Engine engine) {
        if (engine == GameExeResolver.Engine.RPGMAKER_MV) {
            File www = findIndex(new File(gameDir, "www"));
            return www != null ? www : findIndex(gameDir);
        }
        if (engine == GameExeResolver.Engine.TYRANO) {
            File index = findIndex(gameDir);
            return index != null && new File(gameDir, "tyrano").isDirectory() ? index : null;
        }
        return null;
    }

    /** html when the profile says so or leaves it open and the game has an index.html; else null (Wine). */
    public static boolean useHtml(AgvnProfile profile, File index) {
        if (index == null) return false;
        return profile == null || profile.runner == null || !RUNNER_WINE.equals(profile.runner);
    }

    public static boolean isValidRunner(String runner) {
        return runner == null || RUNNER_HTML.equals(runner) || RUNNER_RENPY.equals(runner) || RUNNER_RGSS.equals(runner)
                || RUNNER_WINE.equals(runner);
    }

    /** True for "Chạy nhẹ" (HTML, Ren'Py or RGSS): the game runs on Android itself, without Wine. */
    public static boolean isLight(String runner) {
        return RUNNER_HTML.equals(runner) || RUNNER_RENPY.equals(runner) || RUNNER_RGSS.equals(runner);
    }

    /**
     * Called first thing by XServerDisplayActivity: opens "Chạy nhẹ" instead of Wine when the shortcut asks for it.
     * Returns true when the caller must finish(). A game that moved (no index.html, no Ren'Py 8 or RGSS game) runs in Wine.
     */
    public static boolean redirect(Activity activity) {
        Intent from = activity.getIntent();
        String path = from != null ? from.getStringExtra("shortcut_path") : null;
        if (path == null || path.isEmpty()) return false;
        Map<String, String> extras = readExtras(new File(path));
        if (RUNNER_RENPY.equals(extras.get(EXTRA_RUNNER))) return AgvnRenpyGame.start(activity, from, extras);
        if (RUNNER_RGSS.equals(extras.get(EXTRA_RUNNER))) return AgvnRgssFiles.start(activity, from, extras);
        if (!RUNNER_HTML.equals(extras.get(EXTRA_RUNNER))) return false;
        String index = extras.get(EXTRA_INDEX);
        if (index == null || !new File(index).isFile()) return false;
        Intent intent = new Intent(activity, AgvnHtmlActivity.class);
        if (from.getExtras() != null) intent.putExtras(from.getExtras());
        intent.putExtra(AgvnHtmlActivity.EXTRA_INDEX_PATH, index);
        activity.startActivity(intent);
        return true;
    }

    /**
     * "Chạy bằng Windows" from a "Chạy nhẹ" game: the shortcut remembers Wine. Returns the intent that starts the game
     * in Wine ({@code ownExtra}, the runner's own extra, left out), or null when the game is no longer in the library.
     */
    static Intent toWindows(Activity activity, String ownExtra) {
        Intent from = activity.getIntent();
        String path = from.getStringExtra("shortcut_path");
        int id = from.getIntExtra("container_id", 0);
        if (id == 0 && path != null) id = containerIdIn(new File(path));
        Container container = new ContainerManager(activity).getContainerById(id);
        if (path == null || container == null) return null;
        Shortcut shortcut = new Shortcut(container, new File(path));
        shortcut.putExtra(EXTRA_RUNNER, RUNNER_WINE);
        shortcut.saveData();
        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        if (from.getExtras() != null) intent.putExtras(from.getExtras());
        intent.removeExtra(ownExtra);
        return intent;
    }

    /** False when the game's shortcut starts no real .exe (a copy made for phones): "Chạy bằng Windows" cannot run it. */
    static boolean hasWindowsExe(Activity activity) {
        String path = activity.getIntent().getStringExtra("shortcut_path");
        return path == null || exeExists(new File(path));
    }

    /** True when the exe in the .desktop file's Exec line exists, or when the line cannot be read (nothing to hide then). */
    static boolean exeExists(File desktopFile) {
        for (String line : FileUtils.readLines(desktopFile)) {
            if (!line.startsWith("Exec=")) continue;
            int end = line.lastIndexOf('"'), start = end > 0 ? line.lastIndexOf('"', end - 1) : -1;
            return start < 0 || new File(line.substring(start + 1, end)).isFile();
        }
        return true;
    }

    /** The "container_id:N" line launcher shortcuts rely on, or 0. */
    static int containerIdIn(File desktopFile) {
        for (String line : FileUtils.readLines(desktopFile)) {
            if (!line.startsWith("container_id:")) continue;
            try {
                return Integer.parseInt(line.substring(13).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** The [Extra Data] section of a .desktop file (no Container needed, unlike Shortcut). */
    static Map<String, String> readExtras(File desktopFile) {
        Map<String, String> extras = new HashMap<>();
        boolean inExtras = false;
        for (String raw : FileUtils.readLines(desktopFile)) {
            String line = raw.trim();
            if (line.startsWith("[")) {
                inExtras = line.equals("[Extra Data]");
                continue;
            }
            int eq = line.indexOf('=');
            if (inExtras && eq > 0) extras.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return extras;
    }

    /**
     * Web origin host for a game, one per game folder name so each game keeps its own saves (localStorage) even when the
     * folder moves between internal storage and the SD card. MV's "www" folder is skipped: every MV game has one.
     */
    static String hostFor(File index) {
        File root = index.getParentFile();
        if (root != null && root.getName().equalsIgnoreCase("www") && root.getParentFile() != null) root = root.getParentFile();
        String key = root != null ? root.getName().toLowerCase(Locale.ROOT) : "";
        return "g" + Integer.toHexString(key.hashCode()) + ".agvn.game";
    }

    private static File findIndex(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File f : children) if (f.isFile() && f.getName().equalsIgnoreCase("index.html")) return f;
        return null;
    }
}
