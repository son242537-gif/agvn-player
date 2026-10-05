/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies AGVN overrides to an Unreal game's user Engine.ini. Packaged (Shipping) builds read their Default*.ini
 * from .pak files and ignore loose copies, so overrides go to the per-user config Wine exposes:
 * drive_c/users/xuser/AppData/Local/<Project>/Saved/Config/{Windows,WindowsNoEditor}/Engine.ini.
 * The texture pool is the cvar r.Streaming.PoolSize (MB) in [SystemSettings]. Existing keys are kept.
 */
public final class UeIniWriter {
    public static final String POOL_SECTION = "SystemSettings";
    public static final String POOL_KEY = "r.Streaming.PoolSize";

    private UeIniWriter() {}

    /** Profile overrides plus the texture pool (when > 0). */
    public static Map<String, Map<String, String>> overrides(AgvnProfile profile, int texturePoolMb) {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : profile.ueEngineIni.entrySet())
            all.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        if (texturePoolMb > 0) {
            Map<String, String> section = all.get(POOL_SECTION);
            if (section == null) all.put(POOL_SECTION, section = new LinkedHashMap<>());
            section.put(POOL_KEY, String.valueOf(texturePoolMb));
        }
        return all;
    }

    /** Merges {@code overrides} into INI text: updates keys in place, appends missing keys and sections. */
    public static String merge(String existing, Map<String, Map<String, String>> overrides) {
        List<String> lines = new ArrayList<>();
        if (existing != null && !existing.isEmpty()) for (String l : existing.split("\r?\n", -1)) lines.add(l);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) lines.remove(lines.size() - 1);

        for (Map.Entry<String, Map<String, String>> sec : overrides.entrySet()) {
            Map<String, String> pending = new LinkedHashMap<>(sec.getValue());
            int start = -1, end = lines.size();
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("[") && t.endsWith("]")) {
                    if (start >= 0) { end = i; break; }
                    if (t.substring(1, t.length() - 1).equals(sec.getKey())) start = i;
                }
            }
            if (start < 0) {
                if (!lines.isEmpty()) lines.add("");
                lines.add("[" + sec.getKey() + "]");
                for (Map.Entry<String, String> kv : pending.entrySet()) lines.add(kv.getKey() + "=" + kv.getValue());
                continue;
            }
            for (int i = start + 1; i < end; i++) {
                String l = lines.get(i);
                int eq = l.indexOf('=');
                if (eq <= 0) continue;
                String key = l.substring(0, eq).trim();
                if (pending.containsKey(key)) lines.set(i, key + "=" + pending.remove(key));
            }
            int insertAt = end;
            while (insertAt > start + 1 && lines.get(insertAt - 1).trim().isEmpty()) insertAt--;
            for (Map.Entry<String, String> kv : pending.entrySet()) lines.add(insertAt++, kv.getKey() + "=" + kv.getValue());
        }
        return String.join("\n", lines) + "\n";
    }

    /** Project folder name for "<Project>/Binaries/Win64/<x>.exe", else the game folder name. */
    public static String projectName(File gameDir, String exeRelative) {
        String[] parts = exeRelative.replace('\\', '/').split("/");
        if (parts.length >= 4 && parts[parts.length - 3].equalsIgnoreCase("Binaries")) return parts[parts.length - 4];
        return gameDir.getName();
    }

    /** Writes the overrides into both UE4 (WindowsNoEditor) and UE5 (Windows) user configs. */
    public static List<File> apply(File wineUserDir, String project, Map<String, Map<String, String>> overrides) throws IOException {
        List<File> written = new ArrayList<>();
        if (overrides.isEmpty()) return written;
        for (String platform : new String[]{"Windows", "WindowsNoEditor"}) {
            File ini = new File(wineUserDir, "AppData/Local/" + project + "/Saved/Config/" + platform + "/Engine.ini");
            ini.getParentFile().mkdirs();
            String old = ini.isFile() ? new String(Files.readAllBytes(ini.toPath()), StandardCharsets.UTF_8) : "";
            Files.write(ini.toPath(), merge(old, overrides).getBytes(StandardCharsets.UTF_8));
            written.add(ini);
        }
        return written;
    }
}
