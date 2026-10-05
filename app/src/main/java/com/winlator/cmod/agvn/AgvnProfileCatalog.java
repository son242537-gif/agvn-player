/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Profiles shipped inside the app (assets/agvn/game-profiles.json), tested by AGVN on real phones: a game folder without
 * its own agvn-profile.json still gets tuned settings when it matches an entry. Offline, no player data leaves the phone.
 * <pre>
 * {"schemaVersion": 1, "entries": [{"id": "my-game", "match": ["MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe"],
 *   "profile": {"name": "My Game", "exe": "MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe", "fpsLimit": 30}}]}
 * </pre>
 * An entry matches when every "match" path exists in the folder ("match" defaults to the profile exe). A generic exe
 * name such as Game.exe alone is rejected: add a second path that only this game has.
 */
public final class AgvnProfileCatalog {
    public static final String ASSET = "agvn/game-profiles.json";
    /** Exe names many unrelated games share; mirrored by tools/agvn/them-vao-kho-cau-hinh.py. */
    static final List<String> GENERIC = Arrays.asList("game.exe", "rpg_rt.exe", "nw.exe", "launcher.exe", "start.exe",
            "play.exe", "main.exe", "setup.exe");
    static final AgvnProfileCatalog EMPTY = new AgvnProfileCatalog(new ArrayList<>());

    private static AgvnProfileCatalog bundled;

    static final class Entry {
        final String id;
        final List<String> match;
        final JsonObject profile;

        Entry(String id, List<String> match, JsonObject profile) {
            this.id = id;
            this.match = match;
            this.profile = profile;
        }
    }

    private final List<Entry> entries;

    private AgvnProfileCatalog(List<Entry> entries) {
        this.entries = entries;
    }

    /** The catalog bundled with the app; empty (never null) when the asset is missing or broken. */
    public static synchronized AgvnProfileCatalog get(Context ctx) {
        if (bundled == null) {
            try {
                bundled = parse(FileUtils.readString(ctx, ASSET));
            } catch (Exception e) {
                bundled = EMPTY;
            }
        }
        return bundled;
    }

    public static AgvnProfileCatalog parse(String json) throws AgvnProfileException {
        List<Entry> entries = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1) throw new AgvnProfileException("Kho cấu hình: sai schemaVersion.");
            for (JsonElement el : root.getAsJsonArray("entries")) {
                JsonObject e = el.getAsJsonObject();
                JsonObject profile = e.getAsJsonObject("profile");
                List<String> match = new ArrayList<>();
                JsonArray m = e.getAsJsonArray("match");
                if (m != null) for (JsonElement p : m) match.add(p.getAsString().replace('\\', '/'));
                if (match.isEmpty() && profile.has("exe")) match.add(profile.get("exe").getAsString().replace('\\', '/'));
                String id = e.has("id") ? e.get("id").getAsString() : String.valueOf(entries.size());
                if (!isSpecific(match)) throw new AgvnProfileException("Kho cấu hình: \"" + id + "\" cần đường dẫn riêng của game.");
                entries.add(new Entry(id, match, profile));
            }
        } catch (AgvnProfileException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AgvnProfileException("Kho cấu hình bị lỗi định dạng JSON.");
        }
        return new AgvnProfileCatalog(entries);
    }

    /** True unless the paths are empty, climb out of the folder, or are just one generic exe name. */
    static boolean isSpecific(List<String> match) {
        if (match.isEmpty()) return false;
        for (String p : match) if (p.isEmpty() || p.startsWith("/") || p.contains("..") || p.contains(":")) return false;
        if (match.size() > 1) return true;
        String only = match.get(0);
        return only.contains("/") || !GENERIC.contains(only.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return entries.size();
    }

    /** A fresh profile for {@code gameDir} (name defaults to the folder title), or null when no entry matches. */
    public AgvnProfile find(File gameDir) {
        for (Entry e : entries) {
            boolean all = true;
            for (String p : e.match) if (!new File(gameDir, p).exists()) { all = false; break; }
            if (!all) continue;
            try {
                AgvnProfile p = AgvnProfile.parse(e.profile.toString());
                if (p.schemaVersion == 0) p.schemaVersion = AgvnProfile.SCHEMA_VERSION;
                if (p.name == null || p.name.trim().isEmpty()) p.name = AgvnProfile.defaultFor(gameDir.getName()).name;
                return p;
            } catch (AgvnProfileException ignored) {
                // a broken entry never blocks the import: the folder falls back to auto settings
            }
        }
        return null;
    }
}
