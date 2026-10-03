/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * What {@link AgvnDoctor} remembers per game, in files/agvn/doctor/: the settings of the last session that ran well
 * ("good." keys), the fixes tried since then, and a screen size the game refused (Đồ họa then never goes that low
 * again). The settings are the game's own ones in its shortcut, those that decide how it runs. Pure Java except
 * {@link #load} and {@link #save}.
 */
final class AgvnGoodConfig {
    /** Shortcut extras that decide how a game runs ("nativeFpsLimit...": FpsLimiterControl's keys). */
    static final List<String> KEYS = Arrays.asList("screenSize", AgvnQuality.EXTRA_QUALITY, "nativeFpsLimit",
            "nativeFpsLimiterEnabled", "graphicsDriver", "graphicsDriverConfig", "dxwrapper", "dxwrapperConfig", "emulator",
            "box64Preset", "fexcorePreset", "envVars", "execArgs", "wincomponents", "audioDriver", "lc_all", "cpuList",
            "rendererDriverId", "rendererFilterMode", "rendererNative", "rendererPresentMode", "rendererSwapRB", "surfaceFormat",
            "trueDisplayX", "useDisplayX", "displayXBackPressure", "displayXPerformanceMode", "displayXPrecisePresentation",
            "displayXPresentAtRefreshRate", "sharpnessEffect", "sharpnessLevel", "sharpnessDenoise", "fullscreenStretched");
    static final String GOOD = "good.", HAS_GOOD = "hasGood", TRIED = "tried", TOO_SMALL = "tooSmall";

    private AgvnGoodConfig() {}

    /** The game's settings now; a setting it does not have is left out. */
    static Map<String, String> snapshot(Shortcut shortcut) {
        Map<String, String> now = new LinkedHashMap<>();
        for (String key : KEYS) {
            String value = shortcut.getExtra(key);
            if (!value.isEmpty()) now.put(key, value);
        }
        return now;
    }

    static boolean hasGood(Properties state) {
        return "1".equals(state.getProperty(HAS_GOOD));
    }

    static Map<String, String> good(Properties state) {
        Map<String, String> good = new LinkedHashMap<>();
        for (String key : KEYS) {
            String value = state.getProperty(GOOD + key);
            if (value != null) good.put(key, value);
        }
        return good;
    }

    /** Keeps {@code now} as the settings the game ran well with, and forgets the fixes tried before. */
    static void setGood(Properties state, Map<String, String> now) {
        for (String key : KEYS) state.remove(GOOD + key);
        for (Map.Entry<String, String> e : now.entrySet()) state.setProperty(GOOD + e.getKey(), e.getValue());
        state.setProperty(HAS_GOOD, "1");
        state.remove(TRIED);
    }

    /** The settings whose value differs between the two snapshots (one may lack a setting the other has). */
    static List<String> changed(Map<String, String> good, Map<String, String> now) {
        List<String> keys = new ArrayList<>();
        for (String key : KEYS) {
            String a = good.get(key), b = now.get(key);
            if (a == null ? b != null : !a.equals(b)) keys.add(key);
        }
        return keys;
    }

    /** Puts the good settings back on the game: each one it had, and none it did not. */
    static void restore(Shortcut shortcut, Map<String, String> good) {
        for (String key : KEYS) shortcut.putExtra(key, good.get(key));
    }

    static Set<String> tried(Properties state) {
        Set<String> tried = new LinkedHashSet<>();
        for (String id : state.getProperty(TRIED, "").split(",")) if (!id.isEmpty()) tried.add(id);
        return tried;
    }

    static void addTried(Properties state, String fixId) {
        Set<String> tried = tried(state);
        tried.add(fixId);
        state.setProperty(TRIED, String.join(",", tried));
    }

    /** "640x360" when the game refused that screen, else "". */
    static String tooSmall(Properties state) {
        return state.getProperty(TOO_SMALL, "");
    }

    /** True when a screen of {@code size} ("854x480") is not larger than one the game refused. */
    static boolean refused(Properties state, String size) {
        long refused = pixels(tooSmall(state));
        return refused > 0 && pixels(size) > 0 && pixels(size) <= refused;
    }

    static long pixels(String size) {
        String[] wh = size == null ? new String[0] : size.toLowerCase().split("x");
        try {
            return wh.length == 2 ? Long.parseLong(wh[0].trim()) * Long.parseLong(wh[1].trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static File file(Context context, Shortcut shortcut) {
        String name = shortcut.container.id + "-" + AgvnLogFolders.safeName(shortcut.file.getName().replace(".desktop", ""));
        return new File(new File(context.getFilesDir(), "agvn/doctor"), name + ".properties");
    }

    static Properties load(Context context, Shortcut shortcut) {
        return AgvnPropsFile.load(file(context, shortcut));
    }

    static void save(Context context, Shortcut shortcut, Properties state) {
        File f = file(context, shortcut);
        File dir = f.getParentFile();
        if (dir != null) dir.mkdirs();
        AgvnPropsFile.store(state, f, "AGVN Player: settings that ran well, fixes tried");
    }
}
