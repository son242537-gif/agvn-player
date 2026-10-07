/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.Locale;

/**
 * Engine modules a game's own Godot was built with and AGVN's Godot for Android lacks, known by the files their
 * importers leave in the pack. Mii Chan (Godot 4.5.1 with spine-godot built in, 08/10/2026) ran on "Chạy nhẹ" with
 * its menus but without its Spine scenes: "Cannot get class 'SpineSkeletonDataResource'", and its game manager never
 * loaded. Spine as a GDExtension is in extension_list.cfg instead ({@link AgvnGodotPack#extensions}). Pure Java.
 */
final class AgvnGodotModules {
    /** An imported file's extension, and the module that imports it: spine-godot's atlases and skeletons. */
    private static final String[][] IMPORTED = {{".spatlas", "Spine"}, {".spskel", "Spine"}, {".spjson", "Spine"}};

    private AgvnGodotModules() {}

    /** The first module {@code pack} needs that AGVN's Godot lacks, or null when it needs none of them. */
    static String missing(AgvnGodotPack pack) {
        if (pack == null) return null;
        for (AgvnGodotPack.Entry e : pack.entries) {
            String path = e.path.toLowerCase(Locale.ROOT);
            if (!path.startsWith(".godot/imported/")) continue;
            for (String[] m : IMPORTED) if (path.endsWith(m[0])) return m[1];
        }
        return null;
    }
}
