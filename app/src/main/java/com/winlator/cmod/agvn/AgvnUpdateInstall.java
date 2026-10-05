/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.text.format.Formatter;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads the update with a progress dialog (a cut-off download resumes next time), then opens Android's
 * installer. Android asks once for "Cài ứng dụng không rõ nguồn gốc" and always asks the player to confirm.
 */
final class AgvnUpdateInstall {
    private static final String TAG = "AGVN";
    /** Room for Android's own copy while installing, on top of the downloaded file. */
    private static final long SPARE_BYTES = 200L * 1000 * 1000;

    private AgvnUpdateInstall() {}

    static void start(Activity activity, AgvnUpdateInfo info) {
        File apk = AgvnUpdater.apkFile(activity, info);
        AgvnUpdater.cleanupExcept(activity, apk);
        long need = (info.size - Math.min(apk.length(), info.size)) + info.size + SPARE_BYTES;
        long free = activity.getCacheDir().getUsableSpace();
        if (free < need) {
            AgvnUpdateDialogs.message(activity, activity.getString(R.string.agvn_update_no_space,
                    Formatter.formatShortFileSize(activity, free), Formatter.formatShortFileSize(activity, need)));
            return;
        }

        AtomicBoolean cancel = new AtomicBoolean();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.agvn_update_available_title, info.versionName))
                .setMessage(apk.length() >= info.size ? activity.getString(R.string.agvn_update_verifying)
                        : activity.getString(R.string.agvn_update_downloading, (int) (apk.length() * 100 / info.size)))
                .setNegativeButton(R.string.agvn_cancel, (d, w) -> cancel.set(true))
                .setCancelable(false)
                .show();
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int[] shown = {0};
        new Thread(() -> {
            Exception error = null;
            try {
                AgvnUpdater.download(activity, info, apk, (done, total) -> {
                    int percent = (int) (done * 100 / Math.max(1, total));
                    if (percent == shown[0]) return;
                    shown[0] = percent;
                    activity.runOnUiThread(() -> dialog.setMessage(percent >= 100
                            ? activity.getString(R.string.agvn_update_verifying)
                            : activity.getString(R.string.agvn_update_downloading, percent)));
                }, cancel);
            } catch (Exception e) {
                Log.w(TAG, "update download failed", e);
                error = e;
            }
            Exception failed = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if (dialog.isShowing()) dialog.dismiss();
                if (cancel.get()) Toast.makeText(activity, R.string.agvn_update_paused, Toast.LENGTH_LONG).show();
                else if (failed != null) AgvnUpdateDialogs.message(activity,
                        activity.getString(R.string.agvn_update_failed, AgvnUpdateDialogs.reason(activity, failed)));
                else install(activity, apk);
            });
        }, "AgvnUpdateDownload").start();
    }

    /** Asks Android to install the checked APK; first sends the player to allow installs once if needed. */
    static void install(Activity activity, File apk) {
        if (!AgvnUpdater.canInstall(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.agvn_update_allow_title)
                    .setMessage(R.string.agvn_update_allow)
                    .setPositiveButton(R.string.agvn_update_open_settings, (d, w) -> {
                        try {
                            activity.startActivity(AgvnUpdater.allowInstallIntent(activity));
                        } catch (ActivityNotFoundException e) {
                            Log.w(TAG, "no unknown-sources screen", e);
                        }
                        // shown under the Settings screen, so it is there when the player comes back
                        new AlertDialog.Builder(activity)
                                .setMessage(R.string.agvn_update_allow_done)
                                .setPositiveButton(R.string.agvn_update_continue, (d2, w2) -> install(activity, apk))
                                .setNegativeButton(R.string.agvn_cancel, null)
                                .show();
                    })
                    .setNegativeButton(R.string.agvn_cancel, null)
                    .show();
            return;
        }
        try {
            Toast.makeText(activity, R.string.agvn_update_confirm, Toast.LENGTH_LONG).show();
            activity.startActivity(AgvnUpdater.installIntent(activity, apk));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Log.w(TAG, "installer not opened", e);
            AgvnUpdateDialogs.message(activity, activity.getString(R.string.agvn_update_failed, String.valueOf(e.getMessage())));
        }
    }
}
