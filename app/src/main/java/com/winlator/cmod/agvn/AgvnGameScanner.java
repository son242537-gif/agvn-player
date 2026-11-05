/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds game folders so players never have to browse: a folder counts as a game when it has agvn-profile.json,
 * GameExeResolver finds an exe to launch, or "Chạy nhẹ" can run it without one ({@link AgvnLightGame}). Search stops
 * descending once a game is found. A folder with an exe that no rule recognises is kept apart ({@link Found#unsure}):
 * "Thêm game" lists it under its own heading, so a game of an engine the app does not know is not missed in silence.
 * Pure Java (JVM-testable); the Android roots are chosen in {@link AgvnGameImporter}.
 */
public final class AgvnGameScanner {
    /** One search root and how many folder levels below it may hold a game. */
    public static final class Root {
        final File dir;
        final int depth;
        /** The root itself may be the game (a folder the player picked by hand). */
        final boolean includeSelf;

        public Root(File dir, int depth) {
            this(dir, depth, false);
        }

        public Root(File dir, int depth, boolean includeSelf) {
            this.dir = dir;
            this.depth = depth;
            this.includeSelf = includeSelf;
        }
    }

    static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "android", "dcim", "pictures", "music", "movies", "alarms", "notifications", "ringtones", "podcasts",
            "audiobooks", "recordings", "documents", "agvn-player", "winlator", "miui", "tencent", "lost.dir",
            "engine", "binaries", "content", "_commonredist", "redist", "directx"));
    static final int MAX_VISITED = 5000;
    /** Folders that hold nothing but one folder ("NINJA DISGRACE/Shinobi/Shinobi.exe") passed at the depth limit. */
    static final int MAX_WRAPPERS = 2;
    /** Files a download leaves beside an installer, which are no game's data. */
    static final Pattern ARCHIVE = Pattern.compile("\\.(zip|rar|7z|apk|xapk|tar|gz|iso|txt|url|lnk|ini|pdf|html?)$");

    private AgvnGameScanner() {}

    /** What a search found. */
    public static final class Found {
        public final List<File> games = new ArrayList<>();
        /**
         * Folders with an exe that may be a game of an engine no rule knows: no profile, no engine, no DLL beside the
         * exe (SRPG Studio's game.exe before the app knew it, 05/11/2026), its exe no installer, launcher or tool, and
         * something of the game beside it. Not the search's own folders (Download, AGVN, Games), nor a folder above a
         * game.
         */
        public final List<File> unsure = new ArrayList<>();
    }

    public static boolean isGameDir(File dir) {
        if (new File(dir, AgvnProfile.FILE_NAME).isFile()) return true;
        GameExeResolver.Engine engine = GameExeResolver.detectEngine(dir);
        // copies made for phones often have no .exe; "Chạy nhẹ" runs them anyway
        if (GameExeResolver.resolveExe(dir, engine) == null) return AgvnLightGame.canRun(dir, engine);
        // unknown engine: a lone installer in Download is not a game; real games ship DLLs next to the exe
        return engine != GameExeResolver.Engine.UNKNOWN || hasDll(dir);
    }

    private static boolean hasDll(File dir) {
        String[] names = dir.list();
        if (names == null) return false;
        for (String n : names) if (n.toLowerCase(Locale.ROOT).endsWith(".dll")) return true;
        return false;
    }

    /** A folder of an unknown engine whose exe may start a game ({@link Found#unsure}). */
    static boolean unsure(File dir) {
        String[] names = dir.list();
        if (names == null) return false;
        if (Arrays.stream(names).noneMatch(n -> n.toLowerCase(Locale.ROOT).endsWith(".exe"))) return false;
        if (new File(dir, AgvnProfile.FILE_NAME).isFile()) return false;
        if (GameExeResolver.detectEngine(dir) != GameExeResolver.Engine.UNKNOWN) return false;
        String exe = GameExeResolver.resolveExe(dir, GameExeResolver.Engine.UNKNOWN);
        if (exe == null || GameExeResolver.isHelper(exe.toLowerCase(Locale.ROOT))) return false;
        for (String n : names) {
            String lower = n.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".exe") && !ARCHIVE.matcher(lower).find()) return true; // the game's data, a folder
        }
        return false;
    }

    public static List<File> scan(List<Root> roots) {
        return find(roots).games;
    }

    public static Found find(List<Root> roots) {
        Walk w = new Walk();
        for (Root root : roots) if (root.dir != null) w.roots.add(canonical(root.dir));
        for (Root root : roots) {
            if (root.dir != null && root.dir.isDirectory()) walk(root.dir, root.depth, w, !root.includeSelf, 0);
        }
        for (File game : w.found.games) {
            String inside = canonical(game) + File.separator;
            w.found.unsure.removeIf(dir -> inside.startsWith(canonical(dir) + File.separator));
        }
        return w.found;
    }

    /** Scan state: a folder reached again from a root with a bigger depth budget is searched again. */
    private static final class Walk {
        final Found found = new Found();
        final Set<String> roots = new HashSet<>();
        final Map<String, Integer> budget = new HashMap<>();
        final Set<String> checked = new HashSet<>();
        int visited;
    }

    private static void walk(File dir, int depthLeft, Walk w, boolean isRoot, int wrappers) {
        if (w.visited++ > MAX_VISITED) return;
        String key = canonical(dir);
        Integer before = w.budget.get(key);
        if (before != null && before >= depthLeft) return;
        w.budget.put(key, depthLeft);
        if (!isRoot && w.checked.add(key)) {
            if (isGameDir(dir)) {
                w.found.games.add(dir);
                w.budget.put(key, Integer.MAX_VALUE);
                return;
            }
            if (!w.roots.contains(key) && unsure(dir)) w.found.unsure.add(dir); // and folders below it are searched
        }
        if (depthLeft <= 0) {
            // a download unpacked into a folder of its own name: the wrapper costs no depth (a few in a row at most)
            File only = wrappers < MAX_WRAPPERS ? onlySubfolder(dir) : null;
            if (only != null) walk(only, 0, w, false, wrappers + 1);
            return;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (!skipped(child)) walk(child, depthLeft - 1, w, false, 0);
        }
    }

    /** The single sub-folder of a folder that has no .exe of its own, else null. */
    static File onlySubfolder(File dir) {
        File[] entries = dir.listFiles();
        if (entries == null) return null;
        File only = null;
        for (File e : entries) {
            if (e.isDirectory()) {
                if (skipped(e)) continue;
                if (only != null) return null;
                only = e;
            } else if (e.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                return null;
            }
        }
        return only;
    }

    private static boolean skipped(File dir) {
        String name = dir.getName().toLowerCase(Locale.ROOT);
        return name.startsWith(".") || SKIP.contains(name);
    }

    static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }
}
