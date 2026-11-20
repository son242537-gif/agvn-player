/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps "Thêm từ tệp nén" unpacking while the player is in another app: a foreground service with the notification
 * "Đang giải nén <tệp> · 42%" and "Hủy", and the phone's processor kept awake. The work itself stays where it was
 * ({@link AgvnUnpackDialog}'s thread); this only tells Android the app is busy. Without the notification permission the
 * service runs all the same, the phone just shows nothing. Tapping the notification returns to the app.
 */
public final class AgvnUnpackService extends Service {
    private static final String ACTION_CANCEL = "com.winlator.cmod.agvn.UNPACK_CANCEL";
    private static final String EXTRA_NAME = "agvn_archive";
    private static final int PROGRESS_ID = 0x4A70, RESULT_ID = 0x4A71;
    /** The notification is redrawn this often at most: Android drops faster updates. */
    private static final long REDRAW_MS = 1000;
    /** An archive that takes longer than this has hung; the processor is let go. */
    private static final long AWAKE_MS = 3 * 60 * 60 * 1000L;

    /** The running work's "Hủy"; null while nothing is unpacked. */
    private static volatile AtomicBoolean cancel;
    private static volatile String archiveName = "";
    private static long drawnAt;
    /** The service once it is in the foreground: only then may it be stopped (Android ends an app that stops it earlier). */
    private static volatile AgvnUnpackService live;
    private PowerManager.WakeLock awake;

    /** Unpacking begins (called while the app is on screen). */
    static synchronized void begin(Context context, String archive, AtomicBoolean cancelFlag) {
        cancel = cancelFlag;
        archiveName = archive;
        drawnAt = 0;
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(RESULT_ID);
            ContextCompat.startForegroundService(context, new Intent(context, AgvnUnpackService.class).putExtra(EXTRA_NAME, archive));
        } catch (RuntimeException e) {
            Log.w("AGVN", "unpack service not started", e); // unpacking goes on, only less protected
        }
    }

    /** From the working thread, often. */
    static synchronized void progress(Context context, int permille) {
        long now = SystemClock.uptimeMillis();
        if (cancel == null || now - drawnAt < REDRAW_MS) return;
        drawnAt = now;
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(PROGRESS_ID, progressNotification(context, archiveName, permille));
        } catch (RuntimeException e) {
            // the phone refused the notification: nothing depends on it
        }
    }

    /**
     * Unpacking ended. {@code result}: what to tell a player who is not looking at the app (null: nothing, the app
     * is on screen or the player cancelled).
     */
    static synchronized void end(Context context, String result) {
        cancel = null;
        try {
            // not yet up: it stops itself when it comes up and finds no work
            if (live != null) context.stopService(new Intent(context, AgvnUnpackService.class));
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            manager.cancel(PROGRESS_ID);
            if (result == null) return;
            channel(manager, context);
            manager.notify(RESULT_ID, new NotificationCompat.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.winlator_mark)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(result)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(result))
                    .setContentIntent(openApp(context))
                    .setAutoCancel(true)
                    .build());
        } catch (RuntimeException e) {
            Log.w("AGVN", "unpack result not shown", e);
        }
    }

    private static void channel(NotificationManager manager, Context context) {
        if (manager.getNotificationChannel(MainActivity.NOTIFICATION_CHANNEL_ID) == null)
            manager.createNotificationChannel(new NotificationChannel(MainActivity.NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
    }

    /** Back to the app as the player left it. */
    private static PendingIntent openApp(Context context) {
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (open == null) open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Notification progressNotification(Context context, String archive, int permille) {
        PendingIntent stop = PendingIntent.getService(context, 1, new Intent(context, AgvnUnpackService.class).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.winlator_mark)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.agvn_unpack_notif_progress, archive, permille / 10))
                .setProgress(1000, permille, false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(openApp(context))
                .addAction(0, context.getString(R.string.agvn_cancel), stop)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AtomicBoolean running = cancel;
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            if (running != null) running.set(true);
            else stopSelf(); // a notification left from work that is over
            return START_NOT_STICKY;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        channel(manager, this);
        String archive = intent != null ? intent.getStringExtra(EXTRA_NAME) : null;
        Notification notification = progressNotification(this, archive != null ? archive : archiveName, 0);
        // Android wants startForeground after every startForegroundService, also when there is nothing left to do
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(PROGRESS_ID, notification);
        if (running == null) { // the work ended before the service came up
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        live = this;
        if (awake == null) {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            awake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AGVN::Unpack");
            awake.setReferenceCounted(false);
            awake.acquire(AWAKE_MS);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (awake != null && awake.isHeld()) awake.release();
        awake = null;
        if (live == this) live = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
