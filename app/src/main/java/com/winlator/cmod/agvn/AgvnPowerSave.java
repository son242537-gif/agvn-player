/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

import java.util.Collections;

/**
 * Battery saver ("Tiết kiệm pin") lowers the CPU's clocks on most phones, which an emulated game feels first. Before a
 * game starts from the library, the player is asked: open battery saver's settings, or play anyway (game-problems.json
 * "power-save"). A game started another way says so once with a toast. Nothing is changed on the phone. After "Chơi
 * luôn" neither comes back for {@link #QUIET_MS}: a player who keeps battery saver on was asked before every start.
 */
public final class AgvnPowerSave {
    static final long QUIET_MS = 24 * 60 * 60 * 1000L;
    private static final String PREFS = "agvn_power_save", ASKED_AT = "askedAt";

    private AgvnPowerSave() {}

    static boolean on(Context context) {
        try {
            PowerManager pm = context.getSystemService(PowerManager.class);
            return pm != null && pm.isPowerSaveMode();
        } catch (RuntimeException e) {
            return false; // no answer: as if off
        }
    }

    /** Within {@link #QUIET_MS} after the player's "Chơi luôn" at {@code askedAtMs}; a clock set back counts as not. */
    static boolean playedAnyway(long askedAtMs, long nowMs) {
        return askedAtMs > 0 && nowMs >= askedAtMs && nowMs - askedAtMs < QUIET_MS;
    }

    private static boolean playedAnyway(Context context) {
        long askedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(ASKED_AT, 0);
        return playedAnyway(askedAt, System.currentTimeMillis());
    }

    /** Runs {@code play} at once with battery saver off; with it on, after the player chose to play anyway. */
    static void ask(Activity activity, Runnable play) {
        AgvnProblemCatalog.Finding f = on(activity) && !playedAnyway(activity)
                ? AgvnDoctor.catalog(activity).finding("power-save", Collections.emptyMap()) : null;
        if (f == null) {
            play.run();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(f.title())
                .setMessage(f.cause() + "\n\n" + activity.getString(R.string.agvn_power_save_quiet))
                .setPositiveButton(R.string.agvn_fix_power_save,
                        (d, w) -> AgvnFixApply.open(activity, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)))
                .setNegativeButton(R.string.agvn_fix_play_anyway, (d, w) -> {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putLong(ASKED_AT, System.currentTimeMillis()).commit(); // no question or toast for a day
                    play.run();
                })
                .show();
    }

    public static void warn(Activity activity) {
        if (playedAnyway(activity)) return; // the player chose to play with it on
        if (on(activity)) Toast.makeText(activity, R.string.agvn_power_save_on, Toast.LENGTH_LONG).show();
    }
}
