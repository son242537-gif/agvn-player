/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Guesses the engine of a Windows game folder and the exe that should be launched.
 * Unreal: <Project>/Binaries/Win64/<Project>-Win64-Shipping.exe (the root <Game>.exe is only a bootstrap that
 * needs VC++/.NET redists under Wine). Other engines: the single sensible exe in the folder root, or each game of a
 * collection ({@link #gameExes}).
 */
public final class GameExeResolver {
    /** Stored by name in the shortcut extra "agvnEngine": never rename or remove a value. */
    public enum Engine { UNREAL, UNITY, GODOT, GAMEMAKER, RENPY, KIRIKIRI, TYRANO, SIGLUS, NSCRIPTER, RPGMAKER, RPGMAKER_MV, WOLFRPG, UNKNOWN }

    private static final List<String> IGNORED = Arrays.asList(
            "unitycrashhandler64.exe", "unitycrashhandler32.exe", "crashreportclient.exe", "dxsetup.exe",
            "vc_redist.x64.exe", "vc_redist.x86.exe", "vcredist_x64.exe", "vcredist_x86.exe", "dotnetfx.exe",
            "ue4prereqsetup_x64.exe", "ueprereqsetup_x64.exe", "notification_helper.exe");
    /** Name parts of exes that only serve a game (launcher, setup, crash reporter, settings...), never a game of their own. */
    private static final String[] HELPERS = {"launcher", "setup", "config", "crash", "report", "update", "patch", "install",
            "redist", "setting", "editor", "server", "tool", "helper", "physx", "dotnet", "oalinst", "nwjc", "bssndrpt"};
    /** More game exes than this in one folder: a folder of tools rather than a collection, so it stays one game. */
    static final int MAX_GAMES = 8;

    private GameExeResolver() {}

    /**
     * Cheap fingerprint of the folder top level (plus a few fixed sub-paths). Order matters: Siglus ships Scene.pck, so it
     * must win over the generic *.pck (Godot) rule. Mirrored by tools/agvn/agvn_profile_lib.py detect_engine.
     */
    public static Engine detectEngine(File gameDir) {
        if (findShipping(gameDir) != null || new File(gameDir, "Engine/Binaries").isDirectory()) return Engine.UNREAL;
        File[] children = listOrEmpty(gameDir);
        for (File f : children) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory() && n.endsWith("_data") && new File(f, "globalgamemanagers").exists()) return Engine.UNITY;
            if (f.isFile() && n.equals("unityplayer.dll")) return Engine.UNITY;
        }
        Map<String, File> top = new HashMap<>();
        for (File f : children) top.put(f.getName().toLowerCase(Locale.ROOT), f);
        if (isFile(top.get("scene.pck")) || isFile(top.get("gameexe.dat"))) return Engine.SIGLUS;
        if (isFile(path(top, "www", "js", "rpg_core.js")) || isFile(path(top, "www", "js", "rmmz_core.js"))
                || isFile(path(top, "js", "rpg_core.js")) || isFile(path(top, "js", "rmmz_core.js"))) return Engine.RPGMAKER_MV;
        if (isRpgMaker(top)) return Engine.RPGMAKER;
        if (anyFile(children, "", ".wolf") || anyFile(listOrEmpty(path(top, "data")), "", ".wolf")
                || isFile(top.get("gurugurusmf4.dll"))) return Engine.WOLFRPG;
        if (anyFile(children, "", ".xp3")) return Engine.KIRIKIRI;
        if (anyFile(children, "", ".nsa") || isFile(top.get("nscript.dat"))) return Engine.NSCRIPTER;
        if (isDir(top.get("tyrano")) || isFile(path(top, "data", "system", "config.tjs"))) return Engine.TYRANO;
        File[] renpyGame = listOrEmpty(path(top, "game"));
        if (isDir(top.get("renpy")) || anyFile(renpyGame, "", ".rpa", ".rpyc")) return Engine.RENPY;
        if (anyFile(children, "", ".pck") || embedsGodot(children)) return Engine.GODOT;
        if (isFile(top.get("data.win"))) return Engine.GAMEMAKER;
        return Engine.UNKNOWN;
    }

    /** A Godot game exported as one exe ("Embed PCK"): no .pck beside it, its pack ends the exe (AgvnGodotFiles). */
    private static boolean embedsGodot(File[] children) {
        int read = 0;
        for (File f : children) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) continue;
            if (read++ == MAX_GAMES) return false; // a folder of tools, not one game
            if (AgvnGodotFiles.version(f) != null) return true;
        }
        return false;
    }

    /** RPG Maker 2000/2003 (RPG_RT.*) or XP/VX/VX Ace (Game.ini with an RGSS dll, encrypted archive or Data/ files). */
    private static boolean isRpgMaker(Map<String, File> top) {
        if (isFile(top.get("rpg_rt.ini")) || isFile(top.get("rpg_rt.ldb"))) return true;
        if (!isFile(top.get("game.ini"))) return false;
        File[] children = top.values().toArray(new File[0]);
        File[] data = listOrEmpty(path(top, "data"));
        return anyFile(children, "rgss", ".dll") || anyFile(listOrEmpty(path(top, "system")), "rgss", ".dll")
                || anyFile(children, "", ".rgssad", ".rgss2a", ".rgss3a") || anyFile(data, "", ".rxdata", ".rvdata", ".rvdata2");
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
        for (File exe : exes) if (!isHelper(exe.getName().toLowerCase(Locale.ROOT))) return exe.getName();
        for (File exe : exes) {
            String n = exe.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("launcher") && !n.contains("setup") && !n.contains("config")) return exe.getName();
        }
        return exes.get(0).getName();
    }

    /**
     * The exes that each start a game: one per game for a collection that ships several games in one folder (often next
     * to a launcher that only starts one of them), else just {@link #resolveExe}; empty when nothing fits. Only a folder
     * of an unknown engine can be a collection: a known engine has one main exe.
     */
    public static List<String> gameExes(File gameDir, Engine engine) {
        List<String> games = new ArrayList<>();
        if (engine == Engine.UNKNOWN) {
            for (File f : listOrEmpty(gameDir)) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && n.endsWith(".exe") && !IGNORED.contains(n) && !n.startsWith("unins") && !isHelper(n))
                    games.add(f.getName());
            }
        }
        if (games.size() > 1 && games.size() <= MAX_GAMES) return games;
        String main = resolveExe(gameDir, engine);
        return main != null ? Collections.singletonList(main) : Collections.<String>emptyList();
    }

    private static boolean isHelper(String lowerName) {
        for (String part : HELPERS) if (lowerName.contains(part)) return true;
        return false;
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

    /** Follows lower-case path segments case-insensitively (game folders come from Windows); null when missing. */
    private static File path(Map<String, File> top, String... segments) {
        File f = top.get(segments[0]);
        for (int i = 1; f != null && i < segments.length; i++) {
            File next = null;
            for (File c : listOrEmpty(f)) if (c.getName().toLowerCase(Locale.ROOT).equals(segments[i])) next = c;
            f = next;
        }
        return f;
    }

    /** True when one of {@code files} is a file whose lower-case name starts with {@code prefix} and has one of the suffixes. */
    private static boolean anyFile(File[] files, String prefix, String... suffixes) {
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!n.startsWith(prefix)) continue;
            for (String suffix : suffixes) if (n.endsWith(suffix) && f.isFile()) return true;
        }
        return false;
    }

    private static boolean isFile(File f) {
        return f != null && f.isFile();
    }

    private static boolean isDir(File f) {
        return f != null && f.isDirectory();
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
