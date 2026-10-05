/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Shortcut;

import java.util.List;
import java.util.Properties;

/**
 * At the library, after a game ended badly: "what happened, what you can do", with one button per fix. A fix changes
 * the game's settings and starts it again. Asked once: the problem is forgotten as soon as it is shown.
 */
public final class AgvnDoctorDialog {
    private AgvnDoctorDialog() {}

    /**
     * When the library (or Big Picture) comes to the front: sessions Android ended and a "Chạy nhẹ" game that ended
     * are read off the UI thread (a runner that asked to restart is let end first), then {@link #showIfPending}.
     */
    public static void checkAsync(Activity a) {
        Context app = a.getApplicationContext();
        new Thread(() -> {
            AgvnSessionLog.finishPending(app);
            AgvnDoctorStore.awaitRelaunch(app);
            AgvnLightDoctor.check(app);
            a.runOnUiThread(() -> showIfPending(a));
        }, "AgvnDoctor").start();
    }

    /**
     * At the library: first a game the player asked to start again ("Mở lại game ngay"), else the problem a game left,
     * once. Only while {@code a} is in front: Big Picture over the library asks instead. Call on the UI thread.
     */
    public static void showIfPending(Activity a) {
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        if (a instanceof LifecycleOwner && !((LifecycleOwner) a).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
        AgvnDoctorStore.Entry again = AgvnDoctorStore.take(a, AgvnDoctorStore.RELAUNCH);
        Shortcut restart = again == null || System.currentTimeMillis() - again.time > AgvnDoctorStore.RELAUNCH_MS
                ? null : AgvnRelaunch.find(a, again.container, again.shortcut);
        if (restart != null) {
            AgvnRelaunch.start(a, restart);
            return;
        }
        AgvnDoctorStore.Entry e = AgvnDoctorStore.take(a, AgvnDoctorStore.PENDING);
        if (e == null || System.currentTimeMillis() - e.time > AgvnDoctor.KILL_RECENT_MS) return; // a day old: moved on
        Shortcut s = AgvnRelaunch.find(a, e.container, e.shortcut);
        AgvnProblemCatalog.Finding f = AgvnDoctor.catalog(a).finding(e.problem, e.params);
        if (s != null && f != null) show(a, s, f);
    }

    static void show(Activity a, Shortcut s, AgvnProblemCatalog.Finding f) {
        Properties state = AgvnGoodConfig.load(a, s);
        List<AgvnFixes.Fix> fixes = AgvnFixes.applicable(a, s, f, state);
        int pad = dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(a, 8), pad, 0);
        TextView game = text(a, a.getString(R.string.agvn_doctor_game, s.name), 13, false);
        game.setAlpha(0.7f);
        box.addView(game);
        box.addView(text(a, f.cause(), 15, false));
        if (!fixes.isEmpty()) box.addView(text(a, a.getString(R.string.agvn_doctor_lead), 15, true));
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(f.title())
                .setView(scroll)
                .setNegativeButton(R.string.agvn_doctor_later, null)
                .create();
        boolean restarts = false;
        for (AgvnFixes.Fix fix : fixes) {
            Button b = new Button(a);
            b.setAllCaps(false);
            b.setText(fix.label);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                if (AgvnFixApply.apply(a, s, fix, state)) {
                    Toast.makeText(a, a.getString(R.string.agvn_doctor_fixed, fix.label), Toast.LENGTH_LONG).show();
                    AgvnRelaunch.start(a, s);
                }
            });
            box.addView(b);
            restarts |= fix.changesGame();
        }
        if (restarts) {
            TextView hint = text(a, a.getString(R.string.agvn_doctor_hint), 13, false);
            hint.setAlpha(0.7f);
            box.addView(hint);
        }
        dialog.show();
    }

    private static TextView text(Activity a, String s, int sp, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(a, 6), 0, dp(a, 6));
        return t;
    }

    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
