/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.util.Log;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.InputControlsManager;

import java.io.File;

/**
 * "Bộ phím ảo" of a "Chạy nhẹ" game ({@link AgvnKeySetDialog}): a set loaded becomes the game's pick, as one picked in
 * its settings (controlsProfile in its .desktop file, {@link AgvnLightPick}), and its keys show at once. The game's own
 * keys (edited on its screen, kept in agvn-light.properties) are never dropped unseen: when the game moves to another
 * set, here or in its settings, they are kept as a set named after the game first ({@link #keep}). Before, a set picked
 * in the settings hid them for good, and the game had no list of sets (18/10/2026). UI thread.
 */
final class AgvnLightSets implements AgvnKeySetDialog.Host {
    private static final String TAG = "AGVN";

    private final AgvnLightTools tools;

    AgvnLightSets(AgvnLightTools tools) {
        this.tools = tools;
    }

    void open() {
        try {
            new InputControlsManager(tools.activity).loadProfiles(true); // the app's own profiles, on a first use
        } catch (RuntimeException e) { // a profile it cannot read: the list skips it
            Log.w(TAG, "controls profiles", e);
        }
        AgvnControls.ensureProfiles(tools.activity); // and AGVN's layouts, even before any Windows game ran
        AgvnKeySetDialog.open(tools.activity, this);
    }

    @Override
    public String gameName() {
        return tools.gameName;
    }

    /** The pick, else AGVN's layout of the game type; -1 while the game's own keys show. */
    @Override
    public int shownId() {
        if (tools.prefs.layout(tools.game) != null) return -1;
        return tools.pick.id > 0 ? tools.pick.id : AgvnLayouts.idFor(tools.layout.kind);
    }

    @Override
    public String shownKeys() {
        return tools.layout.toJson();
    }

    @Override
    public boolean load(int id, String json) {
        String path = tools.activity.getIntent() != null ? tools.activity.getIntent().getStringExtra("shortcut_path") : null;
        if (path == null || path.isEmpty() || !AgvnDesktopExtras.put(new File(path), AgvnKeySets.pickExtras(id))) return false;
        keep(tools.activity, tools.prefs, tools.game, tools.gameName);
        tools.keys.releaseAll();
        tools.layout.use(json);
        tools.keys.select(-1);
        tools.pick = new AgvnLightPick(id, json);
        tools.prefs.picked(tools.game, id);
        tools.setKeysShown(true);
        return true;
    }

    @Override
    public void edit() {
        tools.edit();
    }

    @Override
    public void saved(int id) {
        // the dialog lists the sets from their files each time it opens
    }

    /**
     * Keeps the game's own keys, when it has some, as a set named after the game, unless a set has the very same keys,
     * then drops them from the game's settings: the game's pick shows from now on. Left as they are when no set can be
     * written, and tried again at the next start.
     */
    static void keep(Activity a, AgvnLightPrefs prefs, String game, String gameName) {
        String own = prefs.layout(game);
        if (own == null) return;
        File dir = InputControlsManager.getProfilesDir(a);
        int id = AgvnKeySets.same(dir, own);
        boolean fresh = id < 0;
        String name = gameName.trim().isEmpty() ? a.getString(R.string.agvn_edit_own_profile) : gameName.trim();
        if (fresh) id = AgvnKeySets.save(dir, own, name, -1);
        if (id < 0) return;
        prefs.clearLayout(game);
        if (!fresh) return;
        for (AgvnKeySets.Entry set : AgvnKeySets.list(dir)) if (set.id == id) name = set.name;
        Log.i(TAG, "own keys of " + game + " kept as controls profile " + id);
        AppUtils.showToast(a, a.getString(R.string.agvn_sets_kept, name));
    }
}
