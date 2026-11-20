/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A profile's "settings": the settings a game needs whatever the phone (its DirectX layer, Windows libraries, sound
 * driver, emulator preset), as the shortcut keeps them, e.g. {"dxwrapperConfig": "version=1.10.3,..."}. They are what
 * "Tự sửa lỗi" or the maintainer found for the game, so a profile packed with the game's download gives them to every
 * player at import. Nothing of the phone goes in: no driver, screen size or FPS (the tier and the doctor decide those).
 * Also writes a library game's settings out as agvn-profile.json in its folder ("Lưu hồ sơ game"). Pure Java.
 */
public final class AgvnProfileSettings {
    /** Shortcut extras a profile may set; mirrored in tools/agvn/agvn_profile_lib.py (SETTINGS). */
    static final List<String> KEYS = Arrays.asList("dxwrapper", "dxwrapperConfig", "wincomponents", "audioDriver",
            "emulator", "box64Preset", "fexcorePreset", "fullscreenStretched");
    private static final int MAX_VALUE = 400;

    private AgvnProfileSettings() {}

    static void validate(AgvnProfile p) throws AgvnProfileException {
        if (p.settings == null) return;
        for (Map.Entry<String, String> e : p.settings.entrySet()) {
            String v = e.getValue();
            if (!KEYS.contains(e.getKey()) || v == null || v.length() > MAX_VALUE || !AgvnProfileValidator.SAFE_VALUE.matcher(v).matches())
                throw new AgvnProfileException("Cài đặt (settings) không được phép: " + e.getKey()
                        + " (chỉ dùng: " + String.join(", ", KEYS) + ")");
        }
    }

    /** At import, after the app's own choices for the game. */
    static void apply(Shortcut shortcut, AgvnProfile p) {
        if (p.settings == null) return;
        for (String key : KEYS) if (p.settings.containsKey(key)) shortcut.putExtra(key, p.settings.get(key));
    }

    /**
     * The profile of a game as it runs now: {@code base} (the profile it was added with) with its name, exe, runner,
     * language, variables, arguments and {@link #KEYS}. Variables and settings a profile may not hold are left out.
     */
    static AgvnProfile export(AgvnProfile base, String name, File gameDir, File exe, Map<String, String> extras) {
        AgvnProfile p = base;
        p.schemaVersion = AgvnProfile.SCHEMA_VERSION;
        p.name = AgvnProfile.cleanName(name);
        String rel = relative(gameDir, exe);
        if (rel != null) p.exe = rel;
        String runner = extras.get(AgvnHtmlGame.EXTRA_RUNNER);
        p.runner = runner == null || runner.isEmpty() ? null : runner;
        String locale = extras.get("lc_all");
        if (locale != null && AgvnLocale.isValid(locale)) p.locale = locale;
        EnvVars vars = new EnvVars(extras.get("envVars") == null ? "" : extras.get("envVars"));
        Map<String, String> env = new LinkedHashMap<>();
        for (String key : vars) {
            String value = vars.get(key);
            // DLL overrides are found again from the game's folder at import, and the profile's own stay in dllOverrides
            if (!key.equals("WINEDLLOVERRIDES") && AgvnProfileValidator.ENV_KEY.matcher(key).matches()
                    && AgvnProfileValidator.SAFE_VALUE.matcher(value).matches()) env.put(key, value);
        }
        p.env = env;
        String args = extras.get("execArgs") == null ? "" : extras.get("execArgs").trim();
        List<String> list = new ArrayList<>();
        for (String arg : args.isEmpty() ? new String[0] : args.split("\\s+"))
            if (AgvnProfileValidator.SAFE_VALUE.matcher(arg).matches()) list.add(arg);
        p.args = list;
        Map<String, String> settings = new LinkedHashMap<>();
        for (String key : KEYS) {
            String value = extras.get(key);
            if (value != null && !value.isEmpty() && value.length() <= MAX_VALUE
                    && AgvnProfileValidator.SAFE_VALUE.matcher(value).matches()) settings.put(key, value);
        }
        p.settings = settings.isEmpty() ? null : settings;
        return p;
    }

    /** {@code exe} below {@code gameDir} with "/", or null when it is not below it. */
    static String relative(File gameDir, File exe) {
        if (gameDir == null || exe == null) return null;
        String dir = gameDir.getAbsolutePath(), path = exe.getAbsolutePath();
        if (!path.startsWith(dir + File.separator)) return null;
        return path.substring(dir.length() + 1).replace(File.separatorChar, '/');
    }

    /** Writes the game's profile into its folder; the file written, or null when the folder cannot be written. */
    public static File save(Shortcut s) {
        File gameDir = AgvnEngineLogs.gameDir(s);
        if (gameDir == null || !gameDir.isDirectory()) return null;
        Map<String, String> extras = new LinkedHashMap<>();
        List<String> read = new ArrayList<>(KEYS);
        read.addAll(Arrays.asList(AgvnHtmlGame.EXTRA_RUNNER, "lc_all", "envVars", "execArgs"));
        for (String key : read) extras.put(key, s.getExtra(key));
        AgvnProfile p = export(AgvnQuality.profileOf(s), s.name, gameDir, AgvnHtmlGame.exeIn(s.file), extras);
        File out = new File(gameDir, AgvnProfile.FILE_NAME);
        return FileUtils.writeString(out, p.toPrettyJson()) ? out : null;
    }
}
