/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.AppUtils;

/** Library action "Đồ họa": one tap to pick Tự động / Thấp / Trung bình / Cao, with what each one means. */
public final class AgvnQualityDialog {
    private AgvnQualityDialog() {}

    public static void show(Activity activity, Shortcut shortcut, Runnable onChanged) {
        AgvnQuality.Level[] levels = AgvnQuality.Level.values();
        String[] labels = new String[levels.length];
        for (int i = 0; i < levels.length; i++) labels[i] = label(activity, shortcut, levels[i]);
        int checked = AgvnQuality.current(shortcut).ordinal();
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.agvn_quality_title, shortcut.name))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    AgvnQuality.apply(activity, shortcut, levels[which]);
                    AppUtils.showToast(activity, activity.getString(R.string.agvn_quality_applied, name(activity, levels[which])));
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(R.string.agvn_cancel, null)
                .show();
    }

    private static String label(Activity activity, Shortcut shortcut, AgvnQuality.Level level) {
        LaunchPresetResolver.Effective eff = AgvnQuality.effective(activity, shortcut, level);
        String fps = eff.fps > 0 ? eff.fps + " FPS" : activity.getString(R.string.agvn_unlimited);
        String res = eff.resolution != null ? eff.resolution.replace('x', '×') : activity.getString(R.string.agvn_default_value);
        return name(activity, level) + "\n   " + activity.getString(R.string.agvn_quality_detail, res, fps);
    }

    static String name(Activity activity, AgvnQuality.Level level) {
        switch (level) {
            case LOW: return activity.getString(R.string.agvn_quality_low);
            case MEDIUM: return activity.getString(R.string.agvn_quality_medium);
            case HIGH: return activity.getString(R.string.agvn_quality_high);
            default: return activity.getString(R.string.agvn_quality_auto, DeviceTierManager.current(activity).label);
        }
    }
}
