/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

import java.io.File;

/**
 * The other way to install the update when one did not. AGVN hands the checked APK to the phone's installer as a file
 * (a content URI), which most phones install. On OnePlus, OPPO, realme and vivo phones the installer refused the
 * 715 MB update handed over that way, from AGVN as from ZArchiver ("Parsing failed: APK contains no signature
 * files"), while the same file sent over from another device installed (10/10/2026). So when the player comes back
 * to AGVN soon after and the update is still not installed, AGVN offers once the other way: an install session of
 * Android ({@link AgvnSessionInstall}), or the phone's installer after a session that did not install. The way that
 * installed an update is the first one tried next time. Learned from what happened on the phone, never from its maker.
 */
public final class AgvnUpdateRetry {
    static final String PREF_CODE = "agvn_update_tried_code", PREF_AT = "agvn_update_tried_at",
            PREF_TRIED_SESSION = "agvn_update_tried_session", PREF_SESSION = "agvn_update_session";
    /** How long after an install was handed over coming back to AGVN still means coming back from it. */
    static final long WINDOW_MS = 30L * 60 * 1000;

    private AgvnUpdateRetry() {}

    private static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    /** True when an install session put the last update in: tried first next time. */
    static boolean preferSession(Context ctx) {
        return prefs(ctx).getBoolean(PREF_SESSION, false);
    }

    /** The update {@code code} goes to Android now, through a session or through the phone's installer. */
    static void tried(Context ctx, long code, boolean session) {
        prefs(ctx).edit().putLong(PREF_CODE, code).putLong(PREF_AT, System.currentTimeMillis())
                .putBoolean(PREF_TRIED_SESSION, session).apply();
    }

    /** Android did not install the session and said why: the phone's installer comes first again, no other offer. */
    static void sessionFailed(Context ctx) {
        prefs(ctx).edit().remove(PREF_SESSION).remove(PREF_CODE).remove(PREF_AT).remove(PREF_TRIED_SESSION).apply();
    }

    /** From the library's onResume: learns which way installed, or offers the other way once. */
    public static void onResume(Activity activity) {
        SharedPreferences prefs = prefs(activity);
        long code = prefs.getLong(PREF_CODE, 0);
        if (code <= 0) return;
        long at = prefs.getLong(PREF_AT, 0);
        boolean session = prefs.getBoolean(PREF_TRIED_SESSION, false);
        long installed = AgvnUpdater.installedCode(activity);
        SharedPreferences.Editor edit = prefs.edit().remove(PREF_CODE).remove(PREF_AT).remove(PREF_TRIED_SESSION);
        if (installed >= code) edit.putBoolean(PREF_SESSION, session); // this way worked here
        edit.apply();
        File apk = AgvnUpdater.apkFile(activity, code);
        if (!offer(code, installed, at, System.currentTimeMillis(), apk.isFile())) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.agvn_update_retry_title)
                .setMessage(session ? R.string.agvn_update_retry_installer : R.string.agvn_update_retry_session)
                .setPositiveButton(R.string.agvn_update_retry_now,
                        (d, w) -> AgvnUpdateInstall.install(activity, apk, code, !session))
                .setNegativeButton(R.string.agvn_update_later, null)
                .show();
    }

    /** Whether to offer the other way: update {@code tried} not installed, its file there, and the try recent. */
    static boolean offer(long tried, long installed, long at, long now, boolean apkThere) {
        return tried > installed && apkThere && now >= at && now - at < WINDOW_MS;
    }
}
