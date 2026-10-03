/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

import java.util.concurrent.TimeUnit;

/**
 * Once a day in the background, with any network: is a newer AGVN Player published? If so, one notification per
 * version ("AGVN Player có bản mới …"); a tap opens the app, which offers it. Like the check when the app opens, it
 * reads only agvn-update.txt from this repository's GitHub Releases and sends nothing about the phone; "Tự kiểm tra"
 * off stops it.
 */
public final class AgvnUpdateJob extends JobService {
    private static final String TAG = "AGVN";
    private static final int JOB_ID = 0x4A6E;
    private static final int NOTIFICATION_ID = 0x4A6F;
    private static final String CHANNEL = "agvn_update", PREF_NOTIFIED = "agvn_update_notified_code";
    /** On the app's start intent from the notification: offer the update at once. */
    static final String EXTRA_OFFER = "agvn_update_offer";

    /** At app start: the daily background check, once (scheduling it again changes nothing). */
    public static void schedule(Context ctx) {
        JobScheduler jobs = ctx.getSystemService(JobScheduler.class);
        if (jobs == null || jobs.getPendingJob(JOB_ID) != null) return;
        jobs.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(ctx, AgvnUpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context app = getApplicationContext();
        new Thread(() -> {
            try {
                check(app);
            } finally {
                jobFinished(params, false);
            }
        }, "AgvnUpdateJob").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false; // tomorrow's run is soon enough
    }

    private static void check(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (!prefs.getBoolean(AgvnUpdateDialogs.PREF_AUTO, true)) return;
        try {
            AgvnUpdateInfo info = AgvnUpdater.fetch();
            AgvnUpdateBadge.found(ctx, info);
            if (info == null || !info.isNewerThan(AgvnUpdater.installedCode(ctx))) return;
            if (prefs.getInt(PREF_NOTIFIED, 0) >= info.versionCode) return; // told once per version
            notify(ctx, info);
            prefs.edit().putInt(PREF_NOTIFIED, info.versionCode).apply();
        } catch (Exception e) {
            Log.w(TAG, "background update check failed", e);
        }
    }

    private static void notify(Context ctx, AgvnUpdateInfo info) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, ctx.getString(R.string.agvn_update_channel),
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        if (open == null) return;
        open.putExtra(EXTRA_OFFER, true);
        PendingIntent tap = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NOTIFICATION_ID, new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.winlator_mark)
                .setContentTitle(ctx.getString(R.string.agvn_update_available_title, info.versionName))
                .setContentText(ctx.getString(R.string.agvn_update_notify_text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build());
        Log.i(TAG, "update " + info.versionName + " told in a notification");
    }
}
