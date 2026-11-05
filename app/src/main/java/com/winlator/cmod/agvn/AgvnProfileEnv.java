/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.util.Log;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The env and dllOverrides of agvn-profile.json, edited after the game was added. "Thêm game" wrote them into the
 * game's variables once; a game maker who then set WINEDEBUG in the profile, in the game folder and in
 * profiles/&lt;folder&gt;, saw no change in moi-truong.txt (SRPG Studio, 06/11/2026). At each start the profile edited
 * last is read again, and what it gives now replaces what it gave before; what the player or a Doctor fix set stays.
 */
public final class AgvnProfileEnv {
    private static final String TAG = "AGVN";
    /** Shortcut extra: what the profile gave the game's variables when they were last written. */
    static final String EXTRA = "agvnProfileEnv";
    static final String OVERRIDES = "WINEDLLOVERRIDES";
    private static volatile String note;

    private AgvnProfileEnv() {}

    /** What {@code p} gives the game's variables: its env, and its dllOverrides in WINEDLLOVERRIDES. */
    static String given(AgvnProfile p) {
        return AgvnGameImporter.buildEnvVars(p.env, GameDllOverrides.build(Collections.emptyMap(), p.dllOverrides));
    }

    /** Before the game's settings are read (XServerDisplayActivity). Saves the shortcut when it changes; no throw. */
    public static void atLaunch(Shortcut s) {
        if (s == null) return;
        File file = null;
        try {
            String dir = s.getExtra(AgvnGameImporter.EXTRA_GAME_DIR);
            String copy = s.getExtra(AgvnGameImporter.EXTRA_PROFILE_PATH);
            file = edited(dir.isEmpty() ? null : new File(dir, AgvnProfile.FILE_NAME),
                    copy.isEmpty() ? null : new File(copy));
            if (file == null) return;
            AgvnProfile p = AgvnProfile.parse(FileUtils.readString(file));
            AgvnProfileValidator.checkEnv(p);
            String now = given(p), before = s.getExtra(EXTRA);
            if (now.equals(before)) return;
            String env = s.getExtra("envVars"), after = apply(env, before, now);
            s.putExtra("envVars", after.isEmpty() ? null : after);
            s.putExtra(EXTRA, now.isEmpty() ? null : now);
            s.saveData();
            if (!after.equals(env)) {
                note = "agvn-profile.json (" + file.getPath() + ") đổi env/dllOverrides từ khi thêm game, nay áp: "
                        + (now.isEmpty() ? "(không có)" : now);
                Log.i(TAG, s.name + ": " + note);
            }
        } catch (AgvnProfileException e) {
            note = "agvn-profile.json (" + file.getPath() + ") không áp được env/dllOverrides: " + e.getMessage();
        } catch (RuntimeException e) {
            Log.w(TAG, "profile variables not rechecked", e);
        }
    }

    /** The one of the game folder's profile and the app's copy that exists and was edited last; null for neither. */
    static File edited(File inGame, File copy) {
        boolean game = inGame != null && inGame.isFile(), own = copy != null && copy.isFile();
        if (game && own) return inGame.lastModified() > copy.lastModified() ? inGame : copy;
        return game ? inGame : own ? copy : null;
    }

    /** {@code envVars} with what the profile gave {@code before} replaced by what it gives {@code now}. */
    static String apply(String envVars, String before, String now) {
        EnvVars vars = new EnvVars(envVars), was = new EnvVars(before), is = new EnvVars(now);
        for (String name : was) {
            if (!is.has(name) && !name.equals(OVERRIDES) && vars.has(name) && vars.get(name).equals(was.get(name))) {
                vars.remove(name);
            }
        }
        for (String name : is) if (!name.equals(OVERRIDES)) vars.put(name, is.get(name));
        if (!was.get(OVERRIDES).equals(is.get(OVERRIDES))) {
            String dlls = overrides(vars.get(OVERRIDES), was.get(OVERRIDES), is.get(OVERRIDES));
            if (dlls.isEmpty()) vars.remove(OVERRIDES);
            else vars.put(OVERRIDES, dlls);
        }
        return vars.toString();
    }

    /** {@code list} ("a=n,b;b,c=n") with the DLLs {@code before} named set as {@code now} names them. */
    static String overrides(String list, String before, String now) {
        Map<String, String> all = entries(list), was = entries(before), is = entries(now);
        for (Map.Entry<String, String> e : was.entrySet()) {
            String dll = e.getKey();
            if (!is.containsKey(dll) && Objects.equals(all.get(dll), e.getValue())) all.remove(dll);
        }
        all.putAll(is);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey());
            if (e.getValue() != null) sb.append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** DLL name (lower case) → its mode, one per name; an entry without "=" is kept as it is, with no mode. */
    static Map<String, String> entries(String list) {
        Map<String, String> map = new LinkedHashMap<>();
        if (list == null) return map;
        for (String part : list.split(";")) {
            String entry = part.trim();
            int eq = entry.indexOf('=');
            if (entry.isEmpty()) continue;
            if (eq < 0) {
                map.put(entry, null);
                continue;
            }
            for (String name : entry.substring(0, eq).split(",")) {
                String dll = name.trim().toLowerCase(Locale.ROOT);
                if (!dll.isEmpty()) map.put(dll, entry.substring(eq + 1));
            }
        }
        return map;
    }

    /** The line for the session's events, once; null when nothing changed. */
    static String takeNote() {
        String n = note;
        note = null;
        return n;
    }
}
