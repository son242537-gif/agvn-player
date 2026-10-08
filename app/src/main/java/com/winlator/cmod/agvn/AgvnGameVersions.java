/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A new version of a game already in the library: AGVN releases a game again in a new folder ("…-v0.7.6-VIET-…").
 * The library knows games by folder, so the new version was a new game: it went to Proton 10's container while the
 * old one was on Proton 9's, and none of the saves the old one kept in its container (Unity's LocalLow and
 * PlayerPrefs, Unreal's SaveGames, Ren'Py's AppData) were there. The player lost the save, and the first start was
 * long (a Wine of its own, no shader cache). The new version now goes to the old one's container, where those saves
 * already are, and saves kept in the old game folder (RPG Maker, Ren'Py, KiriKiri...) are copied into the new folder
 * when it has none.
 * Same game: the same exe name, and for Unity the same company and product (app.info, what Unity keys saves on), else
 * the same title without its version numbers.
 */
final class AgvnGameVersions {
    private static final String TAG = "AGVN";
    /** "v0.7.6", "1.044", "v2": a version; a lone "2" is a sequel's number and stays. */
    private static final Pattern VERSION = Pattern.compile("v\\d+([._]\\d+)*[a-z]?|\\d+([._]\\d+)+[a-z]?");

    private AgvnGameVersions() {}

    /** The library game that is another version of the one being added ({@code name}, {@code exe}), or null. */
    static Shortcut older(List<Shortcut> library, String name, File exe) {
        String[] unity = null;
        boolean unityRead = false;
        for (Shortcut s : library) {
            File other = exeOf(s);
            if (!other.getName().equalsIgnoreCase(exe.getName())) continue; // cheap test first: app.info is a read
            if (other.getParentFile() != null && other.getParentFile().equals(exe.getParentFile())) continue;
            if (!unityRead) {
                unity = AgvnSaveLocations.unityNames(exe);
                unityRead = true;
            }
            if (sameGame(name, exe.getName(), unity, s.name, other.getName(), AgvnSaveLocations.unityNames(other)))
                return s;
        }
        return null;
    }

    static boolean sameGame(String name, String exeName, String[] unity, String otherName, String otherExe,
                            String[] otherUnity) {
        if (exeName == null || !exeName.equalsIgnoreCase(otherExe)) return false;
        if (unity != null && otherUnity != null)
            return unity[0].equalsIgnoreCase(otherUnity[0]) && unity[1].equalsIgnoreCase(otherUnity[1]);
        String key = key(name);
        return !key.isEmpty() && key.equals(key(otherName));
    }

    /** "Thorn Sin v0.7.5" and "Thorn Sin 0.7.6" -> "thorn sin"; accents and case left out. */
    static String key(String name) {
        List<String> words = new ArrayList<>();
        for (String w : AgvnGameTitle.searchKey(name).split("[^\\p{L}\\p{N}._]+"))
            if (!w.isEmpty() && !VERSION.matcher(w).matches()) words.add(w);
        return String.join(" ", words);
    }

    /**
     * Saves of {@code from}'s game folder copied into {@code to}'s, place by place, where {@code to} has none (a save
     * of the new version is never overwritten): the number of places copied.
     */
    static int copyFolderSaves(Shortcut from, Shortcut to) {
        return copyFolderSaves(AgvnSaveLocations.find(from), AgvnSaveLocations.find(to));
    }

    static int copyFolderSaves(List<AgvnSaveLocations.Location> from, List<AgvnSaveLocations.Location> targets) {
        int copied = 0;
        for (AgvnSaveLocations.Location src : from) {
            if (!src.zipPath.equals("game") && !src.zipPath.startsWith("game/")) continue;
            AgvnSaveLocations.Location dst = byZipPath(targets, src.zipPath);
            List<File> saves = saves(src);
            if (dst == null || saves.isEmpty() || !saves(dst).isEmpty() || same(src.dir, dst.dir)) continue;
            boolean ok = dst.dir.isDirectory() || dst.dir.mkdirs();
            for (File f : saves) ok &= FileUtils.copy(f, new File(dst.dir, f.getName()));
            Log.i(TAG, "saves " + (ok ? "copied" : "partly copied") + " from " + src.dir + " to " + dst.dir);
            copied++;
        }
        return copied;
    }

    /** The save files and folders of a place: all it holds, or for a game folder only the save files. */
    static List<File> saves(AgvnSaveLocations.Location place) {
        List<File> out = new ArrayList<>();
        File[] files = place.dir.listFiles();
        if (files == null) return out;
        for (File f : files) if (place.files == null || f.isFile() && place.takes(f.getName())) out.add(f);
        return out;
    }

    private static AgvnSaveLocations.Location byZipPath(List<AgvnSaveLocations.Location> places, String zipPath) {
        for (AgvnSaveLocations.Location p : places) if (p.zipPath.equals(zipPath)) return p;
        return null;
    }

    private static boolean same(File a, File b) {
        try {
            return a.getCanonicalPath().toLowerCase(Locale.ROOT).equals(b.getCanonicalPath().toLowerCase(Locale.ROOT));
        } catch (IOException e) {
            return a.equals(b);
        }
    }

    private static File exeOf(Shortcut s) {
        String unix = AgvnExeRedirect.toUnixPath(s.path, s.container);
        return new File(unix != null ? unix : s.path.replace("\"", ""));
    }
}
