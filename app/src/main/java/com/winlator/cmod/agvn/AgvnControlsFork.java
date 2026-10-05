/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-game copies of controls profiles for the in-game editor, so editing one game's keys never changes the bundled
 * AGVN layouts or another game. The copy gets a new id outside the reserved ids (900, 9000-9099), the game's name, and
 * no agvnLayoutVersion; the shortcut then points to it (controlsProfile) and remembers it as its own (agvnControlsOwn).
 */
public final class AgvnControlsFork {
    private static final String TAG = "AGVN";
    /** Id of the profile copied for this game; edits to it stay in place. */
    public static final String EXTRA_OWN = "agvnControlsOwn";
    /** "1" while the player hid the on-screen controls of this game. */
    public static final String EXTRA_HIDDEN = "agvnControlsHidden";
    static final int RESERVED_FIRST = 9000, RESERVED_LAST = 9099;

    private AgvnControlsFork() {}

    static boolean isReservedId(int id) {
        return id == AgvnLayouts.LEGACY_ID || inLayoutRange(id);
    }

    /** Ids 9000-9099, kept for AGVN layouts: new profiles (InputControlsManager, the editor's copies) never get one. */
    public static boolean inLayoutRange(int id) {
        return id >= RESERVED_FIRST && id <= RESERVED_LAST;
    }

    /** One above the highest profile id outside 9000-9099, skipping reserved and existing ids. */
    static int nextForkId(Collection<Integer> existing) {
        int max = 0;
        for (int id : existing) if (!inLayoutRange(id)) max = Math.max(max, id);
        int id = max + 1;
        while (isReservedId(id) || existing.contains(id)) id++;
        return id;
    }

    /** Profile id of a file named controls-&lt;id&gt;.icp, else -1. */
    static int idFromFileName(String name) {
        if (name == null || !name.startsWith("controls-") || !name.endsWith(".icp")) return -1;
        try {
            return Integer.parseInt(name.substring(9, name.length() - 4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static Set<Integer> idsOf(String[] fileNames) {
        Set<Integer> ids = new HashSet<>();
        if (fileNames != null) for (String name : fileNames) {
            int id = idFromFileName(name);
            if (id >= 0) ids.add(id);
        }
        return ids;
    }

    /** The profile JSON with a new id and name, and without the AGVN layout version or template flag. */
    static String forkJson(String json, int id, String name) {
        JsonObject profile = JsonParser.parseString(json).getAsJsonObject();
        profile.addProperty("id", id);
        profile.addProperty("name", name);
        profile.remove(AgvnControls.VERSION_KEY);
        profile.remove("template");
        return profile.toString();
    }

    /**
     * Whether the editor must copy the profile first: not when it is already this game's own copy; otherwise always
     * for a game (the profile may be shared), and without a game only for the bundled AGVN layouts.
     */
    static boolean needsFork(int profileId, String ownId, boolean forGame) {
        if (String.valueOf(profileId).equals(ownId != null ? ownId.trim() : null)) return false;
        return forGame || isReservedId(profileId);
    }

    /** Profile id stored in a shortcut extra, or -1 when missing, "0" (controls off) or not a number. */
    static int parseId(String value) {
        try {
            int id = value != null ? Integer.parseInt(value.trim()) : -1;
            return id > 0 ? id : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The profile the editor may change for this game: {@code base} itself if it is the game's own copy, else a copy
     * (written over the game's earlier copy, one per game). Either way the game uses it from now on.
     */
    static ControlsProfile editable(XServerDisplayActivity activity, ControlsProfile base) {
        Shortcut shortcut = activity.agvnShortcut();
        InputControlsManager manager = activity.agvnControlsManager();
        if (base == null || manager == null) return null;
        String own = shortcut != null ? shortcut.getExtra(EXTRA_OWN, null) : null;
        if (!needsFork(base.id, own, shortcut != null)) {
            if (shortcut != null) useFor(shortcut, base.id);
            return base;
        }
        String name = shortcut != null && !shortcut.name.trim().isEmpty() ? shortcut.name.trim()
                : activity.getString(R.string.agvn_edit_own_profile);
        ControlsProfile fork = fork(activity, manager, base.id, parseId(own), name);
        if (fork != null && shortcut != null) {
            shortcut.putExtra(EXTRA_OWN, String.valueOf(fork.id));
            useFor(shortcut, fork.id);
        }
        return fork;
    }

    /** Makes {@code id} the game's controls profile as the player's choice (AGVN no longer re-picks it at launch). */
    private static void useFor(Shortcut shortcut, int id) {
        shortcut.putExtra(AgvnLayouts.EXTRA_PROFILE, String.valueOf(id));
        shortcut.putExtra(AgvnLayouts.EXTRA_AUTO, null);
        shortcut.saveData();
    }

    private static ControlsProfile fork(Context ctx, InputControlsManager manager, int sourceId, int ownId, String name) {
        try {
            File source = ControlsProfile.getProfileFile(ctx, sourceId);
            String json = source.isFile() ? FileUtils.readString(source) : "";
            if (json.trim().isEmpty()) return null;
            Set<Integer> ids = idsOf(InputControlsManager.getProfilesDir(ctx).list());
            int id = ownId > 0 && !isReservedId(ownId) && ids.contains(ownId) ? ownId : nextForkId(ids);
            Set<String> taken = new HashSet<>();
            for (ControlsProfile p : manager.getProfiles()) if (p.id != id) taken.add(p.getName());
            if (!FileUtils.writeString(ControlsProfile.getProfileFile(ctx, id), forkJson(json, id, uniqueName(name, taken)))) return null;
            manager.loadProfiles(false); // same list the game loaded at start (templates included)
            Log.i(TAG, "controls profile " + sourceId + " copied to " + id + " for " + name);
            return manager.getProfile(id);
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot copy controls profile " + sourceId, e);
            return null;
        }
    }

    /** {@code name}, else "name (n)" with the first free n, like the profile manager's duplicate (pickers list by name). */
    static String uniqueName(String name, Collection<String> taken) {
        String candidate = name;
        for (int i = 1; taken.contains(candidate); i++) candidate = name + " (" + i + ")";
        return candidate;
    }

    /**
     * The profile this game uses (its controlsProfile), else its own edited copy (controls turned off since), else the
     * AGVN layout for its kind; null if none is installed.
     */
    static ControlsProfile gameProfile(XServerDisplayActivity activity) {
        InputControlsManager manager = activity.agvnControlsManager();
        Shortcut shortcut = activity.agvnShortcut();
        if (manager == null) return null;
        for (String key : new String[]{AgvnLayouts.EXTRA_PROFILE, EXTRA_OWN}) {
            int id = shortcut != null ? parseId(shortcut.getExtra(key, null)) : -1;
            ControlsProfile profile = id > 0 ? manager.getProfile(id) : null;
            if (profile != null) return profile;
        }
        String kind = AgvnLayouts.PC;
        if (shortcut != null) kind = AgvnLayouts.launchKind(shortcut.getExtra(AgvnLayouts.EXTRA_KIND, null),
                shortcut.getExtra(AgvnGameImporter.EXTRA_ENGINE, null), shortcut.getExtra(AgvnGameImporter.EXTRA_GAME_DIR, null),
                AgvnExeRedirect.toUnixPath(shortcut.path, shortcut.container));
        String chosen = AgvnControls.pick(kind, AgvnControls.ensureProfiles(activity));
        return chosen != null ? manager.getProfile(AgvnLayouts.idFor(chosen)) : null;
    }

    /** Remembers that the player shows {@code profile} in this game (controls back on, not hidden). */
    static void rememberShown(Shortcut shortcut, ControlsProfile profile) {
        if (shortcut == null || profile == null) return;
        String id = String.valueOf(profile.id);
        if (!id.equals(shortcut.getExtra(AgvnLayouts.EXTRA_PROFILE, null))) {
            shortcut.putExtra(AgvnLayouts.EXTRA_PROFILE, id);
            shortcut.putExtra(AgvnLayouts.EXTRA_AUTO, AgvnLayouts.isAgvnId(id) ? "1" : null);
        }
        shortcut.putExtra(EXTRA_HIDDEN, null);
        shortcut.saveData();
    }

    static boolean isHidden(Shortcut shortcut) {
        return shortcut != null && "1".equals(shortcut.getExtra(EXTRA_HIDDEN, null));
    }

    static void setHidden(Shortcut shortcut, boolean hidden) {
        if (shortcut == null || hidden == isHidden(shortcut)) return;
        shortcut.putExtra(EXTRA_HIDDEN, hidden ? "1" : null);
        shortcut.saveData();
    }
}
