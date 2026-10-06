/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The first line of a game's session events (su-kien.txt): its exe, the engine AGVN saw, the DLLs next to the exe (a
 * mod loader's among them: BepInEx's winhttp, MelonLoader's version) and mod folders. A log sent from a player's
 * phone then says what ran, which the Wine and engine logs alone did not (Rina, Isekai NTR Inn, 07/10/2026).
 */
final class AgvnGameFacts {
    static final String[] MOD_FOLDERS = {"BepInEx", "MelonLoader", "ue4ss"};
    static final int MAX_DLLS = 40;

    private AgvnGameFacts() {}

    static String of(Shortcut s) {
        return describe(AgvnEngineLogs.exe(s), s.getExtra(AgvnGameImporter.EXTRA_ENGINE));
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
}
