/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.widget.EditText;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.inputcontrols.InputControlsManager;

import java.io.File;
import java.util.List;

/**
 * "Bộ phím ảo" of a game, from [🗂] on the bar of every game and the menu of a "Chạy nhẹ" game: Tải (tap a set in the
 * list: the game uses it now and at every start), Lưu (the keys on screen kept as a set with a name, listed in every
 * game, {@link AgvnKeySets}) and Sửa (the keys edited on the game screen). Players set keys up in a game's settings and
 * found them missing in the game, or lost them (18/10/2026). Android's own dialogs, as {@link AgvnLightKeyPicker}: the
 * Ren'Py and mkxp-z screens have no AppCompat theme. UI thread.
 */
final class AgvnKeySetDialog {
    /** What the game's screen does for the dialog. */
    interface Host {
        /** The name "Lưu" offers for a new set. */
        String gameName();

        /** The set on screen as saved, or -1 (none, or the game's own keys that no set has). */
        int shownId();

        /** The keys on screen, as an .icp or {"elements": [...]}; null when the game has none. */
        String shownKeys();

        /** Makes set {@code id}, of {@code json}, the game's keys now and at every start; false when it cannot. */
        boolean load(int id, String json);

        /** "Sửa": the keys are edited on the game screen. */
        void edit();

        /** Set {@code id} was written: lists the game holds may show it. */
        void saved(int id);
    }

    private static final int THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    private static final int MAX_NAME = 40;

    private AgvnKeySetDialog() {}

    static void open(Activity a, Host host) {
        File dir = InputControlsManager.getProfilesDir(a);
        List<AgvnKeySets.Entry> sets = AgvnKeySets.list(dir);
        int shown = host.shownId(), checked = -1;
        String[] names = new String[sets.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = sets.get(i).name;
            if (sets.get(i).id == shown) checked = i;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(a, THEME)
                .setTitle(R.string.agvn_sets_title)
                .setPositiveButton(R.string.agvn_sets_save, (d, w) -> askName(a, host, dir))
                .setNeutralButton(R.string.agvn_sets_edit, (d, w) -> host.edit())
                .setNegativeButton(R.string.agvn_cancel, null);
        if (sets.isEmpty()) builder.setMessage(R.string.agvn_sets_none);
        else builder.setSingleChoiceItems(names, checked, (d, which) -> {
            d.dismiss();
            load(a, host, dir, sets.get(which));
        });
        AgvnBindingPicker.show(a, builder.create());
    }

    private static void load(Activity a, Host host, File dir, AgvnKeySets.Entry set) {
        if (set.id == host.shownId()) return;
        String json = AgvnKeySets.json(dir, set.id);
        boolean loaded = json != null && host.load(set.id, json);
        AppUtils.showToast(a, loaded ? a.getString(R.string.agvn_sets_loaded, set.name) : a.getString(R.string.agvn_sets_failed));
    }

    /** "Lưu": the name of the set, the shown set's own when it is the player's, else the game's. */
    private static void askName(Activity a, Host host, File dir) {
        String keys = host.shownKeys();
        if (keys == null) {
            AppUtils.showToast(a, R.string.agvn_sets_nothing);
            return;
        }
        String name = host.gameName().trim();
        int shown = host.shownId();
        for (AgvnKeySets.Entry set : AgvnKeySets.list(dir))
            if (set.id == shown && !AgvnControlsFork.isReservedId(set.id)) name = set.name;
        if (name.isEmpty()) name = a.getString(R.string.agvn_edit_own_profile);
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setText(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NAME)});
        input.setSelectAllOnFocus(true);
        String fallback = name;
        AgvnBindingPicker.show(a, new AlertDialog.Builder(a, THEME)
                .setTitle(R.string.agvn_sets_save_title)
                .setView(input)
                .setPositiveButton(R.string.agvn_edit_save, (d, w) -> {
                    String typed = input.getText().toString().trim();
                    save(a, host, dir, keys, typed.isEmpty() ? fallback : typed);
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .create());
    }

    /** Over the player's set of that name after asking, unless it is the set on screen; else a new set. */
    private static void save(Activity a, Host host, File dir, String keys, String name) {
        int over = AgvnKeySets.named(dir, name);
        if (over < 0 || over == host.shownId()) {
            write(a, host, dir, keys, name, over);
            return;
        }
        AgvnBindingPicker.show(a, new AlertDialog.Builder(a, THEME)
                .setMessage(a.getString(R.string.agvn_sets_overwrite, name))
                .setPositiveButton(R.string.agvn_sets_overwrite_ok, (d, w) -> write(a, host, dir, keys, name, over))
                .setNegativeButton(R.string.agvn_cancel, null)
                .create());
    }

    private static void write(Activity a, Host host, File dir, String keys, String name, int over) {
        int id = AgvnKeySets.save(dir, keys, name, over);
        if (id > 0) host.saved(id);
        String saved = null;
        for (AgvnKeySets.Entry set : AgvnKeySets.list(dir)) if (set.id == id) saved = set.name;
        AppUtils.showToast(a, id > 0 ? a.getString(R.string.agvn_sets_saved, saved != null ? saved : name)
                : a.getString(R.string.agvn_sets_failed));
    }
}
