/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;

import java.util.Collections;

/**
 * Battery saver ("Tiết kiệm pin") lowers the CPU's clocks on most phones, which an emulated game feels first. Before a
 * game starts from the library, the player is asked: open battery saver's settings, or play anyway (game-problems.json
 * "power-save"). A game started another way says so once with a toast. Nothing is changed on the phone.
 */
public final class AgvnPowerSave {
    /** A toast this soon after the question would say it twice. */
    private static final long ASKED_MS = 60_000;
    private static volatile long askedAtMs = -ASKED_MS;

    private AgvnPowerSave() {}

    static boolean on(Context context) {
        try {
            PowerManager pm = context.getSystemService(PowerManager.class);
            return pm != null && pm.isPowerSaveMode();
        } catch (RuntimeException e) {
            return false; // no answer: as if off
        }
    }

    /** Runs {@code play} at once with battery saver off; with it on, after the player chose to play anyway. */
    static void ask(Activity activity, Runnable play) {
        AgvnProblemCatalog.Finding f = on(activity) ? AgvnDoctor.catalog(activity).finding("power-save", Collections.emptyMap()) : null;
        if (f == null) {
            play.run();
            return;
        }
        askedAtMs = SystemClock.uptimeMillis();
        new AlertDialog.Builder(activity)
                .setTitle(f.title())
                .setMessage(f.cause())
                .setPositiveButton(R.string.agvn_fix_power_save,
                        (d, w) -> AgvnFixApply.open(activity, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)))
                .setNegativeButton(R.string.agvn_fix_play_anyway, (d, w) -> play.run())
                .show();
    }

    public static void warn(Activity activity) {
        if (SystemClock.uptimeMillis() - askedAtMs < ASKED_MS) return; // the player was just asked
        if (on(activity)) Toast.makeText(activity, R.string.agvn_power_save_on, Toast.LENGTH_LONG).show();
    }
}
