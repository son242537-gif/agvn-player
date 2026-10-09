/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;

import com.winlator.cmod.container.Shortcut;

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
 * "Tự sửa lỗi" for what the player sees and the app cannot tell from a log (hình chập chờn, đốm đen, chữ lỗi...): the
 * player picks it (game-problems.json, "reported"), and the app tries its fixes one at a time, each with a new start of
 * the game, then asks whether it is gone ({@link AgvnRepairAsk}, or the library's {@link AgvnRepairDialog}). Gone: the
 * fix stays and these settings become the game's good ones. Not gone: the game's settings go back to what they were
 * before the first try and the next fix is tried; when none is left they stay as before and "Gửi nhật ký" is offered.
 * Kept per game with the doctor's state ({@link AgvnGoodConfig}). Only that game changes, and only once reported.
 */
final class AgvnRepair {
    static final String PREFIX = "report", SYMPTOM = "report", TRYING = "report.trying", LABEL = "report.label",
            TRIED = "report.tried", HAS_BEFORE = "report.before", BEFORE = "report.before.", AT = "report.at",
            TO = "report.to";
    /**
     * Settings the fixes change besides AgvnGoodConfig.KEYS: Đồ họa's texture pool, tier and FPS preset, Unity's own
     * quality, the runner of a "Chạy nhẹ" game, RPG Maker's frame skip, Godot for Android's renderer and touches as a
     * real mouse. They go back too.
     */
    static final List<String> MORE_KEYS = Collections.unmodifiableList(Arrays.asList(
            AgvnGameImporter.EXTRA_TEXTURE_POOL, AgvnGameImporter.EXTRA_TIER, "graphicsFpsPreset",
            AgvnUnityQuality.EXTRA_OWN, AgvnHtmlGame.EXTRA_RUNNER, AgvnRgssFiles.EXTRA_FRAME_SKIP,
            AgvnGodotLight.EXTRA_RENDERER, AgvnRawMouse.EXTRA));

    private AgvnRepair() {}

    static List<String> keys() {
        List<String> keys = new ArrayList<>(AgvnGoodConfig.KEYS);
        keys.addAll(MORE_KEYS);
        return keys;
    }

    /** The game's settings the fixes may change, as they are now; one it does not have is left out. */
    static Map<String, String> snapshot(Shortcut s) {
        Map<String, String> now = new LinkedHashMap<>();
        for (String key : keys()) {
            String value = s.getExtra(key);
            if (!value.isEmpty()) now.put(key, value);
        }
        return now;
    }

    /** Puts {@code before} back on the game: each setting it had, and none it did not. The caller saves. */
    static void restore(Shortcut s, Map<String, String> before) {
        for (String key : keys()) s.putExtra(key, before.get(key));
    }

    static String symptom(Properties state) {
        return state.getProperty(SYMPTOM, "");
    }

    /** The fix being tried, "" when none; its words as the player saw them ({@link #label}). */
    static String trying(Properties state) {
        return state.getProperty(TRYING, "");
    }

    static String label(Properties state) {
        return state.getProperty(LABEL, "");
    }

    static Set<String> tried(Properties state) {
        Set<String> tried = new LinkedHashSet<>();
        for (String id : state.getProperty(TRIED, "").split(",")) if (!id.isEmpty()) tried.add(id);
        return tried;
    }

    /**
     * The game has started since the fix on trial was put on ({@code lastRunAt}: the shortcut's, stamped at each
     * start), so the player can tell whether it helped. Before that, "Vẫn còn lỗi" would drop a fix nobody tried. A
     * trial begun before 0.1.27 has no time: asked as before.
     */
    static boolean ran(Properties state, String lastRunAt) {
        long at = number(state.getProperty(AT));
        return at <= 0 || number(lastRunAt) >= at;
    }

    private static long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static Map<String, String> before(Properties state) {
        Map<String, String> before = new LinkedHashMap<>();
        for (String key : keys()) {
            String value = state.getProperty(BEFORE + key);
            if (value != null) before.put(key, value);
        }
        return before;
    }

    /**
     * The player reports {@code symptom}: the settings {@code now} are kept to go back to. The same symptom again goes
     * on where it was (its settings before, the fixes tried); another one starts afresh.
     */
    static void begin(Properties state, String symptom, Map<String, String> now) {
        if (symptom.equals(symptom(state)) && state.containsKey(HAS_BEFORE)) return;
        clear(state);
        state.setProperty(SYMPTOM, symptom);
        state.setProperty(HAS_BEFORE, "1");
        for (Map.Entry<String, String> e : now.entrySet()) state.setProperty(BEFORE + e.getKey(), e.getValue());
    }

    /** {@code fix} is on the game now, to be asked about once the game has run with it ({@link #ran}). */
    static void trying(Properties state, AgvnFixes.Fix fix) {
        state.setProperty(TRYING, fix.id);
        state.setProperty(LABEL, fix.label);
        state.setProperty(TO, fix.to != null ? fix.to : ""); // what it sets: did a start have it (AgvnRepairCheck)
        state.setProperty(AT, String.valueOf(System.currentTimeMillis()));
        Set<String> tried = tried(state);
        tried.add(fix.id);
        state.setProperty(TRIED, String.join(",", tried));
    }

    /** Forgets the report: what was tried and the settings before it. */
    static void clear(Properties state) {
        for (String key : state.stringPropertyNames()) {
            if (key.equals(PREFIX) || key.startsWith(PREFIX + ".")) state.remove(key);
        }
    }

    /** The player picks another problem while a fix is on trial: the settings go back, the report is forgotten. */
    static void abandon(Context a, Shortcut s) {
        Properties state = AgvnGoodConfig.load(a, s);
        restore(s, before(state));
        s.saveData();
        clear(state);
        AgvnGoodConfig.save(a, s, state);
    }

    /** The fixes that change the game and were not tried for {@code finding}'s symptom yet, in the catalog's order. */
    static List<AgvnFixes.Fix> left(Activity a, Shortcut s, AgvnProblemCatalog.Finding finding, Properties state) {
        Set<String> tried = finding.id().equals(symptom(state)) ? tried(state) : Collections.emptySet();
        List<AgvnFixes.Fix> left = new ArrayList<>();
        for (AgvnFixes.Fix fix : AgvnFixes.applicable(a, s, finding, tried, state)) {
            if (fix.changesGame()) left.add(fix);
        }
        return left;
    }

    /** Starts trying {@code fix} for {@code finding}: false when it could not be put on the game. */
    static boolean start(Activity a, Shortcut s, AgvnProblemCatalog.Finding finding, AgvnFixes.Fix fix) {
        Properties state = AgvnGoodConfig.load(a, s);
        begin(state, finding.id(), snapshot(s));
        if (!AgvnFixApply.apply(a, s, fix, state)) return false;
        trying(state, fix);
        AgvnGoodConfig.save(a, s, state);
        AgvnSessionLog.event("Tự sửa lỗi: người chơi báo \"" + finding.title() + "\", thử: " + fix.label);
        return true;
    }

    /** The player says the problem is gone: the fix stays, and the game's settings are its good ones. */
    static void fixed(Context a, Shortcut s) {
        Properties state = AgvnGoodConfig.load(a, s);
        AgvnSessionLog.event("Tự sửa lỗi: hết lỗi nhờ " + label(state));
        clear(state);
        AgvnGoodConfig.setGood(state, AgvnGoodConfig.snapshot(s));
        AgvnGoodConfig.save(a, s, state);
    }

    /**
     * The player says the problem is still there: the settings go back to before the first try, then the next fix is
     * put on the game and returned; null when none is left (the report is then forgotten).
     */
    static AgvnFixes.Fix next(Activity a, Shortcut s) {
        Properties state = AgvnGoodConfig.load(a, s);
        AgvnSessionLog.event("Tự sửa lỗi: vẫn còn lỗi với " + label(state) + ", trả cấu hình về như trước");
        restore(s, before(state));
        s.saveData();
        AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(a).finding(symptom(state), AgvnGodotGame.params(s));
        for (AgvnFixes.Fix fix : f != null ? left(a, s, f, state) : Collections.<AgvnFixes.Fix>emptyList()) {
            if (!AgvnFixApply.apply(a, s, fix, state)) continue;
            trying(state, fix);
            AgvnGoodConfig.save(a, s, state);
            AgvnSessionLog.event("Tự sửa lỗi: thử tiếp " + fix.label);
            return fix;
        }
        clear(state);
        AgvnGoodConfig.save(a, s, state);
        AgvnSessionLog.event("Tự sửa lỗi: đã thử hết cách trong app");
        return null;
    }
}
