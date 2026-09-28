/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cheat menus, translations and mod loaders ship "proxy" DLLs next to the game exe (Ultimate ASI Loader dinput8/
 * version/winmm, BepInEx/Doorstop winhttp, UE4SS dwmapi/xinput1_3 ...). Wine prefers its own builtin copy of those
 * names, so the mod silently never loads. This builds WINEDLLOVERRIDES entries (native first) for the ones present.
 * Graphics DLLs (d3d*, dxgi) are already native through DXVK and are left alone.
 */
public final class GameDllOverrides {
    static final String[] PROXY_DLLS = {
            "dinput8", "version", "winmm", "winhttp", "dsound", "dwmapi",
            "xinput1_3", "xinput1_4", "xinput9_1_0", "xinput1_1", "xinput1_2"};
    static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.-]+$");
    static final Pattern MODE = Pattern.compile("^(n|b|n,b|b,n|)$");

    private GameDllOverrides() {}

    /** Proxy DLLs found in the exe folder, mapped to "n,b". */
    public static Map<String, String> detect(File exeDir) {
        Map<String, String> found = new LinkedHashMap<>();
        File[] files = exeDir != null ? exeDir.listFiles() : null;
        if (files == null) return found;
        for (String dll : PROXY_DLLS) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).equals(dll + ".dll")) found.put(dll, "n,b");
            }
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
     * Covers games added before this feature and games added from the file manager. Unix exe paths only.
     */
    public static void applyAtLaunch(com.winlator.cmod.core.EnvVars env, String exePath) {
        if (exePath == null) return;
        String path = exePath.replace("\"", "").trim();
        if (!path.startsWith("/")) return;
        Map<String, String> detected = detect(new File(path).getParentFile());
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
