/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.core.WineRegistryEditor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marks the Visual C++ 2015-2022 runtime as installed in the Wine prefix. Wine ships builtin msvcp140/vcruntime140
 * that run these games, but launchers and installers read HKLM\SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes and
 * ask the player to install "Visual C++" when the keys are missing. The keys are written into system.reg before Wine
 * starts, only when missing or older than {@link #VERSION}; otherwise the file is read once and left untouched.
 */
public final class VcRuntimeMarker {
    private static final String TAG = "AGVN";
    static final int MAJOR = 14;
    static final int MINOR = 44;
    static final int BLD = 35211;
    static final int RBLD = 0;
    static final String VERSION = "v14.44.35211.00";
    static final String SERVICING_VERSION = "14.44.35211";

    private static final String RUNTIMES = "Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\";
    private static final String SERVICING = "Microsoft\\DevDiv\\vc\\Servicing\\14.0\\RuntimeMinimum";
    /** system.reg key form (relative to HKLM, 32-bit view under Wow6432Node, same casing as the prefix template). */
    static final String[] RUNTIME_KEYS = {
            "Software\\" + RUNTIMES + "x64", "Software\\" + RUNTIMES + "x86",
            "Software\\Wow6432Node\\" + RUNTIMES + "x64", "Software\\Wow6432Node\\" + RUNTIMES + "x86"};
    static final String[] SERVICING_KEYS = {"Software\\" + SERVICING, "Software\\Wow6432Node\\" + SERVICING};

    private VcRuntimeMarker() {}

    /** Writes the missing or outdated markers into {@code systemReg}. Never throws: a failure must not block launch. */
    public static void apply(File systemReg) {
        try {
            if (systemReg == null || !systemReg.isFile()) return;
            List<String> stale = staleKeys(readValues(systemReg));
            if (stale.isEmpty()) return;
            try (WineRegistryEditor editor = new WineRegistryEditor(systemReg)) {
                for (String key : stale) write(editor, key);
            }
            Log.i(TAG, "Visual C++ 2015-2022 registry markers written: " + stale);
        } catch (Exception e) {
            Log.w(TAG, "cannot write Visual C++ registry markers", e);
        }
    }

    /** Marker keys whose values are missing or older than ours, in write order. */
    static List<String> staleKeys(Map<String, Map<String, String>> values) {
        List<String> stale = new ArrayList<>();
        for (String key : RUNTIME_KEYS) if (!isCurrentRuntime(values.get(key))) stale.add(key);
        for (String key : SERVICING_KEYS) if (!isCurrentServicing(values.get(key))) stale.add(key);
        return stale;
    }

    static boolean isCurrentRuntime(Map<String, String> v) {
        if (v == null || dword(v.get("Installed")) != 1) return false;
        long[] have = {dword(v.get("Major")), dword(v.get("Minor")), dword(v.get("Bld"))};
        return compare(have, new long[]{MAJOR, MINOR, BLD}) >= 0 && string(v.get("Version")) != null;
    }

    static boolean isCurrentServicing(Map<String, String> v) {
        if (v == null || dword(v.get("Install")) != 1) return false;
        return compare(parseVersion(string(v.get("Version"))), parseVersion(SERVICING_VERSION)) >= 0;
    }

    /** Raw values ("dword:0000000e", "\"v14.44...\"") of every marker key, read in one pass; keys match exactly. */
    static Map<String, Map<String, String>> readValues(File systemReg) throws IOException {
        Map<String, String> headers = new HashMap<>();
        for (String key : RUNTIME_KEYS) headers.put("[" + key.replace("\\", "\\\\") + "]", key);
        for (String key : SERVICING_KEYS) headers.put("[" + key.replace("\\", "\\\\") + "]", key);
        Map<String, Map<String, String>> out = new HashMap<>();
        Map<String, String> current = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(systemReg))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[")) {
                    int close = line.lastIndexOf(']');
                    String key = close > 0 ? headers.get(line.substring(0, close + 1)) : null;
                    current = key != null ? out.computeIfAbsent(key, k -> new HashMap<>()) : null;
                } else if (current != null && line.startsWith("\"")) {
                    int eq = line.indexOf("\"=", 1);
                    if (eq > 0) current.put(line.substring(1, eq), line.substring(eq + 2));
                }
            }
        }
        return out;
    }

    private static void write(WineRegistryEditor editor, String key) {
        if (key.endsWith("RuntimeMinimum")) {
            editor.setDwordValue(key, "Install", 1);
            editor.setStringValue(key, "Version", SERVICING_VERSION);
            return;
        }
        editor.setDwordValue(key, "Installed", 1);
        editor.setDwordValue(key, "Major", MAJOR);
        editor.setDwordValue(key, "Minor", MINOR);
        editor.setDwordValue(key, "Bld", BLD);
        editor.setDwordValue(key, "Rbld", RBLD);
        editor.setStringValue(key, "Version", VERSION);
    }

    /** "dword:0000000e" -> 14; anything else -> -1. */
    static long dword(String raw) {
        if (raw == null || !raw.startsWith("dword:")) return -1;
        try {
            return Long.parseLong(raw.substring(6).trim(), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String string(String raw) {
        return raw != null && raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ? raw.substring(1, raw.length() - 1) : null;
    }

    /** "v14.44.35211.00" / "14.44.35211" -> {14, 44, 35211, 0}; null or garbage -> empty (older than anything). */
    static long[] parseVersion(String version) {
        if (version == null) return new long[0];
        String[] parts = version.trim().replaceFirst("^[vV]", "").split("\\.");
        long[] out = new long[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Long.parseLong(parts[i].trim());
        } catch (NumberFormatException e) {
            return new long[0];
        }
        return out;
    }

    /** Numeric comparison, missing trailing parts count as 0; an empty array is older than any version. */
    static int compare(long[] a, long[] b) {
        if (a.length == 0 || b.length == 0) return a.length == b.length ? 0 : a.length == 0 ? -1 : 1;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            long x = i < a.length ? a[i] : 0;
            long y = i < b.length ? b[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }
}
