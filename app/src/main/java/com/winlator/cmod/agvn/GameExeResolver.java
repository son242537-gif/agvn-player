/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Guesses the engine of a Windows game folder and the exe that should be launched.
 * Unreal: <Project>/Binaries/Win64/<Project>-Win64-Shipping.exe (the root <Game>.exe is only a bootstrap that
 * needs VC++/.NET redists under Wine). Other engines: the single sensible exe in the folder root.
 */
public final class GameExeResolver {
    public enum Engine { UNREAL, UNITY, GODOT, GAMEMAKER, RENPY, UNKNOWN }

    private static final List<String> IGNORED = Arrays.asList(
            "unitycrashhandler64.exe", "unitycrashhandler32.exe", "crashreportclient.exe", "dxsetup.exe",
            "vc_redist.x64.exe", "vc_redist.x86.exe", "vcredist_x64.exe", "vcredist_x86.exe", "dotnetfx.exe",
            "ue4prereqsetup_x64.exe", "ueprereqsetup_x64.exe", "notification_helper.exe");

    private GameExeResolver() {}

    public static Engine detectEngine(File gameDir) {
        if (findShipping(gameDir) != null || new File(gameDir, "Engine/Binaries").isDirectory()) return Engine.UNREAL;
        File[] children = listOrEmpty(gameDir);
        for (File f : children) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory() && n.endsWith("_data") && new File(f, "globalgamemanagers").exists()) return Engine.UNITY;
            if (f.isFile() && n.equals("unityplayer.dll")) return Engine.UNITY;
        }
        for (File f : children) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && n.endsWith(".pck")) return Engine.GODOT;
            if (f.isFile() && n.equals("data.win")) return Engine.GAMEMAKER;
            if (f.isDirectory() && n.equals("renpy")) return Engine.RENPY;
        }
        return Engine.UNKNOWN;
    }

    /** Exe path relative to {@code gameDir} with '/' separators, or null when nothing fits. */
    public static String resolveExe(File gameDir, Engine engine) {
        if (engine == Engine.UNREAL) {
            File shipping = findShipping(gameDir);
            if (shipping != null) return relative(gameDir, shipping);
        }
        List<File> exes = new ArrayList<>();
        for (File f : listOrEmpty(gameDir)) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && n.endsWith(".exe") && !IGNORED.contains(n) && !n.startsWith("unins")) exes.add(f);
        }
        if (exes.isEmpty()) return null;
        if (engine == Engine.UNITY) {
            for (File exe : exes) {
                String base = exe.getName().substring(0, exe.getName().length() - 4);
                if (new File(gameDir, base + "_Data").isDirectory()) return exe.getName();
            }
        }
        if (exes.size() == 1) return exes.get(0).getName();
        for (File exe : exes) {
            String n = exe.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("launcher") && !n.contains("setup") && !n.contains("config")) return exe.getName();
        }
        return exes.get(0).getName();
    }

    /** Searches <dir>/<Project>/Binaries/Win64/*-Win64-Shipping.exe and <dir>/Binaries/Win64. */
    static File findShipping(File gameDir) {
        List<File> roots = new ArrayList<>();
        roots.add(gameDir);
        for (File f : listOrEmpty(gameDir)) if (f.isDirectory() && !f.getName().equalsIgnoreCase("Engine")) roots.add(f);
        for (File root : roots) {
            File win64 = new File(root, "Binaries/Win64");
            for (File f : listOrEmpty(win64)) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith("-shipping.exe")) return f;
            }
        }
        return null;
    }

    private static File[] listOrEmpty(File dir) {
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null) return new File[0];
        Arrays.sort(files);
        return files;
    }

    private static String relative(File base, File file) {
        String b = base.getAbsolutePath();
        String f = file.getAbsolutePath();
        String rel = f.startsWith(b + File.separator) ? f.substring(b.length() + 1) : file.getName();
        return rel.replace(File.separatorChar, '/');
    }
}
