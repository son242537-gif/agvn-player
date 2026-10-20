/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

import androidx.preference.PreferenceManager;

/**
 * How long each game's last slow start took, for the slow-start line ({@link AgvnStartupProgress}: "Đang khởi động
 * 0:25 · lần trước 1:58"), so the player knows how long to wait. A Unity game on a Mali phone spent 73 s loading its
 * code and about 1:40 before its first screen; its player quit the next start at 0:29, thinking it hung.
 */
final class AgvnStartTimes {
    /** Per game, by its shortcut file: the last slow start, in ms. */
    static final String PREFIX = "agvn_last_start_ms:";

    private AgvnStartTimes() {}

    /** The last start of {@code game} when it was slow, else 0. */
    static long last(Context ctx, String game) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getLong(PREFIX + game, 0);
    }

    /** The game is up after {@code elapsedMs}. */
    static void remember(Context ctx, String game, long elapsedMs) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putLong(PREFIX + game, kept(elapsedMs)).apply();
    }

    /** A slow start's time is kept; a start under {@link AgvnStartupProgress#QUIET_MS} (no line shown) clears it. */
    static long kept(long elapsedMs) {
        return elapsedMs >= AgvnStartupProgress.QUIET_MS ? elapsedMs : 0;
    }
}
