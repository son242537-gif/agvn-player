/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.content.Context;

/**
 * "Máy bố, bố biết" on the heat warning: no heat warning, neither {@link GameSessionGuard}'s dialog nor
 * {@link AgvnHeatWatch}'s bar, for {@link #QUIET_MS} on this phone. A Mali-G610 player's phone reported a severe
 * thermal status at 36–38 °C, so the dialog came at every start, while the game was still loading, and the player
 * could not get into the game. The session log still gets each heat reading.
 */
final class AgvnHeatAck {
    static final long QUIET_MS = 24 * 60 * 60 * 1000L;
    private static final String PREFS = "agvn_heat", ACK_AT = "ackAt";

    private AgvnHeatAck() {}

    /** Within {@link #QUIET_MS} after {@code ackAtMs}; a clock set back counts as not. */
    static boolean quiet(long ackAtMs, long nowMs) {
        return ackAtMs > 0 && nowMs >= ackAtMs && nowMs - ackAtMs < QUIET_MS;
    }

    static boolean quiet(Context ctx) {
        long ackAt = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(ACK_AT, 0);
        return quiet(ackAt, System.currentTimeMillis());
    }

    static void acknowledge(Context ctx) {
        long now = System.currentTimeMillis();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(ACK_AT, now).apply();
        AgvnSessionLog.event("Người chơi chọn \"Máy bố, bố biết\": 24 giờ tới không báo nóng");
    }
}
