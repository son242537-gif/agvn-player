/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which game folders already have a library shortcut, in any container: matched by the AGVN game-folder extra or,
 * for shortcuts made by hand, by an exe path inside the folder. Canonical paths, so /sdcard and /storage/emulated/0 agree.
 */
public final class AgvnLibraryIndex {
    /** The shortcut a game folder already has. */
    public static final class Existing {
        public final Container container;
        public final String name;

        Existing(Container container, String name) {
            this.container = container;
            this.name = name;
        }
    }

    private final Map<String, Existing> byGameDir = new HashMap<>();
    private final List<String> exePaths = new ArrayList<>();
    private final List<Existing> exeOwners = new ArrayList<>();

    public AgvnLibraryIndex(List<Shortcut> shortcuts) {
        for (Shortcut s : shortcuts) {
            Existing e = new Existing(s.container, s.name);
            String gameDir = s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
            if (!gameDir.isEmpty()) byGameDir.put(AgvnGameScanner.canonical(new File(gameDir)), e);
            String exe = unixExe(s.path);
            if (exe != null) {
                exePaths.add(AgvnGameScanner.canonical(new File(exe)));
                exeOwners.add(e);
            }
        }
    }

    /** The shortcut for {@code gameDir}, or null when the game is not in the library yet. */
    public Existing find(File gameDir) {
        String key = AgvnGameScanner.canonical(gameDir);
        Existing e = byGameDir.get(key);
        if (e != null) return e;
        String prefix = key + File.separator;
        for (int i = 0; i < exePaths.size(); i++) if (exePaths.get(i).startsWith(prefix)) return exeOwners.get(i);
        return null;
    }

    /** Android path of the exe in a shortcut's Exec line ("/storage/…/Game.exe"), or null for Wine drive paths. */
    static String unixExe(String path) {
        if (path == null) return null;
        String p = path.trim();
        int end = p.toLowerCase(java.util.Locale.ROOT).lastIndexOf(".exe");
        if (end < 0) return null;
        p = p.substring(0, end + 4);
        if (p.startsWith("\"")) p = p.substring(1);
        return p.startsWith("/") ? p : null;
    }
}
