/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The key sets a player saves and loads in a game ("Bộ phím ảo": Sửa, Lưu, Tải, {@link AgvnKeySetDialog}). They are
 * the controls profiles (files/profiles/controls-&lt;id&gt;.icp) of every game, Windows or "Chạy nhẹ", so a set saved
 * in one game is listed in every game, in "Điều khiển" and in a game's "Cấu hình phím ảo". Loading one makes it the
 * game's keys at every start, as the player's choice ({@link #pickExtras}). Pure Java, files only: the Ren'Py and
 * RPG Maker games run in processes of their own.
 */
final class AgvnKeySets {
    /** A saved set: its profile id and name. */
    static final class Entry {
        final int id;
        final String name;

        Entry(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private AgvnKeySets() {}

    /** Every set in {@code dir} but the templates, by id: the player's first, AGVN's layouts (9000-9005) last. */
    static List<Entry> list(File dir) {
        List<Entry> sets = new ArrayList<>();
        for (int id : AgvnControlsFork.idsOf(dir.list())) {
            JsonObject profile = read(dir, id);
            String name = profile != null ? text(profile, "name") : "";
            if (!name.isEmpty() && !name.toLowerCase(Locale.ENGLISH).contains("template")) sets.add(new Entry(id, name));
        }
        Collections.sort(sets, (a, b) -> Integer.compare(a.id, b.id));
        return sets;
    }

    /** The id of the player's set (not one of AGVN's layouts) named {@code name}, else -1: "Lưu" asks before writing over it. */
    static int named(File dir, String name) {
        for (Entry set : list(dir)) if (set.name.equals(name) && !AgvnControlsFork.isReservedId(set.id)) return set.id;
        return -1;
    }

    /** The id of a set with the very keys of {@code json}, else -1: keys kept once are not kept again. */
    static int same(File dir, String json) {
        JsonArray keys = keys(json);
        if (keys == null) return -1;
        for (Entry set : list(dir)) {
            JsonObject profile = read(dir, set.id);
            if (profile != null && keys.equals(keys(profile.toString()))) return set.id;
        }
        return -1;
    }

    /**
     * Keeps the keys of {@code json} (an .icp, or a "Chạy nhẹ" key set {"elements": [...]}) as the set {@code name}:
     * over the player's set {@code over} when it is one, else as a new profile, its name made unique. The set's id, or
     * -1 when it cannot be written.
     */
    static int save(File dir, String json, String name, int over) {
        try {
            Set<Integer> ids = AgvnControlsFork.idsOf(dir.list());
            boolean in = over > 0 && !AgvnControlsFork.isReservedId(over) && ids.contains(over);
            int id = in ? over : AgvnControlsFork.nextForkId(ids);
            Set<String> taken = new HashSet<>();
            for (Entry set : list(dir)) if (set.id != id) taken.add(set.name);
            String profile = profileJson(json, id, in ? name : AgvnControlsFork.uniqueName(name, taken));
            if (profile == null) return -1;
            File file = new File(dir, "controls-" + id + ".icp");
            File tmp = new File(dir.getParentFile(), file.getName() + ".tmp"); // not in the profile list's folder
            Files.write(tmp.toPath(), profile.getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) { // Windows (the unit tests of a PC build) does not rename over a file
                Files.deleteIfExists(file.toPath());
                if (!tmp.renameTo(file)) return -1;
            }
            return id;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /**
     * {@code json} as an .icp every game reads: id, name, cursor speed, no AGVN layout version or template flag, and
     * each control with what the Windows controls need to load it (a "Chạy nhẹ" key set has no toggleSwitch, iconId).
     * Null when it has no controls list.
     */
    static String profileJson(String json, int id, String name) {
        JsonObject profile;
        try {
            profile = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        JsonArray keys = keys(json);
        if (keys == null) return null;
        JsonObject out = new JsonObject(); // id and name first, as the profile list reads them
        out.addProperty("id", id);
        out.addProperty("name", name);
        out.addProperty("cursorSpeed", 1);
        for (Map.Entry<String, JsonElement> e : profile.entrySet()) {
            String key = e.getKey();
            if (!key.equals("id") && !key.equals("name") && !key.equals("template") && !key.equals(AgvnControls.VERSION_KEY))
                out.add(key, e.getValue());
        }
        out.add("elements", keys);
        return out.toString();
    }

    /** The shortcut extras that make set {@code id} the game's keys: the player's choice, shown, not re-picked by AGVN. */
    static Map<String, String> pickExtras(int id) {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(AgvnLayouts.EXTRA_PROFILE, String.valueOf(id));
        extras.put(AgvnLayouts.EXTRA_AUTO, null);
        extras.put(AgvnControlsFork.EXTRA_HIDDEN, null);
        return extras;
    }

    /** The controls of {@code json}, each with toggleSwitch, iconId and text; null when it is not a key set. */
    private static JsonArray keys(String json) {
        try {
            JsonArray out = new JsonArray();
            for (JsonElement item : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("elements")) {
                JsonObject key = item.getAsJsonObject().deepCopy();
                if (!key.has("type") || !key.has("bindings")) continue;
                if (!key.has("toggleSwitch")) key.addProperty("toggleSwitch", false);
                if (!key.has("iconId")) key.addProperty("iconId", 0);
                if (!key.has("text")) key.addProperty("text", "");
                if (!key.has("scale")) key.addProperty("scale", 1);
                out.add(key);
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject read(File dir, int id) {
        try {
            byte[] bytes = Files.readAllBytes(new File(dir, "controls-" + id + ".icp").toPath());
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The profile file of set {@code id}, or null when it cannot be read. */
    static String json(File dir, int id) {
        JsonObject profile = read(dir, id);
        return profile != null ? profile.toString() : null;
    }

    private static String text(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }
}
