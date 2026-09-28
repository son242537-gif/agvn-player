/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds Windows game folders so players never have to browse: a folder counts as a game when it has
 * agvn-profile.json or GameExeResolver finds an exe to launch. Search stops descending once a game is found.
 * Pure Java (JVM-testable); the Android roots are chosen in {@link AgvnGameImporter}.
 */
public final class AgvnGameScanner {
    /** One search root and how many folder levels below it may hold a game. */
    public static final class Root {
        final File dir;
        final int depth;

        public Root(File dir, int depth) {
            this.dir = dir;
            this.depth = depth;
        }
    }

    static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "android", "dcim", "pictures", "music", "movies", "alarms", "notifications", "ringtones", "podcasts",
            "audiobooks", "recordings", "documents", "agvn-player", "winlator", "miui", "tencent", "lost.dir",
            "engine", "binaries", "content", "_commonredist", "redist", "directx"));
    static final int MAX_VISITED = 3000;

    private AgvnGameScanner() {}

    public static boolean isGameDir(File dir) {
        if (new File(dir, AgvnProfile.FILE_NAME).isFile()) return true;
        GameExeResolver.Engine engine = GameExeResolver.detectEngine(dir);
        if (GameExeResolver.resolveExe(dir, engine) == null) return false;
        // unknown engine: a lone installer in Download is not a game; real games ship DLLs next to the exe
        return engine != GameExeResolver.Engine.UNKNOWN || hasDll(dir);
    }

    private static boolean hasDll(File dir) {
        String[] names = dir.list();
        if (names == null) return false;
        for (String n : names) if (n.toLowerCase(Locale.ROOT).endsWith(".dll")) return true;
        return false;
    }

    public static List<File> scan(List<Root> roots) {
        List<File> games = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int[] visited = {0};
        for (Root root : roots) {
            if (root.dir != null && root.dir.isDirectory()) walk(root.dir, root.depth, games, seen, visited, true);
        }
        return games;
    }

    private static void walk(File dir, int depthLeft, List<File> games, Set<String> seen, int[] visited, boolean isRoot) {
        if (visited[0]++ > MAX_VISITED) return;
        String key = canonical(dir);
        if (!seen.add(key)) return;
        if (!isRoot && isGameDir(dir)) {
            games.add(dir);
            return;
        }
        if (depthLeft <= 0) return;
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith(".") || SKIP.contains(name)) continue;
            walk(child, depthLeft - 1, games, seen, visited, false);
        }
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }
}
