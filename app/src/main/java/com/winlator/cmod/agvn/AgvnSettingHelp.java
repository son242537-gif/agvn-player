/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Texts for the "?" next to each setting, from assets/agvn/setting-help.txt. The file is plain text so it is easy to
 * edit: "[label]" starts a setting (the label exactly as the screen shows it in Vietnamese), then one line each for
 * "Là gì:", "Vì sao chỉnh:" and "Chỉnh thế nào:". Lines starting with "#" are comments.
 * <p>In another language (AgvnLanguage) the texts come from setting-help-&lt;lang&gt;.txt, keyed by the same Vietnamese
 * labels; one it lacks stays Vietnamese. The screen shows the label translated, so it goes back to its Vietnamese
 * through the app's own strings: the string whose text it is, read in Vietnamese.
 */
public final class AgvnSettingHelp {
    private static final String TAG = "AGVN";
    static final String ASSET = "agvn/setting-help.txt";
    static final String WHAT = "Là gì:", WHY = "Vì sao chỉnh:", HOW = "Chỉnh thế nào:";

    public static final class Entry {
        public final String what, why, how;

        Entry(String what, String why, String how) {
            this.what = what;
            this.why = why;
            this.how = how;
        }
    }

    private static volatile Map<String, Entry> entries;
    /** The translated label → its Vietnamese one, for {@link #language}. */
    private static volatile Map<String, String> vietnamese = Collections.emptyMap();
    private static volatile String language;

    private AgvnSettingHelp() {}

    /** Help for the setting shown as {@code label}, or null when there is none. */
    public static Entry get(Context context, String label) {
        if (label == null) return null;
        String lang = AgvnLanguage.textLanguage(context);
        Map<String, Entry> map = entries;
        if (map == null || !lang.equals(language)) {
            synchronized (AgvnSettingHelp.class) {
                map = load(context, ASSET);
                if (!lang.equals(AgvnLanguage.VIETNAMESE)) {
                    map = new HashMap<>(map);
                    map.putAll(load(context, "agvn/setting-help-" + lang + ".txt"));
                    vietnamese = AgvnStringKeys.toVietnamese(context);
                } else {
                    vietnamese = Collections.emptyMap();
                }
                entries = map;
                language = lang;
            }
        }
        String key = label.trim();
        Entry e = map.get(key);
        if (e != null) return e;
        String vi = vietnamese.get(key);
        return vi != null ? map.get(vi) : null;
    }

    private static Map<String, Entry> load(Context context, String asset) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getApplicationContext().getAssets().open(asset), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) lines.add(line);
        } catch (Exception e) {
            Log.w(TAG, "setting help not loaded: " + asset, e);
            return Collections.emptyMap();
        }
        return parse(lines);
    }

    /** Label → entry; an entry missing one of its three lines is left out. */
    static Map<String, Entry> parse(List<String> lines) {
        Map<String, Entry> out = new HashMap<>();
        String label = null, what = null, why = null, how = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                put(out, label, what, why, how);
                label = line.substring(1, line.length() - 1).trim();
                what = why = how = null;
            } else if (line.startsWith(WHAT)) {
                what = line.substring(WHAT.length()).trim();
            } else if (line.startsWith(WHY)) {
                why = line.substring(WHY.length()).trim();
            } else if (line.startsWith(HOW)) {
                how = line.substring(HOW.length()).trim();
            }
        }
        put(out, label, what, why, how);
        return out;
    }

    private static void put(Map<String, Entry> out, String label, String what, String why, String how) {
        if (label == null || label.isEmpty() || isBlank(what) || isBlank(why) || isBlank(how)) return;
        out.put(label, new Entry(what, why, how));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}
