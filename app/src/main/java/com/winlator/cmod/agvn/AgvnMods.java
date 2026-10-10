/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mod loaders beside a game's exe. BepInEx, MelonLoader and UE4SS keep a folder there, and the game loads them through
 * a Windows DLL beside it that Wine takes instead of its own ({@link GameDllOverrides}: native first). "Tự sửa lỗi"
 * can start the game with Wine's own copy of that DLL ("mods-off"): the loader, and every mod with it, stays out, and
 * the game's files stay as they are. Rina (07/10/2026: BepInEx for IL2CPP through winhttp.dll) froze before Unity or
 * BepInEx wrote a line of log. Pure Java (JVM-testable).
 */
final class AgvnMods {
    /** A loader's folder, then the DLLs it is loaded through: Doorstop's for BepInEx, MelonLoader's, UE4SS's. */
    static final List<List<String>> LOADERS = Arrays.asList(
            Arrays.asList("BepInEx", "winhttp", "version", "winmm"),
            Arrays.asList("MelonLoader", "version", "winmm", "winhttp", "dinput8", "dsound"),
            Arrays.asList("ue4ss", "dwmapi", "xinput1_3"));
    static final String OVERRIDES = "WINEDLLOVERRIDES";

    private AgvnMods() {}

    /** The loaders beside the exe in {@code exeDir} (their folder's name), with the DLLs of theirs found there. */
    static Map<String, List<String>> found(File exeDir) {
        Map<String, List<String>> found = new LinkedHashMap<>();
        File[] files = exeDir != null ? exeDir.listFiles() : null;
        if (files == null) return found;
        for (List<String> loader : LOADERS) {
            String folder = null;
            List<String> dlls = new ArrayList<>();
            for (File f : files) {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (f.isDirectory() && name.equals(loader.get(0).toLowerCase(Locale.ROOT))) folder = f.getName();
                if (f.isFile() && name.endsWith(".dll") && loader.contains(name.substring(0, name.length() - 4))) {
                    dlls.add(name.substring(0, name.length() - 4));
                }
            }
            if (folder != null && !dlls.isEmpty()) found.put(folder, dlls);
        }
        return found;
    }

    /** The DLLs of every loader found ({@link #found}), each once. */
    static List<String> proxies(Map<String, List<String>> found) {
        List<String> all = new ArrayList<>();
        for (List<String> dlls : found.values()) for (String dll : dlls) if (!all.contains(dll)) all.add(dll);
        return all;
    }

    /** The game's own environment {@code env} with each of {@code dlls} loaded as Wine's own copy ("b"). */
    static String withBuiltin(String env, List<String> dlls) {
        EnvVars vars = new EnvVars(env == null ? "" : env);
        StringBuilder out = new StringBuilder();
        for (String entry : vars.get(OVERRIDES).split(";")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            List<String> names = new ArrayList<>();
            for (String name : entry.substring(0, eq).split(",")) {
                if (!name.isEmpty() && !dlls.contains(name.toLowerCase(Locale.ROOT))) names.add(name);
            }
            if (names.isEmpty()) continue;
            if (out.length() > 0) out.append(';');
            out.append(String.join(",", names)).append(entry.substring(eq));
        }
        for (String dll : dlls) out.append(out.length() > 0 ? ";" : "").append(dll).append("=b");
        vars.put(OVERRIDES, out.toString());
        return vars.toString();
    }

    /** True when {@code env} already loads each of {@code dlls} as Wine's own copy. */
    static boolean off(String env, List<String> dlls) {
        String overrides = ";" + new EnvVars(env == null ? "" : env).get(OVERRIDES).toLowerCase(Locale.ROOT) + ";";
        for (String dll : dlls) if (!overrides.contains(";" + dll + "=b;")) return false;
        return !dlls.isEmpty();
    }
}
