/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

/** About dialog: AGVN Player, version, agvn.io, and a button to the open-source licenses. */
public final class AgvnAbout {
    private AgvnAbout() {}

    public static void show(Activity activity) {
        String version = "";
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {}
        new AlertDialog.Builder(activity)
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.app_name)
                .setMessage(activity.getString(R.string.agvn_about_message, version))
                .setPositiveButton(R.string.agvn_close, null)
                .setNeutralButton(R.string.agvn_licenses, (d, w) -> activity.startActivity(new Intent(activity, LicensesActivity.class)))
                .show();
    }
}
