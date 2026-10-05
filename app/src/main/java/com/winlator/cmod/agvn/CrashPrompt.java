/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

/** After a recorded crash, offers the player a one-tap log export on the next start. */
public final class CrashPrompt {
    private CrashPrompt() {}

    public static void showIfPending(Activity activity) {
        if (!CrashRecorder.hasPendingCrash(activity)) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_crash_title)
                .setMessage(R.string.agvn_crash_message)
                .setCancelable(false)
                .setPositiveButton(R.string.agvn_export, (dialog, which) -> {
                    Toast.makeText(activity, R.string.agvn_export_running, Toast.LENGTH_SHORT).show();
                    DiagnosticsExporter.export(activity, () -> CrashRecorder.clearPendingCrash(activity));
                })
                .setNegativeButton(R.string.agvn_skip, (dialog, which) -> CrashRecorder.clearPendingCrash(activity))
                .show();
    }
}
