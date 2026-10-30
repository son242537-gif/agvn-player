/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An Unreal game's logs ({@link AgvnEngineLogs}): {@code Saved/Logs} and its newest {@code Saved/Crashes/<crash>/}
 * (the crash's own log and CrashContext.runtime-xml; not its minidump), in the game's folder and in
 * {@code %LOCALAPPDATA%\<Project>}. The project is the folder above {@code Binaries/Win64} of the exe, a folder of the
 * game with {@code Binaries} in it, or the game folder's name: an Unreal Engine 5 game whose folder was not named after
 * its project had "Nhật ký engine đã chép: 0 file" (Adreno 830, 30/10/2026). Pure Java (JVM-testable).
 */
final class AgvnUnrealLogs {
    /** The newest logs of a Saved/Logs folder (the game's, then its backups). */
    static final int LOGS = 2;

    private AgvnUnrealLogs() {}

    /** The files to copy; missing ones are skipped by the copy. {@code profile}: Windows users/xuser. */
    static List<File> files(File exe, File gameDir, File profile) {
        List<File> out = new ArrayList<>();
        for (Map.Entry<String, File> p : projects(exe, gameDir).entrySet()) {
            List<File> saved = new ArrayList<>();
            if (p.getValue() != null) saved.add(new File(p.getValue(), "Saved"));
            saved.add(new File(profile, "AppData/Local/" + p.getKey() + "/Saved"));
            for (File dir : saved) {
                out.add(new File(dir, "Logs/" + p.getKey() + ".log"));
                for (File log : newest(new File(dir, "Logs"), LOGS, false)) if (!out.contains(log)) out.add(log);
                for (File crash : newest(new File(dir, "Crashes"), 1, true)) out.addAll(crashFiles(crash));
            }
        }
        return out;
    }

    /** Project name to its folder in the game (null when only the name is known), in the order they are found. */
    static Map<String, File> projects(File exe, File gameDir) {
        Map<String, File> out = new LinkedHashMap<>();
        File bin = exe.getParentFile(), binaries = bin != null ? bin.getParentFile() : null;
        if (binaries != null && binaries.getName().equalsIgnoreCase("Binaries") && binaries.getParentFile() != null)
            put(out, binaries.getParentFile());
        File[] dirs = gameDir != null ? gameDir.listFiles(f -> f.isDirectory() && new File(f, "Binaries").isDirectory()) : null;
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File d : dirs) put(out, d);
        }
        if (gameDir != null && !out.containsKey(gameDir.getName())) out.put(gameDir.getName(), new File(gameDir, gameDir.getName()));
        return out;
    }

    private static void put(Map<String, File> out, File project) {
        if (!project.getName().equalsIgnoreCase("Engine") && !out.containsKey(project.getName())) out.put(project.getName(), project);
    }

    /** The {@code count} newest .log files, or folders, of {@code dir}. */
    private static List<File> newest(File dir, int count, boolean folders) {
        File[] found = dir.listFiles(f -> folders ? f.isDirectory() : f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".log"));
        if (found == null) return new ArrayList<>();
        Arrays.sort(found, Comparator.comparingLong(File::lastModified).reversed());
        return new ArrayList<>(Arrays.asList(found).subList(0, Math.min(count, found.length)));
    }

    /** A crash folder's log and its CrashContext.runtime-xml (the error and call stack Unreal wrote). */
    private static List<File> crashFiles(File crash) {
        File[] files = crash.listFiles(f -> {
            String n = f.getName().toLowerCase(Locale.ROOT);
            return f.isFile() && (n.endsWith(".log") || n.endsWith(".runtime-xml") || n.endsWith(".xml"));
        });
        List<File> out = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files);
            out.addAll(Arrays.asList(files));
        }
        return out;
    }
}
