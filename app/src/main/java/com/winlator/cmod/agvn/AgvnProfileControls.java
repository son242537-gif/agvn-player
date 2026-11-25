/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A game's own on-screen keys, a controls profile (.icp) its maker put in the game folder and named in
 * agvn-profile.json ("controls": "phim-ao.icp"). AGVN's layouts are by game type; an action game that needs letter keys
 * (A S D F G, Q W E R T, Z X C) had the player load its .icp by hand with "Nhập bộ phím" (Kim Dung Quần Hiệp Truyện 5,
 * 25/11/2026). At "Thêm game" the file becomes a controls profile, outside the ids AGVN keeps for its layouts, and the
 * game uses it as a player's choice would be: AGVN does not pick a layout over it, Windows games and "Chạy nhẹ" alike.
 * The profile remembers the file it came from ({@link #SOURCE}), so adding the game again updates it instead of making
 * another. A key the player edits in game goes to a copy of it ({@link AgvnControlsFork}), which adding the game again
 * leaves alone. Pure Java but {@link #apply}.
 */
public final class AgvnProfileControls {
    private static final String TAG = "AGVN";
    /** In the profile's JSON: the game file it was made from. */
    static final String SOURCE = "agvnSource";

    private AgvnProfileControls() {}

    /** True when {@code controls} names a .icp file rather than one of AGVN's layouts. */
    public static boolean isFile(String controls) {
        return controls != null && controls.trim().toLowerCase(Locale.ROOT).endsWith(".icp");
    }

    /** The reason {@code controls} cannot be loaded from {@code gameDir}, or null when it can. */
    static String problem(File gameDir, String controls) {
        String path = controls.trim().replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*") || path.contains(".."))
            return "Tệp bộ phím (controls) phải nằm trong thư mục game: " + controls;
        File file = new File(gameDir, path);
        if (!AgvnProfileValidator.isInside(file, gameDir))
            return "Tệp bộ phím (controls) phải nằm trong thư mục game: " + controls;
        if (!file.isFile()) return "Không thấy tệp bộ phím (controls): " + controls;
        String json = FileUtils.readString(file);
        if (json != null && elementsOf(json)) return null;
        return "Tệp bộ phím (controls) không phải bộ phím ảo (.icp): " + controls;
    }

    /** True for the JSON of a controls profile: an object with an "elements" array. */
    static boolean elementsOf(String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) return false;
            JsonObject profile = e.getAsJsonObject();
            return profile.has("elements") && profile.get("elements").isJsonArray();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * The profile's JSON as the app keeps it: id {@code id}, name {@code name}, the file it came from, and no AGVN
     * layout version or template flag (it is the game's, not one of AGVN's layouts, nor a template pickers hide).
     */
    static String prepare(String json, int id, String name, String source) {
        JsonObject profile = JsonParser.parseString(json).getAsJsonObject();
        profile.addProperty("id", id);
        profile.addProperty("name", name);
        profile.addProperty(SOURCE, source);
        profile.remove(AgvnControls.VERSION_KEY);
        profile.remove("template");
        return profile.toString();
    }

    /** The source a profile's JSON names, or null. */
    static String sourceOf(String json) {
        return field(json, SOURCE);
    }

    private static String read(Context ctx, int id) {
        return FileUtils.readString(ControlsProfile.getProfileFile(ctx, id));
    }

    private static String nameOf(String json) {
        return field(json, "name");
    }

    private static String field(String json, String key) {
        try {
            JsonObject profile = JsonParser.parseString(json).getAsJsonObject();
            return profile.has(key) ? profile.get(key).getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * At "Thêm game", after the profile's other choices: the game's .icp, when its profile names one, made a controls
     * profile and the game's keys. Nothing changes when the file cannot be read (the validator checked it at load).
     */
    static void apply(Context ctx, Shortcut shortcut, File gameDir, AgvnProfile p) {
        if (!isFile(p.controls)) return;
        try {
            File file = new File(gameDir, p.controls.trim().replace('\\', '/'));
            String json = FileUtils.readString(file);
            if (json == null || !elementsOf(json)) return;
            String source = file.getAbsolutePath();
            File dir = InputControlsManager.getProfilesDir(ctx);
            Set<Integer> ids = AgvnControlsFork.idsOf(dir.list());
            int id = -1;
            for (int existing : ids) {
                if (AgvnControlsFork.inLayoutRange(existing)) continue;
                if (source.equals(sourceOf(read(ctx, existing)))) id = existing;
            }
            if (id < 0) id = AgvnControlsFork.nextForkId(ids);
            Set<String> taken = new HashSet<>(); // pickers list profiles by name
            for (int other : ids) {
                String name = other == id ? null : nameOf(read(ctx, other));
                if (name != null) taken.add(name);
            }
            String game = p.name != null && !p.name.trim().isEmpty() ? p.name.trim() : gameDir.getName();
            String name = AgvnControlsFork.uniqueName(game, taken);
            File out = ControlsProfile.getProfileFile(ctx, id);
            if (!FileUtils.writeString(out, prepare(json, id, name, source))) return;
            shortcut.putExtra(AgvnLayouts.EXTRA_PROFILE, String.valueOf(id));
            shortcut.putExtra(AgvnLayouts.EXTRA_AUTO, null); // the game's choice: AGVN does not pick a layout over it
            Log.i(TAG, "controls profile " + id + " from the game's " + p.controls + " for " + name);
        } catch (RuntimeException e) {
            Log.w(TAG, "game's controls profile not loaded: " + p.controls, e);
        }
    }
}
