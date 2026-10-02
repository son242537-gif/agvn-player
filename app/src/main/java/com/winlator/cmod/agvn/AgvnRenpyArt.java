/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A Ren'Py game's own pictures, loose in game/ or packed in game/*.rpa ({@link AgvnRpaArchive}): for the cover the
 * main menu background (gui/main_menu, else the largest pictures named as menu or title art, else the presplash or
 * gui/game_menu), for the icon gui/window_icon unless it is Ren'Py's default one. Pure Java (JVM-testable).
 */
final class AgvnRenpyArt {
    static final String[] EXTS = {".png", ".jpg", ".jpeg", ".webp"};
    /**
     * Menu or title art by file name (no folder, no extension; "~" stands for an optional space, "_" or "-"):
     * main_menu_bg, title screen, bg-menu, title2, keyart, ...
     */
    private static final Pattern ART_NAME = Pattern.compile(("((bg|background)~)?(main~menu|title(~screen)?|key~art|splash(~screen)?)"
            + "(~(bg|background|art|image|screen))?(~\\d+)?"
            + "|(bg|background)~(main~)?(menu|mm)(~\\d+)?"
            + "|(main~)?(menu|mm)~(bg|background|art)(~\\d+)?").replace("~", "[ _-]?"));
    /** Folders whose pictures are never menu art: overlays, phone layouts, buttons, translations, saves. */
    private static final Pattern SKIP = Pattern.compile("(^|.*/)(overlay|phone|button|bar|scrollbar|slider|tl|saves|cache)/.*");
    /** Ren'Py's default window icon: launcher/game/gui7/icon.png (250x250) recoloured with the project's own colour. */
    static final int DEFAULT_ICON_SIZE = 250;
    private static final int WALK_DEPTH = 3;

    private final File game;
    private final List<File> archives = new ArrayList<>();
    /** Per archive: lower-case name → entry (Ren'Py finds files without regard to case). */
    private final List<Map<String, AgvnRpaArchive.Entry>> indexes = new ArrayList<>();

    private AgvnRenpyArt(File game) {
        this.game = game;
        File[] rpas = game.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".rpa"));
        if (rpas == null) return;
        Arrays.sort(rpas);
        for (File rpa : rpas) {
            Map<String, AgvnRpaArchive.Entry> lower = new HashMap<>();
            for (AgvnRpaArchive.Entry e : AgvnRpaArchive.index(rpa).values()) lower.put(e.name.toLowerCase(Locale.ROOT), e);
            if (lower.isEmpty()) continue;
            archives.add(rpa);
            indexes.add(lower);
        }
    }

    /** The game/ folder of a Ren'Py game in {@code gameDir} (or {@code gameDir} itself when it is game/), else null. */
    static AgvnRenpyArt of(File gameDir) {
        if (gameDir == null) return null;
        File game = new File(gameDir, "game");
        if (game.isDirectory()) return new AgvnRenpyArt(game);
        return gameDir.getName().equalsIgnoreCase("game") && gameDir.isDirectory() ? new AgvnRenpyArt(gameDir) : null;
    }

    void addCovers(List<AgvnArt> out) {
        AgvnCoverSources.add(out, find("gui/main_menu"));
        AgvnCoverSources.addLargest(out, named());
        AgvnCoverSources.add(out, find("presplash")); // shown while the game loads: often the logo
        AgvnCoverSources.add(out, find("gui/game_menu"));
    }

    /** gui/window_icon when the developer made one; null for Ren'Py's default icon or none. */
    AgvnArt icon() {
        AgvnArt icon = find("gui/window_icon");
        byte[] data = icon != null ? icon.read() : null;
        if (data == null) return null;
        int[] size = AgvnArt.pngSize(data);
        if (size != null && size[0] == DEFAULT_ICON_SIZE && size[1] == DEFAULT_ICON_SIZE) return null;
        return AgvnArt.bytes(icon.name, data);
    }

    /** {@code path} under game/ with any picture extension: the loose file first (it wins in Ren'Py too), then the archives. */
    private AgvnArt find(String path) {
        for (String ext : EXTS) {
            File f = new File(game, path + ext);
            if (f.isFile()) return AgvnArt.file(f);
        }
        for (int i = 0; i < archives.size(); i++) {
            for (String ext : EXTS) {
                AgvnRpaArchive.Entry e = indexes.get(i).get(path + ext);
                if (e != null) return AgvnArt.rpa(archives.get(i), e);
            }
        }
        return null;
    }

    /** Pictures whose name says they are menu or title art, loose and packed (the largest go first: a background beats a logo). */
    private List<AgvnArt> named() {
        List<AgvnArt> found = new ArrayList<>();
        walk(game, "", 0, found);
        for (int i = 0; i < archives.size(); i++) {
            for (Map.Entry<String, AgvnRpaArchive.Entry> e : indexes.get(i).entrySet()) {
                if (isArt(e.getKey())) found.add(AgvnArt.rpa(archives.get(i), e.getValue()));
            }
        }
        return found;
    }

    private static void walk(File dir, String rel, int depth, List<AgvnArt> found) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String path = rel + f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory()) {
                if (depth < WALK_DEPTH) walk(f, path + "/", depth + 1, found);
            } else if (isArt(path)) {
                found.add(AgvnArt.file(f));
            }
        }
    }

    /** True for a lower-case path under game/ that names menu or title art with a picture extension (gui/*_menu aside). */
    static boolean isArt(String path) {
        if (SKIP.matcher(path).matches() || path.startsWith("gui/main_menu.") || path.startsWith("gui/game_menu.")) return false;
        String file = path.substring(path.lastIndexOf('/') + 1);
        for (String ext : EXTS) {
            if (file.endsWith(ext)) return ART_NAME.matcher(file.substring(0, file.length() - ext.length())).matches();
        }
        return false;
    }
}
