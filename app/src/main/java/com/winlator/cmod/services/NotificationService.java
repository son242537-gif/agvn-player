package com.winlator.cmod.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;

public class NotificationService extends Service {
    private static boolean isRunning = false;
    public static PowerManager.WakeLock wakeLock = null;

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // AGVN: no stop without the notification permission: a foreground service still keeps a game running (Android
        // 13+ then shows it in the Task Manager only), and games start it themselves now (agvn/AgvnKeepAlive)
        if (intent == null) intent = new Intent(this, getClass());
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(MainActivity.NOTIFICATION_CHANNEL_ID) == null)
            manager.createNotificationChannel(new NotificationChannel(MainActivity.NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW));
        String game = intent.getStringExtra(com.winlator.cmod.agvn.AgvnKeepAlive.EXTRA_GAME);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.winlator_mark)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(game != null ? getString(R.string.agvn_notification_game, game) : getString(R.string.agvn_notification_running))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true);

        Notification notification = builder.build();
        startForeground(notificationId(), notification);
        isRunning = true;

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Winlator::KeepAlive");
        return START_NOT_STICKY;
    }

    /** AGVN: the notification's id; the Ren'Py process's copy of this service has its own. */
    protected int notificationId() {
        return MainActivity.NOTIFICATION_ID;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        com.winlator.cmod.agvn.AgvnSessionLog.removedByPlayer(this); // AGVN: the player's end, not Android's
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
