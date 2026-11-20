/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * What {@link AgvnDoctor} remembers per game, in files/agvn/doctor/: the settings of the last session that ran well
 * ("good." keys), the fixes tried since then, and a screen size the game refused (Đồ họa then never goes that low
 * again). The settings are the game's own ones in its shortcut, those that decide how it runs. The file goes with the
 * game ({@link #forget}, {@link #renamed}, {@link #moved}): it used to stay, so a game added again under the same name
 * took the old one's good settings and fixes tried (audit of 20/11/2026). Pure Java except what takes a Context.
 */
public final class AgvnGoodConfig {
    /** Shortcut extras that decide how a game runs ("nativeFpsLimit...": FpsLimiterControl's keys). */
    static final List<String> KEYS = Arrays.asList("screenSize", AgvnQuality.EXTRA_QUALITY, "nativeFpsLimit",
            "nativeFpsLimiterEnabled", "graphicsDriver", "graphicsDriverConfig", "dxwrapper", "dxwrapperConfig", "emulator",
            "box64Preset", "fexcorePreset", "envVars", "execArgs", "wincomponents", "audioDriver", "lc_all", "cpuList",
            "rendererDriverId", "rendererFilterMode", "rendererNative", "rendererPresentMode", "rendererSwapRB", "surfaceFormat",
            "trueDisplayX", "useDisplayX", "displayXBackPressure", "displayXPerformanceMode", "displayXPrecisePresentation",
            "displayXPresentAtRefreshRate", "sharpnessEffect", "sharpnessLevel", "sharpnessDenoise", "fullscreenStretched");
    /**
     * What follows the Đồ họa step without deciding anything by itself: an Unreal game's texture pool (AgvnQuality).
     * Kept and put back with the good settings, where "Trả cấu hình chạy tốt" left the pool of the step the player had
     * moved to. Never compared ({@link #changed}), and put back only when kept: good settings saved before 20/11/2026
     * have no pool, and those games must neither read as changed nor lose the pool they have.
     */
    static final List<String> FOLLOW = Collections.singletonList(AgvnGameImporter.EXTRA_TEXTURE_POOL);
    private static final List<String> KEPT = kept();
    static final String GOOD = "good.", HAS_GOOD = "hasGood", TRIED = "tried", TOO_SMALL = "tooSmall", FIXED = "fixed.";

    private AgvnGoodConfig() {}

    /** The game's settings now; a setting it does not have is left out. */
    static Map<String, String> snapshot(Shortcut shortcut) {
        Map<String, String> now = new LinkedHashMap<>();
        for (String key : KEPT) {
            String value = shortcut.getExtra(key);
            if (!value.isEmpty()) now.put(key, value);
        }
        return now;
    }

    private static List<String> kept() {
        List<String> keys = new ArrayList<>(KEYS);
        keys.addAll(FOLLOW);
        return keys;
    }

    static boolean hasGood(Properties state) {
        return "1".equals(state.getProperty(HAS_GOOD));
    }

    static Map<String, String> good(Properties state) {
        Map<String, String> good = new LinkedHashMap<>();
        for (String key : KEPT) {
            String value = state.getProperty(GOOD + key);
            if (value != null) good.put(key, value);
        }
        return good;
    }

    /** Keeps {@code now} as the settings the game ran well with, and forgets the fixes tried before. */
    static void setGood(Properties state, Map<String, String> now) {
        for (String key : KEPT) state.remove(GOOD + key);
        for (String key : KEYS) state.remove(FIXED + key);
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

    /**
     * The settings of {@code now} that differ from the good ones and are not what a fix tried since then set
     * ({@link #addTried(Properties, String, Map, Map)}): the doctor's own change, not the player's. After a fix of "Tự
     * sửa lỗi" the next failure read "Game bị lỗi với cấu hình mới" and offered only to undo it, though the game had
     * failed before the fix too (audit of 20/11/2026). A setting changed again after the fix has another value: counts.
     */
    static List<String> changedByPlayer(Properties state, Map<String, String> now) {
        List<String> keys = new ArrayList<>();
        for (String key : changed(good(state), now)) {
            String value = now.get(key);
            if (!(value != null ? value : "").equals(state.getProperty(FIXED + key))) keys.add(key);
        }
        return keys;
    }

    /**
     * {@code good} as "Trả cấu hình chạy tốt" puts it back on a game whose settings are {@code now}: the way it reads
     * movies is kept ({@link AgvnMediaFoundation#keepOldSource}).
     */
    static Map<String, String> restored(Map<String, String> good, Map<String, String> now) {
        Map<String, String> out = new LinkedHashMap<>(good);
        String env = AgvnMediaFoundation.keepOldSource(now.get("envVars"), good.get("envVars"));
        if (env == null || env.isEmpty()) out.remove("envVars");
        else out.put("envVars", env);
        return out;
    }

    /** Puts the good settings back on the game: each one it had, and none it did not; {@link #FOLLOW} when kept. */
    static void restore(Shortcut shortcut, Map<String, String> good) {
        for (String key : KEYS) shortcut.putExtra(key, good.get(key));
        for (String key : FOLLOW) if (good.containsKey(key)) shortcut.putExtra(key, good.get(key));
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

    /** {@code fixId} is tried, and what it set is kept: the settings that differ between {@code was} and {@code now}. */
    static void addTried(Properties state, String fixId, Map<String, String> was, Map<String, String> now) {
        addTried(state, fixId);
        for (String key : changed(was, now)) state.setProperty(FIXED + key, now.containsKey(key) ? now.get(key) : "");
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
        return file(context.getFilesDir(), shortcut.container.id, shortcut.file.getName());
    }

    /** The state file of the game whose entry is {@code desktopName} ("Toeha.desktop") in container {@code id}. */
    static File file(File filesDir, int id, String desktopName) {
        String name = id + "-" + AgvnLogFolders.safeName(desktopName.replace(".desktop", ""));
        return new File(new File(filesDir, "agvn/doctor"), name + ".properties");
    }

    /** The game's entry is removed from the library: nothing of it is kept for a game added under its name later. */
    public static void forget(Context context, Shortcut shortcut) {
        file(context, shortcut).delete();
    }

    /** The entry {@code oldName} of {@code container} is now {@code newName} (both "….desktop"): its state too. */
    public static void renamed(Context context, Container container, String oldName, String newName) {
        carry(file(context.getFilesDir(), container.id, oldName), file(context.getFilesDir(), container.id, newName));
    }

    /**
     * The game went to another container (AgvnGameMove): its state too. "Chạy bằng Wine mới" on trial left its trial
     * with the old container, so nobody asked whether it helped and it could not be undone (audit of 20/11/2026).
     */
    static void moved(Context context, Shortcut from, Shortcut to) {
        carry(file(context, from), file(context, to));
    }

    /** {@code from} becomes {@code to}; what an earlier game of that name left at {@code to} goes either way. */
    static void carry(File from, File to) {
        if (from.equals(to)) return;
        to.delete();
        if (from.isFile()) from.renameTo(to);
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
