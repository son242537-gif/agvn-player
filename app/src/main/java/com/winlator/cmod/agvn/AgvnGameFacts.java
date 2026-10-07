/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The first lines of a game's session events (su-kien.txt): its exe, the engine AGVN saw, the DLLs next to the exe (a
 * mod loader's among them: BepInEx's winhttp, MelonLoader's version) and mod folders. A log sent from a player's
 * phone then says what ran, which the Wine and engine logs alone did not (Rina, Isekai NTR Inn, 07/10/2026). For an
 * engine AGVN does not know, or a game with mods, also what its folder and its mod folders hold, and what its exe
 * says of itself ({@link AgvnPeFacts}): Isekai NTR Inn's engine was still unknown after a second log, and Rina's
 * BepInEx had never written its own log.
 */
final class AgvnGameFacts {
    static final String[] MOD_FOLDERS = {"BepInEx", "MelonLoader", "ue4ss"};
    static final int MAX_DLLS = 40, MAX_ENTRIES = 40;

    private AgvnGameFacts() {}

    static List<String> of(Shortcut s) {
        return facts(AgvnEngineLogs.exe(s), s.getExtra(AgvnGameImporter.EXTRA_ENGINE));
    }

    static List<String> facts(File exe, String engine) {
        List<String> facts = new ArrayList<>(Collections.singletonList(describe(exe, engine)));
        File dir = exe.getParentFile();
        boolean unknown = engine == null || engine.isEmpty() || engine.equals(GameExeResolver.Engine.UNKNOWN.name());
        List<String> mods = new ArrayList<>();
        for (String name : MOD_FOLDERS) {
            File folder = new File(dir, name);
            if (dir != null && folder.isDirectory()) mods.add(listing("Thư mục " + name, folder, false));
        }
        if (dir != null && (unknown || !mods.isEmpty())) facts.add(listing("Thư mục game", dir, true));
        facts.addAll(mods);
        String pe = unknown ? AgvnPeFacts.describe(exe) : null;
        if (pe != null) facts.add(pe);
        return facts;
    }

    static String describe(File exe, String engine) {
        File dir = exe.getParentFile();
        File[] files = dir != null ? dir.listFiles() : null;
        List<String> dlls = new ArrayList<>();
        for (File f : files != null ? files : new File[0]) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".dll")) dlls.add(f.getName());
        }
        Collections.sort(dlls, String.CASE_INSENSITIVE_ORDER);
        int more = dlls.size() - MAX_DLLS;
        if (more > 0) dlls = dlls.subList(0, MAX_DLLS);
        List<String> mods = new ArrayList<>();
        for (String name : MOD_FOLDERS) if (dir != null && new File(dir, name).isDirectory()) mods.add(name);
        return "Game: " + exe.getName() + " · engine " + (engine == null || engine.isEmpty() ? "chưa rõ" : engine)
                + " · DLL cạnh exe: " + (dlls.isEmpty() ? "không có" : String.join(", ", dlls))
                + (more > 0 ? " (+" + more + ")" : "")
                + (mods.isEmpty() ? "" : " · mod: " + String.join(", ", mods));
    }

    /**
     * "{@code title}: a/, b.exe 402 MB, c.txt": what {@code dir} holds at its top level, sorted. The game's own
     * folder ({@code game}) leaves out its DLLs (in the line above) and gives the size of big files only; a mod folder
     * gives every file's, an empty log included ("trống").
     */
    static String listing(String title, File dir, boolean game) {
        File[] files = dir.listFiles();
        List<File> all = files != null ? new ArrayList<>(Arrays.asList(files)) : new ArrayList<>();
        Collections.sort(all, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        List<String> entries = new ArrayList<>();
        for (File f : all) {
            String name = f.getName();
            if (f.isDirectory()) entries.add(name + "/");
            else if (!game) entries.add(name + " " + size(f.length()));
            else if (!name.toLowerCase(Locale.ROOT).endsWith(".dll")) {
                entries.add(f.length() >= 1 << 20 ? name + " " + size(f.length()) : name);
            }
        }
        int more = entries.size() - MAX_ENTRIES;
        String shown = entries.isEmpty() ? "trống"
                : String.join(", ", more > 0 ? entries.subList(0, MAX_ENTRIES) : entries);
        return title + ": " + shown + (more > 0 ? " (+" + more + ")" : "");
    }

    /** "trống", "12 KB", "402 MB", "1,2 GB". */
    static String size(long bytes) {
        if (bytes <= 0) return "trống";
        if (bytes < 1 << 20) return Math.max(1, bytes >> 10) + " KB";
        if (bytes < 1L << 30) return (bytes >> 20) + " MB";
        return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30)).replace('.', ',');
    }
}
