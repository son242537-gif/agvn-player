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

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Installs the bundled AGVN on-screen layouts ({@link AgvnLayouts}, profile ids 9000-9005) and gives every game the
 * layout that fits it, unless the player picked another controls profile or turned the controls off ("0").
 * Installed layouts are refreshed when the bundled agvnLayoutVersion grows, unless the player edited them (the
 * controls editor drops that key when it saves).
 */
public final class AgvnControls {
    private static final String TAG = "AGVN";
    static final String VERSION_KEY = "agvnLayoutVersion";

    /** What to do with the file at a layout's id. */
    enum Install { WRITE, KEEP, FOREIGN }

    private AgvnControls() {}

    /** Writes missing or outdated layouts; returns the kinds whose id really holds an AGVN layout. Safe to call often. */
    public static Set<String> ensureProfiles(Context ctx) {
        Set<String> available = new HashSet<>();
        for (String kind : AgvnLayouts.KINDS) {
            try {
                String asset = FileUtils.readString(ctx, AgvnLayouts.assetFor(kind));
                if (asset == null || asset.isEmpty()) continue;
                File target = ControlsProfile.getProfileFile(ctx, AgvnLayouts.idFor(kind));
                Install action = installAction(target.isFile() ? FileUtils.readString(target) : null, asset);
                if (action == Install.WRITE && !FileUtils.writeString(target, asset)) continue;
                if (action != Install.FOREIGN) available.add(kind);
                else Log.w(TAG, target.getName() + " is not a controls profile; layout " + kind + " not used");
            } catch (Exception e) {
                Log.w(TAG, "cannot install AGVN controls layout " + kind, e);
            }
        }
        return available;
    }

    /**
     * Missing or unreadable file, or an older bundled version: WRITE. Same or newer bundled version: KEEP. A controls
     * profile without the version key is our layout saved by the player (the editors drop the key, a rename changes the
     * name): KEEP, games keep using it. New profiles never get ids 9000-9099 (InputControlsManager, AgvnControlsFork),
     * so nothing else is expected there; a JSON file that is not a controls profile is left alone and not used (FOREIGN).
     */
    static Install installAction(String installed, String asset) {
        if (installed == null || installed.trim().isEmpty()) return Install.WRITE;
        JsonObject current;
        try {
            current = JsonParser.parseString(installed).getAsJsonObject();
        } catch (RuntimeException e) {
            return Install.WRITE; // broken file: unusable anyway
        }
        JsonObject ours = JsonParser.parseString(asset).getAsJsonObject();
        if (current.has(VERSION_KEY)) return intOf(current.get(VERSION_KEY)) < intOf(ours.get(VERSION_KEY)) ? Install.WRITE : Install.KEEP;
        return current.has("elements") && current.get("elements").isJsonArray() ? Install.KEEP : Install.FOREIGN;
    }

    /**
     * Launch hook (before the controls are shown): picks the layout for games that have none, the legacy 900, or an
     * AGVN layout AGVN picked itself. Saved with the shortcut by the activity, which keeps agvnControlsAuto = "1".
     */
    public static void applyDefault(Context ctx, Shortcut shortcut) {
        if (shortcut == null) return;
        Set<String> available = ensureProfiles(ctx);
        String current = shortcut.getExtra(AgvnLayouts.EXTRA_PROFILE, null);
        if (!AgvnLayouts.shouldAssign(current, shortcut.getExtra(AgvnLayouts.EXTRA_AUTO, null))) return;
        String kind = AgvnLayouts.launchKind(shortcut.getExtra(AgvnLayouts.EXTRA_KIND, null),
                shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE, null), shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR, null),
                AgvnExeRedirect.toUnixPath(shortcut.path, shortcut.container));
        String chosen = pick(kind, available);
        if (chosen == null) return;
        String id = String.valueOf(AgvnLayouts.idFor(chosen));
        if (!id.equals(current)) Log.i(TAG, "controls layout " + chosen + " (" + id + ") for " + shortcut.name);
        shortcut.putExtra(AgvnLayouts.EXTRA_PROFILE, id);
        shortcut.putExtra(AgvnLayouts.EXTRA_AUTO, "1");
    }

    /** {@code kind} when installed, else the PC layout when installed, else null. */
    static String pick(String kind, Set<String> available) {
        if (available.contains(kind)) return kind;
        return available.contains(AgvnLayouts.PC) ? AgvnLayouts.PC : null;
    }

    private static int intOf(JsonElement e) {
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
