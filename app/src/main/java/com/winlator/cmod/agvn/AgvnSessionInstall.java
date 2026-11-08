/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the checked update through an install session of Android (PackageInstaller), as app stores do: AGVN
 * writes the APK into the session itself, so no installer has to read it from another app's file. Android still asks
 * the player to confirm (USER_ACTION_REQUIRED from Android 12; before it, always), and the result comes back to
 * {@link AgvnUpdateStatusActivity}. Used when the phone's installer did not take the update ({@link AgvnUpdateRetry}).
 */
final class AgvnSessionInstall {
    private static final String TAG = "AGVN";

    private AgvnSessionInstall() {}

    /** Writes {@code apk} into a session with a dialog, then lets Android ask the player. */
    static void start(Activity activity, File apk) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setMessage(R.string.agvn_update_session_writing)
                .setCancelable(false)
                .show();
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                write(app, apk);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "update session not committed", e);
                error = String.valueOf(e.getMessage());
            }
            String failed = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (dialog.isShowing()) dialog.dismiss();
                if (failed != null) {
                    AgvnUpdateRetry.sessionFailed(activity);
                    AgvnUpdateDialogs.message(activity, activity.getString(R.string.agvn_update_failed, failed));
                }
            });
        }, "AgvnUpdateSession").start();
    }

    /** A new session with {@code apk} in it, committed; Android tells AgvnUpdateStatusActivity what follows. */
    static void write(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(apk.length());
        // never installed without the player's yes (maintainer's rule, 09/10/2026)
        if (Build.VERSION.SDK_INT >= 31)
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int id = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[1 << 20];
                for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
                session.fsync(out);
            }
            session.commit(AgvnUpdateStatusActivity.sender(context, id));
            committed = true;
        } finally {
            if (!committed) installer.abandonSession(id);
        }
    }

    /** Android's failures told apart; their words follow in the same order, the last for any other failure. */
    private static final int[] FAILURES = {PackageInstaller.STATUS_FAILURE_STORAGE,
            PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_BLOCKED};
    private static final int[] WORDS = {R.string.agvn_ui_agvn2_install_storage,
            R.string.agvn_ui_agvn2_install_invalid, R.string.agvn_ui_agvn2_install_conflict,
            R.string.agvn_ui_agvn2_install_incompatible, R.string.agvn_ui_agvn2_install_blocked,
            R.string.agvn_ui_agvn2_install_failed};
    /** The same words in Vietnamese (values/agvn_ui_agvn2_strings.xml), for a caller without a Context: the JVM tests. */
    private static final String[] VIETNAMESE = {"máy không đủ bộ nhớ trống để cài",
            "Android thấy tệp cài không hợp lệ", "bản mới không cài đè lên được app đang có",
            "bản mới không hợp với máy này", "máy đang chặn việc cài app", "Android không cài được bản mới"};

    /** The player's words for a session Android did not install, from its {@code status} and {@code message}. */
    static String reason(Context context, int status, String message) {
        return withMessage(context.getString(WORDS[failure(status)]), message);
    }

    /** {@link #reason(Context, int, String)} in Vietnamese. */
    static String reason(int status, String message) {
        return withMessage(VIETNAMESE[failure(status)], message);
    }

    private static int failure(int status) {
        for (int i = 0; i < FAILURES.length; i++) if (FAILURES[i] == status) return i;
        return FAILURES.length;
    }

    private static String withMessage(String why, String message) {
        return message == null || message.isEmpty() ? why : why + " (" + message + ")";
    }
}
