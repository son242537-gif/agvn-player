/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.services.NotificationService;

/**
 * Keeps the process of a running game at foreground-service level while the player is in another app, with the
 * notification "Đang chạy <game>". Without it HyperOS killed Lo Se Sb in Wine (SIGKILL, importance 300) as the player
 * switched to the file manager during its 10-minute start: the service was only started from the library, and only
 * with the notification permission. A foreground service keeps only the process that hosts it, so the Ren'Py process
 * (":renpy") has its own, {@link Renpy}, and so have the RGSS process, {@link Rgss}, and the Godot one, {@link Godot}.
 */
public final class AgvnKeepAlive {
    public static final String EXTRA_GAME = "agvn_game";

    /** The same service in the Ren'Py process, with a notification of its own. */
    public static final class Renpy extends NotificationService {
        @Override
        protected int notificationId() {
            return MainActivity.NOTIFICATION_ID + 1;
        }
    }

    /** The same service in the RPG Maker XP/VX/VX Ace process (":rgss"). */
    public static final class Rgss extends NotificationService {
        @Override
        protected int notificationId() {
            return MainActivity.NOTIFICATION_ID + 2;
        }
    }

    /** The same service in the Godot process (":godot"). */
    public static final class Godot extends NotificationService {
        @Override
        protected int notificationId() {
            return MainActivity.NOTIFICATION_ID + 3;
        }
    }

    private AgvnKeepAlive() {}

    /** A game starts in this process (Wine, HTML); null when it has ended and the library is back. */
    public static void start(Context context, String game) {
        start(context, NotificationService.class, game);
    }

    /** A Ren'Py game starts in the Ren'Py process. */
    static void startRenpy(Context context, String game) {
        start(context, Renpy.class, game);
    }

    /** An RPG Maker XP/VX/VX Ace game starts in the RGSS process. */
    static void startRgss(Context context, String game) {
        start(context, Rgss.class, game);
    }

    /** A Godot game starts in the Godot process. */
    static void startGodot(Context context, String game) {
        start(context, Godot.class, game);
    }

    private static void start(Context context, Class<?> service, String game) {
        try {
            Intent intent = new Intent(context, service);
            if (game != null && !game.isEmpty()) intent.putExtra(EXTRA_GAME, game);
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException e) {
            Log.w("AGVN", "keep-alive service not started", e); // the game runs anyway, only less protected
        }
    }
}
