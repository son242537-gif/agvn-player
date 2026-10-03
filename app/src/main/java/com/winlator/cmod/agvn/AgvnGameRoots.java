/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Folders the player picked with "Chọn thư mục khác"; scanned together with the usual places, newest first. */
public final class AgvnGameRoots {
    static final String PREF = "agvn_extra_game_roots";
    static final int MAX = 10;
    /** How many folder levels below a picked folder may hold a game. */
    static final int DEPTH = 4;

    private AgvnGameRoots() {}

    public static List<File> load(Context ctx) {
        List<File> roots = new ArrayList<>();
        String saved = prefs(ctx).getString(PREF, "");
        for (String line : saved.split("\n")) if (!line.trim().isEmpty()) roots.add(new File(line.trim()));
        return roots;
    }

    /** Remembers {@code dir} (moved to the front if already known); keeps at most {@link #MAX} folders. */
    public static void add(Context ctx, File dir) {
        List<String> paths = remember(load(ctx), dir);
        prefs(ctx).edit().putString(PREF, String.join("\n", paths)).apply();
    }

    static List<String> remember(List<File> known, File dir) {
        List<String> paths = new ArrayList<>();
        paths.add(dir.getAbsolutePath());
        for (File f : known) {
            String p = f.getAbsolutePath();
            if (!paths.contains(p) && paths.size() < MAX) paths.add(p);
        }
        return paths;
    }

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }
}
