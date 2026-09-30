/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * "Cập nhật ứng dụng" in Settings, and the once-a-day check when the app opens. When a newer version is out the
 * player sees its notes and size and taps "Cập nhật"; {@link AgvnUpdateInstall} downloads, checks and installs it.
 */
public final class AgvnUpdateDialogs {
    private static final String TAG = "AGVN";
    static final String PREF_AUTO = "agvn_update_auto";
    static final String PREF_BETA = "agvn_update_beta";
    static final String PREF_LAST_CHECK = "agvn_update_last_check";

    private AgvnUpdateDialogs() {}

    /** The Settings dialog: installed version, the two switches and "Kiểm tra cập nhật". */
    public static void show(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int pad = dp(activity, 20);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(activity, 8), pad, 0);
        TextView current = new TextView(activity);
        current.setText(activity.getString(R.string.agvn_update_current, AgvnUpdater.installedName(activity)));
        current.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.addView(current);
        box.addView(checkBox(activity, R.string.agvn_update_auto, prefs, PREF_AUTO, true));
        box.addView(checkBox(activity, R.string.agvn_update_beta, prefs, PREF_BETA, false));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_update_title)
                .setView(box)
                .setPositiveButton(R.string.agvn_update_check, (d, w) -> check(activity, true))
                .setNegativeButton(R.string.agvn_update_close, null)
                .show();
    }

    /** At start: frees space of installed downloads, then checks at most once a day when "Tự kiểm tra" is on. */
    public static void checkDaily(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        new Thread(() -> AgvnUpdater.cleanupInstalled(activity), "AgvnUpdateCleanup").start();
        if (!prefs.getBoolean(PREF_AUTO, true)) return;
        if (!AgvnUpdateInfo.dueForCheck(prefs.getLong(PREF_LAST_CHECK, 0), System.currentTimeMillis())) return;
        check(activity, false);
    }

    private static void check(Activity activity, boolean manual) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean beta = prefs.getBoolean(PREF_BETA, false);
        AlertDialog working = manual ? new AlertDialog.Builder(activity)
                .setMessage(R.string.agvn_update_checking).setCancelable(false).show() : null;
        new Thread(() -> {
            AgvnUpdateInfo info = null;
            Exception error = null;
            try {
                info = AgvnUpdater.fetch(beta);
                prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
            } catch (Exception e) {
                Log.w(TAG, "update check failed", e);
                error = e;
            }
            AgvnUpdateInfo found = info;
            Exception failed = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (working != null && working.isShowing()) working.dismiss();
                if (found != null && found.isNewerThan(AgvnUpdater.installedCode(activity))) offer(activity, found);
                else if (!manual) return;
                else if (failed != null) message(activity, activity.getString(R.string.agvn_update_failed, reason(activity, failed)));
                else if (found == null) message(activity, activity.getString(R.string.agvn_update_not_published));
                else message(activity, activity.getString(R.string.agvn_update_latest, AgvnUpdater.installedName(activity)));
            });
        }, "AgvnUpdateCheck").start();
    }

    private static void offer(Activity activity, AgvnUpdateInfo info) {
        String notes = info.notes.isEmpty() ? "" : info.notes + "\n\n";
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.agvn_update_available_title, info.versionName))
                .setMessage(activity.getString(R.string.agvn_update_available, info.versionName, info.sizeText(),
                        AgvnUpdater.installedName(activity), notes))
                .setPositiveButton(R.string.agvn_update_now, (d, w) -> AgvnUpdateInstall.start(activity, info))
                .setNegativeButton(R.string.agvn_update_later, null)
                .show();
    }

    static void message(Activity activity, String text) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity).setMessage(text).setPositiveButton(android.R.string.ok, null).show();
    }

    /** A short reason for the player; network problems get one plain sentence. */
    static String reason(Context context, Exception e) {
        if (e instanceof UnknownHostException || e instanceof SocketTimeoutException || e instanceof ConnectException)
            return context.getString(R.string.agvn_update_no_network);
        return String.valueOf(e.getMessage());
    }

    private static CheckBox checkBox(Activity activity, int text, SharedPreferences prefs, String key, boolean fallback) {
        CheckBox box = new CheckBox(activity);
        box.setText(text);
        box.setChecked(prefs.getBoolean(key, fallback));
        box.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        box.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
        return box;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
