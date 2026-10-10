/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds artwork a game already ships: a named image in the game folder (cover/poster/header/…), else engine art:
 * Unreal Content/Splash/Splash.bmp, the Ren'Py main menu ({@link AgvnRenpyArt}), the RPG Maker MV/MZ title
 * ({@link AgvnMvArt}) or XP/VX/VX Ace title ({@link AgvnRgssArt}), also when the game packs or encrypts it.
 * Pure Java, JVM-testable.
 */
final class AgvnCoverSources {
    static final String[] NAMES = {"agvn-cover", "cover", "poster", "folder", "boxart", "box", "capsule", "header", "banner", "key_art", "keyart"};
    static final String[] EXTS = {".png", ".jpg", ".jpeg", ".webp", ".bmp"};
    private static final String UNREAL_SPLASH = "Content/Splash/Splash";
    /** Pictures of one kind tried at most (the largest ones). */
    static final int MAX_EACH = 4;

    private AgvnCoverSources() {}

    /**
     * Pictures for {@code gameDir}, best first. The caller uses the first that decodes into a real picture: Ren'Py's
     * default menu background is one flat colour, so a later one can be needed.
     */
    static List<AgvnArt> candidates(File gameDir) {
        return candidates(gameDir, null);
    }

    /** {@code rtpRoot}: AGVN-Player/RTP, where an RPG Maker game's RTP title can be (null: the game folder only). */
    static List<AgvnArt> candidates(File gameDir, File rtpRoot) {
        List<AgvnArt> out = new ArrayList<>();
        if (gameDir == null || !gameDir.isDirectory()) return out;
        File local = findLocal(gameDir);
        if (local != null) add(out, AgvnArt.file(local));
        File splash = findUnrealSplash(gameDir);
        if (splash != null) add(out, AgvnArt.file(splash));
        AgvnRenpyArt renpy = AgvnRenpyArt.of(gameDir);
        if (renpy != null) renpy.addCovers(out);
        AgvnMvArt.addCovers(gameDir, out);
        AgvnRgssArt.addCovers(gameDir, out, rtpRoot);
        return out;
    }

    /** An icon the game ships that its engine did not make (a Ren'Py window icon of the developer's own), or null. */
    static AgvnArt icon(File gameDir) {
        AgvnRenpyArt renpy = AgvnRenpyArt.of(gameDir);
        return renpy != null ? renpy.icon() : null;
    }

    /** Adds {@code art} unless it is null or already listed. */
    static void add(List<AgvnArt> out, AgvnArt art) {
        if (art == null) return;
        for (AgvnArt a : out) if (a.name.equals(art.name)) return;
        out.add(art);
    }

    /** Adds the {@link #MAX_EACH} largest of {@code pictures}, largest first: when one cannot be read the next is tried. */
    static void addLargest(List<AgvnArt> out, List<AgvnArt> pictures) {
        List<AgvnArt> sorted = new ArrayList<>(pictures);
        sorted.sort((a, b) -> Long.compare(b.size, a.size));
        for (int i = 0; i < Math.min(MAX_EACH, sorted.size()); i++) add(out, sorted.get(i));
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

    /** Unreal's splash in {@code gameDir} or one folder below it (the project folder), or null. */
    static File findUnrealSplash(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory()) return null;
        File found = splashIn(gameDir);
        if (found != null) return found;
        File[] subs = gameDir.listFiles(File::isDirectory);
        if (subs == null) return null;
        java.util.Arrays.sort(subs);
        for (File sub : subs) {
            if (sub.getName().equalsIgnoreCase("Engine")) continue;
            found = splashIn(sub);
            if (found != null) return found;
        }
        return null;
    }

    private static File splashIn(File dir) {
        for (String ext : EXTS) {
            File f = new File(dir, UNREAL_SPLASH + ext);
            if (f.isFile()) return f;
        }
        return null;
    }
}
