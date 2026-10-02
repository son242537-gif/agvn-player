/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.os.PowerManager;
import android.widget.Toast;

import com.winlator.cmod.R;

/**
 * Battery saver ("Tiết kiệm pin") lowers the CPU's clocks on most phones, which an emulated game feels first: when it is
 * on as a game starts, the player is told, once per start. Nothing is changed on the phone.
 */
public final class AgvnPowerSave {
    private AgvnPowerSave() {}

    public static void warn(Activity activity) {
        try {
            PowerManager pm = activity.getSystemService(PowerManager.class);
            if (pm != null && pm.isPowerSaveMode()) Toast.makeText(activity, R.string.agvn_power_save_on, Toast.LENGTH_LONG).show();
        } catch (RuntimeException ignored) {
            // no answer: no message
        }
    }
}
