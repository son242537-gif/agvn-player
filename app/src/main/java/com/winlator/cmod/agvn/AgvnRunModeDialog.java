/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.AppUtils;

/**
 * Library action "Cách chạy": "Chạy nhẹ" or Windows for one game, the way it runs now marked. Also the question asked
 * before "Chạy bằng Windows" in a game's "Chạy nhẹ" menu, where a slip of the finger used to change it for good.
 */
public final class AgvnRunModeDialog {
    private AgvnRunModeDialog() {}

    public static void show(Activity activity, Shortcut shortcut, Runnable onChanged) {
        AgvnRunMode.Light light = AgvnRunMode.lightFor(shortcut);
        boolean windows = AgvnRunMode.hasWindows(shortcut);
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.agvn_runner_title, shortcut.name));
        if (light == null || !windows) { // one way only: say which, and why there is nothing to choose
            b.setMessage(light == null ? R.string.agvn_runner_only_windows : R.string.agvn_no_windows_exe)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        boolean nowLight = AgvnRunMode.isLight(shortcut);
        CharSequence[] ways = {activity.getString(R.string.agvn_runner_light), activity.getString(R.string.agvn_runner_windows)};
        b.setSingleChoiceItems(ways, nowLight ? 0 : 1, (d, which) -> {
            d.dismiss();
            if ((which == 0) == nowLight) return;
            if (which == 0) AgvnRunMode.toLight(shortcut, light);
            else AgvnRunMode.toWindows(shortcut);
            AppUtils.showToast(activity, activity.getString(which == 0 ? R.string.agvn_runner_now_light : R.string.agvn_runner_now_windows));
            if (onChanged != null) onChanged.run();
        }).setNegativeButton(R.string.agvn_cancel, null).show();
    }

    /** Asks before a game in "Chạy nhẹ" moves to Windows, and says where the way back is. */
    static void confirmWindows(Activity activity, Runnable windows) {
        new AlertDialog.Builder(activity)
                .setMessage(R.string.agvn_runner_confirm_windows)
                .setPositiveButton(R.string.agvn_runner_confirm_yes, (d, w) -> windows.run())
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }
}
