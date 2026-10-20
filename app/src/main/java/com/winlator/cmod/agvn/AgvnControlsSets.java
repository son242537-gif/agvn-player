/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.view.View;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.widget.InputControlsView;

import java.io.File;
import java.util.Map;

/**
 * "Bộ phím ảo" of a Windows game ({@link AgvnKeySetDialog}), and the sidebar's profile list: a set the player picks in
 * either stays the game's at the next start. Before, the sidebar changed the keys for the session only, and a set made
 * in "Điều khiển" while the game ran was not in its lists (each loaded once at the start). UI thread only.
 */
public final class AgvnControlsSets implements AgvnKeySetDialog.Host {
    private final XServerDisplayActivity activity;
    private final Runnable edit, changed;
    /** What the profiles folder held when the game's lists were last read ({@link #refresh}). */
    private String stamp;

    AgvnControlsSets(XServerDisplayActivity activity, Runnable edit, Runnable changed) {
        this.activity = activity;
        this.edit = edit;
        this.changed = changed;
        stamp = stamp(InputControlsManager.getProfilesDir(activity));
    }

    /** Not while the controls are edited on the game screen: the editor's toolbar is up. */
    void open() {
        InputControlsView view = activity.getInputControlsView();
        if (view == null || view.isEditMode()) return;
        refresh();
        AgvnKeySetDialog.open(activity, this);
    }

    /** Reads the profiles again when a set was saved or changed elsewhere since: the sidebar lists it then. */
    void refresh() {
        InputControlsManager manager = activity.agvnControlsManager();
        String now = stamp(InputControlsManager.getProfilesDir(activity));
        if (manager == null || now.equals(stamp)) return;
        stamp = now;
        manager.loadProfiles(false); // as the game loaded them at start (templates included)
        activity.agvnRefreshControlsSidebar();
    }

    @Override
    public String gameName() {
        Shortcut shortcut = activity.agvnShortcut();
        return shortcut != null ? shortcut.name : "";
    }

    @Override
    public int shownId() {
        InputControlsView view = activity.getInputControlsView();
        return view != null && view.getVisibility() == View.VISIBLE && view.getProfile() != null ? view.getProfile().id : -1;
    }

    /** The shown profile's file (every edit is saved to it at once, AgvnControlsEditor), else the game's hidden by 👁. */
    @Override
    public String shownKeys() {
        ControlsProfile hidden = shownId() < 0 ? AgvnControlsFork.gameProfile(activity) : null;
        int id = hidden != null ? hidden.id : shownId();
        File file = id > 0 ? ControlsProfile.getProfileFile(activity, id) : null;
        String json = file != null && file.isFile() ? FileUtils.readString(file) : null;
        return json != null && !json.trim().isEmpty() ? json : null;
    }

    @Override
    public boolean load(int id, String json) {
        InputControlsManager manager = activity.agvnControlsManager();
        InputControlsView view = activity.getInputControlsView();
        if (manager == null || view == null) return false;
        ControlsProfile profile = manager.getProfile(id);
        if (profile == null) {
            manager.loadProfiles(false);
            profile = manager.getProfile(id);
        }
        if (profile == null) return false;
        view.releaseAll();
        activity.agvnShowControls(profile);
        view.setShowTouchscreenControls(true);
        pick(activity.agvnShortcut(), id);
        activity.agvnRefreshControlsSidebar();
        changed.run();
        return true;
    }

    @Override
    public void edit() {
        edit.run();
    }

    @Override
    public void saved(int id) {
        refresh();
    }

    /** The sidebar's list changed to {@code profile} (null: "Tắt") by the player: kept for the game, as "Tải" keeps it. */
    public static void sidebarPicked(Shortcut shortcut, ControlsProfile profile) {
        if (profile != null) pick(shortcut, profile.id);
        else AgvnControlsFork.setHidden(shortcut, true); // as 👁 hides them: the set stays the game's
    }

    static void pick(Shortcut shortcut, int id) {
        if (shortcut == null) return;
        for (Map.Entry<String, String> extra : AgvnKeySets.pickExtras(id).entrySet()) shortcut.putExtra(extra.getKey(), extra.getValue());
        shortcut.saveData();
    }

    /** The names and times of the profile files. */
    private static String stamp(File dir) {
        StringBuilder text = new StringBuilder();
        String[] names = dir.list();
        if (names != null) {
            java.util.Arrays.sort(names);
            for (String name : names) text.append(name).append(':').append(new File(dir, name).lastModified()).append(';');
        }
        return text.toString();
    }
}
