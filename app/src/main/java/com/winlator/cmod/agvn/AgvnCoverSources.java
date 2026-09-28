/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.Locale;

/**
 * Finds artwork a game already ships: a named image in the game folder (cover/poster/header/…), else engine art
 * (Unreal Content/Splash/Splash.bmp, RPG Maker img/titles1, Ren'Py gui/main_menu). Pure Java, JVM-testable.
 */
final class AgvnCoverSources {
    static final String[] NAMES = {"agvn-cover", "cover", "poster", "folder", "boxart", "box", "capsule", "header", "banner", "key_art", "keyart"};
    static final String[] EXTS = {".png", ".jpg", ".jpeg", ".webp", ".bmp"};
    private static final String[] ENGINE_FILES = {"Content/Splash/Splash", "game/gui/main_menu", "gui/main_menu"};
    private static final String[] TITLE_DIRS = {"www/img/titles1", "img/titles1"};

    private AgvnCoverSources() {}

    /** Best image for {@code gameDir}, or null. */
    static File find(File gameDir) {
        File local = findLocal(gameDir);
        return local != null ? local : findEngineArt(gameDir);
    }

    /** First matching image directly in {@code gameDir} (case-insensitive name), or null. */
    static File findLocal(File gameDir) {
        File[] files = gameDir != null ? gameDir.listFiles(File::isFile) : null;
        if (files == null) return null;
        for (String name : NAMES) {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                for (String ext : EXTS) if (n.equals(name + ext)) return f;
            }
        }
        return null;
    }

    /** Engine splash/title art in {@code gameDir} or one folder below it (e.g. the Unreal project folder). */
    static File findEngineArt(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory()) return null;
        File found = engineArtIn(gameDir);
        if (found != null) return found;
        File[] subs = gameDir.listFiles(File::isDirectory);
        if (subs == null) return null;
        java.util.Arrays.sort(subs);
        for (File sub : subs) {
            if (sub.getName().equalsIgnoreCase("Engine")) continue;
            found = engineArtIn(sub);
            if (found != null) return found;
        }
        return null;
    }

    private static File engineArtIn(File dir) {
        for (String path : ENGINE_FILES) {
            for (String ext : EXTS) {
                File f = new File(dir, path + ext);
                if (f.isFile()) return f;
            }
        }
        for (String path : TITLE_DIRS) {
            File largest = largestImage(new File(dir, path));
            if (largest != null) return largest;
        }
        return null;
    }

    /** Largest .png/.jpg in {@code dir} (encrypted RPG Maker images like .png_ are skipped), or null. */
    private static File largestImage(File dir) {
        File[] files = dir.listFiles(File::isFile);
        File best = null;
        if (files == null) return null;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!(n.endsWith(".png") || n.endsWith(".jpg"))) continue;
            if (best == null || f.length() > best.length()) best = f;
        }
        return best;
    }
}
