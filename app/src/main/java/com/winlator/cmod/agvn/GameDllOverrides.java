/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * AGVN game packages ship their own DLLs next to the exe: cheat/mod loaders (winmm/dinput8/version proxies, BepInEx
 * winhttp, UE4SS dwmapi/xinput) and DLLs tuned by AGVN to make the game lighter. Wine prefers its builtin copy of any
 * name it knows, so those files are silently ignored. Every DLL in the exe folder is therefore loaded native-first,
 * except core Windows/Wine system DLLs (replacing them breaks Wine) and graphics DLLs (already native via DXVK/VKD3D).
 */
public final class GameDllOverrides {
    /** Never forced native: Wine core, C runtime base and graphics stacks. */
    static final Set<String> KEEP_BUILTIN = new HashSet<>(Arrays.asList(
            "ntdll", "kernel32", "kernelbase", "user32", "gdi32", "win32u", "advapi32", "sechost", "rpcrt4",
            "ole32", "oleaut32", "combase", "shell32", "shlwapi", "msvcrt", "ucrtbase", "ws2_32", "imm32",
            "d3d8", "d3d9", "d3d10", "d3d10_1", "d3d10core", "d3d11", "d3d12", "d3d12core", "dxgi", "ddraw",
            "wined3d", "opengl32", "vulkan-1", "winevulkan", "dxcore"));
    static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.-]+$");
    static final Pattern MODE = Pattern.compile("^(n|b|n,b|b,n|)$");

    private GameDllOverrides() {}

    /** DLLs found in the exe folder (not subfolders), mapped to "n,b", minus {@link #KEEP_BUILTIN} and api-ms-*. */
    public static Map<String, String> detect(File exeDir) {
        Map<String, String> found = new TreeMap<>();
        File[] files = exeDir != null ? exeDir.listFiles() : null;
        if (files == null) return found;
        for (File f : files) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (!f.isFile() || !lower.endsWith(".dll")) continue;
            String name = lower.substring(0, lower.length() - 4);
            if (KEEP_BUILTIN.contains(name) || name.startsWith("api-ms-") || name.startsWith("ext-ms-")) continue;
            if (!NAME.matcher(name).matches()) continue;
            found.put(name, "n,b");
        }
        return found;
    }

    /** Detected + profile overrides (profile wins) as "a=n,b;b=n" or "" when empty. */
    public static String build(Map<String, String> detected, Map<String, String> fromProfile) {
        Map<String, String> all = new LinkedHashMap<>(detected);
        if (fromProfile != null) {
            for (Map.Entry<String, String> e : fromProfile.entrySet()) {
                String name = e.getKey().toLowerCase(Locale.ROOT).replaceAll("\\.dll$", "");
                all.put(name, e.getValue());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * Adds detected proxy DLLs of the launched exe to WINEDLLOVERRIDES (entries already set keep their mode).
     * Covers games added before this feature and games added from the file manager. {@code exePath} is the path
     * actually launched (after {@link AgvnExeRedirect}); DOS paths are mapped through the container drives.
     */
    public static void applyAtLaunch(EnvVars env, String exePath, com.winlator.cmod.container.Container container) {
        applyToExe(env, AgvnExeRedirect.toUnixPath(exePath, container));
    }

    /** Same with a drive letter -> unix folder map instead of a container. */
    static void applyAtLaunch(EnvVars env, String exePath, Map<String, String> drives) {
        applyToExe(env, AgvnExeRedirect.toUnixPath(exePath, drives));
    }

    /** {@code exeFile}: the exe's mapped path (unquoted, no args); .lnk files and null are ignored. */
    static void applyToExe(EnvVars env, String exeFile) {
        if (exeFile == null || exeFile.toLowerCase(Locale.ROOT).endsWith(".lnk")) return;
        Map<String, String> detected = detect(new File(exeFile).getParentFile());
        if (detected.isEmpty()) return;
        String existing = env.has("WINEDLLOVERRIDES") ? env.get("WINEDLLOVERRIDES") : "";
        String merged = merge(existing, detected);
        if (!merged.equals(existing)) {
            env.put("WINEDLLOVERRIDES", merged);
            android.util.Log.i("AGVN", "WINEDLLOVERRIDES=" + merged);
        }
    }

    /** Appends detected entries whose DLL name is not already listed in {@code existing}. */
    static String merge(String existing, Map<String, String> detected) {
        StringBuilder sb = new StringBuilder(existing == null ? "" : existing);
        String lower = ";" + sb.toString().toLowerCase(Locale.ROOT) + ";";
        for (Map.Entry<String, String> e : detected.entrySet()) {
            if (lower.contains(";" + e.getKey() + "=") || lower.contains("," + e.getKey() + "=")) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static boolean isValid(String name, String mode) {
        return name != null && mode != null && NAME.matcher(name).matches() && MODE.matcher(mode).matches();
    }
}
